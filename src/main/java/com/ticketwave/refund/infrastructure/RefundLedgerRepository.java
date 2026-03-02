package com.ticketwave.refund.infrastructure;

import com.ticketwave.refund.domain.RefundLedger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Repository for managing RefundLedger persistence.
 * Used for financial reconciliation and audit trails.
 */
@Repository
public interface RefundLedgerRepository extends JpaRepository<RefundLedger, UUID> {

    /**
     * Find all ledger entries for a specific refund.
     */
    List<RefundLedger> findByRefundIdOrderByCreatedAt(UUID refundId);

    /**
     * Find paginated ledger entries for a refund (for audit history).
     */
    Page<RefundLedger> findByRefundIdOrderByCreatedAtDesc(UUID refundId, Pageable pageable);

    /**
     * Find all ledger entries for a booking (across all refunds).
     */
    @Query("SELECT l FROM RefundLedger l WHERE l.bookingId = :bookingId ORDER BY l.createdAt DESC")
    List<RefundLedger> findByBookingIdOrderByCreatedAt(@Param("bookingId") UUID bookingId);

    /**
     * Get total refund amount for a booking (sum of FINAL_AMOUNT entries).
     */
    @Query("SELECT SUM(l.amount) FROM RefundLedger l WHERE l.bookingId = :bookingId AND l.entryType = 'FINAL_AMOUNT'")
    BigDecimal getTotalRefundForBooking(@Param("bookingId") UUID bookingId);

    /**
     * Count ledger entries by type.
     */
    long countByEntryType(String entryType);

    /**
     * Find all adjustment entries (for audit).
     */
    @Query("SELECT l FROM RefundLedger l WHERE l.entryType = 'ADJUSTMENT' ORDER BY l.adjustedAt DESC")
    List<RefundLedger> findAllAdjustments();

    /**
     * Find adjustments made by a specific admin.
     */
    @Query("SELECT l FROM RefundLedger l WHERE l.entryType = 'ADJUSTMENT' AND l.adjustedByAdmin = :adminId ORDER BY l.adjustedAt DESC")
    List<RefundLedger> findAdjustmentsByAdmin(@Param("adminId") String adminId);
}
