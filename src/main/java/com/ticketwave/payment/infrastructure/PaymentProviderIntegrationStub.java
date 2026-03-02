package com.ticketwave.payment.infrastructure;

import com.ticketwave.payment.domain.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * PaymentProviderIntegrationStub provides a mock implementation of payment provider integration.
 * Used for testing and development without hitting actual payment gateways.
 * 
 * In production, this would be replaced with actual provider integrations (Stripe, Razorpay, etc.)
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "stub", matchIfMissing = true)
@RequiredArgsConstructor
public class PaymentProviderIntegrationStub {

    /**
     * Simulate payment confirmation webhook.
     * In real implementation, payment gateway would call this endpoint.
     */
    public PaymentWebhookResponse simulatePaymentConfirmation(Payment payment) {
        log.info("STUB: Simulating payment confirmation for transaction: {}", payment.getTransactionId());
        
        // Simulate random success/failure for testing
        boolean shouldFail = Math.random() < 0.05; // 5% failure rate for testing

        if (shouldFail) {
            log.warn("STUB: Simulated payment failure for: {}", payment.getTransactionId());
            return PaymentWebhookResponse.builder()
                    .success(false)
                    .gatewayTransactionId(generateGatewayId())
                    .message("Simulated payment failure")
                    .build();
        }

        log.info("STUB: Simulated payment success for: {}", payment.getTransactionId());
        return PaymentWebhookResponse.builder()
                .success(true)
                .gatewayTransactionId(generateGatewayId())
                .amount(payment.getAmount())
                .message("Simulated payment confirmed")
                .build();
    }

    /**
     * Simulate refund processing with payment gateway.
     * In real implementation, would call gateway refund API.
     */
    public RefundWebhookResponse simulateRefundProcessing(UUID refundId, BigDecimal amount) {
        log.info("STUB: Simulating refund processing - Refund: {}, Amount: {}", refundId, amount);

        // Simulate random failure for testing (2% failure rate)
        boolean shouldFail = Math.random() < 0.02;

        if (shouldFail) {
            log.warn("STUB: Simulated refund failure for: {}", refundId);
            return RefundWebhookResponse.builder()
                    .success(false)
                    .gatewayRefundId(generateGatewayId())
                    .message("Simulated refund failure")
                    .build();
        }

        log.info("STUB: Simulated refund success for: {}", refundId);
        return RefundWebhookResponse.builder()
                .success(true)
                .gatewayRefundId(generateGatewayId())
                .amount(amount)
                .message("Simulated refund completed")
                .build();
    }

    /**
     * Validate webhook signature (stub: always returns true).
     */
    public boolean validateWebhookSignature(String payload, String signature, String timestamp) {
        log.debug("STUB: Validating webhook signature (always returns true in stub)");
        return true;
    }

    /**
     * Generate a fake gateway transaction ID.
     */
    private String generateGatewayId() {
        return "GW-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }

    /**
     * Response DTO for payment webhook simulation.
     */
    public static class PaymentWebhookResponse {
        public boolean success;
        public String gatewayTransactionId;
        public BigDecimal amount;
        public String message;

        private PaymentWebhookResponse(PaymentWebhookResponseBuilder builder) {
            this.success = builder.success;
            this.gatewayTransactionId = builder.gatewayTransactionId;
            this.amount = builder.amount;
            this.message = builder.message;
        }

        public boolean isSuccess() { return success; }
        public String getGatewayTransactionId() { return gatewayTransactionId; }
        public BigDecimal getAmount() { return amount; }
        public String getMessage() { return message; }

        public static PaymentWebhookResponseBuilder builder() {
            return new PaymentWebhookResponseBuilder();
        }

        public static class PaymentWebhookResponseBuilder {
            public boolean success;
            public String gatewayTransactionId;
            public BigDecimal amount;
            public String message;

            public PaymentWebhookResponseBuilder success(boolean success) { this.success = success; return this; }
            public PaymentWebhookResponseBuilder gatewayTransactionId(String id) { this.gatewayTransactionId = id; return this; }
            public PaymentWebhookResponseBuilder amount(BigDecimal amount) { this.amount = amount; return this; }
            public PaymentWebhookResponseBuilder message(String message) { this.message = message; return this; }

            public PaymentWebhookResponse build() {
                return new PaymentWebhookResponse(this);
            }
        }
    }

    /**
     * Response DTO for refund webhook simulation.
     */
    public static class RefundWebhookResponse {
        public boolean success;
        public String gatewayRefundId;
        public BigDecimal amount;
        public String message;

        private RefundWebhookResponse(RefundWebhookResponseBuilder builder) {
            this.success = builder.success;
            this.gatewayRefundId = builder.gatewayRefundId;
            this.amount = builder.amount;
            this.message = builder.message;
        }

        public boolean isSuccess() { return success; }
        public String getGatewayRefundId() { return gatewayRefundId; }
        public BigDecimal getAmount() { return amount; }
        public String getMessage() { return message; }

        public static RefundWebhookResponseBuilder builder() {
            return new RefundWebhookResponseBuilder();
        }

        public static class RefundWebhookResponseBuilder {
            public boolean success;
            public String gatewayRefundId;
            public BigDecimal amount;
            public String message;

            public RefundWebhookResponseBuilder success(boolean success) { this.success = success; return this; }
            public RefundWebhookResponseBuilder gatewayRefundId(String id) { this.gatewayRefundId = id; return this; }
            public RefundWebhookResponseBuilder amount(BigDecimal amount) { this.amount = amount; return this; }
            public RefundWebhookResponseBuilder message(String message) { this.message = message; return this; }

            public RefundWebhookResponse build() {
                return new RefundWebhookResponse(this);
            }
        }
    }
}
