package com.ticketwave.catalog.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@lombok.Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleSearchResult {

    private UUID scheduleId;
    private UUID routeId;
    
    private String originCity;
    private String destinationCity;
    private String vehicleNumber;
    private String transportMode;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime departureTime;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime arrivalTime;

    private Integer totalSeats;
    private Integer availableSeats;
    private Double availabilityPercentage;

    private BigDecimal baseFare;
    private BigDecimal dynamicPrice;
    private Double priceModifier;
    private Double demandFactor;

    private Long durationMinutes;

    private Boolean active;
}
