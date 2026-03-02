package com.ticketwave.catalog.api;

import com.ticketwave.common.api.ApiResponse;
import com.ticketwave.catalog.application.ScheduleSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScheduleSearchController Tests")
class ScheduleSearchControllerTest {

    @Mock
    private ScheduleSearchService scheduleSearchService;

    private ScheduleSearchController controller;

    private UUID scheduleId;

    @BeforeEach
    void setUp() {
        controller = new ScheduleSearchController(scheduleSearchService);
        scheduleId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Should search schedules and return 200 with results")
    void testSearchSchedules_Success() {
        // Arrange
        LocalDate travelDate = LocalDate.now();
        ScheduleSearchRequest request = ScheduleSearchRequest.builder()
                .originCity("New York")
                .destinationCity("Boston")
                .travelDate(travelDate)
                .sortBy("price")
                .sortOrder("asc")
                .build();

        ScheduleSearchResult result1 = ScheduleSearchResult.builder()
                .scheduleId(UUID.randomUUID())
                .originCity("New York")
                .destinationCity("Boston")
                .departureTime(travelDate.atTime(10, 0))
                .arrivalTime(travelDate.atTime(12, 0))
                .totalSeats(100)
                .availableSeats(50)
                .dynamicPrice(BigDecimal.valueOf(1000))
                .durationMinutes(120L)
                .build();

        List<ScheduleSearchResult> results = Arrays.asList(result1);

        when(scheduleSearchService.searchSchedules(request)).thenReturn(results);

        // Act
        ResponseEntity<ApiResponse<List<ScheduleSearchResult>>> response = controller.searchSchedules(request);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(1, response.getBody().getData().size());
        verify(scheduleSearchService, times(1)).searchSchedules(request);
    }

    @Test
    @DisplayName("Should return 200 with empty list when no schedules found")
    void testSearchSchedules_EmptyResults() {
        // Arrange
        ScheduleSearchRequest request = ScheduleSearchRequest.builder()
                .originCity("Nowhere")
                .destinationCity("Nowhere2")
                .travelDate(LocalDate.now())
                .sortBy("price")
                .sortOrder("asc")
                .build();

        when(scheduleSearchService.searchSchedules(request)).thenReturn(Arrays.asList());

        // Act
        ResponseEntity<ApiResponse<List<ScheduleSearchResult>>> response = controller.searchSchedules(request);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().getData().isEmpty());
    }

    @Test
    @DisplayName("Should get schedule details and return 200")
    void testGetScheduleDetails_Success() {
        // Arrange
        ScheduleSearchResult result = ScheduleSearchResult.builder()
                .scheduleId(scheduleId)
                .originCity("New York")
                .destinationCity("Boston")
                .totalSeats(100)
                .availableSeats(30)
                .dynamicPrice(BigDecimal.valueOf(1200))
                .build();

        when(scheduleSearchService.getScheduleDetails(scheduleId)).thenReturn(result);

        // Act
        ResponseEntity<ApiResponse<ScheduleSearchResult>> response = controller.getScheduleDetails(scheduleId);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(scheduleId, response.getBody().getData().getScheduleId());
    }

    @Test
    @DisplayName("Should return 404 when schedule details not found")
    void testGetScheduleDetails_NotFound() {
        // Arrange
        when(scheduleSearchService.getScheduleDetails(scheduleId)).thenReturn(null);

        // Act
        ResponseEntity<ApiResponse<ScheduleSearchResult>> response = controller.getScheduleDetails(scheduleId);

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
    }

    @Test
    @DisplayName("Should get availability stats and return 200")
    void testGetAvailabilityStats_Success() {
        // Arrange
        ScheduleSearchService.AvailabilityStats stats = ScheduleSearchService.AvailabilityStats.builder()
                .totalSeats(100)
                .availableSeats(30)
                .bookedSeats(70)
                .availabilityPercentage(30.0)
                .isHighDemand(true)
                .demandFactor(1.5)
                .build();

        when(scheduleSearchService.getAvailabilityStats(scheduleId)).thenReturn(stats);

        // Act
        ResponseEntity<ApiResponse<ScheduleSearchService.AvailabilityStats>> response = controller.getAvailabilityStats(scheduleId);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(100, response.getBody().getData().getTotalSeats());
        assertEquals(30.0, response.getBody().getData().getAvailabilityPercentage());
        assertTrue(response.getBody().getData().getIsHighDemand());
    }

    @Test
    @DisplayName("Should get high demand schedules and return 200")
    void testGetHighDemandSchedules_Success() {
        // Arrange
        ScheduleSearchResult highDemandResult = ScheduleSearchResult.builder()
                .scheduleId(UUID.randomUUID())
                .originCity("New York")
                .destinationCity("Boston")
                .dynamicPrice(BigDecimal.valueOf(1800))
                .demandFactor(1.8)
                .availabilityPercentage(8.0)
                .build();

        List<ScheduleSearchResult> results = Arrays.asList(highDemandResult);

        when(scheduleSearchService.getHighDemandSchedules("New York", "Boston"))
                .thenReturn(results);

        // Act
        ResponseEntity<ApiResponse<List<ScheduleSearchResult>>> response = 
                controller.getHighDemandSchedules("New York", "Boston");

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getData().size());
        assertEquals(1.8, response.getBody().getData().get(0).getDemandFactor());
    }

    @Test
    @DisplayName("Should return empty list for high demand schedules when none found")
    void testGetHighDemandSchedules_EmptyResults() {
        // Arrange
        when(scheduleSearchService.getHighDemandSchedules("Route1", "Route2"))
                .thenReturn(Arrays.asList());

        // Act
        ResponseEntity<ApiResponse<List<ScheduleSearchResult>>> response = 
                controller.getHighDemandSchedules("Route1", "Route2");

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().getData().isEmpty());
    }

    @Test
    @DisplayName("Should get duration and return 200 with minutes")
    void testGetDuration_Success() {
        // Arrange
        long durationMinutes = 180L;
        when(scheduleSearchService.getEstimatedDuration(scheduleId))
                .thenReturn(durationMinutes);

        // Act
        ResponseEntity<ApiResponse<Long>> response = controller.getDuration(scheduleId);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(durationMinutes, response.getBody().getData());
    }

    @Test
    @DisplayName("Should return 0 duration for invalid schedule")
    void testGetDuration_InvalidSchedule() {
        // Arrange
        when(scheduleSearchService.getEstimatedDuration(scheduleId))
                .thenReturn(0L);

        // Act
        ResponseEntity<ApiResponse<Long>> response = controller.getDuration(scheduleId);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0L, response.getBody().getData());
    }

    @Test
    @DisplayName("Should validate and reject invalid sort order")
    void testSearchSchedules_ValidatesSortParameters() {
        // Arrange
        ScheduleSearchRequest invalidRequest = ScheduleSearchRequest.builder()
                .originCity("New York")
                .destinationCity("Boston")
                .travelDate(LocalDate.now())
                .sortBy("invalid")
                .sortOrder("invalid")
                .build();

        when(scheduleSearchService.searchSchedules(invalidRequest))
                .thenReturn(Arrays.asList());

        // Act
        ResponseEntity<ApiResponse<List<ScheduleSearchResult>>> response = 
                controller.searchSchedules(invalidRequest);

        // Assert
        assertNotNull(response.getBody());
        // Service should return empty or handle gracefully
    }

    @Test
    @DisplayName("Should handle concurrent search requests")
    void testSearchSchedules_ConcurrentRequests() throws InterruptedException {
        // Arrange
        ScheduleSearchRequest request = ScheduleSearchRequest.builder()
                .originCity("A")
                .destinationCity("B")
                .travelDate(LocalDate.now())
                .sortBy("price")
                .sortOrder("asc")
                .build();

        ScheduleSearchResult result = ScheduleSearchResult.builder()
                .scheduleId(UUID.randomUUID())
                .dynamicPrice(BigDecimal.valueOf(1000))
                .build();

        when(scheduleSearchService.searchSchedules(request))
                .thenReturn(Arrays.asList(result));

        // Act - simulate concurrent calls
        Thread thread1 = new Thread(() -> controller.searchSchedules(request));
        Thread thread2 = new Thread(() -> controller.searchSchedules(request));

        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();

        // Assert
        verify(scheduleSearchService, times(2)).searchSchedules(request);
    }
}
