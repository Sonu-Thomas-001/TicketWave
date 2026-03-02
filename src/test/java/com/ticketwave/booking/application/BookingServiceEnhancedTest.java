package com.ticketwave.booking.application;

import com.ticketwave.booking.domain.Booking;
import com.ticketwave.booking.domain.BookingStatus;
import com.ticketwave.booking.domain.SeatHold;
import com.ticketwave.booking.infrastructure.BookingItemRepository;
import com.ticketwave.booking.infrastructure.BookingRepository;
import com.ticketwave.catalog.domain.Schedule;
import com.ticketwave.catalog.domain.Seat;
import com.ticketwave.catalog.infrastructure.SeatRepository;
import com.ticketwave.common.exception.ConflictException;
import com.ticketwave.common.exception.ResourceNotFoundException;
import com.ticketwave.payment.application.PaymentIntentService;
import com.ticketwave.payment.domain.PaymentIntent;
import com.ticketwave.user.domain.Passenger;
import com.ticketwave.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookingServiceEnhanced Tests")
class BookingServiceEnhancedTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingItemRepository bookingItemRepository;

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private SeatHoldService seatHoldService;

    @Mock
    private PaymentIntentService paymentIntentService;

    @Mock
    private BookingEventLogger eventLogger;

    private BookingServiceEnhanced service;
    private User testUser;
    private Schedule testSchedule;
    private UUID scheduleId;
    private UUID seatId;
    private UUID passengerId;

    @BeforeEach
    void setUp() {
        service = new BookingServiceEnhanced(bookingRepository, bookingItemRepository, seatRepository,
                seatHoldService, paymentIntentService, eventLogger);

        scheduleId = UUID.randomUUID();
        seatId = UUID.randomUUID();
        passengerId = UUID.randomUUID();

        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .passwordHash("hash")
                .firstName("John")
                .lastName("Doe")
                .build();

        testSchedule = Schedule.builder()
                .id(scheduleId)
                .totalSeats(100)
                .availableSeats(50)
                .baseFare(BigDecimal.valueOf(1000))
                .departureTime(LocalDateTime.now().plusHours(5))
                .arrivalTime(LocalDateTime.now().plusHours(7))
                .active(true)
                .build();
    }

    @Test
    @DisplayName("Should initiate booking successfully")
    void testInitiateBooking_Success() {
        // Arrange
        SeatHold hold = SeatHold.builder()
                .seatId(seatId)
                .userId(testUser.getId())
                .holdToken("token123")
                .heldAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .build();

        Passenger passenger = Passenger.builder()
                .id(passengerId)
                .firstName("John")
                .lastName("Doe")
                .user(testUser)
                .build();

        Map<UUID, Passenger> passengerBookings = Map.of(seatId, passenger);

        when(seatHoldService.isHoldValid(seatId, testUser.getId(), "token123")).thenReturn(true);
        when(bookingRepository.saveAndFlush(any(Booking.class)))
                .thenAnswer(invocation -> {
                    Booking b = invocation.getArgument(0);
                    b.setId(UUID.randomUUID());
                    return b;
                });
        when(paymentIntentService.createPaymentIntent(any(), any(), any()))
                .thenReturn(PaymentIntent.builder()
                        .intentId("TW-123-abc")
                        .status("PENDING")
                        .expiresAt(Instant.now().plusSeconds(3600))
                        .build());

        // Act
        var result = service.initiateBooking(testUser, testSchedule, Arrays.asList(hold), 
                                              passengerBookings, "idempotency-key-123");

        // Assert
        assertNotNull(result.getBooking());
        assertNotNull(result.getPaymentIntent());
        assertEquals(BookingStatus.PENDING_PAYMENT.toString(), result.getBooking().getBookingStatus());
        assertEquals("TW-123-abc", result.getPaymentIntent().getIntentId());
        verify(eventLogger, times(2)).logEvent(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should fail initiation if seat hold invalid")
    void testInitiateBooking_InvalidHold() {
        // Arrange
        SeatHold hold = SeatHold.builder()
                .seatId(seatId)
                .userId(testUser.getId())
                .holdToken("expired-token")
                .build();

        when(seatHoldService.isHoldValid(seatId, testUser.getId(), "expired-token")).thenReturn(false);

        // Act & Assert
        assertThrows(ConflictException.class, () ->
                service.initiateBooking(testUser, testSchedule, Arrays.asList(hold), 
                                        new HashMap<>(), "idempotency-key")
        );
    }

    @Test
    @DisplayName("Should confirm booking successfully")
    void testConfirmBooking_Success() {
        // Arrange
        UUID bookingId = UUID.randomUUID();
        Booking booking = Booking.builder()
                .id(bookingId)
                .user(testUser)
                .schedule(testSchedule)
                .bookingStatus(BookingStatus.PENDING_PAYMENT.toString())
                .totalAmount(BigDecimal.valueOf(1000))
                .build();

        Seat seat = Seat.builder()
                .id(seatId)
                .seatNumber("1A")
                .seatStatus("AVAILABLE")
                .schedule(testSchedule)
                .build();

        Passenger passenger = Passenger.builder()
                .id(passengerId)
                .firstName("John")
                .lastName("Doe")
                .build();

        SeatHold hold = SeatHold.builder()
                .seatId(seatId)
                .userId(testUser.getId())
                .holdToken("token123")
                .build();

        Map<UUID, Passenger> passengerBookings = Map.of(seatId, passenger);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(seatHoldService.isHoldValid(seatId, testUser.getId(), "token123")).thenReturn(true);
        when(seatRepository.findById(seatId)).thenReturn(Optional.of(seat));
        when(seatRepository.saveAndFlush(any(Seat.class))).thenReturn(seat);
        when(bookingItemRepository.saveAndFlush(any())).thenReturn(null);
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenReturn(booking);

        // Act
        Booking confirmedBooking = service.confirmBooking(bookingId, Arrays.asList(hold), passengerBookings);

        // Assert
        assertNotNull(confirmedBooking);
        assertEquals(BookingStatus.CONFIRMED.toString(), confirmedBooking.getBookingStatus());
        verify(seatHoldService, times(1)).releaseSeatHold(seatId, "token123");
        verify(eventLogger, times(1)).logEvent(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should handle payment failure and release holds")
    void testHandlePaymentFailure() {
        // Arrange
        UUID bookingId = UUID.randomUUID();
        Booking booking = Booking.builder()
                .id(bookingId)
                .user(testUser)
                .bookingStatus(BookingStatus.PENDING_PAYMENT.toString())
                .build();

        SeatHold hold = SeatHold.builder()
                .seatId(seatId)
                .userId(testUser.getId())
                .holdToken("token123")
                .build();

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenReturn(booking);

        // Act
        service.handlePaymentFailure(bookingId, "insufficient_funds", Arrays.asList(hold));

        // Assert
        verify(seatHoldService, times(1)).releaseSeatHold(seatId, "token123");
        verify(bookingRepository, times(1)).saveAndFlush(argThat(b ->
                b.getBookingStatus().equals(BookingStatus.FAILED.toString())
        ));
        verify(eventLogger, times(1)).logEventWithError(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should cancel confirmed booking")
    void testCancelBooking_Success() {
        // Arrange
        UUID bookingId = UUID.randomUUID();
        Booking booking = Booking.builder()
                .id(bookingId)
                .user(testUser)
                .bookingStatus(BookingStatus.CONFIRMED.toString())
                .bookingItems(java.util.new HashSet<>())
                .build();

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenReturn(booking);

        // Act
        service.cancelBooking(bookingId, "User requested cancellation");

        // Assert
        verify(bookingRepository, times(1)).saveAndFlush(argThat(b ->
                b.getBookingStatus().equals(BookingStatus.CANCELLED.toString())
        ));
        verify(eventLogger, times(1)).logEvent(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should not cancel non-confirmed booking")
    void testCancelBooking_NotConfirmed() {
        // Arrange
        UUID bookingId = UUID.randomUUID();
        Booking booking = Booking.builder()
                .id(bookingId)
                .bookingStatus(BookingStatus.INITIATED.toString())
                .build();

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        // Act & Assert
        assertThrows(ConflictException.class, () ->
                service.cancelBooking(bookingId, "User requested cancellation")
        );
    }

    @Test
    @DisplayName("Should retrieve booking by PNR")
    void testGetBookingByPnr() {
        // Arrange
        String pnr = "TW12345678";
        Booking booking = Booking.builder()
                .id(UUID.randomUUID())
                .pnr(pnr)
                .build();

        when(bookingRepository.findByPnr(pnr)).thenReturn(Optional.of(booking));

        // Act
        Optional<Booking> result = service.getBookingByPnr(pnr);

        //Assert
        assertTrue(result.isPresent());
        assertEquals(pnr, result.get().getPnr());
    }
}
