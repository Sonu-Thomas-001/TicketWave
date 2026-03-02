package com.ticketwave.refund.application;

import com.ticketwave.refund.domain.CancellationPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * CancellationPolicyEngine calculates refund amounts based on cancellation policies and timing.
 * Implements prorated refunds within allowed windows.
 * 
 * Refund calculation logic:
 * 1. Check if cancellation is within refund window
 * 2. If full refund window: 100% - processing fee
 * 3. If partial refund window: partial% - deductions
 * 4. If outside window: No refund or admin override
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CancellationPolicyEngine {

    /**
     * Calculate refund amount based on policy and cancellation timing.
     * 
     * @param policy CancellationPolicy defining rules
     * @param originalAmount Original booking amount
     * @param eventStartTime When the event/travel is scheduled
     * @param cancellationTime When cancellation was requested
     * @return RefundCalculation result with breakdown
     */
    public RefundCalculation calculateRefund(CancellationPolicy policy, BigDecimal originalAmount, 
                                            Instant eventStartTime, Instant cancellationTime) {
        log.info("Calculating refund - Policy: {}, Original: {}, Event: {}, Cancelled: {}",
                policy.getPolicyId(), originalAmount, eventStartTime, cancellationTime);

        RefundCalculation.RefundCalculationBuilder builder = RefundCalculation.builder()
                .policy(policy)
                .originalAmount(originalAmount)
                .eventStartTime(eventStartTime)
                .cancellationTime(cancellationTime);

        // Calculate hours until event starts
        long hoursUntilEvent = ChronoUnit.HOURS.between(cancellationTime, eventStartTime);
        builder.hoursUntilEvent(hoursUntilEvent);

        if (hoursUntilEvent < 0) {
            // Event already started; no refund
            log.info("Event already started. No refund applicable.");
            builder.refundType("NO_REFUND")
                    .refundPercentage(BigDecimal.ZERO)
                    .refundAmount(BigDecimal.ZERO)
                    .reason("Event has already started");
            return builder.build();
        }

        // Check full refund window
        if (hoursUntilEvent >= policy.getFullRefundWindowHours()) {
            // Full refund minus processing fee
            BigDecimal refundAmount = originalAmount
                    .multiply(BigDecimal.valueOf(100 - policy.getProcessingFeePercentage().doubleValue()))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            log.info("Full refund applicable: {} hours available, {} refund window", 
                    hoursUntilEvent, policy.getFullRefundWindowHours());

            builder.refundType("FULL_REFUND")
                    .refundPercentage(BigDecimal.valueOf(100))
                    .refundAmount(refundAmount)
                    .processingFeeDeducted(originalAmount.subtract(refundAmount))
                    .reason("Full refund window applicable");
        }
        // Check partial refund window
        else if (hoursUntilEvent >= policy.getPartialRefundWindowHours()) {
            // Partial refund based on policy percentage
            BigDecimal refundPercentage = policy.getPartialRefundPercentage();
            BigDecimal policyDeduction = originalAmount
                    .multiply(BigDecimal.valueOf(100).subtract(refundPercentage))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            BigDecimal refundBeforeFees = originalAmount.subtract(policyDeduction);

            // Apply processing fee
            BigDecimal refundAmount = refundBeforeFees
                    .multiply(BigDecimal.valueOf(100 - policy.getProcessingFeePercentage().doubleValue()))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            // Ensure minimum cancellation fee
            if (refundAmount.compareTo(policy.getMinimumCancellationFee()) < 0) {
                refundAmount = policy.getMinimumCancellationFee();
                log.info("Applying minimum cancellation fee: {}", policy.getMinimumCancellationFee());
            }

            log.info("Partial refund applicable: {} hours until event, {}% refund rate",
                    hoursUntilEvent, refundPercentage);

            builder.refundType("PARTIAL_REFUND")
                    .refundPercentage(refundPercentage)
                    .refundAmount(refundAmount)
                    .policyDeduction(policyDeduction)
                    .processingFeeDeducted(refundBeforeFees.subtract(refundAmount))
                    .reason("Partial refund window applicable");
        }
        // Outside refund windows
        else {
            BigDecimal refundAmount = policy.getAllowNonRefundable() ? BigDecimal.ZERO : originalAmount;
            String reason = policy.getAllowNonRefundable() ? 
                    "Outside cancellation window; non-refundable" : 
                    "Outside cancellation window; full refund due to policy";

            log.info("Outside refund window: {} hours until event, minimum: {}, {}",
                    hoursUntilEvent, policy.getPartialRefundWindowHours(), reason);

            builder.refundType(policy.getAllowNonRefundable() ? "NO_REFUND" : "FULL_REFUND")
                    .refundPercentage(policy.getAllowNonRefundable() ? BigDecimal.ZERO : BigDecimal.valueOf(100))
                    .refundAmount(refundAmount)
                    .reason(reason);
        }

        RefundCalculation calculation = builder.build();
        log.info("Refund calculation complete - Type: {}, Amount: {}", calculation.getRefundType(), calculation.getRefundAmount());
        return calculation;
    }

    /**
     * Builder pattern DTO for refund calculation details.
     */
    public static class RefundCalculation {
        private final CancellationPolicy policy;
        private final BigDecimal originalAmount;
        private final Instant eventStartTime;
        private final Instant cancellationTime;
        private final long hoursUntilEvent;
        private final String refundType; // FULL_REFUND, PARTIAL_REFUND, NO_REFUND
        private final BigDecimal refundPercentage;
        private final BigDecimal refundAmount;
        private final BigDecimal policyDeduction; // Amount deducted based on cancellation window
        private final BigDecimal processingFeeDeducted;
        private final String reason;

        private RefundCalculation(RefundCalculationBuilder builder) {
            this.policy = builder.policy;
            this.originalAmount = builder.originalAmount;
            this.eventStartTime = builder.eventStartTime;
            this.cancellationTime = builder.cancellationTime;
            this.hoursUntilEvent = builder.hoursUntilEvent;
            this.refundType = builder.refundType;
            this.refundPercentage = builder.refundPercentage;
            this.refundAmount = builder.refundAmount;
            this.policyDeduction = builder.policyDeduction;
            this.processingFeeDeducted = builder.processingFeeDeducted;
            this.reason = builder.reason;
        }

        public CancellationPolicy getPolicy() { return policy; }
        public BigDecimal getOriginalAmount() { return originalAmount; }
        public Instant getEventStartTime() { return eventStartTime; }
        public Instant getCancellationTime() { return cancellationTime; }
        public long getHoursUntilEvent() { return hoursUntilEvent; }
        public String getRefundType() { return refundType; }
        public BigDecimal getRefundPercentage() { return refundPercentage; }
        public BigDecimal getRefundAmount() { return refundAmount; }
        public BigDecimal getPolicyDeduction() { return policyDeduction; }
        public BigDecimal getProcessingFeeDeducted() { return processingFeeDeducted; }
        public String getReason() { return reason; }

        public static RefundCalculationBuilder builder() {
            return new RefundCalculationBuilder();
        }

        public static class RefundCalculationBuilder {
            private CancellationPolicy policy;
            private BigDecimal originalAmount;
            private Instant eventStartTime;
            private Instant cancellationTime;
            private long hoursUntilEvent;
            private String refundType;
            private BigDecimal refundPercentage;
            private BigDecimal refundAmount;
            private BigDecimal policyDeduction = BigDecimal.ZERO;
            private BigDecimal processingFeeDeducted = BigDecimal.ZERO;
            private String reason;

            public RefundCalculationBuilder policy(CancellationPolicy policy) { this.policy = policy; return this; }
            public RefundCalculationBuilder originalAmount(BigDecimal originalAmount) { this.originalAmount = originalAmount; return this; }
            public RefundCalculationBuilder eventStartTime(Instant eventStartTime) { this.eventStartTime = eventStartTime; return this; }
            public RefundCalculationBuilder cancellationTime(Instant cancellationTime) { this.cancellationTime = cancellationTime; return this; }
            public RefundCalculationBuilder hoursUntilEvent(long hoursUntilEvent) { this.hoursUntilEvent = hoursUntilEvent; return this; }
            public RefundCalculationBuilder refundType(String refundType) { this.refundType = refundType; return this; }
            public RefundCalculationBuilder refundPercentage(BigDecimal refundPercentage) { this.refundPercentage = refundPercentage; return this; }
            public RefundCalculationBuilder refundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; return this; }
            public RefundCalculationBuilder policyDeduction(BigDecimal policyDeduction) { this.policyDeduction = policyDeduction; return this; }
            public RefundCalculationBuilder processingFeeDeducted(BigDecimal processingFeeDeducted) { this.processingFeeDeducted = processingFeeDeducted; return this; }
            public RefundCalculationBuilder reason(String reason) { this.reason = reason; return this; }

            public RefundCalculation build() {
                return new RefundCalculation(this);
            }
        }
    }
}
