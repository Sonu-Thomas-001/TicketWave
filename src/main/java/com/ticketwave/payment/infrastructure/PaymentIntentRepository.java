package com.ticketwave.payment.infrastructure;

import com.ticketwave.payment.domain.PaymentIntent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentIntentRepository extends JpaRepository<PaymentIntent, UUID> {

    /**
     * Find payment intent by booking ID (one-to-one relationship).
     */
    Optional<PaymentIntent> findByBookingId(UUID bookingId);

    /**
     * Find payment intent by gateway intent ID.
     */
    Optional<PaymentIntent> findByIntentId(String intentId);

    /**
     * Find pending payment intents that have expired (for cleanup/expiry handling).
     */
    List<PaymentIntent> findByStatusAndExpiresAtBefore(String status, Instant instant);

    /**
     * Count pending intents (monitoring).
     */
    long countByStatus(String status);

    /**
     * Find intents with retry count > 0 (for debugging).
     */
    List<PaymentIntent> findByRetryCountGreaterThan(Integer retryCount);
}
