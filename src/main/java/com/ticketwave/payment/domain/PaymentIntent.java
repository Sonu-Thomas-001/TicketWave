package com.ticketwave.payment.domain;

import com.ticketwave.booking.domain.Booking;
import com.ticketwave.common.domain.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payment intent entity for managing payment lifecycle.
 * 
 * Represents:
 * 1. Initial intent creation (before payment)
 * 2. Awaiting gateway response
 * 3. Final success/failure/timeout state
 * 
 * Workflow:
 * 1. CREATE intent: store amount, user, booking info
 * 2. SEND to payment gateway
 * 3. AWAIT gateway response (webhook or poll)
 * 4. UPDATE status based on response (CONFIRMED or FAILED)
 * 5. Process booking state change based on payment status
 */
@Entity
@Table(
        name = "payment_intents",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payment_intents_booking_id", columnNames = {"booking_id"}),
                @UniqueConstraint(name = "uk_payment_intents_intent_id", columnNames = {"intent_id"})
        },
        indexes = {
                @Index(name = "idx_payment_intents_status", columnList = "status"),
                @Index(name = "idx_payment_intents_booking_id", columnList = "booking_id"),
                @Index(name = "idx_payment_intents_expires_at", columnList = "expires_at"),
                @Index(name = "idx_payment_intents_created_at", columnList = "created_at")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentIntent extends AuditedEntity {

    /**
     * Reference to booking.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    /**
     * Gateway-generated payment intent ID (e.g., from Stripe/PayPal).
     */
    @Column(nullable = false, unique = true, length = 100)
    private String intentId;

    /**
     * Amount to be paid in base currency units.
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    /**
     * Payment status: PENDING, CONFIRMED, FAILED, EXPIRED, CANCELLED.
     */
    @Column(nullable = false, length = 25)
    @Builder.Default
    private String status = "PENDING";

    /**
     * Payment method selected (optional until confirmed).
     * Examples: CARD, UPI, NET_BANKING, WALLET
     */
    @Column(length = 50)
    private String paymentMethod;

    /**
     * When intent expires (1 hour typical). After this, booking reverts to INITIATED.
     */
    @Column(nullable = false)
    private Instant expiresAt;

    /**
     * When payment was confirmed (NULL until confirmed).
     */
    @Column
    private Instant confirmedAt;

    /**
     * Gateway response (error message if failed).
     */
    @Column(columnDefinition = "TEXT")
    private String gatewayResponse;

    /**
     * Idempotency key for payment processing.
     */
    @Column(length = 100)
    private String idempotencyKey;

    /**
     * Retry count if payment processing retried.
     */
    @Builder.Default
    private Integer retryCount = 0;

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isPending() {
        return "PENDING".equals(status);
    }

    public boolean isConfirmed() {
        return "CONFIRMED".equals(status);
    }

    public boolean isFailed() {
        return "FAILED".equals(status);
    }
}
