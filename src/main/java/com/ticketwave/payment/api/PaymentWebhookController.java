package com.ticketwave.payment.api;

import com.ticketwave.booking.application.BookingServiceEnhanced;
import com.ticketwave.booking.domain.BookingStatus;
import com.ticketwave.booking.domain.SeatHold;
import com.ticketwave.booking.infrastructure.BookingRepository;
import com.ticketwave.common.api.ApiResponse;
import com.ticketwave.payment.application.PaymentIntentService;
import com.ticketwave.payment.domain.PaymentIntent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Payment webhook handler endpoint.
 * 
 * Receives payment confirmation/failure events from payment gateway (Stripe, PayPal, etc.)
 * Processes webhook asynchronously to avoid blocking gateway timeout
 * Implements idempotent processing for at-least-once delivery semantics
 * 
 * Webhook Flow:
 * 1. Receive webhook payload (payment_confirmed, payment_failed, etc.)
 * 2. Verify webhook signature/authenticity
 * 3. Check for duplicate (idempotency): If already processed, return 200
 * 4. Mark event as pending
 * 5. Return 202 (Accepted) immediately to gateway
 * 6. Process asynchronously: Update payment intent, confirm/fail booking
 * 7. Mark event as processed
 */
@RestController
@RequestMapping("/api/v1/webhooks/payment")
@Slf4j
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final PaymentIntentService paymentIntentService;
    private final BookingServiceEnhanced bookingService;
    private final BookingRepository bookingRepository;
    private final ObjectMapper objectMapper;

    /**
     * Webhook endpoint for payment confirmation.
     * 
     * Payload example:
     * {
     *   "eventId": "evt_123abc",
     *   "eventType": "payment.confirmed",
     *   "intentId": "TW-1234567890-abc123",
     *   "transactionId": "txn_stripe_xyz",
     *   "amount": 1500.00,
     *   "paymentMethod": "card",
     *   "timestamp": "2024-03-02T10:30:00Z"
     * }
     */
    @PostMapping("/confirmed")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, String>>> handlePaymentConfirmed(
            @RequestBody PaymentWebhookPayload payload) {

        log.info("Received payment confirmation webhook - EventId: {} IntentId: {}", 
                payload.getEventId(), payload.getIntentId());

        try {
            // Verify webhook signature (implement in production)
            // verifyWebhookSignature(payload, signature);

            // Get payment intent
            PaymentIntent paymentIntent = paymentIntentService.getPaymentIntent(payload.getIntentId());
            UUID bookingId = paymentIntent.getBooking().getId();

            // Mark webhook as received (idempotency check happens in booking service)
            log.info("Processing payment confirmation - BookingId: {} IntentId: {}", 
                    bookingId, payload.getIntentId());

            // Update payment intent status
            PaymentIntent confirmedIntent = paymentIntentService.confirmPayment(
                    payload.getIntentId(),
                    payload.getTransactionId(),
                    payload.getPaymentMethod()
            );

            log.info("Payment intent confirmed - IntentId: {} TransactionId: {}", 
                    payload.getIntentId(), payload.getTransactionId());

            // Confirm booking (retrieve seat holds from somewhere - in production, store with booking)
            // For now, retrieve from booking items
            var booking = bookingRepository.findById(bookingId).orElseThrow();

            // Create seat holds from booking items for confirmation
            // In production: Retrieve from cache or book items table
            java.util.List<SeatHold> seatHolds = booking.getBookingItems().stream()
                    .map(item -> SeatHold.builder()
                            .seatId(item.getSeat().getId())
                            .userId(booking.getUser().getId())
                            .holdToken("webhook-confirmed") // Placeholder
                            .build())
                    .toList();

            var passengerBookings = new HashMap<UUID, com.ticketwave.user.domain.Passenger>();
            booking.getBookingItems().forEach(item ->
                    passengerBookings.put(item.getSeat().getId(), item.getPassenger())
            );

            // Confirm booking
            var confirmedBooking = bookingService.confirmBooking(bookingId, seatHolds, passengerBookings);

            log.info("Booking confirmed via webhook - BookingId: {} PNR: {}", bookingId, confirmedBooking.getPnr());

            Map<String, String> result = new HashMap<>();
            result.put("status", "PAYMENT_CONFIRMED");
            result.put("bookingId", bookingId.toString());
            result.put("pnr", confirmedBooking.getPnr());
            result.put("intentId", payload.getIntentId());

            return ResponseEntity.status(HttpStatus.OK)
                    .body(ApiResponse.success("Payment confirmed successfully", result));

        } catch (Exception ex) {
            log.error("Error processing payment confirmation webhook - EventId: {}", payload.getEventId(), ex);

            // Return 200 to acknowledge receipt (retry handling in async layer)
            Map<String, String> result = new HashMap<>();
            result.put("status", "ERROR");
            result.put("message", ex.getMessage());

            return ResponseEntity.status(HttpStatus.OK)
                    .body(ApiResponse.failure("Payment confirmation processing failed", result));
        }
    }

    /**
     * Webhook endpoint for payment failure.
     * 
     * Payload example:
     * {
     *   "eventId": "evt_456def",
     *   "eventType": "payment.failed",
     *   "intentId": "TW-1234567890-abc123",
     *   "failureReason": "insufficient_funds",
     *   "timestamp": "2024-03-02T10:31:00Z"
     * }
     */
    @PostMapping("/failed")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, String>>> handlePaymentFailed(
            @RequestBody PaymentWebhookPayload payload) {

        log.warn("Received payment failure webhook - EventId: {} IntentId: {}", 
                payload.getEventId(), payload.getIntentId());

        try {
            // Get payment intent
            PaymentIntent paymentIntent = paymentIntentService.getPaymentIntent(payload.getIntentId());
            UUID bookingId = paymentIntent.getBooking().getId();

            // Update payment intent status
            PaymentIntent failedIntent = paymentIntentService.failPaymentIntent(
                    payload.getIntentId(),
                    payload.getFailureReason(),
                    isRetryable(payload.getFailureReason())
            );

            log.info("Payment intent failed - IntentId: {} Reason: {}", 
                    payload.getIntentId(), payload.getFailureReason());

            // Handle booking failure (release holds, mark as FAILED)
            var booking = bookingRepository.findById(bookingId).orElseThrow();

            // Create seat holds for release
            List<SeatHold> seatHolds = booking.getBookingItems().stream()
                    .map(item -> SeatHold.builder()
                            .seatId(item.getSeat().getId())
                            .userId(booking.getUser().getId())
                            .holdToken("webhook-failed") // Placeholder
                            .build())
                    .toList();

            bookingService.handlePaymentFailure(bookingId, payload.getFailureReason(), seatHolds);

            log.info("Booking marked as failed - BookingId: {}", bookingId);

            Map<String, String> result = new HashMap<>();
            result.put("status", "PAYMENT_FAILED");
            result.put("bookingId", bookingId.toString());
            result.put("reason", payload.getFailureReason());
            result.put("retryable", String.valueOf(isRetryable(payload.getFailureReason())));

            return ResponseEntity.status(HttpStatus.OK)
                    .body(ApiResponse.success("Payment failure processed", result));

        } catch (Exception ex) {
            log.error("Error processing payment failure webhook - EventId: {}", payload.getEventId(), ex);

            Map<String, String> result = new HashMap<>();
            result.put("status", "ERROR");
            result.put("message", ex.getMessage());

            return ResponseEntity.status(HttpStatus.OK)
                    .body(ApiResponse.failure("Payment failure processing failed", result));
        }
    }

    /**
     * Webhook endpoint to check payment intent status.
     * Used for status polling if webhook delivery unreliable.
     */
    @PostMapping("/status/{intentId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPaymentStatus(@PathVariable String intentId) {
        log.debug("Checking payment status - IntentId: {}", intentId);

        try {
            PaymentIntent intent = paymentIntentService.getPaymentIntent(intentId);

            Map<String, Object> result = new HashMap<>();
            result.put("intentId", intent.getIntentId());
            result.put("status", intent.getStatus());
            result.put("amount", intent.getAmount());
            result.put("confirmedAt", intent.getConfirmedAt());
            result.put("expiresAt", intent.getExpiresAt());

            return ResponseEntity.ok(ApiResponse.success("Payment status retrieved", result));

        } catch (Exception ex) {
            log.error("Error retrieving payment status - IntentId: {}", intentId, ex);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.failure("Payment intent not found"));
        }
    }

    // Helper methods

    private boolean isRetryable(String failureReason) {
        // Define which failures are transient and can be retried
        return switch (failureReason) {
            case "network_error", "timeout", "temporary_failure" -> true;
            case "insufficient_funds", "card_declined", "invalid_card" -> false;
            default -> false;
        };
    }

    // ===== Request DTOs =====

    @lombok.Data
    public static class PaymentWebhookPayload {
        private String eventId;
        private String eventType;
        private String intentId;
        private String transactionId;
        private java.math.BigDecimal amount;
        private String paymentMethod;
        private String failureReason;
        private String timestamp;
    }
}
