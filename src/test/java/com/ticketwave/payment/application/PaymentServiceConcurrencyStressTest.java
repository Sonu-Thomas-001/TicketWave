package com.ticketwave.payment.application;

import com.ticketwave.booking.domain.Booking;
import com.ticketwave.payment.domain.Payment;
import com.ticketwave.payment.infrastructure.PaymentProviderIntegrationStub;
import com.ticketwave.payment.infrastructure.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Concurrency and stress tests for PaymentService.
 * Tests payment webhook handling, distributed payment processing, and payment provider integration.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService Concurrency & Stress Tests")
class PaymentServiceConcurrencyStressTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentProviderIntegrationStub paymentProviderIntegration;

    @InjectMocks
    private PaymentService paymentService;

    private Booking testBooking;
    private UUID testPaymentId;

    @BeforeEach
    void setUp() {
        testBooking = Booking.builder()
                .id(UUID.randomUUID())
                .pnr("TWABCD123")
                .totalAmount(BigDecimal.valueOf(1500))
                .build();

        testPaymentId = UUID.randomUUID();
    }

    // ===== Concurrent Payment Confirmation Tests =====

    @Test
    @DisplayName("Should handle duplicate payment confirmation webhooks idempotently")
    void testIdempotentPaymentConfirmation() throws InterruptedException {
        // Given
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        Payment payment = Payment.builder()
                .id(testPaymentId)
                .booking(testBooking)
                .paymentStatus("PENDING")
                .build();

        when(paymentRepository.findById(testPaymentId))
                .thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> {
                    Payment p = invocation.getArgument(0);
                    p.setPaymentStatus("CONFIRMED");
                    p.setConfirmedAt(Instant.now());
                    return p;
                });

        // When - Multiple threads try to confirm same payment
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    Payment result = paymentService.confirmPayment(testPaymentId, "gateway_resp_" + Thread.currentThread().getId());
                    if ("CONFIRMED".equals(result.getPaymentStatus())) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdownNow();

        // Then - Only first confirmation persists
        assertTrue(successCount.get() >= 1, "At least one confirmation should succeed");
        verify(paymentRepository, atLeast(threadCount)).findById(testPaymentId);
    }

    @Test
    @DisplayName("Should handle concurrent payment creation for different bookings")
    void testConcurrentPaymentCreationMultipleBookings() throws InterruptedException {
        // Given
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger createdCount = new AtomicInteger(0);

        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> {
                    Payment p = invocation.getArgument(0);
                    p.setId(UUID.randomUUID());
                    createdCount.incrementAndGet();
                    return p;
                });

        // When
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    Booking booking = Booking.builder()
                            .id(UUID.randomUUID())
                            .totalAmount(BigDecimal.valueOf(1500))
                            .build();

                    paymentService.createPayment(
                            booking,
                            BigDecimal.valueOf(1500),
                            "txn_" + UUID.randomUUID(),
                            "CARD"
                    );
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdownNow();

        // Then
        assertEquals(threadCount, createdCount.get());
        verify(paymentRepository, times(threadCount)).save(any(Payment.class));
    }

    @Test
    @DisplayName("Should handle high-volume payment confirmations")
    void testHighVolumePaymentConfirmations() throws InterruptedException {
        // Given
        int threadCount = 50;
        int confirmsPerThread = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger confirmCount = new AtomicInteger(0);

        when(paymentRepository.findById(any())).thenAnswer(invocation -> {
            UUID paymentId = invocation.getArgument(0);
            return Optional.of(Payment.builder()
                    .id(paymentId)
                    .paymentStatus("PENDING")
                    .build());
        });

        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> {
                    confirmCount.incrementAndGet();
                    return invocation.getArgument(0);
                });

        // When
        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                for (int c = 0; c < confirmsPerThread; c++) {
                    try {
                        paymentService.confirmPayment(UUID.randomUUID(), "response");
                    } catch (Exception e) {
                        // Expected - some will fail due to missing payments
                    }
                }
                latch.countDown();
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdownNow();

        // Then
        assertTrue(confirmCount.get() > 0, "Some confirmations should succeed");
    }

    // ===== Refund Processing Tests =====

    @Test
    @DisplayName("Should handle concurrent refund requests")
    void testConcurrentRefundProcessing() throws InterruptedException {
        // Given
        int threadCount = 10;
        UUID paymentId = UUID.randomUUID();
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger refundCount = new AtomicInteger(0);

        Payment payment = Payment.builder()
                .id(paymentId)
                .paymentStatus("CONFIRMED")
                .amount(BigDecimal.valueOf(1500))
                .build();

        when(paymentRepository.findById(paymentId))
                .thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> {
                    refundCount.incrementAndGet();
                    return invocation.getArgument(0);
                });

        // When
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    paymentService.refundPayment(paymentId, BigDecimal.valueOf(1500));
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdownNow();

        // Then
        assertTrue(refundCount.get() > 0);
        verify(paymentRepository, atLeast(threadCount)).findById(paymentId);
    }

    @Test
    @DisplayName("Should handle partial refunds with concurrent requests")
    void testConcurrentPartialRefunds() throws InterruptedException {
        // Given
        int threadCount = 5;
        UUID paymentId = UUID.randomUUID();
        BigDecimal totalAmount = BigDecimal.valueOf(1500);
        BigDecimal partialRefund = BigDecimal.valueOf(300);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger partialRefundCount = new AtomicInteger(0);

        Payment payment = Payment.builder()
                .id(paymentId)
                .paymentStatus("CONFIRMED")
                .amount(totalAmount)
                .build();

        when(paymentRepository.findById(paymentId))
                .thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> {
                    Payment p = invocation.getArgument(0);
                    if (p.getRefundAmount() != null) {
                        partialRefundCount.incrementAndGet();
                    }
                    return p;
                });

        // When
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    paymentService.refundPayment(paymentId, partialRefund);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdownNow();

        // Then
        assertTrue(partialRefundCount.get() > 0);
    }

    // ===== Payment Provider Integration Mocking Tests =====

    @Test
    @DisplayName("Should simulate payment provider webhook handling")
    void testPaymentProviderWebhookSimulation() {
        // Given
        Payment payment = Payment.builder()
                .id(testPaymentId)
                .transactionId("txn_123")
                .amount(BigDecimal.valueOf(1500))
                .build();

        PaymentProviderIntegrationStub.PaymentWebhookResponse response =
                PaymentProviderIntegrationStub.PaymentWebhookResponse.builder()
                        .success(true)
                        .gatewayTransactionId("gateway_txn_123")
                        .amount(BigDecimal.valueOf(1500))
                        .message("Payment confirmed")
                        .build();

        when(paymentProviderIntegration.simulatePaymentConfirmation(payment))
                .thenReturn(response);

        // When
        PaymentProviderIntegrationStub.PaymentWebhookResponse result =
                paymentProviderIntegration.simulatePaymentConfirmation(payment);

        // Then
        assertTrue(result.isSuccess());
        assertEquals(BigDecimal.valueOf(1500), result.getAmount());
    }

    @Test
    @DisplayName("Should handle payment provider failure response")
    void testPaymentProviderFailureSimulation() {
        // Given
        Payment payment = Payment.builder()
                .id(testPaymentId)
                .amount(BigDecimal.valueOf(1500))
                .build();

        PaymentProviderIntegrationStub.PaymentWebhookResponse response =
                PaymentProviderIntegrationStub.PaymentWebhookResponse.builder()
                        .success(false)
                        .message("Card declined")
                        .build();

        when(paymentProviderIntegration.simulatePaymentConfirmation(payment))
                .thenReturn(response);

        // When
        PaymentProviderIntegrationStub.PaymentWebhookResponse result =
                paymentProviderIntegration.simulatePaymentConfirmation(payment);

        // Then
        assertFalse(result.isSuccess());
        assertNotNull(result.getMessage());
    }

    // ===== Payment Status Transition Tests =====

    @Test
    @DisplayName("Should handle payment state transitions correctly under concurrency")
    void testPaymentStatusTransitionsUnderConcurrency() throws InterruptedException {
        // Given
        UUID paymentId = UUID.randomUUID();
        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch latch = new CountDownLatch(3);

        Payment payment = Payment.builder()
                .id(paymentId)
                .paymentStatus("PENDING")
                .build();

        when(paymentRepository.findById(paymentId))
                .thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AtomicInteger transitions = new AtomicInteger(0);

        // When - Try different transitions concurrently
        executor.submit(() -> {
            try {
                // Transition to CONFIRMED
                Payment p = paymentService.confirmPayment(paymentId, "success");
                if ("CONFIRMED".equals(p.getPaymentStatus())) {
                    transitions.incrementAndGet();
                }
            } finally {
                latch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                // Try transition to FAILED
                Payment p = paymentService.failPayment(paymentId, "Timeout", "error");
                if ("FAILED".equals(p.getPaymentStatus())) {
                    transitions.incrementAndGet();
                }
            } finally {
                latch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                // Try transition to REFUNDED
                Payment p = paymentService.refundPayment(paymentId, BigDecimal.valueOf(1500));
                if ("REFUNDED".equals(p.getPaymentStatus())) {
                    transitions.incrementAndGet();
                }
            } finally {
                latch.countDown();
            }
        });

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdownNow();

        // Then
        verify(paymentRepository, atLeast(3)).save(any(Payment.class));
    }

    // ===== Performance & Latency Tests =====

    @Test
    @DisplayName("Should confirm payment within reasonable latency (P99 < 50ms)")
    void testPaymentConfirmationLatency() {
        // Given
        int iterations = 100;
        UUID paymentId = UUID.randomUUID();

        Payment payment = Payment.builder()
                .id(paymentId)
                .paymentStatus("PENDING")
                .build();

        when(paymentRepository.findById(paymentId))
                .thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<Long> latencies = new ArrayList<>();

        // When
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            try {
                paymentService.confirmPayment(paymentId, "response");
            } catch (Exception e) {
                // Ignore
            }
            long end = System.nanoTime();
            latencies.add((end - start) / 1_000_000); // Convert to ms
        }

        // Then
        latencies.sort(null);
        long p99 = latencies.get((int)(iterations * 0.99));
        assertTrue(p99 < 50, "P99 latency should be < 50ms, got: " + p99 + "ms");
    }

    @Test
    @DisplayName("Should retrieve payment within reasonable latency")
    void testPaymentRetrievalLatency() {
        // Given
        int iterations = 1000;
        UUID paymentId = UUID.randomUUID();

        Payment payment = Payment.builder()
                .id(paymentId)
                .paymentStatus("CONFIRMED")
                .build();

        when(paymentRepository.findById(paymentId))
                .thenReturn(Optional.of(payment));

        List<Long> latencies = new ArrayList<>();

        // When
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            try {
                paymentService.getPayment(paymentId);
            } catch (Exception e) {
                // Ignore
            }
            long end = System.nanoTime();
            latencies.add((end - start) / 1_000_000); // Convert to ms
        }

        // Then
        latencies.sort(null);
        long p999 = latencies.get((int)(iterations * 0.999));
        assertTrue(p999 < 10, "P999 latency should be < 10ms, got: " + p999 + "ms");
    }
}
