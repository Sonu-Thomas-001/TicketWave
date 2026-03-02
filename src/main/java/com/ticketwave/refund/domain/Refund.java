package com.ticketwave.refund.domain;

import com.ticketwave.booking.domain.Booking;
import com.ticketwave.common.domain.AuditedEntity;
import com.ticketwave.payment.domain.Payment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

@Entity
@Table(
        name = "refunds",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_refunds_refund_id", columnNames = {"refund_id"})
        },
        indexes = {
                @jakarta.persistence.Index(name = "idx_refunds_booking_status", columnList = "booking_id,refund_status"),
                @jakarta.persistence.Index(name = "idx_refunds_payment_status", columnList = "payment_id,refund_status"),
                @jakarta.persistence.Index(name = "idx_refunds_refund_id", columnList = "refund_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Refund extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(nullable = false, unique = true, length = 50)
    private String refundId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal refundAmount;

    @Column(nullable = false, length = 20)
    private String refundStatus; // PENDING, APPROVED, REJECTED, COMPLETED

    @Column(length = 500)
    private String reason;

    @Column(length = 500)
    private String gatewayResponse;

    @Column
    private Instant processedAt;

    @Version
    private long version;
}
