package com.ticketwave.refund.application;

import com.ticketwave.refund.domain.RefundLedger;
import com.ticketwave.refund.infrastructure.RefundLedgerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * RefundLedgerService manages financial transaction records for refunds.
 * Each refund can have multiple ledger entries representing deductions, fees, adjustments.
 * 
 * Used for:
 * - Financial reconciliation
 * - Audit trails
 * - Revenue impact analysis
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RefundLedgerService {

    private final RefundLedgerRepository refundLedgerRepository;

    /**
     * Create a ledger entry for a refund transaction.
     * 
     * @param refund Refund entity
     * @param bookingId Booking ID for grouping
     * @param entryType Type of entry (REFUND_AMOUNT, POLICY_DEDUCTION, PROCESSING_FEE, etc.)
     * @param description Description of the entry
     * @param amount Amount (positive for credits, negative for debits)
     * @param balanceAfter Running balance after this entry
     * @return Created RefundLedger entry
     */
    public RefundLedger createLedgerEntry(RefundLedger.RefundLedgerBuilder builder) {
        RefundLedger entry = builder.build();
        RefundLedger savedEntry = refundLedgerRepository.save(entry);
        log.info("Ledger entry created - Type: {}, Amount: {}, Balance: {}", 
                entry.getEntryType(), entry.getAmount(), entry.getBalanceAfter());
        return savedEntry;
    }

    /**
     * Build ledger entry for original refund amount.
     */
    public RefundLedger createRefundAmountEntry(UUID refund, UUID bookingId, BigDecimal amount) {
        String ledgerId = "LED-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return createLedgerEntry(
                RefundLedger.builder()
                        .ledgerEntryId(ledgerId)
                        .bookingId(bookingId)
                        .entryType("REFUND_AMOUNT")
                        .description("Original refundable amount")
                        .amount(amount)
                        .balanceAfter(amount)
        );
    }

    /**
     * Build ledger entry for policy-based deduction.
     */
    public RefundLedger createPolicyDeductionEntry(UUID refundId, UUID bookingId, BigDecimal deduction, 
                                                   BigDecimal balanceBefore, String reason) {
        String ledgerId = "LED-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        BigDecimal balanceAfter = balanceBefore.subtract(deduction);
        
        return createLedgerEntry(
                RefundLedger.builder()
                        .ledgerEntryId(ledgerId)
                        .bookingId(bookingId)
                        .entryType("POLICY_DEDUCTION")
                        .description("Policy-based deduction: " + reason)
                        .amount(deduction.negate())
                        .balanceAfter(balanceAfter)
                        .metadata(reason)
        );
    }

    /**
     * Build ledger entry for processing fee.
     */
    public RefundLedger createProcessingFeeEntry(UUID refundId, UUID bookingId, BigDecimal fee,
                                                BigDecimal balanceBefore) {
        String ledgerId = "LED-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        BigDecimal balanceAfter = balanceBefore.subtract(fee);
        
        return createLedgerEntry(
                RefundLedger.builder()
                        .ledgerEntryId(ledgerId)
                        .bookingId(bookingId)
                        .entryType("PROCESSING_FEE")
                        .description("Payment gateway processing fee")
                        .amount(fee.negate())
                        .balanceAfter(balanceAfter)
                        .metadata("Processing reversal fee")
        );
    }

    /**
     * Build ledger entry for final refund amount.
     */
    public RefundLedger createFinalAmountEntry(UUID refundId, UUID bookingId, BigDecimal finalAmount) {
        String ledgerId = "LED-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        
        return createLedgerEntry(
                RefundLedger.builder()
                        .ledgerEntryId(ledgerId)
                        .bookingId(bookingId)
                        .entryType("FINAL_AMOUNT")
                        .description("Final net refund amount to be processed")
                        .amount(finalAmount)
                        .balanceAfter(finalAmount)
        );
    }

    /**
     * Create admin adjustment entry.
     */
    public RefundLedger createAdjustmentEntry(UUID refundId, UUID bookingId, BigDecimal adjustment,
                                             BigDecimal balanceBefore, String reason, String adminId) {
        String ledgerId = "LED-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        BigDecimal balanceAfter = balanceBefore.add(adjustment);
        
        RefundLedger entry = RefundLedger.builder()
                .ledgerEntryId(ledgerId)
                .bookingId(bookingId)
                .entryType("ADJUSTMENT")
                .description("Admin override adjustment")
                .amount(adjustment)
                .balanceAfter(balanceAfter)
                .adjustmentReason(reason)
                .adjustedByAdmin(adminId)
                .adjustedAt(Instant.now())
                .build();

        RefundLedger savedEntry = refundLedgerRepository.save(entry);
        log.info("Adjustment entry created - Amount: {}, Admin: {}, Reason: {}", 
                adjustment, adminId, reason);
        return savedEntry;
    }

    /**
     * Get all ledger entries for a refund.
     * 
     * @param refundId Refund ID
     * @return List of ledger entries in chronological order
     */
    @Transactional(readOnly = true)
    public List<RefundLedger> getLedgerEntriesForRefund(UUID refundId) {
        return refundLedgerRepository.findByRefundIdOrderByCreatedAt(refundId);
    }

    /**
     * Get all ledger entries for a booking (across all refunds).
     * 
     * @param bookingId Booking ID
     * @return List of ledger entries
     */
    @Transactional(readOnly = true)
    public List<RefundLedger> getLedgerEntriesForBooking(UUID bookingId) {
        return refundLedgerRepository.findByBookingIdOrderByCreatedAt(bookingId);
    }

    /**
     * Get total refund amount for a booking.
     * 
     * @param bookingId Booking ID
     * @return Total refunded amount
     */
    @Transactional(readOnly = true)
    public BigDecimal getTotalRefundForBooking(UUID bookingId) {
        BigDecimal total = refundLedgerRepository.getTotalRefundForBooking(bookingId);
        return total == null ? BigDecimal.ZERO : total;
    }

    /**
     * Get audit trail of all adjustments made by admins.
     * 
     * @param adminId Admin user ID
     * @return List of adjustment entries made by admin
     */
    @Transactional(readOnly = true)
    public List<RefundLedger> getAdjustmentsByAdmin(String adminId) {
        return refundLedgerRepository.findAdjustmentsByAdmin(adminId);
    }

    /**
     * Get count of ledger entries by type (for metrics).
     * 
     * @param entryType Entry type
     * @return Count of entries
     */
    @Transactional(readOnly = true)
    public long countEntriesByType(String entryType) {
        return refundLedgerRepository.countByEntryType(entryType);
    }
}
