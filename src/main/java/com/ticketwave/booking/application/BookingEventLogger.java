package com.ticketwave.booking.application;

import com.ticketwave.booking.domain.BookingEvent;
import com.ticketwave.booking.domain.BookingEventType;
import com.ticketwave.booking.domain.BookingStatus;
import com.ticketwave.booking.infrastructure.BookingEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for logging booking lifecycle events for audit trail and debugging.
 * 
 * Every significant booking operation should log an event:
 * - BOOKING_INITIATED
 * - SEAT_HOLD_CREATED
 * - PAYMENT_INTENT_CREATED
 * - PAYMENT_CONFIRMED / PAYMENT_FAILED
 * - BOOKING_CONFIRMED / BOOKING_FAILED
 * - HOLD_RELEASED
 * - RETRY_ATTEMPT
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BookingEventLogger {

    private final BookingEventRepository bookingEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * Log a booking event with state transition.
     */
    @Transactional
    public void logEvent(UUID bookingId, BookingEventType eventType, 
                        BookingStatus previousStatus, BookingStatus newStatus,
                        Map<String, Object> metadata) {
        logEvent(bookingId, eventType, 
                previousStatus != null ? previousStatus.toString() : null,
                newStatus != null ? newStatus.toString() : null,
                metadata, null, false, "SYSTEM");
    }

    /**
     * Log event with error details for failures.
     */
    @Transactional
    public void logEventWithError(UUID bookingId, BookingEventType eventType,
                                  BookingStatus previousStatus, BookingStatus newStatus,
                                  String errorMessage, Map<String, Object> metadata) {
        logEvent(bookingId, eventType,
                previousStatus != null ? previousStatus.toString() : null,
                newStatus != null ? newStatus.toString() : null,
                metadata, errorMessage, false, "SYSTEM");
    }

    /**
     * Log retry event.
     */
    @Transactional
    public void logRetry(UUID bookingId, BookingEventType eventType,
                        BookingStatus status, int retryAttempt, Map<String, Object> metadata) {
        Map<String, Object> retryMetadata = metadata != null ? new java.util.HashMap<>(metadata) : new java.util.HashMap<>();
        retryMetadata.put("retryAttempt", retryAttempt);
        
        logEvent(bookingId, eventType,
                null, status.toString(),
                retryMetadata, null, true, "SYSTEM");
    }

    /**
     * Internal logging method.
     */
    @Transactional
    private void logEvent(UUID bookingId, BookingEventType eventType,
                         String previousStatus, String newStatus,
                         Map<String, Object> metadata, String errorMessage,
                         boolean isRetry, String triggeredBy) {
        try {
            String metadataJson = metadata != null ? objectMapper.writeValueAsString(metadata) : "{}";
            
            BookingEvent event = BookingEvent.builder()
                    .bookingId(bookingId)
                    .eventType(eventType.name())
                    .previousStatus(previousStatus)
                    .newStatus(newStatus)
                    .metadata(metadataJson)
                    .errorMessage(errorMessage)
                    .isRetry(isRetry)
                    .triggeredBy(triggeredBy)
                    .eventTimestamp(Instant.now())
                    .build();

            bookingEventRepository.save(event);
            
            log.info("Logged booking event - BookingId: {}, EventType: {}, Status: {} → {}, IsRetry: {}",
                    bookingId, eventType, previousStatus, newStatus, isRetry);
            
        } catch (Exception e) {
            log.error("Failed to log booking event - BookingId: {}, EventType: {}", bookingId, eventType, e);
            // Don't rethrow - logging failure shouldn't crash booking process
        }
    }

    /**
     * Get all events for a booking (for audit trail).
     */
    public List<BookingEvent> getBookingEvents(UUID bookingId) {
        return bookingEventRepository.findByBookingIdOrderByCreatedAtDesc(bookingId);
    }

    /**
     * Count retry attempts for a booking.
     */
    public long getRetryCount(UUID bookingId) {
        return bookingEventRepository.countByBookingIdAndIsRetryTrue(bookingId);
    }

    /**
     * Get metrics: count events by type.
     */
    public long getEventCount(BookingEventType eventType) {
        return bookingEventRepository.countByEventType(eventType.name());
    }
}
