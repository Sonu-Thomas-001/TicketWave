# TicketWave — Functional Test Cases Documentation

**Version:** 1.0  
**Date:** March 12, 2026  
**Test Framework:** JUnit 5 + Mockito  
**Total Test Files:** 21  
**Total Test Cases:** 128+  
**Target Coverage:** ≥ 80% Line Coverage  

---

## Table of Contents

1. [Booking Module Tests](#1-booking-module-tests-62-tests)
2. [Payment Module Tests](#2-payment-module-tests-23-tests)
3. [Refund Module Tests](#3-refund-module-tests-40-tests)
4. [Catalog Module Tests](#4-catalog-module-tests-33-tests)
5. [Audit Module Tests](#5-audit-module-tests-50-tests)
6. [Application Context Test](#6-application-context-test-1-test)

---

## 1. Booking Module Tests (62 Tests)

### 1.1 BookingServiceTest (10 Tests)

| # | Test Name | Given | When | Then |
|---|-----------|-------|------|------|
| 1 | `testConfirmBooking_Success` | Valid booking with held seats and assigned passengers | `confirmBooking()` is called | Booking status = CONFIRMED, seats marked BOOKED, holds released |
| 2 | `testCancelBooking_Success` | Confirmed booking exists | `cancelBooking()` is called | Booking status = CANCELLED, seats released back to AVAILABLE |
| 3 | `testGetBookingByPnr_Success` | Booking with PNR exists in DB | `getBookingByPnr()` is called | Returns correct booking details |
| 4 | `testConfirmBooking_InvalidHold` | Seat hold has expired or is invalid | `confirmBooking()` is called | Throws exception, booking not confirmed |
| 5 | `testConfirmBooking_SeatNotFound` | Seat ID does not exist in database | `confirmBooking()` is called | Throws `ResourceNotFoundException` |
| 6 | `testConfirmBooking_PassengerNotAssigned` | Booking item has no passenger | `confirmBooking()` is called | Throws validation exception |
| 7 | `testCancelBooking_AlreadyCancelled` | Booking is already in CANCELLED state | `cancelBooking()` is called | Throws `ConflictException` — idempotent guard |
| 8 | `testGetBookingByPnr_NotFound` | PNR does not exist | `getBookingByPnr()` is called | Throws `ResourceNotFoundException` |
| 9 | `testConfirmBooking_ErrorReleaseHolds` | Hold release fails after confirmation | `confirmBooking()` is called | Booking still confirmed, error logged (graceful degradation) |
| 10 | `testConfirmBooking_MultipleSeats` | Booking with 5 seats, all held | `confirmBooking()` is called | All 5 seats confirmed, batch query used |

### 1.2 BookingServiceEnhancedTest (7 Tests)

| # | Test Name | Given | When | Then |
|---|-----------|-------|------|------|
| 1 | `testInitiateBooking_Success` | Valid seat holds and schedule | `initiateBooking()` is called | Booking created with status INITIATED, PNR generated |
| 2 | `testInitiateBooking_InvalidHold` | Expired or missing hold | `initiateBooking()` is called | Throws exception, no booking created |
| 3 | `testConfirmBooking_Success` | Valid initiated booking with payment | `confirmBooking()` is called | Booking status → CONFIRMED |
| 4 | `testHandlePaymentFailure` | Payment webhook reports failure | `handlePaymentFailure()` is called | Booking status → FAILED, seats released |
| 5 | `testCancelBooking_Success` | Confirmed booking within policy window | `cancelBooking()` is called | Booking cancelled, refund initiated |
| 6 | `testCancelBooking_NotConfirmed` | Booking in INITIATED state | `cancelBooking()` is called | Throws illegal state exception |
| 7 | `testGetBookingByPnr` | Valid PNR in database | `getBookingByPnr()` is called | Returns booking with all items |

### 1.3 SeatHoldServiceTest (7 Tests)

| # | Test Name | Given | When | Then |
|---|-----------|-------|------|------|
| 1 | `testHoldSeat_Success` | Seat is available, no existing hold | `holdSeat()` is called | Redis key set with 600s TTL, hold token returned |
| 2 | `testHoldSeat_AlreadyHeld` | Seat already held by another user | `holdSeat()` is called | Throws `ConflictException` (409) |
| 3 | `testHoldSeat_LockAcquisitionFailed` | Redisson lock times out (2s wait) | `holdSeat()` is called | Throws `ConflictException` |
| 4 | `testIsHoldValid_Valid` | Hold exists in Redis with matching user | `isHoldValid()` is called | Returns `true` |
| 5 | `testIsHoldValid_HoldNotFound` | Hold key absent from Redis (expired) | `isHoldValid()` is called | Returns `false` |
| 6 | `testIsHoldValid_WrongUser` | Hold exists but belongs to different user | `isHoldValid()` is called | Returns `false` |
| 7 | `testIsHoldValid_InvalidToken` | Token does not match stored token | `isHoldValid()` is called | Returns `false` |

### 1.4 SeatHoldServiceEdgeCaseTest (15 Tests)

| # | Test Name | Given | When | Then |
|---|-----------|-------|------|------|
| 1 | `testHoldSeat_NullSeatId` | Null seat ID passed | `holdSeat()` is called | Throws `IllegalArgumentException` |
| 2 | `testHoldSeat_NullUserId` | Null user ID passed | `holdSeat()` is called | Throws `IllegalArgumentException` |
| 3 | `testReleaseSeatHold_NonExistentHold` | No hold exists for seat | `releaseSeatHold()` is called | No-op, no exception thrown (idempotent) |
| 4 | `testGetHoldRemainingSeconds_Expired` | Hold TTL has expired | `getHoldRemainingSeconds()` is called | Returns 0 or negative |
| 5 | `testGetHoldRemainingSeconds_PersistentKey` | Key has no TTL | `getHoldRemainingSeconds()` is called | Returns -1 (persistent) |
| 6 | `testIsHoldValid_ExpiringTTL` | Hold has only 1 second remaining | `isHoldValid()` is called | Returns `true` (still valid) |
| 7 | `testMultipleHoldsSequential_SameUser` | User holds seat 1, then tries seat 2 | Both `holdSeat()` calls | Both succeed independently |
| 8 | `testExtendSeatHold_ValidTTL` | Active hold exists | `extendSeatHold()` is called | TTL refreshed to new duration |
| 9 | `testExtendSeatHold_NoHold` | No existing hold | `extendSeatHold()` is called | Returns `false` — nothing to extend |
| 10 | `testSeatHold_ZeroDuration` | Duration = 0 passed | `holdSeat()` is called | Rejected or uses default TTL |
| 11 | `testSeatHold_NegativeDuration` | Negative duration passed | `holdSeat()` is called | Throws `IllegalArgumentException` |
| 12 | `testIsHoldValid_MismatchedSeatInToken` | Token encodes seat 1, validates for seat 2 | `isHoldValid()` is called | Returns `false` |
| 13 | `testHoldSeat_LockReleaseFailure` | Lock acquired but release throws error | `holdSeat()` completes | Hold still created, error logged |
| 14 | `testExtendSeatHold_ManyExtensions` | Hold extended 10 times | Each `extendSeatHold()` call | All succeed, TTL refreshed each time |
| 15 | `testHoldTokenUniqueness` | Two holds created for different seats | Both `holdSeat()` calls | Tokens are unique (UUID-based) |

### 1.5 SeatHoldServiceConcurrencyTest (6 Tests)

| # | Test Name | Given | When | Then |
|---|-----------|-------|------|------|
| 1 | `testConcurrentSeatHolds_OnlyOneSucceeds` | 10 threads try to hold same seat | All call `holdSeat()` simultaneously | Exactly 1 succeeds, 9 get `ConflictException` |
| 2 | `testRapidReleaseAndReHold` | Hold released, immediately re-held | Release + hold in quick succession | New hold succeeds with new token |
| 3 | `testLockAcquisitionTimeout` | Lock held by another thread beyond wait | Second thread calls `holdSeat()` | Throws `ConflictException` (timeout) |
| 4 | `testValidateHoldAfterConcurrentOperations` | Multiple concurrent hold/release cycles | `isHoldValid()` after operations | Consistent state — valid or invalid |
| 5 | `testInterruptedThreadDuringLockAcquisition` | Thread interrupted while waiting for lock | `holdSeat()` is called | Handles interruption gracefully |
| 6 | `testHoldTTLEnforcement` | Hold created with 600s TTL | Check TTL value in Redis | TTL is within expected range |

### 1.6 SeatHoldServiceConcurrencyStressTest (9 Tests)

| # | Test Name | Given | When | Then |
|---|-----------|-------|------|------|
| 1 | `testPreventDoubleBooking_ConcurrentAttempts` | 50 threads try same seat | All call `holdSeat()` | Exactly 1 winner, no double-hold |
| 2 | `testAtomicLockAcquireRelease` | Rapid lock/unlock cycles | 100 iterations | No lock leaks, all releases clean |
| 3 | `testHighVolumeSeatHolds` | 100 different seats, 100 threads | All hold different seats | All succeed (no contention) |
| 4 | `testRapidHoldExtension` | Hold extended concurrently by same user | 20 threads extend | TTL consistently refreshed |
| 5 | `testLockTimeoutUnderHighContention` | 100 threads target 5 seats | All contend | Only 5 succeed, rest timeout |
| 6 | `testHoldValidationConsistency` | Concurrent holds + validations | Mixed operations | Validation reflects latest state |
| 7 | `testConcurrentReleaseAndValidation` | Release + validate at same time | Concurrent calls | No stale reads or exceptions |
| 8 | `testSeatHoldLatency` | Single seat hold operation | Measure execution time | Completes within acceptable latency |
| 9 | `testHoldValidationLatency` | Single hold validation | Measure execution time | Completes within acceptable latency |

### 1.7 IdempotencyKeyServiceTest (8 Tests)

| # | Test Name | Given | When | Then |
|---|-----------|-------|------|------|
| 1 | `testRegisterOrGetKey_NewKey` | No existing key | `registerOrGetKey()` is called | New key persisted, returns it |
| 2 | `testRegisterOrGetKey_ExistingUnprocessedKey` | Key exists but unprocessed | `registerOrGetKey()` is called | Returns existing key |
| 3 | `testRegisterOrGetKey_ProcessedKey` | Key already processed with response | `registerOrGetKey()` is called | Returns cached response |
| 4 | `testMarkProcessed` | Unprocessed key exists | `markProcessed()` is called | Key marked processed with response body |
| 5 | `testHasCachedResponse_True` | Processed key with cached response | `hasCachedResponse()` is called | Returns `true` |
| 6 | `testHasCachedResponse_Expired` | Key past expiry time | `hasCachedResponse()` is called | Returns `false` |
| 7 | `testGenerateFingerprint` | Request body provided | `generateFingerprint()` is called | Returns consistent hash |
| 8 | `testCleanupExpiredKeys` | Mix of expired and valid keys | `cleanupExpiredKeys()` is called | Only expired keys removed |

### 1.8 BookingEventLoggerTest (Not detailed — event logging utility)

---

## 2. Payment Module Tests (23+ Tests)

### 2.1 PaymentServiceTest (17 Tests)

| # | Test Name | Given | When | Then |
|---|-----------|-------|------|------|
| 1 | `shouldCreatePaymentSuccessfully` | Valid booking and amount | `createPayment()` is called | Payment created with PENDING status |
| 2 | `shouldConfirmPaymentSuccessfully` | Pending payment exists | `confirmPayment()` is called | Status → CONFIRMED, `paidAt` set |
| 3 | `shouldBeIdempotentOnConfirm` | Payment already confirmed | `confirmPayment()` called again | Same result, no duplicate processing |
| 4 | `shouldThrowExceptionWhenConfirmingNonExistent` | Payment ID doesn't exist | `confirmPayment()` is called | Throws `ResourceNotFoundException` |
| 5 | `shouldFailPaymentSuccessfully` | Pending payment exists | `failPayment()` is called | Status → FAILED |
| 6 | `shouldAllowFailingConfirmedPayment` | Already confirmed payment | `failPayment()` is called | Handles gracefully or rejects per policy |
| 7 | `shouldRefundPaymentSuccessfully` | Confirmed payment exists | `refundPayment()` is called | Status → REFUNDED |
| 8 | `shouldThrowExceptionWhenRefundingNonConfirmed` | Payment in PENDING state | `refundPayment()` is called | Throws `IllegalStateException` |
| 9 | `shouldGetPaymentById` | Payment exists | `getPaymentById()` is called | Returns correct payment |
| 10 | `shouldThrowNotFoundExceptionWhenPaymentNotFound` | No payment with given ID | `getPaymentById()` is called | Throws `ResourceNotFoundException` |
| 11 | `shouldGetPaymentByTransactionId` | Payment with transaction ID exists | `getByTransactionId()` | Returns matching payment |
| 12 | `shouldGetPaymentsByBooking` | Multiple payments for booking | `getPaymentsByBooking()` | Returns all payments for that booking |
| 13 | `shouldCheckIfPaymentConfirmed` | Confirmed payment exists | `isPaymentConfirmed()` | Returns `true` |
| 14 | `shouldReturnFalseWhenPaymentNotConfirmed` | Payment in PENDING state | `isPaymentConfirmed()` | Returns `false` |
| 15 | `shouldCalculateTotalPaidAmount` | Multiple confirmed payments | `calculateTotalPaid()` | Returns sum of confirmed amounts |
| 16 | `shouldHandleInvalidStateTransitionOnConfirm` | FAILED payment | `confirmPayment()` is called | Throws `IllegalStateException` |
| 17 | `shouldSucceedWithMultiplePaymentOperationsInSequence` | Sequential create → confirm → refund | Full lifecycle | Each state transition succeeds |

### 2.2 PaymentServiceConcurrencyStressTest (6 Tests)

| # | Test Name | Given | When | Then |
|---|-----------|-------|------|------|
| 1 | `testIdempotentPaymentConfirmation` | Same payment confirmed by 10 threads | All call `confirmPayment()` | Exactly 1 effective confirmation |
| 2 | `testConcurrentPaymentCreationMultipleBookings` | 50 threads create for different bookings | All call `createPayment()` | All succeed independently |
| 3 | `testHighVolumePaymentConfirmations` | 100 distinct payments | All confirmed concurrently | All confirmed without data corruption |
| 4 | `testConcurrentRefundProcessing` | 20 threads refund different payments | All call `refundPayment()` | Each refund processed once |
| 5 | `testConcurrentPartialRefunds` | Same payment, multiple partial refund attempts | Concurrent calls | Only valid refunds processed |
| 6 | `testPaymentProviderWebhookSimulation` | Simulated webhook events | Concurrent webhook processing | Idempotent — no duplicate effects |

---

## 3. Refund Module Tests (40 Tests)

### 3.1 RefundServiceTest (14 Tests)

| # | Test Name | Given | When | Then |
|---|-----------|-------|------|------|
| 1 | `shouldInitiateRefundSuccessfully` | Confirmed booking with payment | `initiateRefund()` is called | Refund created with INITIATED status |
| 2 | `shouldThrowNotFoundExceptionWhenBookingNotFound` | Invalid booking ID | `initiateRefund()` is called | Throws `ResourceNotFoundException` |
| 3 | `shouldThrowExceptionWhenNoPayment` | Booking with no payment record | `initiateRefund()` is called | Throws exception — nothing to refund |
| 4 | `shouldApproveRefundFromInitiated` | Refund in INITIATED state | `approveRefund()` is called | Status → APPROVED |
| 5 | `shouldRejectRefundFromInitiated` | Refund in INITIATED state | `rejectRefund()` is called | Status → REJECTED |
| 6 | `shouldProcessRefundSuccessfully` | Approved refund | `processRefund()` is called | Status → PROCESSING |
| 7 | `shouldCompleteRefundSuccessfully` | Processing refund | `completeRefund()` is called | Status → COMPLETED, payment refunded |
| 8 | `shouldFailRefundFromProcessing` | Refund in PROCESSING state | `failRefund()` is called | Status → FAILED |
| 9 | `shouldOverrideRefundAmount` | Admin provides override amount | `overrideRefundAmount()` | Amount updated, audit logged |
| 10 | `shouldPreventNegativeRefundAmount` | Negative amount provided | `overrideRefundAmount()` | Throws validation exception |
| 11 | `shouldGetRefundById` | Refund exists | `getRefundById()` | Returns correct refund |
| 12 | `shouldGetRefundsForBooking` | Multiple refunds for booking | `getRefundsForBooking()` | Returns all refunds |
| 13 | `shouldGetPendingRefunds` | Mix of statuses in DB | `getPendingRefunds()` | Returns only INITIATED/APPROVED |
| 14 | `shouldPreventInvalidStateTransition` | Completed refund | `approveRefund()` | Throws `IllegalStateException` |

### 3.2 RefundServiceConcurrencyStressTest (4 Tests)

| # | Test Name | Given | When | Then |
|---|-----------|-------|------|------|
| 1 | `testConcurrentAdminOverrideDifferentBookings` | 20 threads override different refunds | All call `overrideRefundAmount()` | All succeed independently |
| 2 | `testApprovalRejectionRaceCondition` | Approve + Reject for same refund | Concurrent calls | Only one wins (optimistic lock) |
| 3 | `testAdminOverrideAuditTrail` | Admin override performed | Check audit log | Override logged with admin ID |
| 4 | `testLedgerConsistencyUnderConcurrency` | Concurrent refund completions | Multiple `completeRefund()` calls | Ledger entries consistent |

### 3.3 CancellationPolicyEngineTest (9 Tests)

| # | Test Name | Given | When | Then |
|---|-----------|-------|------|------|
| 1 | `testFullRefundWindow` | Cancellation ≥72h before event | `calculateRefund()` | 100% refund minus processing fee |
| 2 | `testPartialRefundWindow` | Cancellation 24–72h before event | `calculateRefund()` | Partial % per policy rules |
| 3 | `testNoRefundWindow` | Cancellation <24h before event | `calculateRefund()` | $0 refund |
| 4 | `testEventStarted` | Event has already started | `calculateRefund()` | $0 refund |
| 5 | `testMinimumCancellationFee` | Small booking amount | `calculateRefund()` | Minimum fee enforced |
| 6 | `testProcessingFeeDeduction` | Valid refund calculated | `calculateRefund()` | Processing fee deducted from total |
| 7 | `testNonRefundablePolicy` | Non-refundable ticket type | `calculateRefund()` | Always $0 |
| 8 | `testTimingBoundaries` | Exactly at 24h and 72h boundaries | `calculateRefund()` | Correct tier applied at boundaries |
| 9 | `testCalculationBreakdown` | Standard cancellation | `getBreakdown()` | Returns itemized: base, deduction, fee, net |

### 3.4 RefundLedgerServiceTest (13 Tests)

| # | Test Name | Given | When | Then |
|---|-----------|-------|------|------|
| 1 | `shouldCreateRefundAmountEntry` | Refund initiated | `createEntry(REFUND_AMOUNT)` | Ledger entry with original amount |
| 2 | `shouldCreatePolicyDeductionEntry` | Policy deduction calculated | `createEntry(POLICY_DEDUCTION)` | Negative entry for deduction |
| 3 | `shouldCreateProcessingFeeEntry` | Processing fee calculated | `createEntry(PROCESSING_FEE)` | Fee entry recorded |
| 4 | `shouldCreateFinalAmountEntry` | Net refund determined | `createEntry(FINAL_AMOUNT)` | Final amount = original - deductions |
| 5 | `shouldCreateAdjustmentEntry` | Admin override | `createEntry(ADJUSTMENT)` | Adjustment entry with admin reference |
| 6 | `shouldGetLedgerEntriesForRefund` | Multiple entries for refund | `getEntriesForRefund()` | Returns all entries in order |
| 7 | `shouldGetLedgerEntriesForBooking` | Entries across refunds | `getEntriesForBooking()` | Returns all booking-related entries |
| 8 | `shouldGetTotalRefundForBooking` | Multiple completed refunds | `getTotalRefund()` | Returns sum of FINAL_AMOUNT entries |
| 9 | `shouldReturnZeroWhenNoRefunds` | No refund entries | `getTotalRefund()` | Returns BigDecimal.ZERO |
| 10 | `shouldGetAdjustmentsByAdmin` | Admin has made adjustments | `getAdjustmentsByAdmin()` | Returns only that admin's adjustments |
| 11 | `shouldCountEntriesByType` | Mixed entry types | `countByType()` | Correct count per type |
| 12 | `shouldCreateCompleteBreakdown` | Full refund flow | Create all entry types | Complete audit trail in ledger |
| 13 | `shouldGenerateUniqueEntryIds` | Multiple entries created | Check IDs | All IDs unique |

---

## 4. Catalog Module Tests (33 Tests)

### 4.1 ScheduleSearchServiceTest (6 Tests)

| # | Test Name | Given | When | Then |
|---|-----------|-------|------|------|
| 1 | `testSearchSchedules_SortByPrice` | Schedules with varying fares | Search with sort=price | Results ordered by price ascending |
| 2 | `testSearchSchedules_SortByDuration` | Schedules with varying durations | Search with sort=duration | Shortest duration first |
| 3 | `testSearchSchedules_NoResults` | No matching schedules | Search with non-existent route | Empty list returned |
| 4 | `testSearchSchedules_SortByAvailability` | Schedules with varying seat counts | Search with sort=availability | Most available first |
| 5 | `testGetScheduleDetails_NotFound` | Invalid schedule ID | `getScheduleDetails()` | Throws `ResourceNotFoundException` |
| 6 | `testGetAvailabilityStats` | Schedule with mixed seat statuses | `getAvailabilityStats()` | Returns correct available/total counts |

### 4.2 PricingCalculationServiceTest (16 Tests)

| # | Test Name | Given | When | Then |
|---|-----------|-------|------|------|
| 1 | `testCalculateAvailabilityPercentage` | 70 of 100 seats available | `calculateAvailability()` | Returns 70.0 |
| 2 | `testCalculateAvailabilityPercentage_ZeroSeats` | 0 total seats | `calculateAvailability()` | Returns 0.0 (no division error) |
| 3 | `testCalculateDemandFactor_NormalDemand` | ≥30% availability | `calculateDemandFactor()` | Returns 1.0 (no surge) |
| 4 | `testCalculateDemandFactor_HighDemand` | 10–30% availability | `calculateDemandFactor()` | Returns 1.5 |
| 5 | `testCalculateDemandFactor_CriticalAvailability` | <10% availability | `calculateDemandFactor()` | Returns 1.8 |
| 6 | `testCalculateDemandFactor_BoundaryAtThreshold` | Exactly 30% availability | `calculateDemandFactor()` | Correct tier applied |
| 7 | `testCalculateDemandFactor_BoundaryAtLowAvailability` | Exactly 10% availability | `calculateDemandFactor()` | Correct tier applied |
| 8 | `testCalculateDynamicPrice_NormalDemand` | Base fare 100, normal demand | `calculatePrice()` | Returns 100.00 |
| 9 | `testCalculateDynamicPrice_HighDemand` | Base fare 100, high demand | `calculatePrice()` | Returns 150.00 |
| 10 | `testCalculateDynamicPrice_VeryLowAvailability` | Base fare 100, <10% seats | `calculatePrice()` | Returns 180.00 |
| 11 | `testCalculateDynamicPrice_WithModifier` | Base fare 100, modifier 1.2 | `calculatePrice()` | Returns 120.00 (normal demand) |
| 12 | `testCalculateDynamicPrice_CombinedFactors` | All factors active | `calculatePrice()` | Correct multiplication |
| 13 | `testCalculateDynamicPrice_Rounding` | Fractional result | `calculatePrice()` | Rounded to 2 decimal places |
| 14 | `testIsHighDemand` | Various availability levels | `isHighDemand()` | Returns true when <30% |
| 15 | `testCalculateDynamicPrice_SoldOut` | 0 seats available | `calculatePrice()` | Handles gracefully |
| 16 | `testCalculateDynamicPrice_WithDiscount` | Negative modifier (discount) | `calculatePrice()` | Correct discounted price |

### 4.3 ScheduleSearchControllerTest (11 Tests)

| # | Test Name | Given | When | Then |
|---|-----------|-------|------|------|
| 1 | `testSearchSchedules_Success` | Valid search parameters | GET /api/v1/search | HTTP 200 with results |
| 2 | `testSearchSchedules_EmptyResults` | No matching data | GET /api/v1/search | HTTP 200 with empty list |
| 3 | `testGetScheduleDetails_Success` | Valid schedule ID | GET /api/v1/schedules/{id} | HTTP 200 with details |
| 4 | `testGetScheduleDetails_NotFound` | Invalid ID | GET /api/v1/schedules/{id} | HTTP 404 |
| 5 | `testGetAvailabilityStats_Success` | Valid schedule | GET /api/v1/schedules/{id}/availability | HTTP 200 with stats |
| 6 | `testGetHighDemandSchedules_Success` | High demand schedules exist | GET /api/v1/schedules/high-demand | HTTP 200 with list |
| 7 | `testGetHighDemandSchedules_EmptyResults` | No high demand schedules | GET /api/v1/schedules/high-demand | HTTP 200 with empty list |
| 8 | `testGetDuration_Success` | Valid schedule with times | GET /api/v1/schedules/{id}/duration | HTTP 200 with duration |
| 9 | `testGetDuration_InvalidSchedule` | Invalid schedule ID | GET /api/v1/schedules/{id}/duration | HTTP 404 |
| 10 | `testSearchSchedules_ValidatesSortParameters` | Invalid sort parameter | GET /api/v1/search?sort=invalid | Validation error or defaults |
| 11 | `testSearchSchedules_ConcurrentRequests` | Multiple concurrent searches | Parallel GET requests | All return consistent results |

---

## 5. Audit Module Tests (50 Tests)

### 5.1 AuditServiceTest (17 Tests)

| # | Test Name | Given | When | Then |
|---|-----------|-------|------|------|
| 1 | `shouldLogBasicEventSuccessfully` | Operation with entity | `logEvent()` | Audit log created with timestamp |
| 2 | `shouldLogStatusChangeWithValues` | Before/after values provided | `logStatusChange()` | Old and new values recorded |
| 3 | `shouldLogAdminActionWithOverride` | Admin performs override | `logAdminAction()` | `isAdminOverride=true` set |
| 4 | `shouldLogErrorWithException` | Exception during operation | `logError()` | Error message and type recorded |
| 5 | `shouldQueryAuditLogsForEntity` | Logs exist for entity | `findByEntity()` | Returns matching logs |
| 6 | `shouldQueryAuditLogsByUser` | Logs by specific user | `findByUser()` | Returns user's logs |
| 7 | `shouldQueryFailedOperations` | Mix of success/failure logs | `findFailedOperations()` | Returns only failures |
| 8 | `shouldQueryAdminOverrides` | Admin override logs exist | `findAdminOverrides()` | Returns override entries |
| 9 | `shouldQueryAdminOverridesForSpecificAdmin` | Multiple admins | `findByAdmin()` | Returns only specified admin |
| 10 | `shouldQueryAuditLogsByAction` | Various action types | `findByAction()` | Filtered by action |
| 11 | `shouldGetAuditLogById` | Valid audit log ID | `getById()` | Returns correct log |
| 12 | `shouldReturnNullForInvalidId` | Non-existent ID | `getById()` | Returns null |
| 13 | `shouldCalculateAuditStatistics` | Multiple logs | `getStatistics()` | Correct counts |
| 14 | `shouldCreateAuditLogWithBuilder` | Builder pattern used | `AuditLog.builder()` | All fields set correctly |
| 15 | `shouldCaptureDuration` | Timed operation | `logEvent()` with duration | Duration in ms recorded |
| 16 | `shouldHandleNullErrorMessage` | Null error message | `logError(null)` | Handles gracefully |
| 17 | `shouldSetRelatedEntityInformation` | Related entity reference | `setRelatedEntity()` | Related ID and type stored |

### 5.2 AuditAspectTest (13 Tests)

| # | Test Name | Given | When | Then |
|---|-----------|-------|------|------|
| 1 | `shouldInterceptMethodAndLogExecutionTime` | `@Auditable` method called | Method executes | Execution time logged |
| 2 | `shouldCaptureMethodParametersAsJson` | Method with parameters | Method executes | Parameters serialized to JSON |
| 3 | `shouldExtractEntityIdFromResult` | Method returns entity | Method executes | Entity ID extracted for log |
| 4 | `shouldExtractEntityIdFromArguments` | Entity ID in method args | Method executes | ID extracted from arguments |
| 5 | `shouldMaskSensitiveParameters` | Parameter named "password" | Method executes | Value replaced with `****` |
| 6 | `shouldMaskTokenParameters` | Parameter named "token" | Method executes | Value replaced with `****` |
| 7 | `shouldCatchExceptionAndLogError` | Method throws exception | Method executes | Exception logged as error |
| 8 | `shouldReThrowExceptionAfterLogging` | Method throws exception | Method executes | Original exception re-thrown |
| 9 | `shouldHandleNullParametersGracefully` | Null parameters passed | Method executes | No NPE, logged correctly |
| 10 | `shouldCaptureResultWhenCaptureResultTrue` | `captureResult=true` | Method returns | Result stored in log |
| 11 | `shouldSkipCapturingLargeParameters` | Parameter > 500 chars | Method executes | Parameter truncated |
| 12 | `shouldHandleExceptionsWithStackTraceWhenEnabled` | Stack trace capture enabled | Exception thrown | Stack trace in log |
| 13 | `shouldMeasureExecutionTimeAccurately` | Timed method | Method executes | Time within expected range |

### 5.3 AuditControllerTest (20 Tests)

| # | Test Name | Given | When | Then |
|---|-----------|-------|------|------|
| 1 | `shouldDenyAccessForUnauthenticated` | No JWT token | GET /api/v1/audit | HTTP 401 |
| 2 | `shouldDenyAccessForNonAdmin` | User role = CUSTOMER | GET /api/v1/audit | HTTP 403 |
| 3 | `shouldAllowAccessForAdmin` | User role = ADMIN | GET /api/v1/audit | HTTP 200 |
| 4 | `shouldGetAuditLogsForEntity` | Entity type + ID provided | GET /api/v1/audit/entity | HTTP 200 with logs |
| 5 | `shouldGetAuditLogsForUser` | User ID provided | GET /api/v1/audit/user/{id} | HTTP 200 with user's logs |
| 6 | `shouldGetFailedOperations` | Failed operations exist | GET /api/v1/audit/failures | HTTP 200 with failures |
| 7 | `shouldGetAdminOverrides` | Admin overrides exist | GET /api/v1/audit/overrides | HTTP 200 with overrides |
| 8 | `shouldGetAdminOverridesForSpecificAdmin` | Admin ID provided | GET /api/v1/audit/overrides/{id} | HTTP 200 with admin's overrides |
| 9 | `shouldGetCurrentUserActions` | Authenticated user | GET /api/v1/audit/me | HTTP 200 with own actions |
| 10 | `shouldGetAuditLogsByActionType` | Action type filter | GET /api/v1/audit?action=CREATE | HTTP 200 filtered |
| 11 | `shouldGetSingleAuditLogById` | Valid audit log ID | GET /api/v1/audit/{id} | HTTP 200 with log |
| 12 | `shouldReturn404ForNonExistentAuditLog` | Invalid audit ID | GET /api/v1/audit/{id} | HTTP 404 |
| 13 | `shouldGetAuditStatistics` | Audit data exists | GET /api/v1/audit/stats | HTTP 200 with stats |
| 14 | `shouldSupportPaginationWithPageParameter` | Page=1, size=10 | GET /api/v1/audit?page=1&size=10 | Paginated results |
| 15 | `shouldSupportSortingParameter` | Sort by timestamp | GET /api/v1/audit?sort=timestamp | Sorted results |
| 16 | `shouldIncludeMetadataInAuditLogResponse` | Metadata exists | GET /api/v1/audit/{id} | Metadata included |
| 17 | `shouldIncludeIpAddressInResponse` | IP captured | GET /api/v1/audit/{id} | IP address present |
| 18 | `shouldHandleEmptyAuditLogsGracefully` | No audit data | GET /api/v1/audit | HTTP 200, empty list |
| 19 | *Additional pagination edge case* | — | — | — |
| 20 | *Additional security boundary test* | — | — | — |

---

## 6. Application Context Test (1 Test)

| # | Test Name | Given | When | Then |
|---|-----------|-------|------|------|
| 1 | `contextLoads` | Full application context | Spring Boot starts | No bean creation failures |

---

## Test Coverage Summary by Module

| Module | Test Files | Test Cases | Key Coverage Areas |
|--------|-----------|------------|-------------------|
| **Booking** | 8 | 62 | Lifecycle, seat holds, concurrency, idempotency |
| **Payment** | 2 | 23+ | CRUD, webhooks, idempotency, concurrency |
| **Refund** | 4 | 40 | Workflow, policy engine, ledger, concurrency |
| **Catalog** | 3 | 33 | Search, dynamic pricing, controller integration |
| **Audit** | 3 | 50 | AOP, security, controller, admin-only access |
| **Application** | 1 | 1 | Context load verification |
| **TOTAL** | **21** | **128+** | — |

---

## Test Execution Command

```bash
# Run all tests
mvn clean test

# Run tests with coverage report
mvn clean test jacoco:report

# View coverage report
# Open: target/site/jacoco/index.html
```
