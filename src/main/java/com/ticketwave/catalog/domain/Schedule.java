package com.ticketwave.catalog.domain;

import com.ticketwave.common.domain.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
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
@Table(name = "schedules", indexes = {
        @jakarta.persistence.Index(name = "idx_schedules_departure", columnList = "departure_time"),
        @jakarta.persistence.Index(name = "idx_schedules_route_departure", columnList = "route_id,departure_time")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Schedule extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;

    @Column(nullable = false, length = 50)
    private String vehicleNumber;

    @Column(nullable = false)
    private LocalDateTime departureTime;

    @Column(nullable = false)
    private LocalDateTime arrivalTime;

    @Column(nullable = false)
    private Integer totalSeats;

    @Column(nullable = false)
    private Integer availableSeats;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal baseFare;

    @Column(nullable = false)
    private Boolean active = true;

    @Version
    private long version;

    @Builder.Default
    @OneToMany(mappedBy = "schedule", fetch = FetchType.LAZY, cascade = jakarta.persistence.CascadeType.ALL)
    private Set<Seat> seats = new HashSet<>();

    @Builder.Default
    @OneToMany(mappedBy = "schedule", fetch = FetchType.LAZY, cascade = jakarta.persistence.CascadeType.REMOVE)
    private Set<com.ticketwave.booking.domain.Booking> bookings = new HashSet<>();
}
