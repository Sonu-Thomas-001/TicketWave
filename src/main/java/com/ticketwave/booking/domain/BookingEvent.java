package com.ticketwave.booking.domain;

import com.ticketwave.common.domain.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Booking event log for audit trail and debugging.
 * 
 * Tracks all state transitions and critical events:
 * - BOOKING_INITIATED
 * - SEAT_HOLD_CREATED
 * - PAYMENT_INTENT_CREATED
 * - PAYMENT_CONFIRMED
 * - BOOKING_CONFIRMED
 * - BOOKING_FAILED
 * - HOLD_RELEASED
 * - IDEMPOTENCY_CACHE_HIT
 * 
 * Use cases:
 * - Audit trail: Review booking history
 * - Debugging: Trace issues through events
 * - Metrics: Count events by type
 * - Alerting: Alert on failures or retries
 */
@Entity
@Table(
        name = "booking_events",
        indexes = {
                @Index(name = "idx_booking_events_booking_id", columnList = "booking_id"),
                @Index(name = "idx_booking_events_event_type", columnList = "event_type"),
                @Index(name = "idx_booking_events_created_at", columnList = "created_at"),
                @Index(name = "idx_booking_events_booking_timestamp", columnList = "booking_id,created_at")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingEvent extends AuditedEntity {

    /**
     * Reference to the booking (UUID for joins).
     */
    @Column(nullable = false)
    private UUID bookingId;

    /**
     * Type of event (from BookingEventType enum).
     * Examples: BOOKING_INITIATED, PAYMENT_CONFIRMED, BOOKING_CONFIRMED, BOOKING_FAILED
     */
    @Column(nullable = false, length = 50)
    private String eventType;

    /**
     * Previous booking status.
     */
    @Column(length = 50)
    private String previousStatus;

    /**
     * New booking status after event.
     */
    @Column(length = 50)
    private String newStatus;

    /**
     * Detailed description of event (JSON metadata).
     * Examples:
     * - For PAYMENT_CONFIRMED: {"transactionId": "...", "amount": 1500, "method": "card"}
     * - For BOOKING_FAILED: {"reason": "payment_rejected", "errorCode": "insufficient_funds"}
     * - For SEAT_HOLD_CREATED: {"seats": ["1A", "2B"], "holdToken": "..."}
     */
    @Column(columnDefinition = "TEXT")
    private String metadata;

    /**
     * User who triggered event (system user for auto events, actual user for manual).
     */
    @Column(length = 100)
    private String triggeredBy;

    /**
     * Error message if event is a failure.
     */
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Whether event represents a retry attempt.
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isRetry = false;

    /**
     * Timestamp when event occurred (at creation).
     */
    @Column(nullable = false)
    @Builder.Default
    private Instant eventTimestamp = Instant.now();
}
