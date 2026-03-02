package com.ticketwave.booking.domain;

import com.ticketwave.catalog.domain.Schedule;
import com.ticketwave.common.domain.AuditedEntity;
import com.ticketwave.user.domain.User;
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
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(
        name = "bookings",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_bookings_pnr", columnNames = {"pnr"})
        },
        indexes = {
                @jakarta.persistence.Index(name = "idx_bookings_user_status", columnList = "user_id,booking_status"),
                @jakarta.persistence.Index(name = "idx_bookings_schedule_status", columnList = "schedule_id,booking_status"),
                @jakarta.persistence.Index(name = "idx_bookings_pnr", columnList = "pnr")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Booking extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id", nullable = false)
    private Schedule schedule;

    @Column(nullable = false, unique = true, length = 10)
    private String pnr; // Passenger Name Record

    @Column(nullable = false, length = 20)
    private String bookingStatus; // PENDING, CONFIRMED, CANCELLED

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private LocalDateTime bookedAt;

    @Column
    private LocalDateTime cancelledAt;

    @Version
    private long version;

    @Builder.Default
    @OneToMany(mappedBy = "booking", fetch = FetchType.LAZY, cascade = jakarta.persistence.CascadeType.ALL)
    private Set<BookingItem> bookingItems = new HashSet<>();

    @Builder.Default
    @OneToMany(mappedBy = "booking", fetch = FetchType.LAZY, cascade = jakarta.persistence.CascadeType.REMOVE)
    private Set<com.ticketwave.payment.domain.Payment> payments = new HashSet<>();

    @Builder.Default
    @OneToMany(mappedBy = "booking", fetch = FetchType.LAZY, cascade = jakarta.persistence.CascadeType.REMOVE)
    private Set<com.ticketwave.refund.domain.Refund> refunds = new HashSet<>();
}
