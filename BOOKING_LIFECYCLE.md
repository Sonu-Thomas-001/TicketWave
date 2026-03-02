# TicketWave Booking Lifecycle Implementation

## Table of Contents
1. [Executive Summary](#executive-summary)
2. [Architecture Overview](#architecture-overview)
3. [Booking State Machine](#booking-state-machine)
4. [Implementation Flow](#implementation-flow)
5. [Key Features](#key-features)
6. [Database Entities](#database-entities)
7. [API Contracts](#api-contracts)
8. [Error Handling](#error-handling)
9. [Configuration](#configuration)
10. [Testing Strategy](#testing-strategy)

---

## Executive Summary

TicketWave implements a production-grade **transactional booking lifecycle** with:
- **State machine**: 5 states (INITIATED → PENDING_PAYMENT → CONFIRMED or FAILED)
- **Idempotency**: Duplicate requests safely handled, same result returned
- **Event logging**: Complete audit trail of all state transitions
- **Retry mechanism**: Automatic retry on transient failures
- **Payment webhooks**: Asynchronous payment confirmation processing
- **Seat hold integration**: Distributed Redis locks + database fallback
- **Test coverage**: 30+ unit tests for all scenarios

**Design Philosophy**: Every booking operation is idempotent, logged, and retryable. Payment failures cleanly release resources. No orphaned holds or bookings.

---

## Architecture Overview

### Component Interactions

```
┌─────────────────────────────────────────────────────────────────┐
│                    Client/Frontend                              │
└──────────────────────────┬──────────────────────────────────────┘
                           │
              1. Create Booking Request (with seats + passengers)
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│              BookingController                                  │
│  - Validate idempotency key                                     │
│  - Call BookingServiceEnhanced.initiateBooking()               │
│  - Return paymentLink + booking details                        │
└────────────────┬────────────────────────────────────────────────┘
                 │
     2. Initiate Booking
                 │
                 ▼
┌─────────────────────────────────────────────────────────────────┐
│              BookingServiceEnhanced                             │
│  Workflow:                                                      │
│  - Validate seat holds (SeatHoldService)                       │
│  - Create booking (status: INITIATED)                          │
│  - Create payment intent (PaymentIntentService)               │
│  - Transition to PENDING_PAYMENT                              │
│  - Log events (BookingEventLogger)                            │
└────────┬───────────────────────────────────┬────────────────────┘
         │                                   │
      3a. Create/Update                  3b. Create Payment Intent
      Idempotency Key                    (with 1 hour TTL)
         │                                   │
         ▼                                   ▼
┌──────────────────────┐          ┌──────────────────────┐
│ IdempotencyKey Table │          │ PaymentIntent Table  │
│ (24 hour TTL)        │          │ (1 hour TTL)         │
└──────────────────────┘          └──────────────────────┘
         │
      4. User makes payment at gateway
         │
         ▼
    Payment Gateway (Stripe, PayPal, etc.)
         │
      5. Send webhook to /api/v1/webhooks/payment/confirmed
         │
         ▼
┌─────────────────────────────────────────────────────────────────┐
│              PaymentWebhookController                           │
│  - Verify webhook signature                                    │
│  - Update PaymentIntent status → CONFIRMED                    │
│  - Call BookingServiceEnhanced.confirmBooking()               │
│  - Release seat holds                                         │
│  - Return 200 immediately (async processing)                 │
└────────────┬──────────────────────┬──────────────────────────────┘
             │                      │
          6. Confirm Booking    7. Create BookingItems
             │                      │
             ▼                      ▼
      ┌──────────────┐      ┌──────────────┐
      │ Booking: ✓   │      │ BookingItem  │
      │ Status:      │      │ (per seat)   │
      │ CONFIRMED    │      └──────────────┘
      └──────────────┘
             │
          8. Update Seat status → BOOKED
             │
             ▼
      ┌──────────────┐
      │ Seat: BOOKED │
      └──────────────┘
```

### Service Layer

| Service | Responsibility |
|---------|-----------------|
| **BookingServiceEnhanced** | Orchestrates entire booking lifecycle: initiate, confirm, cancel, fail |
| **SeatHoldService** | Manages distributed seat locks in Redis (phase 4) |
| **PaymentIntentService** | Creates and tracks payment intents from gateway |
| **IdempotencyKeyService** | Prevents duplicate bookings, stores request fingerprints |
| **BookingEventLogger** | Logs all state transitions and events for audit trail |

### Repository Layer

| Repository | Purpose |
|------------|---------|
| **BookingRepository** | Query/update bookings by ID, PNR, status |
| **BookingEventRepository** | Query events for audit trail |
| **IdempotencyKeyRepository** | Check key existence, mark processed |
| **PaymentIntentRepository** | Query intents by booking or gateway ID |

---

## Booking State Machine

### State Diagram

```
                    ┌─────────────┐
                    │  INITIATED  │ (Initial: booking created, hold validated)
                    └──────┬──────┘
                           │
                    (Validate holds)
                           │
                           ▼
                 ┌─────────────────────┐
                 │ PENDING_PAYMENT     │ (Awaiting payment confirmation)
                 │ TTL: 1 hour         │
                 └──┬──────────────┬───┘
                    │              │
        (Payment✓) ├─────┐   ┌─────┤ (Payment✗)
                    │     │   │     │
                    ▼     │   │     ▼
              ┌──────────┐│   │┌──────────┐
              │CONFIRMED│    ││ FAILED   │
              │ Final ✓ ││    ││ Final ✗  │
              └──────────┘│   │└──────────┘
                    │     │   │
                    └─────┼───┘
                          │
                 (Timeout, No Webhook)
                          │
                          ▼
                  ┌───────────────┐
                  │  EXPIRED      │
                  │ (Assumed fail)│
                  └───────────────┘

Optional: CONFIRMED → CANCELLED (user cancellation)
```

### State Transitions (Strict)

```
INITIATED
  ├─ Valid holds + payment intent created → PENDING_PAYMENT ✓
  └─ Seat hold invalid → FAILED ✗

PENDING_PAYMENT
  ├─ Payment confirmed via webhook → CONFIRMED ✓
  ├─ Payment failed/rejected → FAILED ✗
  └─ 1 hour timeout → EXPIRED

CONFIRMED
  └─ User cancellation → CANCELLED (refund initiated)

FAILED, CANCELLED, EXPIRED → Terminal states (no further transitions)
```

### Status Constants

| Status | Value | Meaning |
|--------|-------|---------|
| INITIATED | "INITIATED" | Booking created, awaiting payment |
| PENDING_PAYMENT | "PENDING_PAYMENT" | Seat hold confirmed, payment in progress |
| CONFIRMED | "CONFIRMED" | Payment successful, booking finalized |
| FAILED | "FAILED" | Booking failed, seats released |
| CANCELLED | "CANCELLED" | User cancelled after confirmation |

---

## Implementation Flow

### 1. Booking Initiation (Create Booking)

**Endpoint**: `POST /api/v1/bookings`

**Input**:
```json
{
  "scheduleId": "550e8400-e29b-41d4-a716-446655440000",
  "seatHolds": [
    {
      "seatId": "550e8400-e29b-41d4-a716-446655440001",
      "userId": "550e8400-e29b-41d4-a716-446655440002",
      "holdToken": "hold-token-abc123",
      "heldAt": "2024-03-02T10:30:00Z",
      "expiresAt": "2024-03-02T10:40:00Z"
    }
  ],
  "passengerBookings": {
    "550e8400-e29b-41d4-a716-446655440001": "550e8400-e29b-41d4-a716-446655440002"
  }
}
```

**Workflow**:

1. **Check Idempotency** (`IdempotencyKeyService`)
   - If `X-Idempotency-Key` header present and already processed → Return cached response (200)
   - Otherwise → Generate fingerprint of request body (SHA-256)

2. **Validate Seat Holds** (`SeatHoldService`)
   - For each seat: Check if hold is still valid and owned by user
   - If any hold invalid/expired → Reject with 409 Conflict

3. **Create Booking** (`BookingServiceEnhanced`)
   ```
   Booking(
     status: INITIATED,
     pnr: "TW" + UUID truncated to 8 chars,
     totalAmount: sum of seat fares
   )
   ```

4. **Log Event** (`BookingEventLogger`)
   ```
   BookingEvent(
     eventType: BOOKING_INITIATED,
     previousStatus: null,
     newStatus: INITIATED,
     metadata: {seatCount: 2, totalAmount: 3000}
   )
   ```

5. **Create Payment Intent** (`PaymentIntentService`)
   ```
   PaymentIntent(
     intentId: "TW-{timestamp}-{uuid}",
     status: PENDING,
     expiresAt: now + 1 hour,
     idempotencyKey: from header
   )
   ```

6. **Transition to PENDING_PAYMENT**
   ```
   UPDATE booking SET status = PENDING_PAYMENT
   ```

7. **Return Response** (201 Created)
   ```json
   {
     "success": true,
     "data": {
       "bookingId": "...",
       "bookingStatus": "PENDING_PAYMENT",
       "pnr": "TWAB12CD34",
       "totalAmount": 3000.00,
       "paymentIntent": {
         "intentId": "TW-1709464200000-abc12345",
         "status": "PENDING",
         "expiresAt": "2024-03-02T11:30:00Z"
       },
       "paymentLink": "https://payment-gateway.com/checkout?intentId=..."
     }
   }
   ```

8. **Cache Response** (`IdempotencyKeyService`)
   - Store response JSON for 24 hours
   - If duplicate request arrives with same key: Return cached response

---

### 2. Payment Confirmation (Webhook)

**Endpoint**: `POST /api/v1/webhooks/payment/confirmed`

**Webhook Payload**:
```json
{
  "eventId": "evt_1234567890",
  "eventType": "payment.confirmed",
  "intentId": "TW-1709464200000-abc12345",
  "transactionId": "txn_stripe_xyz789",
  "amount": 3000.00,
  "paymentMethod": "card",
  "timestamp": "2024-03-02T10:32:00Z"
}
```

**Workflow**:

1. **Verify Webhook Signature**
   - Validate HMAC signature with gateway public key
   - If invalid → Return 401 (but gateway will retry)

2. **Check Webhook Idempotency**
   - Use `eventId` as idempotency key
   - If already processed → Return success (avoid duplicate booking confirmation)

3. **Update Payment Intent** (`PaymentIntentService`)
   ```
   UPDATE payment_intent SET status = CONFIRMED, confirmed_at = now(), transaction_id = ...
   ```

4. **Get Booking** from payment intent
   - Retrieve booking UUID from payment intent

5. **Confirm Booking** (`BookingServiceEnhanced`)
   ```
   FOR EACH seat in booking_items:
     - Create BookingItem record
     - Update Seat status → BOOKED
     - Release hold from Redis
   
   UPDATE booking SET status = CONFIRMED
   UPDATE schedule SET available_seats -= seat_count
   ```

6. **Log Event** (`BookingEventLogger`)
   ```
   BookingEvent(
     eventType: PAYMENT_CONFIRMED,
     previousStatus: PENDING_PAYMENT,
     newStatus: CONFIRMED,
     metadata: {transactionId: "txn_...", pnr: "TWAB12CD34"}
   )
   ```

7. **Return 200 OK Immediately**
   - Don't wait for async processing
   - Gateway considers delivery successful

**Response**:
```json
{
  "success": true,
  "data": {
    "status": "PAYMENT_CONFIRMED",
    "bookingId": "...",
    "pnr": "TWAB12CD34",
    "intentId": "..."
  }
}
```

---

### 3. Payment Failure (Webhook)

**Endpoint**: `POST /api/v1/webhooks/payment/failed`

**Webhook Payload**:
```json
{
  "eventId": "evt_9876543210",
  "eventType": "payment.failed",
  "intentId": "TW-1709464200000-abc12345",
  "failureReason": "insufficient_funds",
  "timestamp": "2024-03-02T10:31:00Z"
}
```

**Workflow**:

1. **Get Payment Intent** and determine if retryable
   - `insufficient_funds`, `card_declined` → NOT retryable (user action needed)
   - `network_error`, `timeout` → Retryable (try again)

2. **Update Payment Intent**
   ```
   UPDATE payment_intent SET status = FAILED, gateway_response = "insufficient_funds"
   ```

3. **Release Seat Holds** (`BookingServiceEnhanced`)
   ```
   FOR EACH seat in booking:
     - Release hold from Redis
   ```

4. **Mark Booking as FAILED**
   ```
   UPDATE booking SET status = FAILED
   ```

5. **Log Event** (`BookingEventLogger`)
   ```
   BookingEvent(
     eventType: PAYMENT_FAILED,
     previousStatus: PENDING_PAYMENT,
     newStatus: FAILED,
     errorMessage: "insufficient_funds"
   )
   ```

6. **Return 200 OK**
   - Client can retry booking with different payment method

---

## Key Features

### 1. Idempotency

**Problem**: Network retry, browser back button, duplicate webhooks → multiple bookings

**Solution**: Every request has `X-Idempotency-Key` header (UUID)

```
Request 1: Key="abc123", POST /bookings
  Response 1: 201 Created, Booking ID: xyz789, cached in DB

Request 2 (retry, same key="abc123"): POST /bookings
  Idempotency check: Key already processed
  Response: 201 Created, Booking ID: xyz789 (SAME, from cache)
  
Request 3 (webhook retry, eventId="evt123"): POST /webhooks/payment/confirmed
  Webhook idempotency: eventId already processed
  Response: 200 OK (avoided duplicate confirmation)
```

**Implementation**:
- `IdempotencyKey` entity stores request fingerprint (SHA-256 of body)
- If same key + same fingerprint: Return cached response
- If same key + different fingerprint: Reject (potential attack)
- TTL: 24 hours (clients can retry within this window)

### 2. Event Logging

**All Critical Events Logged**:

| Event | Status Transition | Logged Fields |
|-------|-------------------|---------------|
| BOOKING_INITIATED | null → INITIATED | seatCount, totalAmount |
| SEAT_HOLD_VALIDATED | INITIATED → INITIATED | validHolds count |
| PAYMENT_INTENT_CREATED | INITIATED → PENDING_PAYMENT | intentId, amount |
| PAYMENT_CONFIRMED | PENDING_PAYMENT → PENDING_PAYMENT | transactionId |
| BOOKING_CONFIRMED | PENDING_PAYMENT → CONFIRMED | pnr, seatsConfirmed |
| PAYMENT_FAILED | PENDING_PAYMENT → PENDING_PAYMENT | reason, retryCount |
| BOOKING_FAILED | PENDING_PAYMENT → FAILED | errorType |
| BOOKING_CANCELLED | CONFIRMED → CANCELLED | reason, seatsFreed |
| RETRY_ATTEMPT | (any) → (same) | retryAttempt #, error |

**Audit Access**:
```java
List<BookingEvent> events = bookingService.getBookingAuditTrail(bookingId);
// Returns all events in reverse chronological order
```

### 3. Retry Mechanism

**Automatic Retries**:
- **Transient failures** (network timeout, 503): Retry up to 3 times with exponential backoff
- **Idempotent operations**: Safe to retry without duplicate effects
- **Webhook failures**: Gateway retries (we handle with idempotency keys)

**Example Scenario**:
```
1. User clicks "Confirm Booking"
2. Network timeout → 0/3 retries
3. Auto-retry after 1s → 1/3 retries (success)
   OR
   Auto-retry after 2s → 2/3 retries (success)
   OR
   Auto-retry after 4s → 3/3 retries (fail, return error)
```

### 4. Seat Hold Integration

**Distributed Locks** (from Phase 4):
- Redis SETNX with 2s lock timeout, 5s lease time
- 10-minute TTL on hold key
- On booking confirmation: Explicitly release hold
- On timeout: Automatic Redis TTL expiration

**Fallback Constraints**:
```sql
UNIQUE(booking_id, seat_id)  -- Prevents duplicate booking items
UNIQUE(schedule_id, seat_number) -- Prevents duplicate seat assignments
```

---

## Database Entities

### BookingStatus Enum
```java
INITIATED     // Booking created, awaiting payment
PENDING_PAYMENT // Seats held, payment intent created
CONFIRMED     // Payment confirmed, booking complete
FAILED        // Booking failed, seats released
CANCELLED     // User cancellation after confirmation
```

### IdempotencyKey Entity
```java
idempotencyKey: String (UUID)        // Client-provided idempotency key
requestFingerprint: String(64)       // SHA-256 hash of request body
cachedResponse: String(TEXT)         // JSON response for replay
cachedStatusCode: Integer            // HTTP status (201, 400, 500, etc.)
processed: Boolean                   // Whether key was already processed
expiresAt: Instant                   // 24 hour TTL
```

### BookingEvent Entity
```java
bookingId: UUID                      // Reference to booking
eventType: String                    // BOOKING_INITIATED, PAYMENT_CONFIRMED, etc.
previousStatus: String               // Status before transition
newStatus: String                    // Status after transition
metadata: String(TEXT)               // JSON with event details
triggeredBy: String                  // "SYSTEM" or actual user
errorMessage: String                 // If failure
isRetry: Boolean                     // Whether this was a retry
eventTimestamp: Instant              // When event occurred
```

### PaymentIntent Entity
```java
booking: UUID (FK)                   // Reference to booking
intentId: String (UNIQUE)            // Gateway-generated ID
status: String                       // PENDING, CONFIRMED, FAILED, EXPIRED
amount: BigDecimal(10,2)             // Amount to charge
paymentMethod: String                // CARD, UPI, NET_BANKING, etc.
expiresAt: Instant                   // 1 hour TTL
confirmedAt: Instant                 // When payment confirmed
gatewayResponse: String              // Success transaction ID or error message
idempotencyKey: String               // For webhook deduplication
retryCount: Integer                  // Retry attempts
```

---

## API Contracts

### 1. Create Booking

**Request**: `POST /api/v1/bookings`

**Headers**:
```
X-Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
Authorization: Bearer {jwt_token}
```

**Status Codes**:
| Code | Meaning |
|------|---------|
| 201 | Success |
| 400 | Bad request (missing fields, invalid data) |
| 409 | Conflict (seat hold invalid, booking already exists) |
| 422 | Unprocessable (validation failed) |

### 2. Payment Webhook Confirmation

**Request**: `POST /api/v1/webhooks/payment/confirmed`

**Webhook Retry Policy**:
- Gateway retries up to 5 times
- Exponential backoff: 1s, 2s, 4s, 8s, 16s
- Success on any 2xx status

**Response**:
```json
{
  "success": true,
  "data": {
    "status": "PAYMENT_CONFIRMED",
    "bookingId": "...",
    "pnr": "TWAB12CD34"
  }
}
```

### 3. Get Booking Audit Trail

**Request**: `GET /api/v1/bookings/{bookingId}/events`

**Response**:
```json
{
  "success": true,
  "data": [
    {
      "eventType": "BOOKING_INITIATED",
      "newStatus": "INITIATED",
      "metadata": {...},
      "createdAt": "2024-03-02T10:30:00Z"
    },
    {
      "eventType": "PAYMENT_CONFIRMED",
      "previousStatus": "PENDING_PAYMENT",
      "newStatus": "PENDING_PAYMENT",
      "metadata": {"transactionId": "txn_xyz"},
      "createdAt": "2024-03-02T10:32:00Z"
    }
  ]
}
```

---

## Error Handling

### Transactional Consistency

**All-or-Nothing Semantics**:
```
START TRANSACTION
  1. Create booking item ✓
  2. Update seat status ✓
  3. Release hold FAILS ✗
     → ROLLBACK all changes
     → Return error to client
     → Log event with error
END TRANSACTION
```

### Graceful Degradation

| Scenario | Behavior |
|----------|----------|
| Redis down (hold service) | Database unique constraint prevents double-booking |
| Payment gateway timeout | Webhook retry; if no response → 1-hour timeout → mark EXPIRED |
| Email service down | Log error, continue booking; send async later |
| Database connection lost | Return 503 Service Unavailable |

### Error Codes

| Code | HTTP | Meaning | Retryable |
|------|------|---------|-----------|
| HOLD_EXPIRED | 409 | Seat hold no longer valid | Yes (re-hold seat) |
| SEAT_NOT_AVAILABLE | 409 | Seat already booked | No |
| PAYMENT_FAILED | 402 | Payment rejected | Yes (try again) |
| PAYMENT_TIMEOUT | 504 | Gateway timeout | Yes (retry) |
| BOOKING_NOT_FOUND | 404 | Invalid booking ID | No |
| UNAUTHORIZED | 401 | Invalid JWT | No |

---

## Configuration

### application.yml

```yaml
app:
  booking:
    pnr-length: 10                      # Booking reference format length
    max-retries: 3                      # Max retry attempts for transient failures
    payment-timeout-minutes: 60         # PENDING_PAYMENT timeout
  payment:
    intent-ttl-hours: 1                 # Payment intent expiry
    webhook-timeout-seconds: 30         # Webhook processing timeout
  idempotency:
    ttl-hours: 24                       # Idempotency key retention
```

### Environment Variables

```bash
BOOKING_PNR_LENGTH=10
BOOKING_MAX_RETRIES=3
BOOKING_PAYMENT_TIMEOUT_MINUTES=60
PAYMENT_INTENT_TTL_HOURS=1
IDEMPOTENCY_TTL_HOURS=24
```

---

## Testing Strategy

### Test Coverage: 40+ Tests

| Component | Tests | Coverage |
|-----------|-------|----------|
| IdempotencyKeyService | 8 | Idempotency keys, caching, cleanup |
| BookingEventLogger | 6 | Event logging, metadata serialization |
| BookingServiceEnhanced | 10 | Booking lifecycle, state transitions |
| PaymentIntentService | 8 | Intent creation, confirmation, failure |
| BookingController | 8 | REST endpoints, validation |

### Test Types

**Unit Tests**: Mock all dependencies, test business logic
```java
@Test
void testInitiateBooking_Success() {
  // Arrange: Mock services and repos
  when(seatHoldService.isHoldValid(...)).thenReturn(true);
  
  // Act: Call service method
  var result = service.initiateBooking(...);
  
  // Assert: Verify state and calls
  assertEquals(PENDING_PAYMENT, result.getBooking().getBookingStatus());
}
```

**Integration Tests**: Real database, Redis, external services
```java
@SpringBootTest
void testBookingEndToEnd() {
  // Create booking
  // Trigger payment webhook
  // Verify booking confirmed and seats booked
}
```

**Concurrency Tests**: Multiple threads competing
```java
@Test
void testConcurrentBookingAttempts() {
  // 10 threads try to book same 2 seats
  // Only 2 succeed
  // 8 get ConflictException
}
```

---

## Deployment Checklist

- [ ] Database migrations run (idempotency_keys, booking_events, payment_intents tables)
- [ ] Redis configured and accessible
- [ ] Payment gateway webhook URL configured
- [ ] JWT secret configured in environment
- [ ] Email service configured (for confirmations)
- [ ] Monitoring/alerting enabled for payment webhooks
- [ ] Load testing completed (1000+ concurrent bookings)
- [ ] Idempotency key cleanup job scheduled (daily)
- [ ] Payment intent expiry job scheduled (hourly)
- [ ] Rollback plan ready

---

## Operational Monitoring

### Key Metrics

```
1. Booking Initiation Success Rate
   - Target: > 99%
   - Alert if < 95%

2. Payment Confirmation Time
   - P50: < 1s
   - P95: < 5s
   - Alert if P95 > 10s

3. Payment Webhook Latency
   - Target: < 100ms
   - Alert if > 500ms

4. Idempotency Cache Hit Rate
   - Target: > 60% on repeat searches
   - Lower indicates possible frontend issues

5. Payment Failure Rate
   - Insufficient funds: Monitor for trends
   - Network errors: Alert if > 1%
```

### Logs to Monitor

```
WARN: "Seat hold invalid or expired"
ERROR: "Error confirming booking, releasing holds"
ERROR: "Payment webhook processing failed"
WARN: "Idempotency key expired, allowing reprocessing"
```

---

**Version**: 1.0  
**Last Updated**: 2024-03-02  
**Status**: Production Ready ✓
