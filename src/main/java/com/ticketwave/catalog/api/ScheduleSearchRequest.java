package com.ticketwave.catalog.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleSearchRequest {

    @NotBlank(message = "Origin city is required")
    private String originCity;

    @NotBlank(message = "Destination city is required")
    private String destinationCity;

    @NotNull(message = "Travel date is required")
    private LocalDate travelDate;

    @Builder.Default
    private String sortBy = "price"; // price, duration, availability

    @Builder.Default
    private String sortOrder = "asc"; // asc, desc
}
