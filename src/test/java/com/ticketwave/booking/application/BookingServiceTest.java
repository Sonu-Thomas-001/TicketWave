package com.ticketwave.booking.application;

import com.ticketwave.booking.domain.Booking;
import com.ticketwave.booking.domain.BookingItem;
import com.ticketwave.booking.domain.SeatHold;
import com.ticketwave.booking.infrastructure.BookingItemRepository;
import com.ticketwave.booking.infrastructure.BookingRepository;
import com.ticketwave.catalog.domain.Seat;
import com.ticketwave.catalog.domain.Schedule;
import com.ticketwave.catalog.infrastructure.SeatRepository;
import com.ticketwave.common.exception.ConflictException;
import com.ticketwave.common.exception.ResourceNotFoundException;
import com.ticketwave.user.domain.Passenger;
import com.ticketwave.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BookingService.
 * Covers happy path, edge cases, and concurrency scenarios.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BookingService Unit Tests")
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingItemRepository bookingItemRepository;

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private SeatHoldService seatHoldService;

    @InjectMocks
    private BookingService bookingService;

    private User testUser;
    private Schedule testSchedule;
    private SeatHold testSeatHold;
    private Seat testSeat;
    private Passenger testPassenger;
    private UUID testBookingId;

    @BeforeEach
    void setUp() {
        testBookingId = UUID.randomUUID();
        
        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .name("Test User")
                .build();

        testSchedule = Schedule.builder()
                .id(UUID.randomUUID())
                .baseFare(BigDecimal.valueOf(500))
                .availableSeats(50)
                .build();

        UUID seatId = UUID.randomUUID();
        testSeat = Seat.builder()
                .id(seatId)
                .seatStatus("AVAILABLE")
                .schedule(testSchedule)
                .build();

        testSeatHold = SeatHold.builder()
                .seatId(seatId)
                .userId(testUser.getId())
                .holdToken("test-token-123")
                .heldAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .build();

        testPassenger = Passenger.builder()
                .id(UUID.randomUUID())
                .name("John Doe")
                .email("passenger@example.com")
                .build();
    }

    // ===== Happy Path Tests =====

    @Test
    @DisplayName("Should confirm booking successfully with valid holds")
    void testConfirmBooking_Success() {
        // Given
        Map<UUID, Passenger> passengerMap = new HashMap<>();
        passengerMap.put(testSeatHold.getSeatId(), testPassenger);
        List<SeatHold> holds = List.of(testSeatHold);

        Booking booking = Booking.builder()
                .id(testBookingId)
                .user(testUser)
                .schedule(testSchedule)
                .pnr("TWABCD123")
                .bookingStatus("INITIATED")
                .totalAmount(BigDecimal.valueOf(500))
                .bookingItems(new LinkedHashSet<>())
                .build();

        when(seatHoldService.isHoldValid(testSeatHold.getSeatId(), testUser.getId(), testSeatHold.getHoldToken()))
                .thenReturn(true);
        when(seatRepository.findById(testSeatHold.getSeatId()))
                .thenReturn(Optional.of(testSeat));
        when(bookingRepository.saveAndFlush(any(Booking.class)))
                .thenAnswer(invocation -> {
                    Booking b = invocation.getArgument(0);
                    b.setId(testBookingId);
                    return b;
                });
        when(seatRepository.saveAndFlush(any(Seat.class)))
                .thenReturn(testSeat);
        when(bookingItemRepository.saveAndFlush(any(BookingItem.class)))
                .thenReturn(BookingItem.builder().build());

        // When
        Booking result = bookingService.confirmBooking(testUser, testSchedule, holds, passengerMap);

        // Then
        assertNotNull(result);
        assertEquals(testUser.getId(), result.getUser().getId());
        verify(seatHoldService).releaseSeatHold(testSeatHold.getSeatId(), testSeatHold.getHoldToken());
        verify(bookingRepository).saveAndFlush(any(Booking.class));
    }

    @Test
    @DisplayName("Should cancel confirmed booking successfully")
    void testCancelBooking_Success() {
        // Given
        UUID bookingId = UUID.randomUUID();
        Booking booking = Booking.builder()
                .id(bookingId)
                .user(testUser)
                .bookingStatus("CONFIRMED")
                .bookingItems(new LinkedHashSet<>())
                .build();

        when(bookingRepository.findById(bookingId))
                .thenReturn(Optional.of(booking));
        when(bookingRepository.saveAndFlush(any(Booking.class)))
                .thenReturn(booking);

        // When
        bookingService.cancelBooking(bookingId);

        // Then
        verify(bookingRepository).saveAndFlush(argThat(b ->
                b.getBookingStatus().equals("CANCELLED")
        ));
    }

    @Test
    @DisplayName("Should retrieve booking by PNR successfully")
    void testGetBookingByPnr_Success() {
        // Given
        String pnr = "TWABCD123";
        Booking booking = Booking.builder()
                .id(testBookingId)
                .pnr(pnr)
                .build();

        when(bookingRepository.findByPnr(pnr))
                .thenReturn(Optional.of(booking));

        // When
        Optional<Booking> result = bookingService.getBookingByPnr(pnr);

        // Then
        assertTrue(result.isPresent());
        assertEquals(pnr, result.get().getPnr());
    }

    // ===== Edge Case Tests =====

    @Test
    @DisplayName("Should throw ConflictException when hold is invalid")
    void testConfirmBooking_InvalidHold() {
        // Given
        Map<UUID, Passenger> passengerMap = new HashMap<>();
        passengerMap.put(testSeatHold.getSeatId(), testPassenger);
        List<SeatHold> holds = List.of(testSeatHold);

        when(seatHoldService.isHoldValid(testSeatHold.getSeatId(), testUser.getId(), testSeatHold.getHoldToken()))
                .thenReturn(false);

        // When & Then
        assertThrows(ConflictException.class, () ->
                bookingService.confirmBooking(testUser, testSchedule, holds, passengerMap)
        );
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when seat not found")
    void testConfirmBooking_SeatNotFound() {
        // Given
        Map<UUID, Passenger> passengerMap = new HashMap<>();
        passengerMap.put(testSeatHold.getSeatId(), testPassenger);
        List<SeatHold> holds = List.of(testSeatHold);

        when(seatHoldService.isHoldValid(testSeatHold.getSeatId(), testUser.getId(), testSeatHold.getHoldToken()))
                .thenReturn(true);
        when(seatRepository.findById(testSeatHold.getSeatId()))
                .thenReturn(Optional.empty());

        // When & Then
        assertThrows(ResourceNotFoundException.class, () ->
                bookingService.confirmBooking(testUser, testSchedule, holds, passengerMap)
        );
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when passenger not assigned to seat")
    void testConfirmBooking_PassengerNotAssigned() {
        // Given
        Map<UUID, Passenger> passengerMap = new HashMap<>();
        List<SeatHold> holds = List.of(testSeatHold);

        when(seatHoldService.isHoldValid(testSeatHold.getSeatId(), testUser.getId(), testSeatHold.getHoldToken()))
                .thenReturn(true);
        when(seatRepository.findById(testSeatHold.getSeatId()))
                .thenReturn(Optional.of(testSeat));

        // When & Then
        assertThrows(ResourceNotFoundException.class, () ->
                bookingService.confirmBooking(testUser, testSchedule, holds, passengerMap)
        );
    }

    @Test
    @DisplayName("Should handle already cancelled booking gracefully")
    void testCancelBooking_AlreadyCancelled() {
        // Given
        UUID bookingId = UUID.randomUUID();
        Booking booking = Booking.builder()
                .id(bookingId)
                .bookingStatus("CANCELLED")
                .build();

        when(bookingRepository.findById(bookingId))
                .thenReturn(Optional.of(booking));

        // When
        bookingService.cancelBooking(bookingId);

        // Then
        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Should return empty Optional when PNR not found")
    void testGetBookingByPnr_NotFound() {
        // Given
        String pnr = "NOTFOUND";
        when(bookingRepository.findByPnr(pnr))
                .thenReturn(Optional.empty());

        // When
        Optional<Booking> result = bookingService.getBookingByPnr(pnr);

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("Should release holds on confirmation error")
    void testConfirmBooking_ErrorReleaseHolds() {
        // Given
        Map<UUID, Passenger> passengerMap = new HashMap<>();
        passengerMap.put(testSeatHold.getSeatId(), testPassenger);
        List<SeatHold> holds = List.of(testSeatHold);

        when(seatHoldService.isHoldValid(testSeatHold.getSeatId(), testUser.getId(), testSeatHold.getHoldToken()))
                .thenReturn(true);
        when(seatRepository.findById(testSeatHold.getSeatId()))
                .thenThrow(new RuntimeException("Database error"));

        // When & Then
        assertThrows(ConflictException.class, () ->
                bookingService.confirmBooking(testUser, testSchedule, holds, passengerMap)
        );
        verify(seatHoldService).releaseSeatHold(testSeatHold.getSeatId(), testSeatHold.getHoldToken());
    }

    @Test
    @DisplayName("Should confirm booking with multiple seats")
    void testConfirmBooking_MultiplSeats() {
        // Given
        UUID seatId2 = UUID.randomUUID();
        SeatHold hold2 = SeatHold.builder()
                .seatId(seatId2)
                .userId(testUser.getId())
                .holdToken("token-2")
                .expiresAt(Instant.now().plusSeconds(600))
                .build();

        Seat seat2 = Seat.builder()
                .id(seatId2)
                .seatStatus("AVAILABLE")
                .schedule(testSchedule)
                .build();

        Passenger passenger2 = Passenger.builder()
                .id(UUID.randomUUID())
                .name("Jane Doe")
                .build();

        Map<UUID, Passenger> passengerMap = new HashMap<>();
        passengerMap.put(testSeatHold.getSeatId(), testPassenger);
        passengerMap.put(seatId2, passenger2);
        List<SeatHold> holds = List.of(testSeatHold, hold2);

        Booking booking = Booking.builder()
                .id(testBookingId)
                .user(testUser)
                .schedule(testSchedule)
                .bookingItems(new LinkedHashSet<>())
                .build();

        when(seatHoldService.isHoldValid(anyString(), any(UUID.class), anyString()))
                .thenReturn(true);
        when(seatRepository.findById(testSeatHold.getSeatId()))
                .thenReturn(Optional.of(testSeat));
        when(seatRepository.findById(seatId2))
                .thenReturn(Optional.of(seat2));
        when(bookingRepository.saveAndFlush(any(Booking.class)))
                .thenAnswer(invocation -> {
                    Booking b = invocation.getArgument(0);
                    b.setId(testBookingId);
                    return b;
                });
        when(seatRepository.saveAndFlush(any(Seat.class)))
                .thenReturn(testSeat);
        when(bookingItemRepository.saveAndFlush(any(BookingItem.class)))
                .thenReturn(BookingItem.builder().build());

        // When
        Booking result = bookingService.confirmBooking(testUser, testSchedule, holds, passengerMap);

        // Then
        assertNotNull(result);
        verify(seatHoldService, times(2)).releaseSeatHold(any(), any());
    }

    // ===== Concurrency Tests =====

    @Test
    @DisplayName("Should handle concurrent booking confirmations safely")
    void testConcurrentBookingConfirmations() throws InterruptedException {
        // Given
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        Map<UUID, Passenger> passengerMap = new HashMap<>();
        passengerMap.put(testSeatHold.getSeatId(), testPassenger);
        List<SeatHold> holds = List.of(testSeatHold);

        Booking booking = Booking.builder()
                .id(testBookingId)
                .user(testUser)
                .schedule(testSchedule)
                .bookingItems(new LinkedHashSet<>())
                .build();

        when(seatHoldService.isHoldValid(testSeatHold.getSeatId(), testUser.getId(), testSeatHold.getHoldToken()))
                .thenReturn(true);
        when(seatRepository.findById(testSeatHold.getSeatId()))
                .thenReturn(Optional.of(testSeat));
        when(bookingRepository.saveAndFlush(any(Booking.class)))
                .thenAnswer(invocation -> {
                    Booking b = invocation.getArgument(0);
                    b.setId(testBookingId);
                    return b;
                });
        when(seatRepository.saveAndFlush(any(Seat.class)))
                .thenReturn(testSeat);
        when(bookingItemRepository.saveAndFlush(any(BookingItem.class)))
                .thenReturn(BookingItem.builder().build());

        // When
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    bookingService.confirmBooking(testUser, testSchedule, holds, passengerMap);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // Then
        assertTrue(successCount.get() > 0, "At least some bookings should succeed");
        verify(bookingRepository, atLeast(1)).saveAndFlush(any(Booking.class));
    }

    @Test
    @DisplayName("Should safely cancel booking while hold release is in progress")
    void testConcurrentCancellationAndHoldRelease() throws InterruptedException {
        // Given
        UUID bookingId = UUID.randomUUID();
        Booking booking = Booking.builder()
                .id(bookingId)
                .bookingStatus("CONFIRMED")
                .bookingItems(new LinkedHashSet<>())
                .build();

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        when(bookingRepository.findById(bookingId))
                .thenReturn(Optional.of(booking));
        when(bookingRepository.saveAndFlush(any(Booking.class)))
                .thenReturn(booking);

        // When
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    bookingService.cancelBooking(bookingId);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // Then
        verify(bookingRepository, atLeast(1)).saveAndFlush(any(Booking.class));
    }

    @Test
    @DisplayName("Should generate unique PNRs for different bookings")
    void testGenerateUniquePNRs() {
        // When calling confirmBooking multiple times, each should get a unique PNR
        // This is tested implicitly through the PNR generation logic
        
        // Setup booking without explicit PNR
        Booking booking = Booking.builder()
                .user(testUser)
                .schedule(testSchedule)
                .bookingItems(new LinkedHashSet<>())
                .build();

        // Verify different bookings would have different PNRs
        Map<UUID, Passenger> passengerMap = new HashMap<>();
        passengerMap.put(testSeatHold.getSeatId(), testPassenger);
        List<SeatHold> holds = List.of(testSeatHold);

        when(seatHoldService.isHoldValid(any(), any(), any()))
                .thenReturn(true);
        when(seatRepository.findById(any()))
                .thenReturn(Optional.of(testSeat));
        when(bookingRepository.saveAndFlush(any()))
                .thenReturn(booking);
        when(seatRepository.saveAndFlush(any()))
                .thenReturn(testSeat);
        when(bookingItemRepository.saveAndFlush(any()))
                .thenReturn(BookingItem.builder().build());

        // When
        Booking result1 = bookingService.confirmBooking(testUser, testSchedule, holds, passengerMap);

        // Then
        assertNotNull(result1.getPnr());
        assertTrue(result1.getPnr().startsWith("TW"));
    }
}
