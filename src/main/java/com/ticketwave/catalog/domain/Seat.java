package com.ticketwave.catalog.domain;

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

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(
        name = "seats",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_seats_schedule_number", columnNames = {"schedule_id", "seat_number"})
        },
        indexes = {
                @jakarta.persistence.Index(name = "idx_seats_schedule_status", columnList = "schedule_id,seat_status")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Seat extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id", nullable = false)
    private Schedule schedule;

    @Column(nullable = false, length = 10)
    private String seatNumber;

    @Column(nullable = false, length = 20)
    private String class_; // ECONOMY, BUSINESS, FIRST, etc.

    @Column(nullable = false, length = 20)
    private String seatStatus; // AVAILABLE, HELD, BOOKED, BLOCKED

    @Version
    private long version;

    @Builder.Default
    @OneToMany(mappedBy = "seat", fetch = FetchType.LAZY, cascade = jakarta.persistence.CascadeType.REMOVE)
    private Set<com.ticketwave.booking.domain.BookingItem> bookingItems = new HashSet<>();
}
