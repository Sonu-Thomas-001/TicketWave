package com.ticketwave.refund.infrastructure;

import com.ticketwave.booking.domain.Booking;
import com.ticketwave.payment.domain.Payment;
import com.ticketwave.refund.domain.Refund;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefundRepository extends JpaRepository<Refund, UUID> {
    Optional<Refund> findByRefundId(String refundId);

    List<Refund> findByBooking(Booking booking);

    List<Refund> findByBookingAndRefundStatus(Booking booking, String refundStatus);

    List<Refund> findByPaymentAndRefundStatus(Payment payment, String refundStatus);

    int countByRefundStatus(String refundStatus);

    /**
     * Find all refunds for a specific booking ID ordered by creation date.
     */
    List<Refund> findByBookingIdOrderByCreatedAtDesc(UUID bookingId);

    /**
     * Find paginated refunds for a booking.
     */
    Page<Refund> findByBookingIdOrderByCreatedAtDesc(UUID bookingId, Pageable pageable);

    /**
     * Find all pending refunds (INITIATED, APPROVED, PROCESSING).
     */
    @Query("SELECT r FROM Refund r WHERE r.refundStatus IN ('INITIATED', 'APPROVED', 'PROCESSING') ORDER BY r.createdAt ASC")
    List<Refund> findPendingRefunds();

    /**
     * Find refunds that have been processing for more than specified duration.
     */
    @Query("SELECT r FROM Refund r WHERE r.refundStatus = 'PROCESSING' AND r.createdAt < :cutoffTime ORDER BY r.createdAt ASC")
    List<Refund> findStalledProcessingRefunds(@Param("cutoffTime") Instant cutoffTime);

    /**
     * Find completed refunds for a date range.
     */
    @Query("SELECT r FROM Refund r WHERE r.refundStatus = 'COMPLETED' AND r.processedAt BETWEEN :startDate AND :endDate")
    List<Refund> findCompletedRefundsInDateRange(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate);
}
