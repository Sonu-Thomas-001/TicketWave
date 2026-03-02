package com.ticketwave.refund.application;

import com.ticketwave.booking.domain.Booking;
import com.ticketwave.booking.infrastructure.BookingRepository;
import com.ticketwave.common.exception.ResourceNotFoundException;
import com.ticketwave.payment.application.PaymentService;
import com.ticketwave.payment.domain.Payment;
import com.ticketwave.refund.domain.CancellationPolicy;
import com.ticketwave.refund.domain.Refund;
import com.ticketwave.refund.domain.RefundLedger;
import com.ticketwave.refund.domain.RefundStatus;
import com.ticketwave.refund.infrastructure.CancellationPolicyRepository;
import com.ticketwave.refund.infrastructure.RefundRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * RefundService orchestrates the refund process from calculation through processing.
 * Handles:
 * - Refund eligibility check
 * - Amount calculation based on cancellation policy
 * - Ledger entry creation for reconciliation
 * - Admin override refunds
 * - Payment gateway integration triggering
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RefundService {

    private final RefundRepository refundRepository;
    private final CancellationPolicyRepository policyRepository;
    private final RefundLedgerService refundLedgerService;
    private final PaymentService paymentService;
    private final CancellationPolicyEngine policyEngine;
    private final BookingRepository bookingRepository;

    @Value("${app.refund.max-concurrent-processing:100}")
    private int maxConcurrentProcessing;

    @Value("${app.refund.auto-approve-within-hours:1}")
    private int autoApproveWithinHours;

    /**
     * Initiate a refund request for a booking.
     * Calculates refund amount based on cancellation policy and timing.
     * 
     * @param bookingId Booking ID to refund
     * @param reason Reason for refund request
     * @return Created Refund entity in INITIATED status
     */
    public Refund initiateRefund(UUID bookingId, String reason) {
        log.info("Initiating refund for booking: {}", bookingId);

        Booking booking = bookingRepository.findById(bookingId)
            .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));

        if (booking.getPayments().isEmpty()) {
            log.warn("No payments found for booking: {}", bookingId);
            throw new IllegalStateException("No payments found for booking");
        }

        // Get the most recent confirmed payment
        Payment payment = booking.getPayments().stream()
                .filter(p -> "CONFIRMED".equals(p.getPaymentStatus()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No confirmed payment found for booking"));

        // Get cancellation policy for event type
        String transportMode = booking.getSchedule().getRoute() != null ? booking.getSchedule().getRoute().getTransportMode() : "UNKNOWN";
        CancellationPolicy policy = policyRepository
                .findActiveByEventType(transportMode, Instant.now())
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Cancellation policy not found for event type: " + 
                        transportMode));

        // Calculate refund amount based on timing
        CancellationPolicyEngine.RefundCalculation calculation = policyEngine.calculateRefund(
                policy,
                payment.getAmount(),
                booking.getSchedule().getDepartureTime().atZone(java.time.ZoneId.systemDefault()).toInstant(),
                Instant.now()
        );

        // Create refund record
        String refundId = "REF-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        Refund refund = Refund.builder()
                .refundId(refundId)
                .booking(booking)
                .payment(payment)
                .refundAmount(calculation.getRefundAmount())
                .refundStatus(RefundStatus.INITIATED.name())
                .reason(reason + " | Policy: " + policy.getPolicyName() + " | Type: " + calculation.getRefundType())
                .build();

        Refund savedRefund = refundRepository.save(refund);
        log.info("Refund initiated - ID: {}, Amount: {}, Type: {}", 
                refundId, calculation.getRefundAmount(), calculation.getRefundType());

        // Create ledger entries to track calculation breakdown
        createRefundLedgerEntries(savedRefund, payment, calculation);

        return savedRefund;
    }

    /**
     * Create ledger entries to record refund calculation breakdown.
     */
    private void createRefundLedgerEntries(Refund refund, Payment payment, 
                                          CancellationPolicyEngine.RefundCalculation calculation) {
        UUID refundId = refund.getId();
        UUID bookingId = refund.getBooking().getId();

        // Entry 1: Original refundable amount
        refundLedgerService.createRefundAmountEntry(refundId, bookingId, payment.getAmount());

        // Entry 2: Policy deduction (if applicable)
        if (calculation.getPolicyDeduction() != null && calculation.getPolicyDeduction().compareTo(BigDecimal.ZERO) > 0) {
            refundLedgerService.createPolicyDeductionEntry(
                    refundId, bookingId, 
                    calculation.getPolicyDeduction(),
                    payment.getAmount(),
                    calculation.getReason()
            );
        }

        // Entry 3: Processing fee (if applicable)
        if (calculation.getProcessingFeeDeducted() != null && calculation.getProcessingFeeDeducted().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal balanceAfter = payment.getAmount();
            if (calculation.getPolicyDeduction() != null) {
                balanceAfter = balanceAfter.subtract(calculation.getPolicyDeduction());
            }
            refundLedgerService.createProcessingFeeEntry(
                    refundId, bookingId,
                    calculation.getProcessingFeeDeducted(),
                    balanceAfter
            );
        }

        // Entry 4: Final refund amount
        refundLedgerService.createFinalAmountEntry(refundId, bookingId, calculation.getRefundAmount());
    }

    /**
     * Approve a refund request (transition from INITIATED to APPROVED).
     * 
     * @param refundId Refund ID to approve
     * @return Updated Refund entity
     */
    public Refund approveRefund(UUID refundId) {
        log.info("Approving refund: {}", refundId);

        Refund refund = refundRepository.findById(refundId)
            .orElseThrow(() -> new ResourceNotFoundException("Refund not found: " + refundId));

        RefundStatus currentStatus = RefundStatus.valueOf(refund.getRefundStatus());
        if (!currentStatus.canTransitionTo(RefundStatus.APPROVED)) {
            log.warn("Cannot approve refund in status: {}", currentStatus);
            throw new IllegalStateException("Cannot approve refund in status: " + currentStatus);
        }

        refund.setRefundStatus(RefundStatus.APPROVED.name());
        Refund approvedRefund = refundRepository.save(refund);
        log.info("Refund approved: {}", refundId);
        return approvedRefund;
    }

    /**
     * Reject a refund request (transition from INITIATED to REJECTED).
     * 
     * @param refundId Refund ID to reject
     * @param reason Reason for rejection
     * @return Updated Refund entity
     */
    public Refund rejectRefund(UUID refundId, String reason) {
        log.warn("Rejecting refund: {} - Reason: {}", refundId, reason);

        Refund refund = refundRepository.findById(refundId)
            .orElseThrow(() -> new ResourceNotFoundException("Refund not found: " + refundId));

        RefundStatus currentStatus = RefundStatus.valueOf(refund.getRefundStatus());
        if (!currentStatus.canTransitionTo(RefundStatus.REJECTED)) {
            log.warn("Cannot reject refund in status: {}", currentStatus);
            throw new IllegalStateException("Cannot reject refund in status: " + currentStatus);
        }

        refund.setRefundStatus(RefundStatus.REJECTED.name());
        refund.setReason(reason);
        Refund rejectedRefund = refundRepository.save(refund);
        log.info("Refund rejected: {}", refundId);
        return rejectedRefund;
    }

    /**
     * Process a refund (trigger payment gateway refund, transition to PROCESSING).
     * 
     * @param refundId Refund ID to process
     * @return Updated Refund entity in PROCESSING status
     */
    public Refund processRefund(UUID refundId) {
        log.info("Processing refund: {}", refundId);

        Refund refund = refundRepository.findById(refundId)
            .orElseThrow(() -> new ResourceNotFoundException("Refund not found: " + refundId));

        RefundStatus currentStatus = RefundStatus.valueOf(refund.getRefundStatus());
        if (!currentStatus.canTransitionTo(RefundStatus.PROCESSING)) {
            log.warn("Cannot process refund in status: {}", currentStatus);
            throw new IllegalStateException("Cannot process refund in status: " + currentStatus);
        }

        // Check concurrent processing limit
        long processingCount = refundRepository.countByRefundStatus(RefundStatus.PROCESSING.name());
        if (processingCount >= maxConcurrentProcessing) {
            log.warn("Max concurrent refunds ({}) reached", maxConcurrentProcessing);
            throw new IllegalStateException("Max concurrent refunds limit reached");
        }

        refund.setRefundStatus(RefundStatus.PROCESSING.name());
        Refund processingRefund = refundRepository.save(refund);
        log.info("Refund marked as PROCESSING: {}", refundId);

        // TODO: Trigger payment gateway refund via PaymentProviderIntegration
        // This would call the payment provider's refund API
        // Result would trigger webhook callback to completeRefund or failRefund

        return processingRefund;
    }

    /**
     * Mark refund as completed (after successful payment reversal confirmation).
     * 
     * @param refundId Refund ID that completed
     * @param gatewayRefundId Reference ID from payment gateway
     * @return Updated Refund entity in COMPLETED status
     */
    public Refund completeRefund(UUID refundId, String gatewayRefundId) {
        log.info("Completing refund: {} - Gateway ID: {}", refundId, gatewayRefundId);

        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("Refund not found: " + refundId));

        refund.setRefundStatus(RefundStatus.COMPLETED.name());
        refund.setProcessedAt(Instant.now());
        refund.setGatewayResponse("Refund successful. Gateway Reference: " + gatewayRefundId);

        Refund completedRefund = refundRepository.save(refund);
        
        // Mark payment as refunded
        paymentService.refundPayment(refund.getPayment().getId(), refund.getRefundAmount());

        log.info("Refund completed successfully: {}", refundId);
        return completedRefund;
    }

    /**
     * Mark refund as failed (payment gateway rejection).
     * 
     * @param refundId Refund ID that failed
     * @param errorMessage Error from payment gateway
     * @return Updated Refund entity in FAILED status
     */
    public Refund failRefund(UUID refundId, String errorMessage) {
        log.error("Failing refund: {} - Error: {}", refundId, errorMessage);

        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("Refund not found: " + refundId));

        if (!RefundStatus.PROCESSING.name().equals(refund.getRefundStatus())) {
            log.warn("Cannot fail refund not in PROCESSING status: {}", refund.getRefundStatus());
            throw new IllegalStateException("Refund must be in PROCESSING status to fail");
        }

        refund.setRefundStatus(RefundStatus.FAILED.name());
        refund.setGatewayResponse(errorMessage);
        Refund failedRefund = refundRepository.save(refund);
        log.error("Refund failed: {} - {}", refundId, errorMessage);
        return failedRefund;
    }

    /**
     * Admin override: Manually adjust refund amount (increase or decrease).
     * Creates an ADJUSTMENT ledger entry and marks for reprocessing.
     * 
     * @param refundId Refund ID to adjust
     * @param adjustmentAmount Amount to add/subtract (positive to increase, negative to decrease)
     * @param reason Reason for adjustment
     * @param adminId Admin user ID making adjustment
     * @return Updated Refund entity
     */
    public Refund overrideRefundAmount(UUID refundId, BigDecimal adjustmentAmount, String reason, String adminId) {
        log.warn("Admin override refund amount - ID: {}, Adjustment: {}, Admin: {}, Reason: {}", 
                refundId, adjustmentAmount, adminId, reason);

        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("Refund not found: " + refundId));

        BigDecimal oldAmount = refund.getRefundAmount();
        BigDecimal newAmount = oldAmount.add(adjustmentAmount);

        if (newAmount.compareTo(BigDecimal.ZERO) < 0) {
            log.warn("Cannot adjust refund to negative amount: {}", newAmount);
            throw new IllegalArgumentException("Refund amount cannot be negative");
        }

        refund.setRefundAmount(newAmount);
        Refund adjustedRefund = refundRepository.save(refund);

        // Create adjustment ledger entry
        List<RefundLedger> ledgerEntries = refundLedgerService.getLedgerEntriesForRefund(refund.getId());
        BigDecimal lastBalance = ledgerEntries.isEmpty() ? BigDecimal.ZERO : 
                ledgerEntries.get(ledgerEntries.size() - 1).getBalanceAfter();

        refundLedgerService.createAdjustmentEntry(
                refund.getId(),
                refund.getBooking().getId(),
                adjustmentAmount,
                lastBalance,
                reason,
                adminId
        );

        log.info("Refund adjusted - Old: {}, New: {}, Adjustment: {}", oldAmount, newAmount, adjustmentAmount);
        return adjustedRefund;
    }

    /**
     * Get refund by ID.
     */
    @Transactional(readOnly = true)
    public Refund getRefund(UUID refundId) {
        return refundRepository.findById(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("Refund not found: " + refundId));
    }

    /**
     * Get all refunds for a booking.
     */
    @Transactional(readOnly = true)
    public List<Refund> getRefundsForBooking(UUID bookingId) {
        return refundRepository.findByBookingIdOrderByCreatedAtDesc(bookingId);
    }

    /**
     * Get all pending refunds (for processing batch jobs).
     */
    @Transactional(readOnly = true)
    public List<Refund> getPendingRefunds() {
        return refundRepository.findPendingRefunds();
    }

    /**
     * Get refunds stalled in PROCESSING status (for timeout/retry handling).
     */
    @Transactional(readOnly = true)
    public List<Refund> getStalledRefunds(int maxAgeHours) {
        Instant cutoffTime = Instant.now().minusSeconds((long) maxAgeHours * 3600);
        return refundRepository.findStalledProcessingRefunds(cutoffTime);
    }
}
