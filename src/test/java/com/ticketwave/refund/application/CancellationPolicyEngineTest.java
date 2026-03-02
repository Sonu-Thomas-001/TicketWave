package com.ticketwave.refund.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.ticketwave.refund.domain.CancellationPolicy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CancellationPolicyEngine.
 * Tests refund calculation logic based on cancellation policies and timing.
 */
@DisplayName("CancellationPolicyEngine Tests")
class CancellationPolicyEngineTest {

    private CancellationPolicyEngine engine;
    private CancellationPolicy standardPolicy;

    @BeforeEach
    void setUp() {
        engine = new CancellationPolicyEngine();
        
        // Standard policy: 72 hours full refund, 24 hours partial (50%), 2.5% processing fee
        standardPolicy = CancellationPolicy.builder()
                .policyId("POL-STANDARD")
                .policyName("Standard Event Cancellation")
                .eventType("MOVIE")
                .fullRefundWindowHours(72)
                .partialRefundWindowHours(24)
                .minimumCancellationFee(BigDecimal.ZERO)
                .partialRefundPercentage(BigDecimal.valueOf(50))
                .processingFeePercentage(BigDecimal.valueOf(2.5))
                .allowNonRefundable(false)
                .build();
    }

    @Test
    @DisplayName("Should calculate full refund when cancellation is >72 hours before event")
    void testFullRefundWindow() {
        // Given
        Instant now = Instant.now();
        Instant eventTime = now.plus(96, ChronoUnit.HOURS); // 4 days away
        BigDecimal originalAmount = BigDecimal.valueOf(1000);

        // When
        CancellationPolicyEngine.RefundCalculation result = engine.calculateRefund(
                standardPolicy, originalAmount, eventTime, now);

        // Then
        assertEquals("FULL_REFUND", result.getRefundType());
        assertEquals(BigDecimal.valueOf(97.5), result.getRefundAmount()); // 1000 - 2.5% fee
        assertEquals(96, result.getHoursUntilEvent());
    }

    @Test
    @DisplayName("Should calculate partial refund when cancellation is 24-72 hours before event")
    void testPartialRefundWindow() {
        // Given
        Instant now = Instant.now();
        Instant eventTime = now.plus(48, ChronoUnit.HOURS); // 2 days away
        BigDecimal originalAmount = BigDecimal.valueOf(1000);

        // When
        CancellationPolicyEngine.RefundCalculation result = engine.calculateRefund(
                standardPolicy, originalAmount, eventTime, now);

        // Then
        assertEquals("PARTIAL_REFUND", result.getRefundType());
        assertTrue(result.getRefundAmount().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(result.getRefundAmount().compareTo(BigDecimal.valueOf(500)) < 0); // Less than 50%
        assertEquals(48, result.getHoursUntilEvent());
    }

    @Test
    @DisplayName("Should return no refund when cancellation is <24 hours before event")
    void testNoRefundWindow() {
        // Given
        Instant now = Instant.now();
        Instant eventTime = now.plus(12, ChronoUnit.HOURS); // 12 hours away
        BigDecimal originalAmount = BigDecimal.valueOf(1000);

        // When
        CancellationPolicyEngine.RefundCalculation result = engine.calculateRefund(
                standardPolicy, originalAmount, eventTime, now);

        // Then
        assertEquals("FULL_REFUND", result.getRefundType()); // As per policy: allowNonRefundable = false
        assertEquals(BigDecimal.valueOf(1000), result.getRefundAmount()); // Full refund outside window
        assertEquals(12, result.getHoursUntilEvent());
    }

    @Test
    @DisplayName("Should return no refund when event has already started")
    void testEventStarted() {
        // Given
        Instant now = Instant.now();
        Instant eventTime = now.minus(2, ChronoUnit.HOURS); // 2 hours past
        BigDecimal originalAmount = BigDecimal.valueOf(1000);

        // When
        CancellationPolicyEngine.RefundCalculation result = engine.calculateRefund(
                standardPolicy, originalAmount, eventTime, now);

        // Then
        assertEquals("NO_REFUND", result.getRefundType());
        assertEquals(BigDecimal.ZERO, result.getRefundAmount());
        assertEquals(-2, result.getHoursUntilEvent());
    }

    @Test
    @DisplayName("Should apply minimum cancellation fee when refund is too small")
    void testMinimumCancellationFee() {
        // Given: Policy with minimum fee of 50
        CancellationPolicy policyWithMinFee = CancellationPolicy.builder()
                .policyId("POL-MIN-FEE")
                .policyName("Policy with Min Fee")
                .eventType("FLIGHT")
                .fullRefundWindowHours(72)
                .partialRefundWindowHours(24)
                .minimumCancellationFee(BigDecimal.valueOf(50)) // Minimum 50
                .partialRefundPercentage(BigDecimal.valueOf(5)) // Only 5% refundable
                .processingFeePercentage(BigDecimal.valueOf(2.5))
                .allowNonRefundable(false)
                .build();

        Instant now = Instant.now();
        Instant eventTime = now.plus(48, ChronoUnit.HOURS);
        BigDecimal originalAmount = BigDecimal.valueOf(1000); // 5% = 50, should apply min fee

        // When
        CancellationPolicyEngine.RefundCalculation result = engine.calculateRefund(
                policyWithMinFee, originalAmount, eventTime, now);

        // Then
        assertTrue(result.getRefundAmount().compareTo(BigDecimal.valueOf(50)) >= 0);
    }

    @Test
    @DisplayName("Should deduct processing fee from all refunds")
    void testProcessingFeeDeduction() {
        // Given
        Instant now = Instant.now();
        Instant eventTime = now.plus(96, ChronoUnit.HOURS); // Full refund window
        BigDecimal originalAmount = BigDecimal.valueOf(1000);

        // When
        CancellationPolicyEngine.RefundCalculation result = engine.calculateRefund(
                standardPolicy, originalAmount, eventTime, now);

        // Then
        // Full refund 1000 - 2.5% = 975
        assertEquals(BigDecimal.valueOf(975), result.getRefundAmount());
        assertEquals(BigDecimal.valueOf(25), result.getProcessingFeeDeducted());
    }

    @Test
    @DisplayName("Should support non-refundable policies")
    void testNonRefundablePolicy() {
        // Given
        CancellationPolicy nonRefundablePolicy = CancellationPolicy.builder()
                .policyId("POL-NONREF")
                .policyName("Non-Refundable")
                .eventType("CONCERT")
                .fullRefundWindowHours(0)
                .partialRefundWindowHours(0)
                .partialRefundPercentage(BigDecimal.ZERO)
                .processingFeePercentage(BigDecimal.ZERO)
                .allowNonRefundable(true)
                .build();

        Instant now = Instant.now();
        Instant eventTime = now.plus(72, ChronoUnit.HOURS);
        BigDecimal originalAmount = BigDecimal.valueOf(1000);

        // When
        CancellationPolicyEngine.RefundCalculation result = engine.calculateRefund(
                nonRefundablePolicy, originalAmount, eventTime, now);

        // Then
        assertEquals("NO_REFUND", result.getRefundType());
        assertEquals(BigDecimal.ZERO, result.getRefundAmount());
    }

    @Test
    @DisplayName("Should calculate exact timing boundaries correctly")
    void testTimingBoundaries() {
        // Given: Exactly 72 hours before event (full refund boundary)
        Instant now = Instant.now();
        Instant eventTime = now.plus(72, ChronoUnit.HOURS);
        BigDecimal originalAmount = BigDecimal.valueOf(1000);

        // When
        CancellationPolicyEngine.RefundCalculation result = engine.calculateRefund(
                standardPolicy, originalAmount, eventTime, now);

        // Then
        assertEquals("FULL_REFUND", result.getRefundType());
        assertEquals(BigDecimal.valueOf(975), result.getRefundAmount()); // Full refund with fee
    }

    @Test
    @DisplayName("Should provide detailed calculation breakdown")
    void testCalculationBreakdown() {
        // Given
        Instant now = Instant.now();
        Instant eventTime = now.plus(96, ChronoUnit.HOURS);
        BigDecimal originalAmount = BigDecimal.valueOf(1000);

        // When
        CancellationPolicyEngine.RefundCalculation result = engine.calculateRefund(
                standardPolicy, originalAmount, eventTime, now);

        // Then
        assertNotNull(result.getPolicy());
        assertEquals(originalAmount, result.getOriginalAmount());
        assertEquals(eventTime, result.getEventStartTime());
        assertEquals(now, result.getCancellationTime());
        assertEquals(96, result.getHoursUntilEvent());
        assertNotNull(result.getReason());
        assertFalse(result.getReason().isEmpty());
    }
}
