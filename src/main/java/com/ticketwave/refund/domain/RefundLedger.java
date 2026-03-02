package com.ticketwave.refund.domain;

import com.ticketwave.common.domain.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Index;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * RefundLedger records all financial transactions related to refunds for accounting/reconciliation.
 * Each refund can have multiple ledger entries (breakdown of deductions, fees, etc.).
 * Used for:
 * - Financial reconciliation with payment providers
 * - Audit trails for refund calculations
 * - Revenue impact analysis
 */
@Entity
@Table(
        name = "refund_ledger",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_refund_ledger_entry_id", columnNames = {"ledger_entry_id"})
        },
        indexes = {
                @Index(name = "idx_refund_ledger_refund_id", columnList = "refund_id"),
                @Index(name = "idx_refund_ledger_entry_type", columnList = "entry_type"),
                @Index(name = "idx_refund_ledger_created_at", columnList = "created_at"),
                @Index(name = "idx_refund_ledger_booking_id", columnList = "booking_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundLedger extends AuditedEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String ledgerEntryId; // e.g., "LED-{uuid}"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_id", nullable = false)
    private Refund refund;

    @Column(nullable = false)
    private UUID bookingId;

    /**
     * Type of ledger entry:
     * REFUND_AMOUNT: Original refundable amount
     * POLICY_DEDUCTION: Amount deducted based on cancellation policy
     * PROCESSING_FEE: Payment processing reversal fee
     * PLATFORM_CHARGE: Any platform administrative charges
     * ADJUSTMENT: Manual adjustment (admin override)
     * FINAL_AMOUNT: Net amount to be refunded
     */
    @Column(nullable = false, length = 30)
    private String entryType;

    @Column(nullable = false, length = 100)
    private String description;

    /**
     * Amount for this line item (credit positive, debit negative).
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    /**
     * Running balance after this entry.
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal balanceAfter;

    /**
     * Additional metadata for this entry (e.g., percentage applied, reason for adjustment).
     */
    @Column(length = 500)
    private String metadata;

    /**
     * Reference to adjustment reason if admin override.
     */
    @Column(length = 500)
    private String adjustmentReason;

    /**
     * User ID of admin who made adjustment (if applicable).
     */
    @Column(length = 50)
    private String adjustedByAdmin;

    @Column
    private Instant adjustedAt;
}
