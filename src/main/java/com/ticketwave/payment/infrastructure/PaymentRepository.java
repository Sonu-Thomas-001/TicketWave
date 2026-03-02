package com.ticketwave.payment.infrastructure;

import com.ticketwave.booking.domain.Booking;
import com.ticketwave.payment.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByTransactionId(String transactionId);

    List<Payment> findByBooking(Booking booking);

    List<Payment> findByBookingAndPaymentStatus(Booking booking, String paymentStatus);

    int countByPaymentStatus(String paymentStatus);
}
