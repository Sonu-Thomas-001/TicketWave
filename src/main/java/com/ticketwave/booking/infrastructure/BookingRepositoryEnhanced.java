package com.ticketwave.booking.infrastructure;

import com.ticketwave.booking.domain.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Enhanced BookingRepository with optimized queries using JOIN FETCH.
 * 
 * PRODUCTION IMPROVEMENTS:
 * - Eager loading of associations to eliminate N+1 queries
 * - Efficient batch fetching for related entities
 */
@Repository
public interface BookingRepositoryEnhanced extends JpaRepository<Booking, UUID> {

    /**
     * Find booking by ID with all related entities eagerly loaded.
     * 
     * USE CASE: Display full booking details including payments, schedule, event.
     * 
     * N+1 PROBLEM ELIMINATED:
     * WITHOUT JOIN FETCH:
     * 1. SELECT * FROM bookings WHERE id = ?
     * 2. SELECT * FROM payments WHERE booking_id = ? (lazy load)
     * 3. SELECT * FROM schedules WHERE id = ? (lazy load)
     * 4. SELECT * FROM events WHERE id = ? (lazy load)
     * TOTAL: 4 queries
     * 
     * WITH JOIN FETCH:
     * 1. SELECT b.*, p.*, s.*, e.* FROM bookings b 
     *    LEFT JOIN payments p ON b.id = p.booking_id
     *    LEFT JOIN schedules s ON b.schedule_id = s.id
     *    LEFT JOIN events e ON s.event_id = e.id
     *    WHERE b.id = ?
     * TOTAL: 1 query (4x improvement!)
     * 
     * @param bookingId booking ID
     * @return optional booking with payments and schedule
     */
    @Query("SELECT b FROM Booking b " +
           "LEFT JOIN FETCH b.payments p " +
           "LEFT JOIN FETCH b.schedule s " +
           "LEFT JOIN FETCH s.route " +
           "WHERE b.id = :bookingId")
    Optional<Booking> findByIdWithPaymentsAndSchedule(@Param("bookingId") UUID bookingId);

    /**
     * Find booking by ID with booking items and seats.
     * 
     * USE CASE: Booking cancellation - need to access all items and seats efficiently.
     * 
     * ELIMINATES N+1 on booking items and seats.
     * 
     * @param bookingId booking ID
     * @return optional booking with items and seats
     */
    @Query("SELECT b FROM Booking b " +
           "LEFT JOIN FETCH b.bookingItems bi " +
           "LEFT JOIN FETCH bi.seat s " +
           "WHERE b.id = :bookingId")
    Optional<Booking> findByIdWithItemsAndSeats(@Param("bookingId") UUID bookingId);

    /**
     * Find booking by PNR with minimal data.
     * 
     * USE CASE: PNR lookup without loading all associations.
     * 
     * @param pnr passenger name record
     * @return optional booking
     */
    Optional<Booking> findByPnr(String pnr);

    /**
     * Find booking by PNR with full details.
     * 
     * USE CASE: Display full booking details via PNR lookup.
     * 
     * @param pnr passenger name record
     * @return optional booking with all associations
     */
    @Query("SELECT b FROM Booking b " +
           "LEFT JOIN FETCH b.bookingItems bi " +
           "LEFT JOIN FETCH bi.seat " +
           "LEFT JOIN FETCH bi.passenger " +
           "LEFT JOIN FETCH b.payments " +
           "LEFT JOIN FETCH b.schedule s " +
           "LEFT JOIN FETCH s.route " +
           "WHERE b.pnr = :pnr")
    Optional<Booking> findByPnrWithDetails(@Param("pnr") String pnr);
}
