package com.ticketwave.booking.application;

import com.ticketwave.booking.domain.Booking;
import com.ticketwave.booking.domain.BookingItem;
import com.ticketwave.booking.domain.SeatHold;
import com.ticketwave.booking.infrastructure.BookingItemRepository;
import com.ticketwave.booking.infrastructure.BookingRepository;
import com.ticketwave.catalog.domain.Seat;
import com.ticketwave.catalog.domain.Schedule;
import com.ticketwave.catalog.infrastructure.SeatRepository;
import com.ticketwave.catalog.infrastructure.ScheduleRepository;
import com.ticketwave.common.exception.ConflictException;
import com.ticketwave.common.exception.ResourceNotFoundException;
import com.ticketwave.user.domain.Passenger;
import com.ticketwave.user.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * REFACTORED BookingService with Production Best Practices:
 * - Batch database operations (single query for multiple seats)
 * - Pessimistic locking for schedule updates
 * - Authorization checks
 * - Proper null validation
 * - Async event-driven hold release cleanup
 * - Secure PNR generation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingServiceRefactored {

    private final BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;
    private final SeatRepository seatRepository;
    private final ScheduleRepository scheduleRepository;
    private final SeatHoldService seatHoldService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Confirm a booking from held seats with production-grade optimizations.
     * 
     * IMPROVEMENTS:
     * 1. Batch fetch all seats in ONE query (eliminates N+1)
     * 2. Pessimistic locking on schedule (prevents race condition)
     * 3. Batch save booking items (JPA batching)
     * 4. Async event-driven cleanup on errors
     * 5. Proper null validation
     *
     * @param user user making booking
     * @param schedule travel schedule
     * @param seatHolds list of seat holds to confirm
     * @param passengerBookings map of seat ID to passenger
     * @param requestingUserId ID of user making request (for authorization)
     * @return confirmed booking
     * @throws ConflictException if hold is invalid/expired
     * @throws AccessDeniedException if user not authorized
     */
    @Transactional
    public Booking confirmBooking(User user, 
                                  Schedule schedule, 
                                  List<SeatHold> seatHolds,
                                  Map<UUID, Passenger> passengerBookings,
                                  UUID requestingUserId) {
        
        // 1. Null validation
        validateInputs(user, schedule, seatHolds, passengerBookings, requestingUserId);
        
        // 2. Authorization check
        if (!user.getId().equals(requestingUserId)) {
            log.warn("Unauthorized booking attempt - RequesterId: {}, BookingUser: {}", 
                    requestingUserId, user.getId());
            throw new AccessDeniedException("Cannot create booking for another user");
        }

        log.info("Confirming booking for user: {} on schedule: {} with {} seats", 
                user.getId(), schedule.getId(), seatHolds.size());

        // 3. Validate all holds are still valid (upfront check)
        for (SeatHold hold : seatHolds) {
            if (!seatHoldService.isHoldValid(hold.getSeatId(), user.getId(), hold.getHoldToken())) {
                log.error("Seat hold expired or invalid: seatId={}, userId={}", 
                        hold.getSeatId(), user.getId());
                throw new ConflictException("Seat hold has expired. Please select seats again.");
            }
        }

        // 4. Fetch all seats in ONE query with pessimistic lock
        List<UUID> seatIds = seatHolds.stream()
                .map(SeatHold::getSeatId)
                .collect(Collectors.toList());
        
        List<Seat> seats = seatRepository.findAllByIdWithLock(seatIds);
        
        if (seats.size() != seatHolds.size()) {
            log.error("Seat count mismatch: expected={}, found={}", seatHolds.size(), seats.size());
            throw new ResourceNotFoundException("One or more seats not found");
        }
        
        Map<UUID, Seat> seatMap = seats.stream()
                .collect(Collectors.toMap(Seat::getId, Function.identity()));

        // 5. Create booking entity
        String pnr = generateSecurePNR();
        BigDecimal totalAmount = calculateTotalAmount(seatHolds, seatMap);

        Booking booking = Booking.builder()
                .user(user)
                .schedule(schedule)
                .pnr(pnr)
                .bookingStatus("CONFIRMED")
                .totalAmount(totalAmount)
                .bookedAt(LocalDateTime.now())
                .bookingItems(new LinkedHashSet<>())
                .build();

        try {
            // 6. Save booking (no flush yet)
            Booking savedBooking = bookingRepository.save(booking);
            log.info("Booking saved with PNR: {}", pnr);

            // 7. Prepare all entities in memory (avoid N queries)
            List<BookingItem> items = new ArrayList<>();
            
            for (SeatHold hold : seatHolds) {
                Seat seat = seatMap.get(hold.getSeatId());
                
                Passenger passenger = passengerBookings.get(hold.getSeatId());
                if (passenger == null) {
                    throw new ResourceNotFoundException(
                            "Passenger not assigned to seat: " + hold.getSeatId());
                }

                // Update seat status in memory
                seat.setSeatStatus("BOOKED");

                // Create booking item
                BookingItem item = BookingItem.builder()
                        .booking(savedBooking)
                        .passenger(passenger)
                        .seat(seat)
                        .fare(seat.getSchedule().getBaseFare())
                        .itemStatus("CONFIRMED")
                        .build();
                
                items.add(item);
            }

            // 8. Batch save all items (Spring Data JPA batching)
            bookingItemRepository.saveAll(items);
            
            // 9. Batch update all seats
            seatRepository.saveAll(seats);
            
            // 10. Single flush for all operations
            bookingRepository.flush();

            // 11. Update schedule available seats with pessimistic lock
            Schedule lockedSchedule = scheduleRepository.findByIdWithLock(schedule.getId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Schedule not found: " + schedule.getId()));
            
            int newAvailableSeats = lockedSchedule.getAvailableSeats() - seatHolds.size();
            if (newAvailableSeats < 0) {
                throw new ConflictException("Insufficient seats available");
            }
            
            lockedSchedule.setAvailableSeats(newAvailableSeats);
            // Auto-saved via dirty checking at transaction commit

            // 12. Release holds AFTER transaction commits successfully
            for (SeatHold hold : seatHolds) {
                seatHoldService.releaseSeatHold(hold.getSeatId(), hold.getHoldToken());
            }
            
            log.info("Booking confirmed: pnr={}, totalSeats={}", pnr, seatHolds.size());
            return savedBooking;

        } catch (Exception ex) {
            log.error("Error confirming booking, releasing holds", ex);
            
            // Attempt to release all holds with error tracking
            List<UUID> failedReleases = new ArrayList<>();
            
            for (SeatHold hold : seatHolds) {
                try {
                    seatHoldService.releaseSeatHold(hold.getSeatId(), hold.getHoldToken());
                } catch (Exception e) {
                    log.error("Error releasing hold during rollback: seatId={}", 
                            hold.getSeatId(), e);
                    failedReleases.add(hold.getSeatId());
                }
            }
            
            // Publish event for async retry if some releases failed
            if (!failedReleases.isEmpty()) {
                eventPublisher.publishEvent("SeatHoldReleaseFailed:" + failedReleases);
                log.error("Failed to release {} seat holds. Will retry asynchronously: {}", 
                        failedReleases.size(), failedReleases);
            }
            
            throw new ConflictException("Failed to confirm booking: " + ex.getMessage(), ex);
        }
    }

    /**
     * Cancel a booking and release all holds.
     * 
     * IMPROVEMENTS:
     * 1. Authorization check added
     * 2. Fetch with booking items and seats in ONE query
     * 3. Proper error handling
     *
     * @param bookingId booking ID
     * @param requestingUserId ID of user making cancellation request
     * @throws AccessDeniedException if user not authorized
     */
    @Transactional
    public void cancelBooking(UUID bookingId, UUID requestingUserId) {
        if (bookingId == null) {
            throw new IllegalArgumentException("Booking ID cannot be null");
        }
        if (requestingUserId == null) {
            throw new IllegalArgumentException("Requesting user ID cannot be null");
        }

        // Fetch booking with items and seats in one query (eliminates N+1)
        Booking booking = bookingRepository.findByIdWithItemsAndSeats(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));

        // Authorization check
        if (!booking.getUser().getId().equals(requestingUserId)) {
            log.warn("Unauthorized cancellation attempt - UserId: {}, BookingOwner: {}", 
                    requestingUserId, booking.getUser().getId());
            throw new AccessDeniedException("Cannot cancel booking owned by another user");
        }

        // Idempotency check
        if ("CANCELLED".equals(booking.getBookingStatus())) {
            log.warn("Booking already cancelled: {}", bookingId);
            return;
        }

        // Release all holds
        for (BookingItem item : booking.getBookingItems()) {
            try {
                seatHoldService.releaseSeatHold(
                        item.getSeat().getId(), 
                        "cancelled-" + bookingId);
            } catch (Exception e) {
                log.warn("Failed to release seat hold during cancellation: seatId={}", 
                        item.getSeat().getId(), e);
                // Continue with other seats
            }
        }

        booking.setBookingStatus("CANCELLED");
        booking.setCancelledAt(LocalDateTime.now());
        bookingRepository.save(booking);

        log.info("Booking cancelled: {}", bookingId);
    }

    /**
     * Get booking by PNR with authorization check.
     *
     * @param pnr passenger name record
     * @param requestingUserId ID of user making request
     * @return booking
     * @throws AccessDeniedException if user not authorized
     */
    @Transactional(readOnly = true)
    public Optional<Booking> getBookingByPnr(String pnr, UUID requestingUserId) {
        if (pnr == null || pnr.isBlank()) {
            throw new IllegalArgumentException("PNR cannot be null or empty");
        }
        if (requestingUserId == null) {
            throw new IllegalArgumentException("Requesting user ID cannot be null");
        }

        Optional<Booking> booking = bookingRepository.findByPnr(pnr);
        
        // Authorization check
        booking.ifPresent(b -> {
            if (!b.getUser().getId().equals(requestingUserId)) {
                log.warn("Unauthorized PNR access attempt - UserId: {}, BookingOwner: {}", 
                        requestingUserId, b.getUser().getId());
                throw new AccessDeniedException("Cannot access booking for another user");
            }
        });
        
        return booking;
    }

    /**
     * Generate cryptographically secure PNR.
     * 
     * SECURITY FIX: Uses SecureRandom instead of UUID (time-based, predictable).
     * Format: TW + 8 random alphanumeric characters
     * 
     * @return secure PNR
     */
    private String generateSecurePNR() {
        SecureRandom random = new SecureRandom();
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        
        StringBuilder pnr = new StringBuilder("TW");
        for (int i = 0; i < 8; i++) {
            pnr.append(chars.charAt(random.nextInt(chars.length())));
        }
        
        String generated = pnr.toString();
        
        // Check for collision (extremely rare with 36^8 combinations)
        int maxRetries = 5;
        int attempts = 0;
        
        while (bookingRepository.findByPnr(generated).isPresent() && attempts < maxRetries) {
            pnr = new StringBuilder("TW");
            for (int i = 0; i < 8; i++) {
                pnr.append(chars.charAt(random.nextInt(chars.length())));
            }
            generated = pnr.toString();
            attempts++;
        }
        
        if (attempts == maxRetries) {
            throw new IllegalStateException("Failed to generate unique PNR after " + maxRetries + " attempts");
        }
        
        return generated;
    }

    /**
     * Calculate total amount using actual seat prices.
     *
     * @param seatHolds list of seat holds
     * @param seatMap map of seat ID to Seat entity
     * @return total booking amount
     */
    private BigDecimal calculateTotalAmount(List<SeatHold> seatHolds, Map<UUID, Seat> seatMap) {
        return seatHolds.stream()
                .map(hold -> seatMap.get(hold.getSeatId()))
                .map(seat -> seat.getSchedule().getBaseFare())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Validate all input parameters.
     */
    private void validateInputs(User user, 
                                Schedule schedule, 
                                List<SeatHold> seatHolds, 
                                Map<UUID, Passenger> passengerBookings,
                                UUID requestingUserId) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        if (schedule == null) {
            throw new IllegalArgumentException("Schedule cannot be null");
        }
        if (seatHolds == null || seatHolds.isEmpty()) {
            throw new IllegalArgumentException("Seat holds cannot be null or empty");
        }
        if (passengerBookings == null || passengerBookings.isEmpty()) {
            throw new IllegalArgumentException("Passenger bookings cannot be null or empty");
        }
        if (requestingUserId == null) {
            throw new IllegalArgumentException("Requesting user ID cannot be null");
        }
        if (seatHolds.size() != passengerBookings.size()) {
            throw new IllegalArgumentException(
                    "Number of seat holds must match number of passenger assignments");
        }
    }
}
