package com.ticketwave.payment.domain;

import com.ticketwave.booking.domain.Booking;
import com.ticketwave.common.domain.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
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
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(
        name = "payments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payments_transaction_id", columnNames = {"transaction_id"})
        },
        indexes = {
                @jakarta.persistence.Index(name = "idx_payments_booking_status", columnList = "booking_id,payment_status"),
                @jakarta.persistence.Index(name = "idx_payments_transaction_id", columnList = "transaction_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(nullable = false, unique = true, length = 50)
    private String transactionId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 20)
    private String paymentMethod; // CARD, UPI, NET_BANKING, WALLET, etc.

    @Column(nullable = false, length = 20)
    private String paymentStatus; // PENDING, CONFIRMED, FAILED, REFUNDED

    @Column(length = 500)
    private String gatewayResponse;

    @Column
    private Instant confirmedAt;

    @Version
    private long version;

    @Builder.Default
    @OneToMany(mappedBy = "payment", fetch = FetchType.LAZY, cascade = jakarta.persistence.CascadeType.REMOVE)
    private Set<com.ticketwave.refund.domain.Refund> refunds = new HashSet<>();
}
