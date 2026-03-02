package com.ticketwave.catalog.application;

import com.ticketwave.catalog.domain.Schedule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PricingCalculationService Unit Tests")
class PricingCalculationServiceTest {

    private PricingCalculationService pricingService;

    @BeforeEach
    void setUp() {
        pricingService = new PricingCalculationService();
        // Set properties via reflection or use test configuration
        pricingService.highDemandThreshold = 0.3;
        pricingService.baseModifier = 1.0;
        pricingService.highDemandMultiplier = 1.5;
        pricingService.lowAvailabilityMultiplier = 1.8;
    }

    @Test
    @DisplayName("Should calculate availability percentage correctly")
    void testCalculateAvailabilityPercentage() {
        // Arrange
        Schedule schedule = Schedule.builder()
                .totalSeats(100)
                .availableSeats(50)
                .baseFare(BigDecimal.valueOf(500))
                .build();

        // Act
        double availability = pricingService.calculateAvailabilityPercentage(schedule);

        // Assert
        assertEquals(0.5, availability, 0.01);
    }

    @Test
    @DisplayName("Should return 0 when total seats is 0")
    void testCalculateAvailabilityPercentage_ZeroSeats() {
        // Arrange
        Schedule schedule = Schedule.builder()
                .totalSeats(0)
                .availableSeats(0)
                .baseFare(BigDecimal.valueOf(500))
                .build();

        // Act
        double availability = pricingService.calculateAvailabilityPercentage(schedule);

        // Assert
        assertEquals(0.0, availability);
    }

    @Test
    @DisplayName("Should apply base demand factor (1.0) when availability >= 30%")
    void testCalculateDemandFactor_NormalDemand() {
        // Act
        double demandFactor = pricingService.calculateDemandFactor(0.4); // 40% availability

        // Assert
        assertEquals(1.0, demandFactor);
    }

    @Test
    @DisplayName("Should apply high demand factor (1.5x) when availability < 30%")
    void testCalculateDemandFactor_HighDemand() {
        // Act
        double demandFactor = pricingService.calculateDemandFactor(0.25); // 25% availability

        // Assert
        assertEquals(1.5, demandFactor);
    }

    @Test
    @DisplayName("Should apply premium factor (1.8x) when availability < 10%")
    void testCalculateDemandFactor_CriticalAvailability() {
        // Act
        double demandFactor = pricingService.calculateDemandFactor(0.05); // 5% availability

        // Assert
        assertEquals(1.8, demandFactor);
    }

    @Test
    @DisplayName("Should apply base factor (1.0) at exactly 30% availability boundary")
    void testCalculateDemandFactor_BoundaryAtThreshold() {
        // Act
        double demandFactorAt30 = pricingService.calculateDemandFactor(0.30);
        double demandFactorBelow30 = pricingService.calculateDemandFactor(0.2999);

        // Assert
        assertEquals(1.0, demandFactorAt30, "30% should use base factor");
        assertEquals(1.5, demandFactorBelow30, "Below 30% should use high demand factor");
    }

    @Test
    @DisplayName("Should apply premium factor at exactly 10% availability boundary")
    void testCalculateDemandFactor_BoundaryAtLowAvailability() {
        // Act
        double demandFactorAt10 = pricingService.calculateDemandFactor(0.10);
        double demandFactorBelow10 = pricingService.calculateDemandFactor(0.0999);

        // Assert
        assertEquals(1.5, demandFactorAt10, "10% should use high demand factor");
        assertEquals(1.8, demandFactorBelow10, "Below 10% should use premium factor");
    }

    @Test
    @DisplayName("Should calculate dynamic price: base_fare * modifier * demand_factor")
    void testCalculateDynamicPrice_NormalDemand() {
        // Arrange
        Schedule schedule = Schedule.builder()
                .totalSeats(100)
                .availableSeats(50)
                .baseFare(BigDecimal.valueOf(1000))
                .build();

        double priceModifier = 1.0;
        // Expected: 1000 * 1.0 * 1.0 = 1000

        // Act
        BigDecimal dynamicPrice = pricingService.calculateDynamicPrice(schedule, priceModifier);

        // Assert
        assertEquals(BigDecimal.valueOf(1000), dynamicPrice);
    }

    @Test
    @DisplayName("Should apply high demand pricing multiplier")
    void testCalculateDynamicPrice_HighDemand() {
        // Arrange
        Schedule schedule = Schedule.builder()
                .totalSeats(100)
                .availableSeats(20) // 20% availability < 30%
                .baseFare(BigDecimal.valueOf(1000))
                .build();

        double priceModifier = 1.0;
        // Expected: 1000 * 1.0 * 1.5 = 1500

        // Act
        BigDecimal dynamicPrice = pricingService.calculateDynamicPrice(schedule, priceModifier);

        // Assert
        assertEquals(BigDecimal.valueOf(1500.00), dynamicPrice);
    }

    @Test
    @DisplayName("Should apply premium pricing for very low availability")
    void testCalculateDynamicPrice_VeryLowAvailability() {
        // Arrange
        Schedule schedule = Schedule.builder()
                .totalSeats(100)
                .availableSeats(5) // 5% availability < 10%
                .baseFare(BigDecimal.valueOf(1000))
                .build();

        double priceModifier = 1.0;
        // Expected: 1000 * 1.0 * 1.8 = 1800

        // Act
        BigDecimal dynamicPrice = pricingService.calculateDynamicPrice(schedule, priceModifier);

        // Assert
        assertEquals(BigDecimal.valueOf(1800.00), dynamicPrice);
    }

    @Test
    @DisplayName("Should apply price modifier correctly")
    void testCalculateDynamicPrice_WithModifier() {
        // Arrange
        Schedule schedule = Schedule.builder()
                .totalSeats(100)
                .availableSeats(50)
                .baseFare(BigDecimal.valueOf(1000))
                .build();

        double priceModifier = 1.2; // 20% increase
        // Expected: 1000 * 1.2 * 1.0 = 1200

        // Act
        BigDecimal dynamicPrice = pricingService.calculateDynamicPrice(schedule, priceModifier);

        // Assert
        assertEquals(BigDecimal.valueOf(1200.00), dynamicPrice);
    }

    @Test
    @DisplayName("Should apply combined modifiers and demand factors")
    void testCalculateDynamicPrice_CombinedFactors() {
        // Arrange
        Schedule schedule = Schedule.builder()
                .totalSeats(100)
                .availableSeats(15) // 15% availability = high demand
                .baseFare(BigDecimal.valueOf(1000))
                .build();

        double priceModifier = 1.25; // 25% weekend surcharge
        // Expected: 1000 * 1.25 * 1.5 = 1875

        // Act
        BigDecimal dynamicPrice = pricingService.calculateDynamicPrice(schedule, priceModifier);

        // Assert
        assertEquals(BigDecimal.valueOf(1875.00), dynamicPrice);
    }

    @Test
    @DisplayName("Should round to 2 decimal places")
    void testCalculateDynamicPrice_Rounding() {
        // Arrange
        Schedule schedule = Schedule.builder()
                .totalSeats(3)
                .availableSeats(1) // 33.33% - high demand threshold edge
                .baseFare(BigDecimal.valueOf(333.33))
                .build();

        double priceModifier = 1.0;

        // Act
        BigDecimal dynamicPrice = pricingService.calculateDynamicPrice(schedule, priceModifier);

        // Assert
        assertEquals(2, dynamicPrice.scale(), "Result should have 2 decimal places");
    }

    @Test
    @DisplayName("Should identify high demand schedules correctly")
    void testIsHighDemand() {
        // Arrange
        Schedule lowDemandSchedule = Schedule.builder()
                .totalSeats(100)
                .availableSeats(50)
                .baseFare(BigDecimal.valueOf(500))
                .build();

        Schedule highDemandSchedule = Schedule.builder()
                .totalSeats(100)
                .availableSeats(20)
                .baseFare(BigDecimal.valueOf(500))
                .build();

        // Act & Assert
        assertFalse(pricingService.isHighDemand(lowDemandSchedule), "50% availability should not be high demand");
        assertTrue(pricingService.isHighDemand(highDemandSchedule), "20% availability should be high demand");
    }

    @Test
    @DisplayName("Should handle edge case: completely sold out")
    void testCalculateDynamicPrice_SoldOut() {
        // Arrange
        Schedule schedule = Schedule.builder()
                .totalSeats(100)
                .availableSeats(0) // 0% availability
                .baseFare(BigDecimal.valueOf(1000))
                .build();

        double priceModifier = 1.0;
        // Expected: 1000 * 1.0 * 1.8 = 1800

        // Act
        BigDecimal dynamicPrice = pricingService.calculateDynamicPrice(schedule, priceModifier);

        // Assert
        assertEquals(BigDecimal.valueOf(1800.00), dynamicPrice);
    }

    @Test
    @DisplayName("Should handle negative price modifier (discounts)")
    void testCalculateDynamicPrice_WithDiscount() {
        // Arrange
        Schedule schedule = Schedule.builder()
                .totalSeats(100)
                .availableSeats(80)
                .baseFare(BigDecimal.valueOf(1000))
                .build();

        double priceModifier = 0.8; // 20% discount
        // Expected: 1000 * 0.8 * 1.0 = 800

        // Act
        BigDecimal dynamicPrice = pricingService.calculateDynamicPrice(schedule, priceModifier);

        // Assert
        assertEquals(BigDecimal.valueOf(800.00), dynamicPrice);
    }
}
