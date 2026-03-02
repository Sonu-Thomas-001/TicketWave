package com.ticketwave.catalog.application;

import com.ticketwave.catalog.api.ScheduleSearchResult;
import com.ticketwave.catalog.domain.Schedule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;

@Slf4j
@Service
public class PricingCalculationService {

    @Value("${app.pricing.high-demand-threshold:0.3}")
    private double highDemandThreshold; // Demand factor when availability < 30%

    @Value("${app.pricing.base-modifier:1.0}")
    private double baseModifier;

    @Value("${app.pricing.high-demand-multiplier:1.5}")
    private double highDemandMultiplier;

    @Value("${app.pricing.low-availability-multiplier:1.8}")
    private double lowAvailabilityMultiplier; // When < 10% seats available

    /**
     * Calculate dynamic price based on demand and availability.
     * Formula: final_price = base_fare * price_modifier * demand_factor
     *
     * Demand factor logic:
     * - High demand (availability < 30%): 1.5x
     * - Very high demand (< 10% availability): 1.8x
     * - Normal demand (>= 30% availability): 1.0x
     */
    public BigDecimal calculateDynamicPrice(Schedule schedule, double priceModifier) {
        double availabilityPercentage = calculateAvailabilityPercentage(schedule);
        double demandFactor = calculateDemandFactor(availabilityPercentage);

        BigDecimal baseFare = schedule.getBaseFare();
        BigDecimal modifierValue = BigDecimal.valueOf(priceModifier);
        BigDecimal demandValue = BigDecimal.valueOf(demandFactor);

        BigDecimal dynamicPrice = baseFare
                .multiply(modifierValue)
                .multiply(demandValue);

        log.debug("Dynamic price calculated: scheduleId={}, baseFare={}, modifier={}, demandFactor={}, finalPrice={}", 
                schedule.getId(), baseFare, priceModifier, demandFactor, dynamicPrice);

        return dynamicPrice.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Calculate demand factor based on seat availability percentage.
     * Higher demand when fewer seats available.
     */
    public double calculateDemandFactor(double availabilityPercentage) {
        if (availabilityPercentage < 0.10) {
            return lowAvailabilityMultiplier; // 1.8x for < 10% availability
        } else if (availabilityPercentage < highDemandThreshold) {
            return highDemandMultiplier; // 1.5x for < 30% availability
        } else {
            return 1.0; // Base price for >= 30% availability
        }
    }

    /**
     * Calculate availability percentage (0-1 decimal).
     */
    public double calculateAvailabilityPercentage(Schedule schedule) {
        if (schedule.getTotalSeats() == 0) {
            return 0.0;
        }
        return (double) schedule.getAvailableSeats() / schedule.getTotalSeats();
    }

    /**
     * Enrich schedule search result with pricing and availability details.
     */
    public ScheduleSearchResult enrichWithPricing(ScheduleSearchResult result, Schedule schedule, double priceModifier) {
        double availability = calculateAvailabilityPercentage(schedule);
        double demandFactor = calculateDemandFactor(availability);
        BigDecimal dynamicPrice = calculateDynamicPrice(schedule, priceModifier);

        long durationMinutes = Duration.between(
                result.getDepartureTime().toLocalTime(),
                result.getArrivalTime().toLocalTime()
        ).toMinutes();

        result.setAvailabilityPercentage(availability * 100); // Convert to percentage
        result.setPriceModifier(priceModifier);
        result.setDemandFactor(demandFactor);
        result.setDynamicPrice(dynamicPrice);
        result.setDurationMinutes(durationMinutes);

        return result;
    }

    /**
     * Check if schedule qualifies for high-demand pricing.
     */
    public boolean isHighDemand(Schedule schedule) {
        double availabilityPercentage = calculateAvailabilityPercentage(schedule);
        return availabilityPercentage < highDemandThreshold;
    }
}
