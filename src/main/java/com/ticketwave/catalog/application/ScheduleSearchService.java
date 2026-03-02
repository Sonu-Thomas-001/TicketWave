package com.ticketwave.catalog.application;

import com.ticketwave.catalog.api.ScheduleSearchRequest;
import com.ticketwave.catalog.api.ScheduleSearchResult;
import com.ticketwave.catalog.domain.Schedule;
import com.ticketwave.catalog.infrastructure.ScheduleRepository;
import com.ticketwave.catalog.mapper.ScheduleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
public class ScheduleSearchService {

    private static final double DEFAULT_PRICE_MODIFIER = 1.0;

    private final ScheduleRepository scheduleRepository;
    private final ScheduleMapper scheduleMapper;
    private final PricingCalculationService pricingService;

    public ScheduleSearchService(ScheduleRepository scheduleRepository,
                               ScheduleMapper scheduleMapper,
                               PricingCalculationService pricingService) {
        this.scheduleRepository = scheduleRepository;
        this.scheduleMapper = scheduleMapper;
        this.pricingService = pricingService;
    }

    /**
     * Search schedules by origin, destination, and date with caching.
     * Applies dynamic pricing and sorts results.
     *
     * @param request search criteria
     * @return list of matching schedules with dynamic pricing
     */
    @Cacheable(value = "scheduleSearch", 
               key = "#request.originCity + '-' + #request.destinationCity + '-' + #request.travelDate + '-' + #request.sortBy + '-' + #request.sortOrder",
               unless = "#result == null || #result.isEmpty()")
    public List<ScheduleSearchResult> searchSchedules(ScheduleSearchRequest request) {
        log.info("Searching schedules: origin={}, destination={}, date={}", 
                request.getOriginCity(), request.getDestinationCity(), request.getTravelDate());

        // Convert date to LocalDateTime range
        LocalDateTime startOfDay = request.getTravelDate().atStartOfDay();
        LocalDateTime endOfDay = request.getTravelDate().atTime(LocalTime.MAX);

        // Query schedules from repository
        List<Schedule> schedules = scheduleRepository.searchByOriginDestinationDate(
                request.getOriginCity(),
                request.getDestinationCity(),
                startOfDay
        );

        log.debug("Found {} schedules before filtering", schedules.size());

        // Map to DTO and enrich with pricing
        List<ScheduleSearchResult> results = schedules.stream()
                .map(scheduleMapper::scheduleToSearchResult)
                .map(result -> pricingService.enrichWithPricing(result, 
                        scheduleRepository.findById(result.getScheduleId()).orElse(null),
                        DEFAULT_PRICE_MODIFIER))
                .filter(result -> result != null) // Remove nulls from missing schedules
                .collect(Collectors.toList());

        // Apply sorting
        applySort(results, request.getSortBy(), request.getSortOrder());

        log.info("Returning {} schedules after filtering and sorting", results.size());
        return results;
    }

    /**
     * Sort results by specified criteria.
     */
    private void applySort(List<ScheduleSearchResult> results, String sortBy, String sortOrder) {
        Comparator<ScheduleSearchResult> comparator = switch (sortBy.toLowerCase()) {
            case "price" -> Comparator.comparing(ScheduleSearchResult::getDynamicPrice);
            case "duration" -> Comparator.comparing(ScheduleSearchResult::getDurationMinutes);
            case "availability" -> Comparator.comparing(ScheduleSearchResult::getAvailableSeats).reversed();
            case "departure" -> Comparator.comparing(ScheduleSearchResult::getDepartureTime);
            default -> Comparator.comparing(ScheduleSearchResult::getDynamicPrice);
        };

        if ("desc".equalsIgnoreCase(sortOrder)) {
            comparator = comparator.reversed();
        }

        results.sort(comparator);
        log.debug("Applied sorting: sortBy={}, order={}", sortBy, sortOrder);
    }

    /**
     * Get live schedule details with current availability and pricing.
     * Not cached - always fetches fresh data.
     *
     * @param scheduleId schedule UUID
     * @return enriched schedule details
     */
    public ScheduleSearchResult getScheduleDetails(java.util.UUID scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElse(null);

        if (schedule == null) {
            log.warn("Schedule not found: {}", scheduleId);
            return null;
        }

        ScheduleSearchResult result = scheduleMapper.scheduleToSearchResult(schedule);
        return pricingService.enrichWithPricing(result, schedule, DEFAULT_PRICE_MODIFIER);
    }

    /**
     * Get high-demand schedules for a specific route.
     * These schedules qualify for premium pricing (demand factor > 1.0).
     *
     * @param originCity origin city
     * @param destinationCity destination city
     * @return list of high-demand schedules
     */
    @Cacheable(value = "highDemandSchedules",
               key = "#originCity + '-' + #destinationCity",
               unless = "#result == null || #result.isEmpty()")
    public List<ScheduleSearchResult> getHighDemandSchedules(String originCity, String destinationCity) {
        log.debug("Fetching high-demand schedules: {} -> {}", originCity, destinationCity);

        return scheduleRepository.searchByOriginDestination(originCity, destinationCity, 
                        org.springframework.data.domain.Pageable.unpaged())
                .stream()
                .map(scheduleMapper::scheduleToSearchResult)
                .map(result -> pricingService.enrichWithPricing(result,
                        scheduleRepository.findById(result.getScheduleId()).orElse(null),
                        DEFAULT_PRICE_MODIFIER))
                .filter(result -> result != null)
                .filter(result -> pricingService.isHighDemand(
                        scheduleRepository.findById(result.getScheduleId()).orElse(null)))
                .collect(Collectors.toList());
    }

    /**
     * Calculate estimated travel duration for marketing/display purposes.
     *
     * @param scheduleId schedule ID
     * @return duration in minutes
     */
    public Long getEstimatedDuration(java.util.UUID scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId).orElse(null);
        if (schedule == null) {
            return null;
        }

        return java.time.Duration.between(
                schedule.getDepartureTime(),
                schedule.getArrivalTime()
        ).toMinutes();
    }

    /**
     * Get seat availability statistics for a schedule.
     *
     * @param scheduleId schedule ID
     * @return availability details including percentage and demand status
     */
    public AvailabilityStats getAvailabilityStats(java.util.UUID scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId).orElse(null);
        if (schedule == null) {
            return null;
        }

        double availabilityPercentage = pricingService.calculateAvailabilityPercentage(schedule);
        boolean isHighDemand = pricingService.isHighDemand(schedule);

        return AvailabilityStats.builder()
                .scheduleId(scheduleId)
                .totalSeats(schedule.getTotalSeats())
                .availableSeats(schedule.getAvailableSeats())
                .bookedSeats(schedule.getTotalSeats() - schedule.getAvailableSeats())
                .availabilityPercentage(availabilityPercentage * 100)
                .isHighDemand(isHighDemand)
                .demandFactor(pricingService.calculateDemandFactor(availabilityPercentage))
                .build();
    }

    /**
     * Nested DTO for availability statistics
     */
    @lombok.Getter
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AvailabilityStats {
        private java.util.UUID scheduleId;
        private Integer totalSeats;
        private Integer availableSeats;
        private Integer bookedSeats;
        private Double availabilityPercentage;
        private Boolean isHighDemand;
        private Double demandFactor;
    }
}
