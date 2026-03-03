package com.ticketwave.catalog.api;

import com.ticketwave.booking.application.SeatHoldService;
import com.ticketwave.booking.domain.SeatHold;
import com.ticketwave.catalog.application.ScheduleSearchService;
import com.ticketwave.catalog.domain.Seat;
import com.ticketwave.catalog.infrastructure.SeatRepository;
import com.ticketwave.catalog.infrastructure.ScheduleRepository;
import com.ticketwave.common.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/schedules")
public class ScheduleSearchController {

    private final ScheduleSearchService scheduleSearchService;
    private final ScheduleRepository scheduleRepository;
    private final SeatRepository seatRepository;
    private final SeatHoldService seatHoldService;

    public ScheduleSearchController(ScheduleSearchService scheduleSearchService,
                                     ScheduleRepository scheduleRepository,
                                     SeatRepository seatRepository,
                                     SeatHoldService seatHoldService) {
        this.scheduleSearchService = scheduleSearchService;
        this.scheduleRepository = scheduleRepository;
        this.seatRepository = seatRepository;
        this.seatHoldService = seatHoldService;
    }

    /**
     * Browse all active schedules with dynamic pricing.
     * Public endpoint for the events/browse page.
     *
     * @return list of all active schedules
     */
    @GetMapping("/browse")
    public ResponseEntity<ApiResponse<List<ScheduleSearchResult>>> browseSchedules() {
        log.info("Browse all schedules request");

        List<ScheduleSearchResult> results = scheduleSearchService.getAllActiveSchedules();

        return ResponseEntity.ok(ApiResponse.success("Schedules retrieved successfully", results));
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

    /**
     * Get all seats for a schedule with their current status.
     * Used by the frontend seat map component.
     *
     * @param scheduleId schedule UUID
     * @return list of seats with id, seatNumber, class, and status
     */
    @GetMapping("/{scheduleId}/seats")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getScheduleSeats(
            @PathVariable UUID scheduleId) {

        log.info("Fetching seats for schedule: {}", scheduleId);

        var scheduleOpt = scheduleRepository.findById(scheduleId);
        if (scheduleOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.failure("Schedule not found"));
        }

        List<Seat> seats = seatRepository.findBySchedule(scheduleOpt.get());

        List<Map<String, Object>> seatData = seats.stream().map(seat -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", seat.getId());
            map.put("seatNumber", seat.getSeatNumber());
            map.put("seatClass", seat.getClass_());
            map.put("seatStatus", seat.getSeatStatus());
            return map;
        }).toList();

        return ResponseEntity.ok(ApiResponse.success("Seats retrieved", seatData));
    }

    /**
     * Hold one or more seats for the authenticated user.
     * Creates a time-limited hold in Redis (10 min TTL).
     */
    @PostMapping("/{scheduleId}/hold")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> holdSeats(
            @PathVariable UUID scheduleId,
            @RequestBody Map<String, List<String>> request,
            Authentication authentication) {

        UUID userId = UUID.fromString(authentication.getName());
        List<String> seatIds = request.get("seatIds");

        if (seatIds == null || seatIds.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.failure("seatIds are required"));
        }

        log.info("Hold request: userId={} scheduleId={} seats={}", userId, scheduleId, seatIds.size());

        List<Map<String, Object>> holds = new ArrayList<>();
        List<SeatHold> successfulHolds = new ArrayList<>();
        try {
            for (String seatIdStr : seatIds) {
                UUID seatId = UUID.fromString(seatIdStr);
                SeatHold hold = seatHoldService.holdSeat(seatId, userId);
                successfulHolds.add(hold);
                Map<String, Object> holdData = new HashMap<>();
                holdData.put("seatId", hold.getSeatId());
                holdData.put("holdToken", hold.getHoldToken());
                holdData.put("heldAt", hold.getHeldAt());
                holdData.put("expiresAt", hold.getExpiresAt());
                holds.add(holdData);
            }
        } catch (Exception ex) {
            // Rollback previously successful holds on failure
            for (SeatHold h : successfulHolds) {
                try {
                    seatHoldService.releaseSeatHold(h.getSeatId(), h.getHoldToken());
                } catch (Exception releaseEx) {
                    log.warn("Failed to rollback hold for seat {}: {}", h.getSeatId(), releaseEx.getMessage());
                }
            }
            throw ex;
        }

        return ResponseEntity.ok(ApiResponse.success("Seats held successfully", holds));
    }
}
