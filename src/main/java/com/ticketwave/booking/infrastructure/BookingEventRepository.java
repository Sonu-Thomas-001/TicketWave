package com.ticketwave.booking.infrastructure;

import com.ticketwave.booking.domain.BookingEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface BookingEventRepository extends JpaRepository<BookingEvent, UUID> {

    /**
     * Get all events for a booking, most recent first.
     */
    List<BookingEvent> findByBookingIdOrderByCreatedAtDesc(UUID bookingId);

    /**
     * Get paginated events for a booking.
     */
    Page<BookingEvent> findByBookingIdOrderByCreatedAtDesc(UUID bookingId, Pageable pageable);

    /**
     * Find events of specific type.
     */
    List<BookingEvent> findByEventTypeOrderByCreatedAtDesc(String eventType);

    /**
     * Count events by type (for metrics).
     */
    long countByEventType(String eventType);

    /**
     * Count retry events.
     */
    long countByBookingIdAndIsRetryTrue(UUID bookingId);

    /**
     * Find booking events within time window (for alerting).
     */
    List<BookingEvent> findByEventTypeAndCreatedAtAfterOrderByCreatedAtDesc(
            String eventType, Instant since
    );
}
