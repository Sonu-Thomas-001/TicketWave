package com.ticketwave.catalog.domain;

import com.ticketwave.common.domain.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "routes", indexes = {
        @jakarta.persistence.Index(name = "idx_routes_origin_destination", columnList = "origin_city,destination_city")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Route extends AuditedEntity {

    @Column(nullable = false, length = 100)
    private String originCity;

    @Column(nullable = false, length = 100)
    private String destinationCity;

    @Column(nullable = false, length = 50)
    private String transportMode; // FLIGHT, TRAIN, BUS, etc.

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private Boolean active = true;

    @Version
    private long version;

    @Builder.Default
    @OneToMany(mappedBy = "route", fetch = jakarta.persistence.FetchType.LAZY, cascade = jakarta.persistence.CascadeType.ALL)
    private Set<Schedule> schedules = new HashSet<>();
}
