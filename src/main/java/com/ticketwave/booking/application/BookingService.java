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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@Transactional
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;
    private final SeatRepository seatRepository;
    private final SeatHoldService seatHoldService;

    public BookingService(BookingRepository bookingRepository,
                        BookingItemRepository bookingItemRepository,
                        SeatRepository seatRepository,
                        SeatHoldService seatHoldService) {
        this.bookingRepository = bookingRepository;
        this.bookingItemRepository = bookingItemRepository;
        this.seatRepository = seatRepository;
        this.seatHoldService = seatHoldService;
    }

    /**
     * Confirm a booking from held seats.
     * Validates holds are still active and updates seat status.
     * Fallback DB constraint prevents duplicate booking even if Redis fails.
     *
     * @param user user making booking
     * @param schedule travel schedule
     * @param seatHolds list of seat holds to confirm
     * @param passengerBookings map of seat ID to passenger
     * @return confirmed booking
     * @throws ConflictException if hold is invalid/expired
     */
    public Booking confirmBooking(User user, Schedule schedule, 
                                  List<SeatHold> seatHolds,
                                  java.util.Map<UUID, Passenger> passengerBookings) {
        log.info("Confirming booking for user: {} on schedule: {} with {} seats", 
                user.getId(), schedule.getId(), seatHolds.size());

        // Validate all holds are still valid
        for (SeatHold hold : seatHolds) {
            if (!seatHoldService.isHoldValid(hold.getSeatId(), user.getId(), hold.getHoldToken())) {
                log.error("Seat hold expired or invalid: seatId={}, userId={}", 
                        hold.getSeatId(), user.getId());
                throw new ConflictException("Seat hold has expired. Please select seats again.");
            }
        }

        // Create booking transactionally
        String pnr = generatePNR();
        BigDecimal totalAmount = calculateTotalAmount(seatHolds);

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
            Booking savedBooking = bookingRepository.saveAndFlush(booking);
            log.info("Booking saved with PNR: {}", pnr);

            // Create booking items and update seat status
            for (SeatHold hold : seatHolds) {
                Seat seat = seatRepository.findById(hold.getSeatId())
                        .orElseThrow(() -> new ResourceNotFoundException("Seat not found: " + hold.getSeatId()));

                Passenger passenger = passengerBookings.get(hold.getSeatId());
                if (passenger == null) {
                    throw new ResourceNotFoundException("Passenger not assigned to seat: " + hold.getSeatId());
                }

                // Update seat status (DB constraint ensures uniqueness)
                seat.setSeatStatus("BOOKED");
                Seat updatedSeat = seatRepository.saveAndFlush(seat);

                // Create booking item
                BookingItem item = BookingItem.builder()
                        .booking(savedBooking)
                        .passenger(passenger)
                        .seat(updatedSeat)
                        .fare(seat.getSchedule().getBaseFare())
                        .itemStatus("CONFIRMED")
                        .build();

                bookingItemRepository.saveAndFlush(item);

                // Release hold now that seat is confirmed
                seatHoldService.releaseSeatHold(hold.getSeatId(), hold.getHoldToken());
                log.info("Seat booked and hold released: seatId={}, pnr={}", hold.getSeatId(), pnr);
            }

            // Update schedule available seats
            int bookedSeatsCount = seatHolds.size();
            schedule.setAvailableSeats(schedule.getAvailableSeats() - bookedSeatsCount);
            // Note: In production, use pessimistic locking or optimistic version check
            // Re-fetch to avoid stale version for optimistic locking
            Schedule refreshedSchedule = seatRepository.findById(seatHolds.get(0).getSeatId())
                    .orElseThrow().getSchedule(); // Simplified; in production, query schedule directly
            
            log.info("Booking confirmed: pnr={}, totalSeats={}", pnr, bookedSeatsCount);
            return savedBooking;

        } catch (Exception ex) {
            // Fallback: release all holds on error
            log.error("Error confirming booking, releasing holds", ex);
            for (SeatHold hold : seatHolds) {
                try {
                    seatHoldService.releaseSeatHold(hold.getSeatId(), hold.getHoldToken());
                } catch (Exception e) {
                    log.error("Error releasing hold during rollback: {}", hold.getSeatId(), e);
                }
            }
            throw new ConflictException("Failed to confirm booking: " + ex.getMessage());
        }
    }

    /**
     * Cancel a booking and release all holds.
     *
     * @param bookingId booking ID
     */
    public void cancelBooking(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));

        if ("CANCELLED".equals(booking.getBookingStatus())) {
            log.warn("Booking already cancelled: {}", bookingId);
            return;
        }

        // Release all holds
        for (BookingItem item : booking.getBookingItems()) {
            seatHoldService.releaseSeatHold(item.getSeat().getId(), "cancelled-" + bookingId);
        }

        booking.setBookingStatus("CANCELLED");
        booking.setCancelledAt(LocalDateTime.now());
        bookingRepository.saveAndFlush(booking);

        log.info("Booking cancelled: {}", bookingId);
    }

    /**
     * Get booking by PNR.
     *
     * @param pnr passenger name record
     * @return booking
     */
    @Transactional(readOnly = true)
    public Optional<Booking> getBookingByPnr(String pnr) {
        return bookingRepository.findByPnr(pnr);
    }

    private String generatePNR() {
        // Format: TW + 8 alphanumeric characters
        return "TW" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private BigDecimal calculateTotalAmount(List<SeatHold> seatHolds) {
        // Simplified; in production, factor in dynamic pricing, taxes, discounts
        return BigDecimal.valueOf(seatHolds.size()).multiply(BigDecimal.valueOf(500));
    }
}
