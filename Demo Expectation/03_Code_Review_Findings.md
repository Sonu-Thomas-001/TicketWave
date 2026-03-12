# TicketWave Production Code Review Report
**Date**: March 12, 2026  
**Scope**: Security, Webhook Reliability, Idempotency, Concurrency, Validation, Exception Mapping  
**Review Method**: Copilot-assisted analysis (Explore mode) + manual source verification  
**Severity Levels**: High | Medium | Low

---

## Executive Summary
This review identified multiple production-impacting issues concentrated in payment webhook security/reliability and booking confirmation integrity.

- **5 High severity findings**
- **5 Medium severity findings**
- **Primary risk area**: webhook authenticity, replay protection, and confirmation correctness

---

## Findings (Ordered by Severity)

### High 1: Webhook signature verification is not implemented
**Why this matters**: Unauthenticated callers can spoof payment success/failure callbacks.  
**File**: `src/main/java/com/ticketwave/payment/api/PaymentWebhookController.java:78`  
**Evidence**: `verifyWebhookSignature` is commented out.  
**Recommendation**: Enforce provider signature verification before state transitions; reject invalid signatures (`401/403`).

### High 2: Webhook endpoints are not publicly accessible for payment provider callbacks
**Why this matters**: Real provider webhooks typically cannot present application JWTs, so callbacks can fail at security filter level.  
**File**: `src/main/java/com/ticketwave/common/security/SecurityConfig.java:32`  
**Evidence**: only `"/", "/error", "/actuator/**", "/api/v1/auth/**"` are `permitAll`; webhook path missing.  
**Recommendation**: Permit webhook route explicitly (for example `/api/v1/webhooks/payment/**`) and protect using strict signature validation + replay controls.

### High 3: Webhook confirmation uses placeholder hold tokens
**Why this matters**: Booking confirmation relies on real seat hold tokens; placeholder values can invalidate hold checks and break confirmation flow.  
**Files**:  
- `src/main/java/com/ticketwave/payment/api/PaymentWebhookController.java:108`  
- `src/main/java/com/ticketwave/booking/application/SeatHoldService.java:104`  
**Evidence**: hardcoded `"webhook-confirmed"` token while hold validity checks Redis token-seat mapping.  
**Recommendation**: Persist original hold tokens at booking initiation and reuse exact tokens during webhook confirmation/failure handling.

### High 4: Webhook event replay/dedup by provider event ID is missing
**Why this matters**: Payment providers are at-least-once delivery systems; duplicate events must be deduplicated.  
**Files**:  
- `src/main/java/com/ticketwave/payment/api/PaymentWebhookController.java:73`  
- `src/main/java/com/ticketwave/payment/api/PaymentWebhookController.java:258`  
**Evidence**: `eventId` exists and is logged but not persisted/checked for deduplication.  
**Recommendation**: Store provider `eventId` with uniqueness constraint; short-circuit duplicate processing with deterministic response.

### High 5: Webhook catch blocks return HTTP 200 even on processing failure
**Why this matters**: Provider cannot differentiate success from failure, which breaks retry semantics and operational observability.  
**Files**:  
- `src/main/java/com/ticketwave/payment/api/PaymentWebhookController.java:139`  
- `src/main/java/com/ticketwave/payment/api/PaymentWebhookController.java:211`  
**Evidence**: error paths still return `HttpStatus.OK`.  
**Recommendation**: Return non-2xx for unprocessed failures (or move to explicit async acceptance/processing model).

### Medium 1: Password minimum length is weak for production
**Why this matters**: 6-character minimum increases brute-force risk.  
**Files**:  
- `src/main/java/com/ticketwave/auth/api/AuthRequest.java:18`  
- `src/main/java/com/ticketwave/auth/api/RegisterRequest.java:18`  
**Recommendation**: Raise minimum length and enforce stronger policy (length + complexity or passphrase policy).

### Medium 2: Generic IllegalArgumentException mapped to 401 AUTHENTICATION_FAILED
**Why this matters**: Non-auth argument errors become misclassified as authentication failures.  
**File**: `src/main/java/com/ticketwave/common/exception/GlobalExceptionHandler.java:47`  
**Recommendation**: Map generic illegal arguments to `400 INVALID_REQUEST`; use a dedicated auth exception type for `401`.

### Medium 3: Idempotency key reactivation after expiry does not re-check fingerprint
**Why this matters**: Same idempotency key can potentially replay different payloads after expiry window.  
**Files**:  
- `src/main/java/com/ticketwave/booking/application/IdempotencyKeyService.java:56`  
- `src/main/java/com/ticketwave/booking/application/IdempotencyKeyService.java:59`  
**Recommendation**: Validate stored fingerprint against incoming fingerprint before allowing reprocessing.

### Medium 4: Broad cascade settings on route/schedule relations
**Why this matters**: `CascadeType.ALL` can unintentionally propagate destructive operations across aggregates.  
**Files**:  
- `src/main/java/com/ticketwave/catalog/domain/Schedule.java:64`  
- `src/main/java/com/ticketwave/catalog/domain/Route.java:48`  
**Recommendation**: Restrict cascade operations to required lifecycle behaviors and add delete-safety tests.

### Medium 5: Payment amount input validation missing at service entry
**Why this matters**: Null/zero/negative or malformed amounts can enter payment records.  
**File**: `src/main/java/com/ticketwave/payment/application/PaymentService.java:38`  
**Recommendation**: Validate amount (`!= null`, `> 0`, currency scale constraints) before persistence.

---

## Testing Gaps
1. No direct tests were found for `PaymentWebhookController` covering signature verification, replay handling, and failure status codes.
2. No direct tests were found for `GlobalExceptionHandler` status/error-code mapping behavior.
3. No explicit tests were found validating webhook route security behavior in `SecurityConfig`.

---

## Suggested Remediation Order
1. Implement webhook authenticity + dedup + status semantics (`PaymentWebhookController`, `SecurityConfig`).
2. Fix booking confirmation hold-token integrity for webhook-driven flow.
3. Tighten error mapping, idempotency-fingerprint checks, and payment amount validation.
4. Add targeted tests for all above paths before release.
