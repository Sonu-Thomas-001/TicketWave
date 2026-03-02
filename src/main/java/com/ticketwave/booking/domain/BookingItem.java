package com.ticketwave.booking.domain;

import com.ticketwave.catalog.domain.Seat;
import com.ticketwave.common.domain.AuditedEntity;
import com.ticketwave.user.domain.Passenger;
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

@Entity
@Table(
        name = "booking_items",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_booking_items_booking_seat", columnNames = {"booking_id", "seat_id"})
        },
        indexes = {
                @jakarta.persistence.Index(name = "idx_booking_items_booking", columnList = "booking_id"),
                @jakarta.persistence.Index(name = "idx_booking_items_seat", columnList = "seat_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingItem extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "passenger_id", nullable = false)
    private Passenger passenger;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal fare;

    @Column(nullable = false, length = 20)
    private String itemStatus; // PENDING, CONFIRMED, CANCELLED

    @Version
    private long version;
}
