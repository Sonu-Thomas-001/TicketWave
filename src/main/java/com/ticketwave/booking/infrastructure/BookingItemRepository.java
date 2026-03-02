package com.ticketwave.booking.infrastructure;

import com.ticketwave.booking.domain.Booking;
import com.ticketwave.booking.domain.BookingItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BookingItemRepository extends JpaRepository<BookingItem, UUID> {
    List<BookingItem> findByBooking(Booking booking);

    List<BookingItem> findByBookingAndItemStatus(Booking booking, String itemStatus);
}
