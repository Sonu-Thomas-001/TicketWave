# TicketWave - Functional Specification Document (FSD)

**Version**: 1.0  
**Date**: March 2, 2026  
**Status**: Production Ready  
**Technology Stack**: Spring Boot 3.4.3 | Java 17 | PostgreSQL | Redis | JWT

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [System Architecture Overview](#system-architecture-overview)
3. [Core Use Cases](#core-use-cases)
4. [API Contracts](#api-contracts)
5. [State Transitions](#state-transitions)
6. [Database Schema](#database-schema)
7. [Error Codes & Handling](#error-codes--handling)
8. [Sequence Diagrams](#sequence-diagrams)
9. [Non-Functional Requirements](#non-functional-requirements)

---

## Executive Summary

**TicketWave** is a production-grade, modular monolithic ticket booking system specializing in travel and event ticketing. The system implements:

- **Transactional bookings** with payment integration
- **Distributed seat hold mechanism** using Redis (10-minute TTL)
- **Webhook-driven payment confirmation** for asynchronous processing
- **Idempotent operations** for retry safety
- **CQRS-friendly domain boundaries** (7 modules)
- **Pessimistic & optimistic locking** for concurrency control
- **Complete audit trail** with admin overrides
- **80%+ test coverage** (JUnit 5 + Mockito)

**Core Business Flow**: User selects event/route → Holds seats → Creates payment intent → Completes payment via webhook → System confirms booking & generates PNR.

---

## System Architecture Overview

### Module Structure

```
TicketWave (Modular Monolith)
├── user/          (User & Passenger management)
├── catalog/       (Routes, Schedules, Seats, Search & Pricing)
├── booking/       (Booking lifecycle, seat holds, events)
├── payment/       (Payment intents, webhooks, gateways)
├── refund/        (Refunds, cancellation policies)
├── inventory/     (Seat availability tracking)
├── admin/         (Admin operations, overrides)
└── common/        (Security, logging, audit, exceptions)
```

### Key Technologies

| Component | Technology | Purpose |
|-----------|-----------|---------|
| **API Layer** | Spring REST | RESTful endpoints, validation |
| **Persistence** | Spring Data JPA + Hibernate | ORM, optimistic locking |
| **Relational DB** | PostgreSQL | System of record |
| **Distributed Lock** | Redis + Redisson | Seat holds, distributed locking |
| **Auth** | JWT (JJWT 0.12.6) | Stateless authentication |
| **Streaming** | Spring Events | Internal event publishing |
| **Testing** | JUnit 5 + Mockito | Unit/integration tests |

### Deployment Architecture

```
┌─────────────────────────────────────────────────────┐
│              Client (Web/Mobile)                    │
│                    (JWT Bearer)                     │
└────────────┬─────────────────────────────────────────┘
             │
             │ HTTPS
             ▼
┌──────────────────────────────────────────────────────┐
│         Spring Boot Application Server               │
│  ┌─────────────────────────────────────────────┐   │
│  │ API Controllers (Authentication, Validation)│   │
│  │ - BookingController                         │   │
│  │ - PaymentWebhookController                  │   │
│  │ - RefundController                          │   │
│  │ - AuditController                           │   │
│  └─────────────────────────────────────────────┘   │
└────┬──────────────────────┬───────┬────────────────┘
     │                      │       │
 ┌───▼────────┐     ┌───────▼────┐ │
 │PostgreSQL  │     │   Redis    │ │
 │ (Relational)│     │(Distributed)│ │
 └────────────┘     └────────────┘ │
                                     │ Webhook
                                     ▼
                            Payment Gateway
                            (Stripe/PayPal)
```

---

## Core Use Cases

### UC-1: Search & Browse Events

**Actor**: Customer (Unauthenticated OK)  
**Trigger**: User opens app, wants to find travel/event  
**Precondition**: None  
**Postcondition**: User sees available schedules with pricing

**Main Flow**:
1. Client calls `GET /api/v1/schedules/search?origin=NYC&destination=LA&departureDate=2026-03-10`
2. System queries schedules matching criteria
3. System calculates pricing (base fare + dynamic pricing)
4. Return schedule list with available seat counts

**Alternative Flows**:
- A1: No matching schedules → Return empty list with 200 OK
- A2: Invalid date (past) → Return 422 Unprocessable Entity

**Related Endpoints**: 
- `GET /api/v1/schedules/search` 
- `GET /api/v1/schedules/{scheduleId}` (Details)

---

### UC-2: Hold Seats

**Actor**: Customer (Authenticated)  
**Trigger**: User selects seats and clicks "Confirm Selection"  
**Precondition**: Schedule exists, seats available  
**Postcondition**: Seats locked for 10 minutes in Redis, hold token issued

**Main Flow**:
1. Client authenticates with JWT
2. Client calls `POST /api/v1/seat-holds` with `{scheduleId, seatIds}`
3. System acquires distributed lock (Redisson)
4. System verifies seats available (status = "AVAILABLE")
5. System creates hold in Redis with TTL=600s
6. Return `{holdToken, expiresAt}`

**Alternative Flows**:
- A1: Seat already held → Return 409 Conflict `{"error": "SEAT_ALREADY_HELD"}`
- A2: Lock acquisition timeout → Return 408 Request Timeout `{"error": "LOCK_CONTENTION"}`
- A3: Hold TTL expires during operation → Return 409 `{"error": "HOLD_EXPIRED"}`

**Related Endpoints**:
- `POST /api/v1/seat-holds` (Create hold)
- `GET /api/v1/seat-holds/{seatId}` (Check hold status)
- `POST /api/v1/seat-holds/{seatId}/extend` (Extend TTL)
- `DELETE /api/v1/seat-holds/{seatId}` (Release hold)

---

### UC-3: Create Booking (with Payment Intent)

**Actor**: Customer (Authenticated)  
**Trigger**: User confirms seat selection and passenger details  
**Precondition**: Seats held, passengers validated  
**Postcondition**: Booking created (status=PENDING_PAYMENT), payment intent issued

**Main Flow**:
1. Client calls `POST /api/v1/bookings` with:
   - `scheduleId`
   - `seatHolds[]` (with holdToken)
   - `passengerBookings` (map of seatId → passengerId)
   - `X-Idempotency-Key` header (optional)
2. System validates:
   - Holds still valid
   - Passengers exist
   - No duplicate bookings (idempotency check)
3. System creates Booking entity (status=INITIATED)
4. System calls PaymentIntentService.createIntent
5. System transitions booking to PENDING_PAYMENT
6. System emits `BookingInitiatedEvent`
7. Return `{bookingId, pnr, paymentIntent, paymentLink}`

**Alternative Flows**:
- A1: Idempotency key in cache → Return cached response 200 OK
- A2: Hold expired → Return 409 `{"error": "HOLD_EXPIRED"}`, retry holding
- A3: Seat now booked by another user → Return 409 `{"error": "SEAT_BOOKED"}`
- A4: Payment intent creation failed → Return 503 `{"error": "PAYMENT_SERVICE_DOWN"}`

**Related Endpoints**:
- `POST /api/v1/bookings` (Create booking + payment intent)
- `GET /api/v1/bookings/{bookingId}` (Get booking details)
- `GET /api/v1/bookings/pnr/{pnr}` (Lookup by PNR)
- `DELETE /api/v1/bookings/{bookingId}` (Cancel booking)

---

### UC-4: Payment Confirmation (Webhook)

**Actor**: Payment Gateway (Stripe/PayPal)  
**Trigger**: User completes payment on payment gateway  
**Precondition**: Payment intent exists, awaiting confirmation  
**Postcondition**: Booking confirmed, seats booked, PNR generated

**Main Flow**:
1. Payment gateway (external) calls `POST /api/v1/webhooks/payment/confirmed`
2. System validates webhook signature
3. System checks idempotency (event ID not previously processed)
4. System retrieves PaymentIntent by intentId
5. System transitions PaymentIntent to CONFIRMED
6. System retrieves Booking from PaymentIntent
7. System confirms booking:
   - Creates BookingItem records (per seat)
   - Updates Seats to status=BOOKED
   - Generates PNR (secure random)
   - Releases seat holds from Redis
   - Updates schedule available_seats
8. System emits `BookingConfirmedEvent`
9. Return 200 OK immediately (processing continues asynchronously)

**Alternative Flows**:
- A1: Event already processed → Return 200 OK (idempotency)
- A2: Booking not found → Return 404, log error
- A3: Hold already released → Rollback booking, mark as FAILED
- A4: Redis down → Database constraint prevents double-booking, graceful degradation

**Related Endpoints**:
- `POST /api/v1/webhooks/payment/confirmed` (Success callback)
- `POST /api/v1/webhooks/payment/failed` (Failure callback)
- `POST /api/v1/webhooks/payment/status/{intentId}` (Status polling)

---

### UC-5: Payment Failure (Webhook)

**Actor**: Payment Gateway  
**Trigger**: User fails payment or payment times out  
**Precondition**: Payment intent exists  
**Postcondition**: Booking marked FAILED, holds released, seats unlocked

**Main Flow**:
1. Payment gateway calls `POST /api/v1/webhooks/payment/failed`
2. System validates signature, checks idempotency
3. System retrieves PaymentIntent, marks FAILED
4. System retrieves Booking, transitions to FAILED
5. System releases seat holds from Redis
6. System logs failure reason (insufficient_funds, timeout, rejected, etc.)
7. System emits `BookingFailedEvent`
8. Return 200 OK

**Notes**: Booking marked FAILED allows user to retry with different payment method.

---

### UC-6: Initiate Refund

**Actor**: Customer (via API) or Admin (dashboard)  
**Trigger**: User cancelled booking or admin initiates refund  
**Precondition**: Booking is CONFIRMED, Payment is CONFIRMED  
**Postcondition**: Refund request created (status=INITIATED), awaiting approval

**Main Flow**:
1. Customer/Admin calls `POST /api/v1/refunds` with:
   - `bookingId`
   - `refundAmount`
   - `reason` (cancellation, refund request, etc.)
2. System retrieves Booking, validates status=CONFIRMED
3. System retrieves Payment, validates status=CONFIRMED
4. System checks CancellationPolicy (user eligible?)
5. System creates Refund entity (status=INITIATED)
6. System emits `RefundInitiatedEvent`
7. Return `{refundId, refundAmount, refundStatus}`

**Rules**:
- Full refund within cancellation window (configurable: typically 24 hours)
- Partial refund after window (configurable: 50% deduction)
- No refund if < 4 hours before departure

**Related Endpoints**:
- `POST /api/v1/refunds` (Initiate refund)
- `GET /api/v1/refunds/{refundId}` (Get refund details)
- `POST /api/v1/refunds/{refundId}/approve` (ADMIN: approve)
- `POST /api/v1/refunds/{refundId}/reject` (ADMIN: reject)

---

### UC-7: Process Refund (Admin)

**Actor**: Admin (Authenticated with ADMIN role)  
**Trigger**: Refund approval in dashboard  
**Precondition**: Refund status=INITIATED  
**Postcondition**: Refund processed with payment gateway, status=COMPLETED or FAILED

**Main Flow**:
1. Admin calls `POST /api/v1/refunds/{refundId}/approve`
2. System validates refund status=INITIATED
3. System checks max concurrent refunds (default: 100)
4. System transitions refund to APPROVED
5. System transitions refund to PROCESSING
6. System calls payment gateway refund API
7. If gateway confirms: transition to COMPLETED, create RefundLedger entry
8. If gateway fails: transition to FAILED, log error
9. System emits `RefundCompletedEvent` or `RefundFailedEvent`
10. Return refund response with new status

**Related Endpoints**:
- `POST /api/v1/refunds/{refundId}/approve`
- `POST /api/v1/refunds/{refundId}/reject`
- `POST /api/v1/refunds/{refundId}/complete`
- `POST /api/v1/refunds/{refundId}/fail`

---

### UC-8: View Audit Logs (Admin)

**Actor**: Admin (ADMIN role)  
**Trigger**: Admin opens audit dashboard  
**Precondition**: Admin authenticated  
**Postcondition**: Paginated audit logs displayed

**Main Flow**:
1. Admin calls `GET /api/v1/admin/audit?page=0&size=20&sort=timestamp,desc`
2. System returns paginated AuditLog records with:
   - Entity type (Booking, Payment, Refund, etc.)
   - Action (CREATE, CONFIRM, APPROVE, REJECT, etc.)
   - User ID
   - Timestamp
   - HTTP method + status
   - Result (success/failure)
3. Admin can filter by entity, user, action type, date range

**Related Endpoints**:
- `GET /api/v1/admin/audit` (All logs)
- `GET /api/v1/admin/audit/user/{userId}` (By user)
- `GET /api/v1/admin/audit/entity/{entityType}/{entityId}` (By entity)
- `GET /api/v1/admin/audit/action/{action}` (By action type)
- `GET /api/v1/admin/audit/{auditId}` (Single log detail)
- `GET /api/v1/admin/audit/admin-overrides` (Admin actions only)
- `GET /api/v1/admin/audit/stats/overview` (Statistics)

---

## API Contracts

### Authentication

**Header**: `Authorization: Bearer <jwt_token>`

**JWT Payload**:
```json
{
  "sub": "user-uuid",
  "username": "john@example.com",
  "roles": ["USER", "ADMIN"],
  "iat": 1709462400,
  "exp": 1709548800
}
```

---

### Endpoints by Module

#### Booking Module

##### 1. Create Booking

**Request**:
```http
POST /api/v1/bookings HTTP/1.1
Authorization: Bearer <jwt>
X-Idempotency-Key: <uuid>
Content-Type: application/json

{
  "scheduleId": "550e8400-e29b-41d4-a716-446655440000",
  "seatHolds": [
    {
      "seatId": "550e8400-e29b-41d4-a716-446655440001",
      "userId": "550e8400-e29b-41d4-a716-446655440002",
      "holdToken": "abcd-1234-efgh-5678",
      "expiresAt": 1709548800000
    }
  ],
  "passengerBookings": {
    "550e8400-e29b-41d4-a716-446655440001": "550e8400-e29b-41d4-a716-446655440003"
  }
}
```

**Response** (Success - 201 Created):
```json
{
  "timestamp": "2026-03-02T10:30:00Z",
  "success": true,
  "message": "Booking created successfully",
  "data": {
    "bookingId": "550e8400-e29b-41d4-a716-446655440000",
    "bookingStatus": "PENDING_PAYMENT",
    "pnr": "TWAB12CD34",
    "totalAmount": 3000.00,
    "currency": "USD",
    "paymentIntent": {
      "intentId": "TW-1234567890-abc123",
      "amount": 3000.00,
      "status": "PENDING",
      "expiresAt": "2026-03-02T11:30:00Z"
    },
    "paymentLink": "https://payment-gateway.com/checkout?intentId=TW-1234567890-abc123",
    "bookedAt": "2026-03-02T10:30:00Z"
  }
}
```

**Response** (Idempotency Cache Hit - 200 OK):
```json
{
  "timestamp": "2026-03-02T10:30:00Z",
  "success": true,
  "message": "Booking already created (idempotency cache hit)",
  "data": {
    "bookingId": "550e8400-e29b-41d4-a716-446655440000",
    "bookingStatus": "PENDING_PAYMENT",
    "pnr": "TWAB12CD34",
    "totalAmount": 3000.00
  }
}
```

**Response** (Error - 409 Conflict):
```json
{
  "timestamp": "2026-03-02T10:30:00Z",
  "status": 409,
  "errorCode": "HOLD_EXPIRED",
  "message": "Seat hold expired. Please select seats again.",
  "path": "/api/v1/bookings",
  "correlationId": "req-uuid-123"
}
```

---

##### 2. Get Booking

**Request**:
```http
GET /api/v1/bookings/550e8400-e29b-41d4-a716-446655440000 HTTP/1.1
Authorization: Bearer <jwt>
```

**Response** (200 OK):
```json
{
  "timestamp": "2026-03-02T10:30:00Z",
  "success": true,
  "message": "Booking retrieved",
  "data": {
    "bookingId": "550e8400-e29b-41d4-a716-446655440000",
    "pnr": "TWAB12CD34",
    "bookingStatus": "CONFIRMED",
    "totalAmount": 3000.00,
    "user": {
      "userId": "550e8400-e29b-41d4-a716-446655440002",
      "fullName": "John Doe",
      "email": "john@example.com",
      "phoneNumber": "+1-555-0123"
    },
    "schedule": {
      "scheduleId": "550e8400-e29b-41d4-a716-446655440000",
      "eventName": "NYC to LA Flight",
      "eventType": "FLIGHT",
      "departureTime": "2026-03-05T08:00:00Z",
      "arrivalTime": "2026-03-05T11:00:00Z",
      "origin": "NYC",
      "destination": "LA",
      "baseFare": 1500.00
    },
    "bookingItems": [
      {
        "bookingItemId": "550e8400-e29b-41d4-a716-446655440010",
        "seatNumber": "12A",
        "seatType": "ECONOMY",
        "fare": 1500.00,
        "itemStatus": "CONFIRMED",
        "passenger": {
          "passengerId": "550e8400-e29b-41d4-a716-446655440003",
          "firstName": "John",
          "lastName": "Doe",
          "age": 35,
          "gender": "M"
        }
      }
    ],
    "payments": [
      {
        "paymentId": "550e8400-e29b-41d4-a716-446655440020",
        "amount": 3000.00,
        "paymentStatus": "CONFIRMED",
        "paymentMethod": "CARD",
        "transactionId": "txn_1234567890",
        "paymentDate": "2026-03-02T10:31:00Z"
      }
    ],
    "bookedAt": "2026-03-02T10:30:00Z",
    "cancelledAt": null
  }
}
```

---

##### 3. Cancel Booking

**Request**:
```http
DELETE /api/v1/bookings/550e8400-e29b-41d4-a716-446655440000 HTTP/1.1
Authorization: Bearer <jwt>
Content-Type: application/json

{
  "reason": "Changed plans"
}
```

**Response** (200 OK):
```json
{
  "timestamp": "2026-03-02T10:30:00Z",
  "success": true,
  "message": "Booking cancelled successfully",
  "data": {
    "bookingId": "550e8400-e29b-41d4-a716-446655440000",
    "bookingStatus": "CANCELLED",
    "cancelledAt": "2026-03-02T10:35:00Z"
  }
}
```

---

#### Payment Webhooks

##### 1. Payment Success Webhook

**Sender**: External Payment Gateway  
**Request**:
```http
POST /api/v1/webhooks/payment/confirmed HTTP/1.1
X-Gateway-Signature: sha256=...
Content-Type: application/json

{
  "eventId": "evt_123456789",
  "eventType": "payment.confirmed",
  "intentId": "TW-1234567890-abc123",
  "transactionId": "txn_gateway_123",
  "amount": 3000.00,
  "currency": "USD",
  "paymentMethod": "CARD",
  "timestamp": "2026-03-02T10:31:00Z"
}
```

**Response** (200 OK):
```json
{
  "timestamp": "2026-03-02T10:31:00Z",
  "success": true,
  "message": "Payment confirmed and booking processed",
  "data": {
    "intentId": "TW-1234567890-abc123",
    "status": "CONFIRMED",
    "bookingId": "550e8400-e29b-41d4-a716-446655440000",
    "pnr": "TWAB12CD34"
  }
}
```

---

##### 2. Payment Failure Webhook

**Request**:
```http
POST /api/v1/webhooks/payment/failed HTTP/1.1
X-Gateway-Signature: sha256=...
Content-Type: application/json

{
  "eventId": "evt_987654321",
  "eventType": "payment.failed",
  "intentId": "TW-1234567890-abc123",
  "failureReason": "insufficient_funds",
  "failureCode": "card_declined",
  "timestamp": "2026-03-02T10:31:00Z"
}
```

**Response** (200 OK):
```json
{
  "timestamp": "2026-03-02T10:31:00Z",
  "success": true,
  "message": "Payment failure processed",
  "data": {
    "intentId": "TW-1234567890-abc123",
    "status": "FAILED",
    "reason": "insufficient_funds",
    "retryable": true
  }
}
```

---

#### Refund Module

##### 1. Initiate Refund

**Request**:
```http
POST /api/v1/refunds HTTP/1.1
Authorization: Bearer <jwt>
Content-Type: application/json

{
  "bookingId": "550e8400-e29b-41d4-a716-446655440000",
  "refundAmount": 3000.00,
  "reason": "User cancellation",
  "notes": "Optional admin notes"
}
```

**Response** (201 Created):
```json
{
  "timestamp": "2026-03-02T10:30:00Z",
  "success": true,
  "message": "Refund initiated",
  "data": {
    "refundId": "550e8400-e29b-41d4-a716-446655440050",
    "bookingId": "550e8400-e29b-41d4-a716-446655440000",
    "refundAmount": 3000.00,
    "refundStatus": "INITIATED",
    "reason": "User cancellation",
    "createdAt": "2026-03-02T10:30:00Z"
  }
}
```

---

##### 2. Approve Refund (Admin)

**Request**:
```http
POST /api/v1/refunds/550e8400-e29b-41d4-a716-446655440050/approve HTTP/1.1
Authorization: Bearer <jwt>
Content-Type: application/json

{
  "approvalReason": "Meets cancellation policy"
}
```

**Response** (200 OK):
```json
{
  "timestamp": "2026-03-02T10:31:00Z",
  "success": true,
  "message": "Refund approved",
  "data": {
    "refundId": "550e8400-e29b-41d4-a716-446655440050",
    "refundStatus": "APPROVED",
    "refundAmount": 3000.00,
    "approvedAt": "2026-03-02T10:31:00Z"
  }
}
```

**Workflow Continues**:
1. System transitions APPROVED → PROCESSING
2. System calls payment gateway refund API
3. On success: PROCESSING → COMPLETED
4. On failure: PROCESSING → FAILED

---

#### Audit Module

##### 1. Get All Audit Logs

**Request**:
```http
GET /api/v1/admin/audit?page=0&size=20&sort=timestamp,desc HTTP/1.1
Authorization: Bearer <jwt>
```

**Response** (200 OK):
```json
{
  "timestamp": "2026-03-02T10:30:00Z",
  "success": true,
  "message": "Audit logs retrieved",
  "data": {
    "content": [
      {
        "auditId": "audit-uuid-1",
        "userId": "user-uuid-1",
        "entityType": "Booking",
        "entityId": "booking-uuid-1",
        "action": "CONFIRM",
        "previousValue": "{\"status\": \"PENDING_PAYMENT\"}",
        "newValue": "{\"status\": \"CONFIRMED\"}",
        "httpMethod": "POST",
        "endpoint": "/api/v1/webhooks/payment/confirmed",
        "httpStatus": 200,
        "timestamp": "2026-03-02T10:31:00Z",
        "durationMillis": 245,
        "isAdminOverride": false,
        "source": "API",
        "correlationId": "req-uuid-123"
      }
    ],
    "pageable": {
      "pageNumber": 0,
      "pageSize": 20,
      "totalElements": 1500,
      "totalPages": 75
    }
  }
}
```

---

##### 2. Get Audit by Entity

**Request**:
```http
GET /api/v1/admin/audit/entity/Booking/550e8400-e29b-41d4-a716-446655440000?page=0&size=10 HTTP/1.1
Authorization: Bearer <jwt>
```

**Response**: [Same structure as above, filtered by entity]

---

## State Transitions

### Booking State Machine

```
┌──────────────────────────────────────────────────────────────┐
│                     INITIATED                                │
│ (Created, hold validated, payment intent created)            │
└────────────────┬─────────────────────────┬─────────────────────┘
                 │                         │
        (Payment OK)                 (Hold Expired)
                 │                         │
                 ▼                         ▼
    ┌──────────────────────┐     ┌─────────────────┐
    │ PENDING_PAYMENT      │     │    FAILED       │
    │ (Awaiting confirmation)│    │ (Terminal)      │
    │ TTL: 1 hour          │     └─────────────────┘
    └──────────┬───────────┘
               │
      ┌────────┴──────────┐
      │                   │
(Webhook✓)          (Webhook✗)
      │                   │
      ▼                   ▼
┌──────────────┐   ┌────────────┐
│ CONFIRMED    │   │  FAILED    │
│ (Terminal)   │   │(Terminal)  │
└──────┬───────┘   └────────────┘
       │
   (Cancellation)
       │
       ▼
┌──────────────┐
│  CANCELLED   │
│ (Terminal)   │
└──────────────┘
```

**Status Enum**:
```java
public enum BookingStatus {
    INITIATED,        // Initial state
    PENDING_PAYMENT,  // Awaiting payment confirmation
    CONFIRMED,        // Booking confirmed, seats booked
    FAILED,          // Booking failed, seats released
    CANCELLED        // User cancelled (after confirmation)
}
```

**Transition Rules**:
```java
INITIATED
  ├─→ PENDING_PAYMENT (if hold valid + payment intent created)
  └─→ FAILED (if hold expired or no holds)

PENDING_PAYMENT
  ├─→ CONFIRMED (webhook: payment succeeded)
  └─→ FAILED (webhook: payment failed)

CONFIRMED
  └─→ CANCELLED (user initiates cancellation)

FAILED, CANCELLED → Terminal (no further transitions)
```

---

### Payment Status Machine

```
┌─────────────┐
│   PENDING   │ (Initial, awaiting gateway response)
└──────┬──────┘
       │
   ┌───┴────┐
   │        │
(Success) (Failure)
   │        │
   ▼        ▼
CONFIRMED FAILED
(Terminal)(Terminal)
```

**Transitions**:
- PENDING → CONFIRMED (webhook/polling returns success)
- PENDING → FAILED (webhook returns failure)
- Idempotency: If already CONFIRMED/FAILED, return existing state (no update)

---

### Refund Status Machine

```
┌──────────────┐
│  INITIATED   │ (Created, pending approval)
└──────┬───────┘
       │
   ┌───┴───┐
   │       │
(Approve) (Reject)
   │       │
   ▼       ▼
APPROVED  REJECTED
   │      (Terminal)
   │
   ▼
PROCESSING (Calling payment gateway)
   │
   ├─→ COMPLETED (Success)
   └─→ FAILED (Gateway error)

Terminal: COMPLETED, REJECTED, FAILED
```

**State Methods** (in RefundStatus enum):
```java
public boolean canTransitionTo(RefundStatus target) {
    return switch (this) {
        case INITIATED -> target == APPROVED || target == REJECTED;
        case APPROVED -> target == PROCESSING;
        case PROCESSING -> target == COMPLETED || target == FAILED;
        case REJECTED, COMPLETED, FAILED -> false;
    };
}
```

---

## Database Schema

### Entity Relationship Diagram

```
User (1) ──────────── (N) Booking
  ├── id (PK)             ├── id (PK)
  ├── email (UNIQUE)      ├── userId (FK) → User
  ├── passwordHash        ├── scheduleId (FK) → Schedule
  └── roles               ├── pnr (UNIQUE)
                          ├── bookingStatus
                          ├── totalAmount
Passenger (1) ─────── (N) BookingItem
  ├── id (PK)             ├── id (PK)
  ├── userId (FK)         ├── bookingId (FK) → Booking
  ├── firstName           ├── passengerId (FK) → Passenger
  ├── lastName            ├── seatId (FK) → Seat
  └── documentNumber      ├── fare
                          └── itemStatus
Routes (1) ─────────── (N) Schedule
  ├── id (PK)             ├── id (PK)
  ├── origin              ├── routeId (FK) → Route
  ├── destination         ├── departureTime
  └── distance            ├── arrivalTime
                          ├── baseFare
Schedule (1) ───────── (N) Seat
  ├── id (PK)             ├── id (PK)
  ├── routeId (FK)        ├── scheduleId (FK) → Schedule
  ├── eventId (FK)        ├── seatNumber (UNIQUE per schedule)
  └── availableSeats      ├── class
                          ├── seatStatus (AVAILABLE|HELD|BOOKED|BLOCKED)
Booking (1) ────────── (N) Payment
  ├── id (PK)             ├── id (PK)
  └── status              ├── bookingId (FK) → Booking
                          ├── transactionId (UNIQUE)
Payment (1) ────────── (N) Refund
  ├── id (PK)             ├── id (PK)
  ├── amount              ├── paymentId (FK) → Payment
  ├── status              ├── bookingId (FK) → Booking
  └── confirmedAt         ├── refundAmount
                          ├── refundStatus
                          └── gatewayResponse
```

### Core Tables

#### Users Table
```sql
CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(50),
    last_name VARCHAR(50),
    phone_number VARCHAR(20),
    roles VARCHAR(50),  -- JSON array: ["USER", "ADMIN"]
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_users_email ON users(email);
```

#### Bookings Table
```sql
CREATE TABLE bookings (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    schedule_id UUID NOT NULL REFERENCES schedules(id),
    pnr VARCHAR(10) UNIQUE NOT NULL,
    booking_status VARCHAR(20) NOT NULL,  -- INITIATED, PENDING_PAYMENT, CONFIRMED, FAILED
    total_amount DECIMAL(10,2) NOT NULL,
    booked_at TIMESTAMP NOT NULL,
    cancelled_at TIMESTAMP,
    version BIGINT,  -- Optimistic locking
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
CREATE UNIQUE INDEX idx_bookings_pnr ON bookings(pnr);
CREATE INDEX idx_bookings_user_status ON bookings(user_id, booking_status);
CREATE INDEX idx_bookings_schedule_status ON bookings(schedule_id, booking_status);
```

#### Seats Table
```sql
CREATE TABLE seats (
    id UUID PRIMARY KEY,
    schedule_id UUID NOT NULL REFERENCES schedules(id),
    seat_number VARCHAR(10) NOT NULL,
    class VARCHAR(20) NOT NULL,  -- ECONOMY, BUSINESS, FIRST
    seat_status VARCHAR(20) DEFAULT 'AVAILABLE',  -- AVAILABLE, HELD, BOOKED, BLOCKED
    version BIGINT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    UNIQUE(schedule_id, seat_number)
);
CREATE INDEX idx_seats_schedule_status ON seats(schedule_id, seat_status);
```

#### Payments Table
```sql
CREATE TABLE payments (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES bookings(id),
    transaction_id VARCHAR(50) UNIQUE NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    payment_method VARCHAR(20),  -- CARD, UPI, NET_BANKING, WALLET
    payment_status VARCHAR(20),  -- PENDING, CONFIRMED, FAILED, REFUNDED
    gateway_response TEXT,
    confirmed_at TIMESTAMP,
    version BIGINT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
CREATE UNIQUE INDEX idx_payments_transaction_id ON payments(transaction_id);
CREATE INDEX idx_payments_booking_status ON payments(booking_id, payment_status);
```

#### PaymentIntents Table
```sql
CREATE TABLE payment_intents (
    id UUID PRIMARY KEY,
    booking_id UUID UNIQUE NOT NULL REFERENCES bookings(id),
    intent_id VARCHAR(100) UNIQUE NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(25) DEFAULT 'PENDING',  -- PENDING, CONFIRMED, FAILED, EXPIRED, CANCELLED
    expires_at TIMESTAMP NOT NULL,
    confirmed_at TIMESTAMP,
    idempotency_key VARCHAR(100),
    retry_count INT DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_payment_intents_status ON payment_intents(status);
CREATE INDEX idx_payment_intents_booking_id ON payment_intents(booking_id);
CREATE INDEX idx_payment_intents_expires_at ON payment_intents(expires_at);
```

#### Refunds Table
```sql
CREATE TABLE refunds (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES bookings(id),
    payment_id UUID NOT NULL REFERENCES payments(id),
    refund_id VARCHAR(50) UNIQUE NOT NULL,
    refund_amount DECIMAL(10,2) NOT NULL,
    refund_status VARCHAR(20),  -- INITIATED, APPROVED, REJECTED, PROCESSING, COMPLETED, FAILED
    reason VARCHAR(500),
    gateway_response TEXT,
    processed_at TIMESTAMP,
    version BIGINT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_refunds_booking_status ON refunds(booking_id, refund_status);
CREATE INDEX idx_refunds_refund_id ON refunds(refund_id);
```

#### Audit Logs Table
```sql
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    user_id VARCHAR(100),
    entity_type VARCHAR(50),
    entity_id VARCHAR(100),
    action VARCHAR(50),
    previous_value TEXT,
    new_value TEXT,
    http_method VARCHAR(10),
    endpoint VARCHAR(500),
    http_status INT,
    timestamp TIMESTAMP NOT NULL,
    error_message VARCHAR(500),
    duration_millis BIGINT,
    is_admin_override BOOLEAN DEFAULT FALSE,
    source VARCHAR(50),
    correlation_id UUID,
    created_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_audit_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_user ON audit_logs(user_id);
CREATE INDEX idx_audit_timestamp ON audit_logs(timestamp DESC);
CREATE INDEX idx_audit_action ON audit_logs(action);
```

---

## Error Codes & Handling

### HTTP Status Codes

| Code | Meaning | Example Cause |
|------|---------|---------------|
| **200** | OK | Successful GET, POST with existing result (idempotency), webhook processing |
| **201** | Created | Successful resource creation (booking, refund) |
| **204** | No Content | Successful DELETE |
| **400** | Bad Request | Missing required field, invalid format |
| **401** | Unauthorized | Missing/invalid JWT token |
| **403** | Forbidden | User lacks permission (e.g., non-admin accessing admin endpoint) |
| **404** | Not Found | Resource doesn't exist (booking, seat, payment) |
| **409** | Conflict | Seat already booked, hold expired, invalid state transition |
| **422** | Unprocessable | Validation error (date in past, negative amount) |
| **429** | Too Many Requests | Rate limit exceeded |
| **503** | Service Unavailable | Payment gateway down, database unavailable |

---

### Error Response Format

```json
{
  "timestamp": "2026-03-02T10:30:00Z",
  "status": 409,
  "errorCode": "SEAT_ALREADY_HELD",
  "message": "Seat is already held by another user. Please select a different seat.",
  "path": "/api/v1/seat-holds",
  "correlationId": "req-uuid-123"
}
```

### Domain Error Codes

#### Booking Errors

| Code | Status | Meaning | Retryable |
|------|--------|---------|-----------|
| HOLD_EXPIRED | 409 | Seat hold TTL exceeded | Yes (re-hold) |
| HOLD_INVALID | 409 | Hold token doesn't match | No |
| SEAT_NOT_AVAILABLE | 409 | Seat status ≠ AVAILABLE or HELD | No |
| SEAT_ALREADY_HELD | 409 | Another user holds seat | No |
| BOOKING_NOT_FOUND | 404 | Invalid booking ID | No |
| INVALID_BOOKING_STATUS | 409 | Cannot perform action in current status | No |
| UNAUTHORIZED_BOOKING_ACCESS | 403 | User doesn't own booking | No |
| INCORRECT_PASSENGER | 422 | Passenger not assigned to seat | No |

#### Payment Errors

| Code | Status | Meaning | Retryable |
|------|--------|---------|-----------|
| PAYMENT_INTENT_EXPIRED | 409 | Intent TTL (1 hour) exceeded | Yes (recreate) |
| PAYMENT_INTENT_NOT_FOUND | 404 | Intent ID invalid | No |
| INVALID_PAYMENT_STATUS | 409 | Cannot confirm payment in current status | No |
| PAYMENT_GATEWAY_ERROR | 503 | External gateway error | Yes (retry) |
| PAYMENT_TIMEOUT | 504 | Gateway didn't respond in time | Yes (retry) |
| INSUFFICIENT_FUNDS | 402 | Card declined | Yes (different card) |
| INVALID_PAYMENT_METHOD | 422 | Unsupported payment method | No |

#### Refund Errors

| Code | Status | Meaning | Retryable |
|------|--------|---------|-----------|
| REFUND_NOT_FOUND | 404 | Refund ID invalid | No |
| REFUND_NOT_ELIGIBLE | 409 | Outside refund window/policy | No |
| INVALID_REFUND_STATUS | 409 | Cannot perform action in current status | No |
| REFUND_AMOUNT_MISMATCH | 422 | Requested amount > eligible amount | No |
| REFUND_PROCESSING_FAILED | 503 | Gateway refund failed | Yes (retry) |
| MAX_CONCURRENT_REFUNDS_EXCEEDED | 429 | Too many concurrent refunds | Yes (retry later) |

#### Audit/Admin Errors

| Code | Status | Meaning |
|------|--------|---------|
| INSUFFICIENT_PERMISSIONS | 403 | Not ADMIN role |
| INVALID_QUERY_PARAMETER | 422 | Bad filter/sort param |
| ENTITY_NOT_FOUND | 404 | Entity ID invalid |

---

### Exception Mapping (GlobalExceptionHandler)

```java
@ExceptionHandler(MethodArgumentNotValidException.class)
→ 400 Bad Request (VALIDATION_ERROR)

@ExceptionHandler(ConstraintViolationException.class)
→ 400 Bad Request (CONSTRAINT_VIOLATION)

@ExceptionHandler(ResourceNotFoundException.class)
→ 404 Not Found (RESOURCE_NOT_FOUND)

@ExceptionHandler(ConflictException.class)
→ 409 Conflict (CONFLICT)

@ExceptionHandler(AccessDeniedException.class)
→ 403 Forbidden (FORBIDDEN)

@ExceptionHandler(Exception.class)
→ 500 Internal Server Error (INTERNAL_ERROR)
```

---

## Sequence Diagrams

### SD-1: Complete Booking Flow

```
┌──────────┐        ┌─────────────┐        ┌───────────────┐
│  Client  │        │  TicketWave │        │ Payment Gw    │
└────┬─────┘        └──────┬──────┘        └───────┬───────┘
     │                     │                       │
     │ POST /bookings      │                       │
     │ (seatHolds, etc)    │                       │
     │────────────────────>│                       │
     │                     │ [1] Validate holds    │
     │                     │ [2] Create booking    │
     │                     │ [3] Create intent     │
     │                     │ [4] Generate PNR      │
     │                     │                       │
     │ 201 Created         │                       │
     │ {paymentIntent,     │                       │
     │  paymentLink}       │                       │
     │<────────────────────│                       │
     │                     │                       │
     │ (User opens link)   │                       │
     │ ────────────────────────────────────────── │
     │                     │                       │
     │                     │ Webhook: payment.confirmed
     │                     │<──────────────────────
     │                     │                       │
     │                     │ [5] Update intent     │
     │                     │ [6] Confirm booking   │
     │                     │ [7] Book seats        │
     │                     │ [8] Release holds     │
     │                     │                       │
     │                     │ 200 OK                │
     │                     │─────────────────────>│
     │                     │                       │
     │ Polling (optional)  │                       │
     │ GET /bookings/{id}  │                       │
     │────────────────────>│                       │
     │ 200 CONFIRMED       │                       │
     │<────────────────────│                       │
     
Timeline:
  T=0s:   User creates booking
  T=1s:   Payment Intent created, link generated
  T=2s:   User opens payment link
  T=3-10s: User enters card details
  T=11s:  Payment completed
  T=12s:  Webhook arrives, booking confirmed
```

---

### SD-2: Payment Failure Flow

```
┌──────────┐        ┌─────────────┐        ┌───────────────┐
│  Client  │        │  TicketWave │        │ Payment Gw    │
└────┬─────┘        └──────┬──────┘        └───────┬───────┘
     │                     │                       │
     │ POST /bookings      │                       │
     │────────────────────>│                       │
     │ 201 PENDING_PAYMENT │                       │
     │<────────────────────│                       │
     │                     │                       │
     │ (User enters card)  │                       │
     │ ────────────────────────────────────────── │
     │                     │                       │
     │                     │ Webhook: payment.failed
     │                     │<──────────────────────
     │                     │                       │
     │                     │ [1] Update intent     │
     │                     │ [2] Mark booking FAILED
     │                     │ [3] Release holds     │
     │                     │ [4] Return seats      │
     │                     │                       │
     │                     │ 200 OK                │
     │                     │─────────────────────>│
     │                     │                       │
     │ Polling/Refresh    │                       │
     │ GET /bookings/{id}  │                       │
     │────────────────────>│                       │
     │ 200 FAILED          │                       │
     │<────────────────────│                       │
     │                     │                       │
     │ Retry: POST /bookings (new attempt)
     │────────────────────>│                       │
     
Result: Booking marked FAILED, seats released, user can retry
```

---

### SD-3: Refund Processing

```
┌──────────┐        ┌─────────────┐        ┌───────────────┐
│  Client/ │        │  TicketWave │        │ Payment Gw    │
│  Admin   │        │             │        │               │
└────┬─────┘        └──────┬──────┘        └───────┬───────┘
     │                     │                       │
     │ POST /refunds       │                       │
     │─────────────────────>│                       │
     │ {bookingId,         │                       │
     │  reason}            │                       │
     │                     │ [1] Validate policy   │
     │                     │ [2] Create refund     │
     │ 201 INITIATED       │  (status=INITIATED)   │
     │<─────────────────────│                       │
     │                     │                       │
     │                     │                       │
     │ POST /refunds/{id}/approve (ADMIN)
     │─────────────────────>│                       │
     │                     │ [3] Transition:       │
     │                     │  INITIATED→APPROVED   │
     │                     │  APPROVED→PROCESSING  │
     │                     │                       │
     │                     │ Call refund API       │
     │                     │───────────────────────>
     │                     │                       │
     │                     │ Refund confirmed      │
     │                     │<───────────────────────
     │                     │                       │
     │                     │ [4] Transition:       │
     │                     │  PROCESSING→COMPLETED│
     │ 200 COMPLETED       │ [5] Create ledger     │
     │<─────────────────────│                       │
     
Alternative: Admin rejects during INITIATED phase
     │ POST /refunds/{id}/reject
     │─────────────────────>│
     │                     │ Transition to REJECTED
     │ 200 REJECTED        │
     │<─────────────────────│
```

---

### SD-4: Seat Hold Extension (Race Condition Fix)

```
┌──────────────────────────────────────────────────────────────┐
│ Problem: Classic TOCTOU (Time-of-check-time-of-use)         │
│                                                               │
│ Thread 1: Read TTL = 500s  (Check)                           │
│ Thread 2: Read TTL = 500s  (Check)  ← Race!                 │
│                                                               │
│ Network delay...                                              │
│                                                               │
│ Thread 1: Set TTL = 1200s  (Act)                             │
│ Thread 2: Set TTL = 1200s  (Act)  ← Both succeeded!          │
│ Meanwhile: Key expired! ↓  ← Oops, key gone!                 │
│                                                               │
└──────────────────────────────────────────────────────────────┘

Solution: Lua Script (Atomic on Redis Server)

┌─────────────────────────────────────────────────────┐
│ BEFORE: Java Code (Multiple Round-Trips)           │
│                                                     │
│ Thread A: SELECT ttl FROM redis   ─┐               │
│           Wait...                  │ Network delay │
│           UPDATE ttl               │ (Race)        │
│                                    │               │
│ Thread B: SELECT ttl FROM redis   ─┤               │
│           Wait...                  │               │
│           UPDATE ttl               │               │
│                                    │               │
│ Result: Both succeeded (bad!)    ─┘               │
└─────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────┐
│ AFTER: Lua Script (Single Atomic Execute)           │
│                                                      │
│ Thread A: Send Lua script to Redis  ──┐             │
│           (Atomic execution)          │ No race:    │
│           Returns 1 (success)         │ Lua runs    │
│                                       │ alone on    │
│ Thread B: Send Lua script to Redis  ──┤ server     │
│           (Waits for A to finish)     │             │
│           Returns 0 (key not found)   │             │
│           ..→ Refresh key fails       │             │
│                                       │             │
│ Result: Only one succeeds (good!)  ──┘             │
└──────────────────────────────────────────────────────┘

Lua Script:
  IF key EXISTS AND value MATCHES:
    EXPIRE key WITH new_ttl
    RETURN 1 (success)
  ELSE:
    RETURN 0 (key missing or token mismatch)
```

---

### SD-5: Audit Logging Flow

```
┌──────────────┐        ┌─────────────────┐        ┌──────────────┐
│  API Client  │        │   Controller    │        │    Service   │
└──────┬───────┘        └────────┬────────┘        └────────┬─────┘
       │                         │                          │
       │ POST /bookings          │                          │
       │ (with X-User-Id header) │                          │
       │────────────────────────>│                          │
       │                         │                          │
       │                         │ [1] Generate correlation_id
       │                         │     (MDC context)        │
       │                         │                          │
       │                         │ confirmBooking()          │
       │                         │─────────────────────────>│
       │                         │                          │
       │                         │  [2] Business logic      │
       │                         │  [3] Save booking        │
       │                         │  [4] Emit events         │
       │                         │                          │
       │                         │ Event: BookingConfirmed  │
       │                         │<─────────────────────────│
       │                         │                          │
       │ 201 Created             │                          │
       │<────────────────────────│                          │
       │                         │                          │
       │                [Async Task]                        │
       │                         │                          │
       │          ┌──────────────────────────┐              │
       │          │ AuditLogger Thread      │              │
       │          │ (EntityAuditListener)   │              │
       │          │ [5] Read MDC context    │              │
       │          │ [6] Extract entity diff │              │
       │          │ [7] Determine action    │              │
       │          │ [8] Save AuditLog entry │              │
       │          │ [9] Populate fields:    │              │
       │          │     - userId            │              │
       │          │     - entityType        │              │
       │          │     - action            │              │
       │          │     - previousValue     │              │
       │          │     - newValue          │              │
       │          │     - timestamp         │              │
       │          │     - correlationId     │              │
       │          │     - httpStatus        │              │
       │          │     - durationMillis    │              │
       │          └──────────────────────────┘              │
       │                                                    │
       │ (Later) Admin queries: GET /admin/audit            │
       │────────────────────────> [AuditLog returned]     │

Data Flow:
  1. HTTP Request → MDC.put("correlationId", uuid)
  2. Service method executes → ModificationListener detects changes
  3. Event emitted → AuditLogger subscribes
  4. AuditLog saved with all context
  5. MDC removed at request completion
```

---

## Non-Functional Requirements

### Performance

| Requirement | Target | Mechanism |
|-------------|--------|-----------|
| **Booking confirmation** | < 200ms P99 | JPA batch operations, JOIN FETCH queries |
| **Seat hold** | < 50ms P99 | Redis direct operations, distributed locking |
| **Search** | < 500ms P99 | Database indexes, pagination (limit 100) |
| **Refund gateway call** | < 5s timeout | Retry logic (3 attempts + exponential backoff) |
| **Payment webhook ingestion** | < 1s | Async processing, immediate 200 OK response |

### Scalability

- **Database**: Connection pooling (HikariCP max 20), read replicas optional
- **Redis**: Cluster mode for distributed holds (max 100 keys per second)
- **API**: Horizontal scaling via load balancer
- **Concurrency**: Optimistic locking on contention-prone entities (@Version)

### Reliability

- **Idempotency**: All mutations have idempotency key support
- **Retries**: Exponential backoff (100ms, 200ms, 400ms) for transient failures
- **Graceful Degradation**: Redis down → PostgreSQL uniqueness constraint prevents double-booking
- **Backup**: Database backups every 6 hours, disaster recovery plan in place

### Security

- **Authentication**: JWT tokens (HS256), 24-hour expiry
- **Authorization**: Role-based (@PreAuthorize("hasRole('ADMIN')")), ownership checks
- **Encryption**: TLS 1.3 for transport, sensitive fields encrypted at rest
- **Validation**: Input validation on all endpoints (Jakarta Bean Validation)
- **Rate Limiting**: Optional per-IP, per-user limiting
- **Audit Trail**: All state changes logged with user context

### Observability

- **Logging**: SLF4J with correlation IDs (MDC), structured JSON logs
- **Metrics**: Micrometer + Prometheus for business/technical metrics
- **Tracing**: OpenTelemetry (optional) for distributed tracing
- **Health Checks**: `/actuator/health` with Redis, database probes

### Compliance

- **Data Retention**: Audit logs retained for 1 year per policy
- **PII Handling**: Payment card data never stored (tokenized via gateway)
- **Refund Window**: Configurable refund deadline (24 hours default)
- **Cancellation Policy**: Business rules enforced in CancellationPolicyEngine

---

## Key Implementation Patterns

### 1. Idempotency Pattern

```
Request header: X-Idempotency-Key: <uuid>

Flow:
  1. Check IdempotencyKeyService cache
  2. If hit: Return cached response (same HTTP status + data)
  3. If miss: Execute operation, cache result with TTL (24 hours)
  4. Idempotency key → request fingerprint + outcome mapping
```

### 2. Event-Driven Booking Lifecycle

```
User Action → Emit Event → Multiple Listeners

Examples:
  - BookingInitiatedEvent → [EmailService, AuditLogger, MetricsCollector]
  - PaymentConfirmedEvent → [BookingService, NotificationService, AnalyticsService]
  - BookingFailedEvent → [SeatHoldService, AuditLogger, AlertingService]
```

### 3. Distributed Locking

```
Redisson distributed lock for seat selection:
  
  seatLock = redissonClient.getLock("seat:lock:" + seatId)
  
  try {
    // Wait max 2 seconds for lock, hold for 5 seconds
    isLocked = seatLock.tryLock(2, 5, TimeUnit.SECONDS)
    
    // Critical section: validate & hold seat
    
  } finally {
    if (seatLock.isHeldByCurrentThread()) {
      seatLock.unlock()
    }
  }
```

### 4. Optimistic Locking

```
Entity field: @Version private long version;

Conflict resolution:
  
  // ON UPDATE, Hibernate checks IF version matches
  // If NOT: throws OptimisticLockingFailureException
  
  catch (OptimisticLockingFailureException ex) {
    // Booking was modified by another transaction
    // Retry or return 409 Conflict
  }
```

### 5. Webhook Idempotency

```
Process payment webhook:
  1. Extract eventId from webhook
  2. Query: SELECT * FROM webhook_events WHERE event_id = ?
  3. If exists: Return 200 (already processed)
  4. If new: Process, save webhook_events row, return 200
  5. Webhook delivery guaranteed by gateway (retry on non-200)
```

---

## Deployment & Monitoring

### Environment Variables

```bash
# Database
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres.example.com:5432/ticketwave
SPRING_DATASOURCE_USERNAME=ticketwave_user
SPRING_DATASOURCE_PASSWORD=<encrypted>

# Redis
SPRING_DATA_REDIS_HOST=redis.example.com
SPRING_DATA_REDIS_PORT=6379

# JWT
JWT_SECRET=<256-bit encoded secret>
JWT_EXPIRATION_HOURS=24

# Payment Gateway
PAYMENT_GATEWAY_URL=https://api.stripe.com
PAYMENT_GATEWAY_KEY=sk_live_...
PAYMENT_WEBHOOK_SECRET=whsec_...
```

### Health Checks

```
GET /actuator/health

Response:
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "redis": {"status": "UP"},
    "livenessState": {"status": "LIVE"},
    "readinessState": {"status": "READY"}
  }
}
```

---

## Testing Strategy

### Test Coverage Goals
- **Unit Tests**: 80%+ line coverage
- **Integration Tests**: Critical workflows (booking creation, payment, refund)
- **Concurrency Tests**: Race condition scenarios (pessimistic lock, Lua scripts)
- **Contract Tests**: API contract validation

### Example Test Suite

```java
class BookingServiceTest {
  // Booking lifecycle: INITIATED → PENDING_PAYMENT → CONFIRMED
  @Test void testPendingPaymentTransition();
  
  // Concurrency: Multiple threads holding same seat
  @Test void testSeatHoldConcurrency();
  
  // Idempotency: Duplicate requests with same key
  @Test void testIdempotencyKeyDeduplication();
  
  // Error handling: Hold expired during confirmation
  @Test void testExpiredHoldHandling();
  
  // Webhooks: Payment confirmed after 1 hour timeout
  @Test void testWebhookAfterTimeout();
}
```

---

**End of Functional Specification Document (FSD)**

For implementation details, refer to:
- CODE_REVIEW_REPORT.md (Code quality assessment)
- BOOKING_LIFECYCLE.md (Detailed state machine)
- PAYMENT_REFUND_MODULE.md (Payment & refund details)
- AUDIT_LOGGING_MODULE.md (Audit trail implementation)
