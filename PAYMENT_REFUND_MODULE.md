# Payment & Refund Module Documentation

## Overview

The Payment & Refund Module provides a comprehensive system for managing payment operations and issuing refunds based on flexible cancellation policies. The system supports:

- **Payment Lifecycle**: Creation, confirmation, failure, and refund tracking
- **Cancellation Policies**:  Time-based refund windows with prorated calculations
- **Financial Reconciliation**: Ledger entries for audit trails and accounting
- **Admin Overrides**: Manual refund adjustments with full audit trail
- **Payment Gateway Integration**: Stub implementation for testing, extensible for real providers

## Architecture

### Module Structure

```
payment/
├── api/                      # REST controllers
├── application/              # Business logic services
│   ├── PaymentService        # Payment operations
│   └── PaymentIntentService  # Payment intent tracking (existing)
├── domain/                   # Domain entities
│   ├── Payment              # Payment entity
│   └── PaymentIntent        # Payment intent entity (existing)
└── infrastructure/          # Data access & external integrations
    ├── PaymentRepository
    ├── PaymentIntentRepository
    └── PaymentProviderIntegrationStub

refund/
├── api/                      # REST endpoints (admin)
│   └── RefundController      # Refund management endpoints
├── application/              # Business services
│   ├── RefundService         # Core refund orchestration
│   ├── CancellationPolicyEngine  # Refund calculation logic
│   └── RefundLedgerService   # Financial reconciliation
├── domain/                   # Domain entities
│   ├── Refund               # Refund entity
│   ├── CancellationPolicy   # Policy definitions
│   ├── RefundLedger         # Ledger entries
│   ├── RefundStatus         # Status enum
│   └── (existing entities)
└── infrastructure/          # Data access
    ├── RefundRepository
    ├── CancellationPolicyRepository
    └── RefundLedgerRepository
```

## Domain Model

### Payment Entity

```
Payment {
  id: UUID
  booking: Booking (FK)
  transactionId: String (UNIQUE)
  amount: BigDecimal
  paymentMethod: String (CARD, UPI, NET_BANKING, WALLET)
  paymentStatus: String (PENDING, CONFIRMED, FAILED, REFUNDED)
  gatewayResponse: String
  confirmedAt: Instant
  version: long (optimistic locking)
}
```

**Status Transitions**:
- PENDING → CONFIRMED (successful payment)
- PENDING → FAILED (payment declined)
- CONFIRMED → REFUNDED (refund issued)

**Uniqueness Constraints**:
- `uk_payments_transaction_id`: Prevents duplicate transactions

### Refund Entity

```
Refund {
  id: UUID
  booking: Booking (FK)
  payment: Payment (FK)
  refundId: String (UNIQUE)
  refundAmount: BigDecimal
  refundStatus: String (INITIATED, APPROVED, REJECTED, PROCESSING, COMPLETED, FAILED)
  reason: String
  gatewayResponse: String
  processedAt: Instant
  version: long (optimistic locking)
}
```

**Status Transitions**:
```
INITIATED → APPROVED → PROCESSING → COMPLETED
                      ↓
                    FAILED
                      
INITIATED → REJECTED
```

**Uniqueness Constraints**:
- `uk_refunds_refund_id`: Prevents duplicate refund IDs

### CancellationPolicy Entity

```
CancellationPolicy {
  id: UUID
  policyId: String (UNIQUE)
  policyName: String
  description: String
  eventType: String (FLIGHT, MOVIE, CONCERT, SPORTS, etc.)
  
  fullRefundWindowHours: Integer       // 72 hours = full refund
  partialRefundWindowHours: Integer    // 24 hours = partial refund
  minimumCancellationFee: BigDecimal   // Minimum refund amount
  partialRefundPercentage: BigDecimal  // 50% = 50% refund in partial window
  processingFeePercentage: BigDecimal  // 2.5% deducted from all refunds
  allowNonRefundable: Boolean          // Allow zero refund policies
  
  isActive: Boolean
  effectiveFrom: Instant
  effectiveTo: Instant
}
```

### RefundLedger Entity (Financial Ledger)

```
RefundLedger {
  id: UUID
  ledgerEntryId: String (UNIQUE)
  refund: Refund (FK)
  bookingId: UUID
  
  entryType: String (REFUND_AMOUNT, POLICY_DEDUCTION, PROCESSING_FEE, 
                     PLATFORM_CHARGE, ADJUSTMENT, FINAL_AMOUNT)
  description: String
  amount: BigDecimal (positive = credit, negative = debit)
  balanceAfter: BigDecimal (running total)
  
  metadata: String (JSON, context-specific data)
  adjustmentReason: String (if admin override)
  adjustedByAdmin: String (user ID of admin)
  adjustedAt: Instant
}
```

**Ledger Entry Sequence**:
For a refund, entries are created in order:
1. **REFUND_AMOUNT**: Original amount (e.g., 1000)
2. **POLICY_DEDUCTION**: Amount deducted by policy (e.g., -100)
3. **PROCESSING_FEE**: Payment gateway fee (e.g., -22.50)
4. **FINAL_AMOUNT**: Net amount to refund (e.g., 877.50)
5. **ADJUSTMENT** (optional): Admin override adjustments

## Services

### PaymentService

Manages payment operations throughout their lifecycle.

**Key Methods**:

```java
// Create payment
Payment createPayment(Booking booking, BigDecimal amount, 
                     String transactionId, String paymentMethod)

// Confirm payment (idempotent)
Payment confirmPayment(UUID paymentId, String gatewayResponse)

// Fail payment
Payment failPayment(UUID paymentId, String failureReason, 
                   String gatewayResponse)

// Refund payment
Payment refundPayment(UUID paymentId, BigDecimal refundAmount)

// Query operations
Payment getPayment(UUID paymentId)
Payment getPaymentByTransactionId(String transactionId)
List<Payment> getPaymentsByBooking(Booking booking)
boolean isPaymentConfirmed(UUID paymentId)
BigDecimal getTotalPaidAmount(Booking booking)
```

### CancellationPolicyEngine

Calculates refund amounts based on cancellation policies and timing.

**Calculation Logic**:

```
if (hoursUntilEvent >= fullRefundWindowHours) {
  // Full refund minus processing fee
  refund = originalAmount * (100 - processingFeePercentage) / 100
} else if (hoursUntilEvent >= partialRefundWindowHours) {
  // Partial refund based on policy percentage
  refund = originalAmount * partialRefundPercentage / 100
  refund = refund * (100 - processingFeePercentage) / 100
  refund = max(refund, minimumCancellationFee)
} else {
  // Outside windows
  refund = allowNonRefundable ? 0 : originalAmount
}
```

**Key Method**:

```java
RefundCalculation calculateRefund(CancellationPolicy policy, 
                                 BigDecimal originalAmount,
                                 Instant eventStartTime, 
                                 Instant cancellationTime)
```

**RefundCalculation DTO**:
- `refundType`: FULL_REFUND | PARTIAL_REFUND | NO_REFUND
- `refundAmount`: Calculated refund
- `refundPercentage`: Percentage of original
- `policyDeduction`: Amount deducted by policy
- `processingFeeDeducted`: Payment processing fee
- `hoursUntilEvent`: Time calculation
- `reason`: Explanation for calculation

### RefundLedgerService

Manages financial transaction records for reconciliation and audit.

**Key Methods**:

```java
// Create various ledger entry types
RefundLedger createRefundAmountEntry(UUID refund, UUID booking, 
                                     BigDecimal amount)
RefundLedger createPolicyDeductionEntry(UUID refund, UUID booking, 
                                        BigDecimal deduction, 
                                        BigDecimal balanceBefore, 
                                        String reason)
RefundLedger createProcessingFeeEntry(UUID refund, UUID booking, 
                                      BigDecimal fee, 
                                      BigDecimal balanceBefore)
RefundLedger createFinalAmountEntry(UUID refund, UUID booking, 
                                    BigDecimal finalAmount)

// Admin adjustment
RefundLedger createAdjustmentEntry(UUID refund, UUID booking, 
                                   BigDecimal adjustment, 
                                   BigDecimal balanceBefore, 
                                   String reason, String adminId)

// Query operations
List<RefundLedger> getLedgerEntriesForRefund(UUID refundId)
List<RefundLedger> getLedgerEntriesForBooking(UUID bookingId)
BigDecimal getTotalRefundForBooking(UUID bookingId)
List<RefundLedger> getAdjustmentsByAdmin(String adminId)
```

### RefundService

Orchestrates the complete refund process from initiation through completion.

**Refund Lifecycle**:

1. **Initiate**: 
   - Validates booking and payment
   - Retrieves applicable cancellation policy
   - Calculates refund amount
   - Creates ledger entries for breakdown
   - Status: INITIATED

2. **Approve/Reject**:
   - Admin approves/rejects the refund request
   - Status: APPROVED or REJECTED

3. **Process**:
   - Submits refund to payment gateway
   - Checks concurrent processing limit
   - Status: PROCESSING

4. **Complete/Fail**:
   - On success: Updates payment as REFUNDED, marks COMPLETED
   - On failure: Marks FAILED for retry/manual intervention

**Key Methods**:

```java
// Initiation
Refund initiateRefund(UUID bookingId, String reason)

// Approval workflow
Refund approveRefund(UUID refundId)
Refund rejectRefund(UUID refundId, String reason)

// Processing workflow
Refund processRefund(UUID refundId)
Refund completeRefund(UUID refundId, String gatewayRefundId)
Refund failRefund(UUID refundId, String errorMessage)

// Admin overrides
Refund overrideRefundAmount(UUID refundId, BigDecimal adjustment, 
                            String reason, String adminId)

// Query operations
Refund getRefund(UUID refundId)
List<Refund> getRefundsForBooking(UUID bookingId)
List<Refund> getPendingRefunds()
List<Refund> getStalledRefunds(int maxAgeHours)
```

## REST API

### Refund Management Endpoints (Admin)

All endpoints require `ADMIN` role and are protected by Spring Security.

#### GET /api/v1/refunds/{refundId}
Get refund details by ID.

**Response**: `RefundResponse`

#### GET /api/v1/refunds/booking/{bookingId}
Get all refunds for a booking.

**Response**: `List<RefundResponse>`

#### GET /api/v1/refunds/pending
Get all pending refunds (for batch processing).

**Response**: `List<RefundResponse>`

#### POST /api/v1/refunds/{refundId}/approve
Approve a refund request.

**Response**: `RefundResponse`

#### POST /api/v1/refunds/{refundId}/reject
Reject a refund request.

**Request**:
```json
{
  "reason": "Policy violation"
}
```

**Response**: `RefundResponse`

#### POST /api/v1/refunds/{refundId}/process
Trigger refund processing with payment gateway.

**Response**: `RefundResponse`

#### POST /api/v1/refunds/{refundId}/complete
Mark refund as completed (internal, called by webhook).

**Request**:
```json
{
  "gatewayRefundId": "GW-REF-12345"
}
```

**Response**: `RefundResponse`

#### POST /api/v1/refunds/{refundId}/fail
Mark refund as failed (internal, called by webhook).

**Request**:
```json
{
  "errorMessage": "Insufficient funds in merchant account"
}
```

**Response**: `RefundResponse`

#### POST /api/v1/refunds/{refundId}/override-amount
Admin override: Manually adjust refund amount.

**Request**:
```json
{
  "adjustmentAmount": 50.00,
  "reason": "Customer goodwill gesture",
  "adminId": "ADMIN-001"
}
```

**Response**: `RefundResponse`

## Configuration

### Application Properties

```yaml
app:
  refund:
    max-concurrent-processing: 100        # Limit concurrent refund processing
    auto-approve-within-hours: 1          # Auto-approve refunds within 1 hour
    stalled-processing-hours: 24          # Consider processing stalled after 24h
  
  payment:
    provider: stub                         # 'stub' for testing, 'real' for production
    intent-ttl-hours: 1                   # Payment intent expiry
    webhook-timeout-seconds: 30           # Webhook processing timeout
```

### Environment Variables

All properties are overridable via environment variables:

```bash
REFUND_MAX_CONCURRENT_PROCESSING=100
REFUND_AUTO_APPROVE_WITHIN_HOURS=1
REFUND_STALLED_PROCESSING_HOURS=24
PAYMENT_PROVIDER=stub
PAYMENT_INTENT_TTL_HOURS=1
PAYMENT_WEBHOOK_TIMEOUT_SECONDS=30
```

## Integration Stub

The `PaymentProviderIntegrationStub` provides a mock implementation for testing without real payment gateways.

### Features

- **Webhook Simulation**: Simulates payment confirmation/failure webhooks
- **Rate Testing**: 5% failure rate for payments, 2% for refunds (configurable)
- **Signature Validation**: Always returns true (replace with real validation in production)
- **Gateway ID Generation**: Generates realistic gateway transaction IDs

### Usage

```java
@Autowired
private PaymentProviderIntegrationStub integrationStub;

// Simulate payment confirmation
PaymentProviderIntegrationStub.PaymentWebhookResponse response = 
    integrationStub.simulatePaymentConfirmation(payment);

if (response.isSuccess()) {
    paymentService.confirmPayment(payment.getId(), response.getMessage());
}

// Simulate refund processing
PaymentProviderIntegrationStub.RefundWebhookResponse refundResponse = 
    integrationStub.simulateRefundProcessing(refund.getId(), refund.getRefundAmount());

if (refundResponse.isSuccess()) {
    refundService.completeRefund(refund.getId(), refundResponse.getGatewayRefundId());
}
```

### Extending to Real Provider

Replace `PaymentProviderIntegrationStub` with real implementation:

```java
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "stripe")
public class StripePaymentIntegration implements PaymentProvider {
    // Call Stripe API
}
```

## Test Coverage

Comprehensive test suite with 60+ tests covering:

### 1. CancellationPolicyEngineTest (10 tests)
- Full refund window calculation
- Partial refund with policy percentage
- Outside window handling
- Minimum fee application
- Processing fee deduction
- Boundary conditions
- Non-refundable policies
- Detailed breakdown calculation

### 2. RefundServiceTest (15 tests)
- Refund initiation with policy calculation
- Approval/rejection workflow
- Filing and completion states
- Admin override with ledger entries
- Not found scenarios
- Invalid state transitions
- Concurrent processing limits
- Query operations (pending, stalled, by booking)

### 3. PaymentServiceTest (15 tests)
- Payment creation and lifecycle
- Confirmation with idempotency
- Failure handling
- Refund processing
- Payment status queries
- Total paid amount calculation
- Sequential operations
- Invalid state transitions

### 4. RefundLedgerServiceTest (15 tests)
- All ledger entry types creation
- Entry breakdown sequence
- Total refund calculation
- Admin adjustment tracking
- Refund retrieval by booking
- Entry counting by type
- Unique ID generation
- Null/zero handling

**Overall Coverage**: ~80% line coverage

## Example Usage Flow

### Scenario 1: Full Refund (Cancellation 96 hours before event)

```java
// 1. Customer initiates refund
Refund refund = refundService.initiateRefund(bookingId, "Customer request");
// Status: INITIATED
// Amount: 975.00 (1000 - 2.5% fee)
// Ledger entries: REFUND_AMOUNT (1000) → PROCESSING_FEE (-25) → FINAL_AMOUNT (975)

// 2. Admin approves refund
refund = refundService.approveRefund(refund.getId());
// Status: APPROVED

// 3. Process with payment gateway
refund = refundService.processRefund(refund.getId());
// Status: PROCESSING

// 4. Webhook callback on success
refund = refundService.completeRefund(refund.getId(), "GW-REF-12345");
// Status: COMPLETED
// Payment status: REFUNDED
```

### Scenario 2: Partial Refund (Cancellation 48 hours before event)

```java
// Policy: 72h full, 24h partial (50%), 2.5% fee

// 1. Initiate - automatically calculates
Refund refund = refundService.initiateRefund(bookingId, "Customer request");
// Amount calculated: 1000 * 50% = 500
// Minus 2.5% fee: 500 - 12.50 = 487.50
// Ledger: REFUND_AMOUNT (1000) → POLICY_DEDUCTION (-500) → 
//         PROCESSING_FEE (-12.50) → FINAL_AMOUNT (487.50)
```

### Scenario 3: Admin Override

```java
// Refund was rejected, but admin wants to override (goodwill)
Refund refund = refundService.overrideRefundAmount(
    refundId, 
    BigDecimal.valueOf(100),  // Increase by 100
    "Goodwill for VIP customer",
    "ADMIN-001"
);
// Amount: 487.50 + 100 = 587.50
// New ledger entry: ADJUSTMENT (+100) with admin metadata
```

## Error Handling

### Custom Exceptions

- `NotFoundException`: Refund, payment, or policy not found
- `IllegalStateException`: Invalid state transition (e.g., approve COMPLETED refund)
- `IllegalArgumentException`: Invalid input (e.g., negative refund amount)

### Error Responses

All errors return standardized format:

```json
{
  "status": "409",
  "errorCode": "INVALID_STATE_TRANSITION",
  "message": "Cannot approve refund in status: COMPLETED",
  "timestamp": "2026-03-02T10:30:00Z",
  "path": "/api/v1/refunds/123/approve",
  "correlationId": "req-abc123"
}
```

## Operational Considerations

### Running Scheduled Jobs

Scheduled jobs for maintenance (not yet implemented, requires Spring Scheduler):

```java
// Cleanup expired idempotency keys
@Scheduled(cron = "0 0 * * * *")  // Daily at midnight
public void cleanupExpiredRefunds() {
    // Mark PROCESSING refunds older than 24h as STALLED
}

// Process auto-approved refunds
@Scheduled(cron = "0 */15 * * * *")  // Every 15 minutes
public void processApprovedRefunds() {
    // Transition APPROVED → PROCESSING for payment gateway
}
```

### Monitoring & Alerting

Key metrics to monitor:

- `refund.pending_count`: Number of INITIATED refunds
- `refund.processing_duration_max`: Max time in PROCESSING
- `refund.completion_rate`: % of refunds completing successfully
- `payment.confirmation_latency_seconds`: Payment confirmation delay
- `ledger.entry_count_by_type`: Breakdown of ledger entries

### Audit Trail

All administrative actions are recorded in `RefundLedger`:

```java
// Query all adjustments by specific admin
List<RefundLedger> adjustments = refundLedgerService.getAdjustmentsByAdmin("ADMIN-001");

// View complete ledger for a booking
List<RefundLedger> ledger = refundLedgerService.getLedgerEntriesForBooking(bookingId);
```

## Security

- All refund endpoints require `ADMIN` role
- Admin ID is captured from JWT principal
- All adjustments are logged with admin metadata
- Payment data is treated as sensitive (PCI compliance ready)
- Transaction IDs are unique and indexed for quick lookup

## Future Enhancements

1. **Real Payment Gateway Integration**: Stripe, Razorpay, PayPal adapters
2. **Partial Refunds**: Support percentage-based partial refunds
3. **Refund Webhooks**: Notify users of refund status changes
4. **Batch Processing**: Bulk refund operations
5. **Analytics Dashboard**: Refund metrics and trends
6. **Chargeback Handling**: Dispute resolution workflow
7. **Multi-currency Support**: Refunds in different currencies
8. **Delayed Refunds**: Schedule refunds for future dates
