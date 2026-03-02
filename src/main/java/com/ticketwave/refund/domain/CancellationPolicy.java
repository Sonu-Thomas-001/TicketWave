package com.ticketwave.refund.domain;

import com.ticketwave.common.domain.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * CancellationPolicy defines refund eligibility and amounts based on timing and conditions.
 * Each event type can have one or more policies applicable based on event characteristics.
 */
@Entity
@Table(
        name = "cancellation_policies",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_cancellation_policies_policy_id", columnNames = {"policy_id"})
        },
        indexes = {
                @jakarta.persistence.Index(name = "idx_cancellation_policies_policy_id", columnList = "policy_id"),
                @jakarta.persistence.Index(name = "idx_cancellation_policies_event_type", columnList = "event_type"),
                @jakarta.persistence.Index(name = "idx_cancellation_policies_active", columnList = "is_active")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancellationPolicy extends AuditedEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String policyId; // e.g., "POL-EVENT-STANDARD", "POL-FLIGHT-STRICT"

    @Column(nullable = false, length = 100)
    private String policyName; // e.g., "Standard Event Cancellation"

    @Column(length = 500)
    private String description;

    @Column(nullable = false, length = 20)
    private String eventType; // e.g., "FLIGHT", "MOVIE", "CONCERT", "SPORTS"

    /**
     * Hours before event departure/start when full refund is available.
     * Example: 72 hours = 100% refund if cancelled >72 hours before event
     */
    @Column(nullable = false)
    private Integer fullRefundWindowHours;

    /**
     * Hours before event departure/start when partial refund is available.
     * Example: 24 hours = partial refund if cancelled 24-72 hours before event
     */
    @Column(nullable = false)
    private Integer partialRefundWindowHours;

    /**
     * Minimum cancellation fee in absolute amount (subtracted from refund).
     * Example: 100.00 (cannot go below this)
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal minimumCancellationFee = BigDecimal.ZERO;

    /**
     * Partial refund percentage if cancelled within partial window.
     * Example: 50.00 = 50% of original amount refunded
     */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal partialRefundPercentage = BigDecimal.valueOf(50);

    /**
     * Whether non-refundable cancellations are allowed (e.g., special pricing).
     */
    @Column(nullable = false)
    private Boolean allowNonRefundable = false;

    /**
     * Processing fee percentage (deducted from refund).
     * Example: 2.5 = 2.5% deduction for payment processing reversal
     */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal processingFeePercentage = BigDecimal.ZERO;

    /**
     * Whether this policy is currently active.
     */
    @Column(nullable = false)
    private Boolean isActive = true;

    @Column
    private Instant effectiveFrom;

    @Column
    private Instant effectiveTo;

    @Version
    private long version;
}
