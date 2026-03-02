package com.ticketwave.payment.application;

import com.ticketwave.booking.domain.Booking;
import com.ticketwave.payment.domain.PaymentIntent;
import com.ticketwave.payment.infrastructure.PaymentIntentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Service for managing payment intents.
 * 
 * Payment intent lifecycle:
 * 1. CREATE intent (status: PENDING)
 * 2. Send to payment gateway
 * 3. Wait for webhook callback
 * 4. UPDATE status: CONFIRMED or FAILED
 * 5. Trigger booking confirmation/failure based on status
 * 
 * Timeout handling:
 * If intent creation > 1 hour and still PENDING: Mark as EXPIRED
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentIntentService {

    private final PaymentIntentRepository paymentIntentRepository;

    @Value("${app.payment.intent-ttl-hours:1}")
    private int intentTtlHours;

    /**
     * Create payment intent for booking.
     * 
     * @param booking The booking requiring payment
     * @param amount Total amount to charge
     * @param idempotencyKey For idempotent processing
     * @return Created PaymentIntent
     */
    @Transactional
    public PaymentIntent createPaymentIntent(Booking booking, BigDecimal amount, String idempotencyKey) {
        log.info("Creating payment intent for booking: {} amount: {}", booking.getId(), amount);

        // Generate unique intent ID (would be replaced with gateway response in real implementation)
        String intentId = generateIntentId();

        Instant expiresAt = Instant.now().plusSeconds(3600L * intentTtlHours);

        PaymentIntent intent = PaymentIntent.builder()
                .booking(booking)
                .intentId(intentId)
                .amount(amount)
                .status("PENDING")
                .expiresAt(expiresAt)
                .idempotencyKey(idempotencyKey)
                .retryCount(0)
                .build();

        PaymentIntent saved = paymentIntentRepository.save(intent);
        log.info("Payment intent created - IntentId: {}, BookingId: {}, Amount: {}", 
                 intentId, booking.getId(), amount);

        return saved;
    }

    /**
     * Confirm payment intent (called from payment webhook).
     *
     * @param intentId Gateway payment intent ID
     * @param transactionId Transaction ID from gateway
     * @param paymentMethod Payment method used
     * @return Updated PaymentIntent
     */
    @Transactional
    public PaymentIntent confirmPayment(String intentId, String transactionId, String paymentMethod) {
        log.info("Confirming payment intent: {} transactionId: {}", intentId, transactionId);

        var intent = paymentIntentRepository.findByIntentId(intentId)
                .orElseThrow(() -> new RuntimeException("Payment intent not found: " + intentId));

        if (intent.isConfirmed()) {
            log.warn("Payment already confirmed for intent: {}", intentId);
            return intent; // Idempotent: allow re-confirmation
        }

        intent.setStatus("CONFIRMED");
        intent.setConfirmedAt(Instant.now());
        intent.setPaymentMethod(paymentMethod);
        intent.setGatewayResponse(transactionId);

        PaymentIntent saved = paymentIntentRepository.save(intent);
        log.info("Payment confirmed - IntentId: {}, Status: CONFIRMED", intentId);

        return saved;
    }

    /**
     * Mark payment intent as failed.
     *
     * @param intentId Payment intent ID
     * @param errorMessage Failure reason
     * @param shouldRetry Whether retrying is recommended
     * @return Updated PaymentIntent
     */
    @Transactional
    public PaymentIntent failPaymentIntent(String intentId, String errorMessage, boolean shouldRetry) {
        log.warn("Failing payment intent: {} error: {}", intentId, errorMessage);

        var intent = paymentIntentRepository.findByIntentId(intentId)
                .orElseThrow(() -> new RuntimeException("Payment intent not found: " + intentId));

        if (shouldRetry && intent.getRetryCount() < 3) {
            intent.setRetryCount(intent.getRetryCount() + 1);
            log.info("Payment intent will be retried - IntentId: {}, RetryCount: {}", 
                     intentId, intent.getRetryCount());
        } else {
            intent.setStatus("FAILED");
        }

        intent.setGatewayResponse(errorMessage);
        return paymentIntentRepository.save(intent);
    }

    /**
     * Get payment intent for booking.
     */
    public PaymentIntent getPaymentIntentForBooking(UUID bookingId) {
        return paymentIntentRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new RuntimeException("Payment intent not found for booking: " + bookingId));
    }

    /**
     * Get payment intent by intent ID (from gateway webhook).
     */
    public PaymentIntent getPaymentIntent(String intentId) {
        return paymentIntentRepository.findByIntentId(intentId)
                .orElseThrow(() -> new RuntimeException("Payment intent not found: " + intentId));
    }

    /**
     * Check if payment is confirmed.
     */
    public boolean isPaymentConfirmed(UUID bookingId) {
        return paymentIntentRepository.findByBookingId(bookingId)
                .map(PaymentIntent::isConfirmed)
                .orElse(false);
    }

    /**
     * Mark intent as expired (called from cleanup job).
     */
    @Transactional
    public void markAsExpired(String intentId) {
        var intent = paymentIntentRepository.findByIntentId(intentId)
                .orElseThrow(() -> new RuntimeException("Payment intent not found: " + intentId));

        if (intent.isPending()) {
            intent.setStatus("EXPIRED");
            paymentIntentRepository.save(intent);
            log.warn("Payment intent marked as expired: {}", intentId);
        }
    }

    /**
     * Generate unique payment intent ID (format: TW-{timestamp}-{random}).
     */
    private String generateIntentId() {
        return "TW-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
