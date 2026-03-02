package com.ticketwave.catalog.api;

import com.ticketwave.catalog.application.ScheduleSearchService;
import com.ticketwave.common.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/schedules")
public class ScheduleSearchController {

    private final ScheduleSearchService scheduleSearchService;

    public ScheduleSearchController(ScheduleSearchService scheduleSearchService) {
        this.scheduleSearchService = scheduleSearchService;
    }

    /**
     * Search schedules by origin, destination, and date with dynamic pricing.
     * Results are cached for performance.
     *
     * @param request search criteria (origin, destination, date, sort)
     * @return list of schedules with dynamic pricing and availability
     */
    @PostMapping("/search")
    public ResponseEntity<ApiResponse<List<ScheduleSearchResult>>> searchSchedules(
            @Valid @RequestBody ScheduleSearchRequest request) {

        log.info("Search request received: origin={}, destination={}, date={}, sortBy={}", 
                request.getOriginCity(), request.getDestinationCity(), 
                request.getTravelDate(), request.getSortBy());

        List<ScheduleSearchResult> results = scheduleSearchService.searchSchedules(request);

        if (results.isEmpty()) {
            log.info("No schedules found for search criteria");
            return ResponseEntity.ok(ApiResponse.success("No schedules available", results));
        }

        log.info("Found {} schedules", results.size());
        return ResponseEntity.ok(ApiResponse.success("Schedules retrieved successfully", results));
    }

    /**
     * Get detailed information about a specific schedule.
     * Includes real-time availability and current dynamic pricing.
     *
     * @param scheduleId schedule UUID
     * @return schedule details with pricing
     */
    @GetMapping("/{scheduleId}")
    public ResponseEntity<ApiResponse<ScheduleSearchResult>> getScheduleDetails(
            @PathVariable UUID scheduleId) {

        log.info("Fetching schedule details: {}", scheduleId);

        ScheduleSearchResult result = scheduleSearchService.getScheduleDetails(scheduleId);

        if (result == null) {
            log.warn("Schedule not found: {}", scheduleId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.failure("Schedule not found", null));
        }

        return ResponseEntity.ok(ApiResponse.success("Schedule details retrieved", result));
    }

    /**
     * Get availability statistics for a schedule.
     * Shows total seats, available seats, and demand status.
     *
     * @param scheduleId schedule UUID
     * @return availability statistics
     */
    @GetMapping("/{scheduleId}/availability")
    public ResponseEntity<ApiResponse<ScheduleSearchService.AvailabilityStats>> getAvailability(
            @PathVariable UUID scheduleId) {

        log.debug("Fetching availability for schedule: {}", scheduleId);

        ScheduleSearchService.AvailabilityStats stats = scheduleSearchService.getAvailabilityStats(scheduleId);

        if (stats == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.failure("Schedule not found", null));
        }

        return ResponseEntity.ok(ApiResponse.success("Availability stats retrieved", stats));
    }

    /**
     * Get high-demand schedules (with premium pricing).
     * Demand factor > 1.0 when availability < 30%.
     *
     * @param originCity departure city
     * @param destinationCity arrival city
     * @return list of high-demand schedules
     */
    @GetMapping("/high-demand")
    public ResponseEntity<ApiResponse<List<ScheduleSearchResult>>> getHighDemandSchedules(
            String originCity, String destinationCity) {

        log.info("Fetching high-demand schedules: {} -> {}", originCity, destinationCity);

        List<ScheduleSearchResult> results = scheduleSearchService.getHighDemandSchedules(originCity, destinationCity);

        if (results.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success("No high-demand schedules", results));
        }

        return ResponseEntity.ok(ApiResponse.success("High-demand schedules retrieved", results));
    }

    /**
     * Get estimated travel duration for a schedule.
     *
     * @param scheduleId schedule UUID
     * @return duration in minutes
     */
    @GetMapping("/{scheduleId}/duration")
    public ResponseEntity<ApiResponse<Long>> getEstimatedDuration(
            @PathVariable UUID scheduleId) {

        log.debug("Fetching estimated duration for schedule: {}", scheduleId);

        Long duration = scheduleSearchService.getEstimatedDuration(scheduleId);

        if (duration == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.failure("Schedule not found", null));
        }

        return ResponseEntity.ok(ApiResponse.success("Duration calculated", duration));
    }
}
