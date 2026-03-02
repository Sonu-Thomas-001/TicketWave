package com.ticketwave.booking.infrastructure;

import com.ticketwave.booking.domain.Booking;
import com.ticketwave.user.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {
    Optional<Booking> findByPnr(String pnr);

    Page<Booking> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    Page<Booking> findByUserAndBookingStatusOrderByCreatedAtDesc(User user, String bookingStatus, Pageable pageable);

    int countByBookingStatus(String bookingStatus);

    /**
     * Find bookings by status (for state machine queries).
     */
    List<Booking> findByBookingStatus(String bookingStatus);

    /**
     * Find bookings pending payment that were initiated before timeout window.
     */
    List<Booking> findByBookingStatusAndCreatedAtBefore(String bookingStatus, LocalDateTime dateTime);

    /**
     * Count bookings by user and status.
     */
    int countByUserAndBookingStatus(User user, String bookingStatus);

    /**
     * Check existence with pessimistic locking (for concurrent updates).
     */
    @Query("SELECT b FROM Booking b WHERE b.id = :id")
    Optional<Booking> findByIdForUpdate(UUID id);

    @Query("SELECT DISTINCT b FROM Booking b LEFT JOIN FETCH b.bookingItems bi LEFT JOIN FETCH bi.seat WHERE b.id = :id")
    Optional<Booking> findByIdWithItemsAndSeats(UUID id);
}
