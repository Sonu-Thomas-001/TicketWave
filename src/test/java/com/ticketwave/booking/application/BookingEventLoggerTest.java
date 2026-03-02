package com.ticketwave.booking.application;

import com.ticketwave.booking.domain.BookingEvent;
import com.ticketwave.booking.domain.BookingEventType;
import com.ticketwave.booking.domain.BookingStatus;
import com.ticketwave.booking.infrastructure.BookingEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookingEventLogger Tests")
class BookingEventLoggerTest {

    @Mock
    private BookingEventRepository repository;

    private BookingEventLogger logger;
    private ObjectMapper objectMapper;
    private UUID bookingId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        logger = new BookingEventLogger(repository, objectMapper);
        bookingId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Should log event with status transition")
    void testLogEvent_WithTransition() {
        // Arrange
        Map<String, Object> metadata = Map.of("seatCount", 2, "amount", "3000");
        when(repository.save(any(BookingEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        logger.logEvent(
                bookingId,
                BookingEventType.BOOKING_INITIATED,
                BookingStatus.INITIATED,
                BookingStatus.PENDING_PAYMENT,
                metadata
        );

        // Assert
        verify(repository, times(1)).save(argThat(event ->
                bookingId.equals(event.getBookingId()) &&
                "BOOKING_INITIATED".equals(event.getEventType()) &&
                "INITIATED".equals(event.getPreviousStatus()) &&
                "PENDING_PAYMENT".equals(event.getNewStatus())
        ));
    }

    @Test
    @DisplayName("Should log event with error details")
    void testLogEventWithError() {
        // Arrange
        String errorMsg = "Payment gateway timeout";
        Map<String, Object> metadata = Map.of("gatewayCode", "timeout_500");
        when(repository.save(any(BookingEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        logger.logEventWithError(
                bookingId,
                BookingEventType.PAYMENT_FAILED,
                BookingStatus.PENDING_PAYMENT,
                BookingStatus.FAILED,
                errorMsg,
                metadata
        );

        // Assert
        verify(repository, times(1)).save(argThat(event ->
                event.getErrorMessage().equals(errorMsg) &&
                "PAYMENT_FAILED".equals(event.getEventType())
        ));
    }

    @Test
    @DisplayName("Should log retry events")
    void testLogRetry() {
        // Arrange
        Map<String, Object> metadata = Map.of("attemptedAt", "2024-03-02T10:30:00Z");
        when(repository.save(any(BookingEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        logger.logRetry(
                bookingId,
                BookingEventType.RETRY_ATTEMPT,
                BookingStatus.PENDING_PAYMENT,
                1,
                metadata
        );

        // Assert
        verify(repository, times(1)).save(argThat(event ->
                event.getIsRetry() &&
                event.getMetadata().contains("\"retryAttempt\":1")
        ));
    }

    @Test
    @DisplayName("Should retrieve booking events")
    void testGetBookingEvents() {
        // Arrange
        BookingEvent event1 = BookingEvent.builder()
                .bookingId(bookingId)
                .eventType("BOOKING_INITIATED")
                .build();
        BookingEvent event2 = BookingEvent.builder()
                .bookingId(bookingId)
                .eventType("PAYMENT_CONFIRMED")
                .build();

        when(repository.findByBookingIdOrderByCreatedAtDesc(bookingId))
                .thenReturn(Arrays.asList(event2, event1));

        // Act
        List<BookingEvent> events = logger.getBookingEvents(bookingId);

        // Assert
        assertEquals(2, events.size());
        assertEquals("PAYMENT_CONFIRMED", events.get(0).getEventType());
        assertEquals("BOOKING_INITIATED", events.get(1).getEventType());
    }

    @Test
    @DisplayName("Should get retry count for booking")
    void testGetRetryCount() {
        // Arrange
        when(repository.countByBookingIdAndIsRetryTrue(bookingId)).thenReturn(2L);

        // Act
        long count = logger.getRetryCount(bookingId);

        // Assert
        assertEquals(2L, count);
        verify(repository, times(1)).countByBookingIdAndIsRetryTrue(bookingId);
    }

    @Test
    @DisplayName("Should get event count by type")
    void testGetEventCount() {
        // Arrange
        when(repository.countByEventType("BOOKING_CONFIRMED")).thenReturn(150L);

        // Act
        long count = logger.getEventCount(BookingEventType.BOOKING_CONFIRMED);

        // Assert
        assertEquals(150L, count);
    }

    @Test
    @DisplayName("Should handle metadata serialization")
    void testMetadataSerialization() {
        // Arrange
        Map<String, Object> complexMetadata = new HashMap<>();
        complexMetadata.put("intentId", "TW-123-abc");
        complexMetadata.put("amount", 1500.50);
        complexMetadata.put("seats", Arrays.asList("1A", "2B"));
        complexMetadata.put("nested", Map.of("key", "value"));

        when(repository.save(any(BookingEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        logger.logEvent(
                bookingId,
                BookingEventType.BOOKING_INITIATED,
                null,
                BookingStatus.INITIATED,
                complexMetadata
        );

        // Assert
        verify(repository, times(1)).save(argThat(event ->
                event.getMetadata() != null &&
                event.getMetadata().contains("intentId") &&
                event.getMetadata().contains("TW-123-abc")
        ));
    }
}
