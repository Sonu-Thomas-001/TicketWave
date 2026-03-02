package com.ticketwave.catalog.application;

import com.ticketwave.catalog.api.ScheduleSearchRequest;
import com.ticketwave.catalog.api.ScheduleSearchResult;
import com.ticketwave.catalog.domain.Route;
import com.ticketwave.catalog.domain.Schedule;
import com.ticketwave.catalog.infrastructure.ScheduleRepository;
import com.ticketwave.catalog.mapper.ScheduleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScheduleSearchService Tests")
class ScheduleSearchServiceTest {

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private ScheduleMapper scheduleMapper;

    @Mock
    private PricingCalculationService pricingService;

    private ScheduleSearchService scheduleSearchService;

    private UUID routeId;
    private UUID schedule1Id;
    private UUID schedule2Id;
    private UUID schedule3Id;

    @BeforeEach
    void setUp() {
        scheduleSearchService = new ScheduleSearchService(scheduleRepository, scheduleMapper, pricingService);

        routeId = UUID.randomUUID();
        schedule1Id = UUID.randomUUID();
        schedule2Id = UUID.randomUUID();
        schedule3Id = UUID.randomUUID();
    }

    @Test
    @DisplayName("Should search and return schedules sorted by price ascending")
    void testSearchSchedules_SortByPrice() {
        // Arrange
        LocalDate travelDate = LocalDate.now();
        ScheduleSearchRequest request = ScheduleSearchRequest.builder()
                .originCity("New York")
                .destinationCity("Boston")
                .travelDate(travelDate)
                .sortBy("price")
                .sortOrder("asc")
                .build();

        Route route = Route.builder().id(routeId).originCity("New York").destinationCity("Boston").build();

        Schedule schedule1 = createSchedule(schedule1Id, route, 100, 30, 1500); // High price, low availability
        Schedule schedule2 = createSchedule(schedule2Id, route, 100, 50, 1000); // Low price, normal availability
        Schedule schedule3 = createSchedule(schedule3Id, route, 100, 70, 1200); // Medium price, high availability

        List<Schedule> schedules = Arrays.asList(schedule1, schedule2, schedule3);

        when(scheduleRepository.searchByOriginDestinationDate(
                "New York", "Boston", travelDate.atStartOfDay()))
                .thenReturn(schedules);

        // Mock mapper
        when(scheduleMapper.scheduleToSearchResult(any(Schedule.class)))
                .thenAnswer(invocation -> {
                    Schedule s = invocation.getArgument(0);
                    return ScheduleSearchResult.builder()
                            .scheduleId(s.getId())
                            .dynamicPrice(BigDecimal.valueOf(999)) // Placeholder
                            .build();
                });

        // Mock pricing service
        when(pricingService.enrichWithPricing(any(), any(), anyDouble()))
                .thenAnswer(invocation -> {
                    ScheduleSearchResult result = invocation.getArgument(0);
                    Schedule schedule = invocation.getArgument(1);
                    result.setDynamicPrice(schedule.getBaseFare());
                    result.setAvailabilityPercentage(
                            (double) schedule.getAvailableSeats() / schedule.getTotalSeats() * 100);
                    result.setDurationMinutes(120L);
                    return result;
                });

        when(scheduleRepository.findById(any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            if (id.equals(schedule1Id)) return Optional.of(schedule1);
            if (id.equals(schedule2Id)) return Optional.of(schedule2);
            if (id.equals(schedule3Id)) return Optional.of(schedule3);
            return Optional.empty();
        });

        // Act
        List<ScheduleSearchResult> results = scheduleSearchService.searchSchedules(request);

        // Assert
        assertEquals(3, results.size());
        assertEquals(BigDecimal.valueOf(1000), results.get(0).getDynamicPrice(), "Lowest price first");
        assertEquals(BigDecimal.valueOf(1200), results.get(1).getDynamicPrice());
        assertEquals(BigDecimal.valueOf(1500), results.get(2).getDynamicPrice(), "Highest price last");
    }

    @Test
    @DisplayName("Should search and return schedules sorted by duration")
    void testSearchSchedules_SortByDuration() {
        // Arrange
        LocalDate travelDate = LocalDate.now();
        ScheduleSearchRequest request = ScheduleSearchRequest.builder()
                .originCity("New York")
                .destinationCity("Boston")
                .travelDate(travelDate)
                .sortBy("duration")
                .sortOrder("asc")
                .build();

        Route route = Route.builder().id(routeId).originCity("New York").destinationCity("Boston").build();

        Schedule schedule1 = Schedule.builder()
                .id(schedule1Id)
                .route(route)
                .departureTime(travelDate.atTime(8, 0))
                .arrivalTime(travelDate.atTime(14, 0)) // 6 hours
                .totalSeats(100)
                .availableSeats(30)
                .baseFare(BigDecimal.valueOf(1000))
                .active(true)
                .build();

        Schedule schedule2 = Schedule.builder()
                .id(schedule2Id)
                .route(route)
                .departureTime(travelDate.atTime(8, 0))
                .arrivalTime(travelDate.atTime(11, 0)) // 3 hours
                .totalSeats(100)
                .availableSeats(50)
                .baseFare(BigDecimal.valueOf(1200))
                .active(true)
                .build();

        List<Schedule> schedules = Arrays.asList(schedule1, schedule2);

        when(scheduleRepository.searchByOriginDestinationDate(anyString(), anyString(), any()))
                .thenReturn(schedules);

        when(scheduleMapper.scheduleToSearchResult(any()))
                .thenAnswer(invocation -> {
                    Schedule s = invocation.getArgument(0);
                    return ScheduleSearchResult.builder()
                            .scheduleId(s.getId())
                            .departureTime(s.getDepartureTime())
                            .arrivalTime(s.getArrivalTime())
                            .build();
                });

        when(pricingService.enrichWithPricing(any(), any(), anyDouble()))
                .thenAnswer(invocation -> {
                    ScheduleSearchResult result = invocation.getArgument(0);
                    Schedule schedule = invocation.getArgument(1);
                    long duration = java.time.Duration.between(
                            result.getDepartureTime(), result.getArrivalTime()).toMinutes();
                    result.setDurationMinutes(duration);
                    result.setDynamicPrice(schedule.getBaseFare());
                    return result;
                });

        when(scheduleRepository.findById(any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            if (id.equals(schedule1Id)) return Optional.of(schedule1);
            if (id.equals(schedule2Id)) return Optional.of(schedule2);
            return Optional.empty();
        });

        // Act
        List<ScheduleSearchResult> results = scheduleSearchService.searchSchedules(request);

        // Assert
        assertEquals(2, results.size());
        assertEquals(180L, results.get(0).getDurationMinutes(), "3 hours first (shortest)");
        assertEquals(360L, results.get(1).getDurationMinutes(), "6 hours second (longest)");
    }

    @Test
    @DisplayName("Should return empty list when no schedules match search criteria")
    void testSearchSchedules_NoResults() {
        // Arrange
        LocalDate travelDate = LocalDate.now();
        ScheduleSearchRequest request = ScheduleSearchRequest.builder()
                .originCity("NonExistent")
                .destinationCity("AlsoNone")
                .travelDate(travelDate)
                .sortBy("price")
                .sortOrder("asc")
                .build();

        when(scheduleRepository.searchByOriginDestinationDate(anyString(), anyString(), any()))
                .thenReturn(Arrays.asList());

        // Act
        List<ScheduleSearchResult> results = scheduleSearchService.searchSchedules(request);

        // Assert
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("Should sort by availability (descending - most available first)")
    void testSearchSchedules_SortByAvailability() {
        // Arrange
        LocalDate travelDate = LocalDate.now();
        ScheduleSearchRequest request = ScheduleSearchRequest.builder()
                .originCity("New York")
                .destinationCity("Boston")
                .travelDate(travelDate)
                .sortBy("availability")
                .sortOrder("desc")
                .build();

        Route route = Route.builder().originCity("New York").destinationCity("Boston").build();

        Schedule schedule1 = createSchedule(schedule1Id, route, 100, 20, 1000);
        Schedule schedule2 = createSchedule(schedule2Id, route, 100, 50, 1000);
        Schedule schedule3 = createSchedule(schedule3Id, route, 100, 80, 1000);

        when(scheduleRepository.searchByOriginDestinationDate(anyString(), anyString(), any()))
                .thenReturn(Arrays.asList(schedule1, schedule2, schedule3));

        when(scheduleMapper.scheduleToSearchResult(any()))
                .thenAnswer(invocation -> {
                    Schedule s = invocation.getArgument(0);
                    return ScheduleSearchResult.builder()
                            .scheduleId(s.getId())
                            .availableSeats(s.getAvailableSeats())
                            .build();
                });

        when(pricingService.enrichWithPricing(any(), any(), anyDouble()))
                .thenAnswer(invocation -> {
                    ScheduleSearchResult result = invocation.getArgument(0);
                    result.setDurationMinutes(0L);
                    result.setDynamicPrice(BigDecimal.zero());
                    return result;
                });

        when(scheduleRepository.findById(any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            if (id.equals(schedule1Id)) return Optional.of(schedule1);
            if (id.equals(schedule2Id)) return Optional.of(schedule2);
            if (id.equals(schedule3Id)) return Optional.of(schedule3);
            return Optional.empty();
        });

        // Act
        List<ScheduleSearchResult> results = scheduleSearchService.searchSchedules(request);

        // Assert
        assertEquals(3, results.size());
        assertEquals(80, results.get(0).getAvailableSeats(), "Most available first (desc)");
        assertEquals(50, results.get(1).getAvailableSeats());
        assertEquals(20, results.get(2).getAvailableSeats(), "Least available last");
    }

    @Test
    @DisplayName("Should return null for non-existent schedule details")
    void testGetScheduleDetails_NotFound() {
        // Arrange
        UUID scheduleId = UUID.randomUUID();
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.empty());

        // Act
        ScheduleSearchResult result = scheduleSearchService.getScheduleDetails(scheduleId);

        // Assert
        assertNull(result);
    }

    @Test
    @DisplayName("Should get availability statistics correctly")
    void testGetAvailabilityStats() {
        // Arrange
        Route route = Route.builder().originCity("A").destinationCity("B").build();
        Schedule schedule = createSchedule(schedule1Id, route, 100, 25, 1000);

        when(scheduleRepository.findById(schedule1Id)).thenReturn(Optional.of(schedule));
        when(pricingService.calculateAvailabilityPercentage(schedule)).thenReturn(0.25);
        when(pricingService.isHighDemand(schedule)).thenReturn(true);
        when(pricingService.calculateDemandFactor(0.25)).thenReturn(1.5);

        // Act
        ScheduleSearchService.AvailabilityStats stats = scheduleSearchService.getAvailabilityStats(schedule1Id);

        // Assert
        assertNotNull(stats);
        assertEquals(100, stats.getTotalSeats());
        assertEquals(25, stats.getAvailableSeats());
        assertEquals(75, stats.getBookedSeats());
        assertEquals(25.0, stats.getAvailabilityPercentage());
        assertTrue(stats.getIsHighDemand());
        assertEquals(1.5, stats.getDemandFactor());
    }

    private Schedule createSchedule(UUID id, Route route, int totalSeats, int availableSeats,
                                   double baseFareAmount) {
        LocalDate today = LocalDate.now();
        return Schedule.builder()
                .id(id)
                .route(route)
                .departureTime(today.atTime(10, 0))
                .arrivalTime(today.atTime(12, 0))
                .totalSeats(totalSeats)
                .availableSeats(availableSeats)
                .baseFare(BigDecimal.valueOf(baseFareAmount))
                .active(true)
                .build();
    }
}
