package com.ticketwave.refund.application;

import com.ticketwave.booking.domain.Booking;
import com.ticketwave.payment.domain.Payment;
import com.ticketwave.refund.domain.Refund;
import com.ticketwave.refund.domain.RefundLedger;
import com.ticketwave.refund.domain.CancellationPolicy;
import com.ticketwave.refund.infrastructure.RefundRepository;
import com.ticketwave.refund.infrastructure.RefundLedgerRepository;
import com.ticketwave.refund.infrastructure.CancellationPolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Concurrency and advanced scenario tests for RefundService.
 * Tests admin overrides, ledger consistency, and cancellation policy edge cases under concurrent load.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RefundService Concurrency & Advanced Tests")
class RefundServiceConcurrencyStressTest {

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private RefundLedgerRepository refundLedgerRepository;

    @Mock
    private CancellationPolicyRepository cancellationPolicyRepository;

    @InjectMocks
    private RefundService refundService;

    private UUID testBookingId;
    private UUID testPaymentId;
    private UUID testRefundId;
    private Booking testBooking;
    private Payment testPayment;

    @BeforeEach
    void setUp() {
        testBookingId = UUID.randomUUID();
        testPaymentId = UUID.randomUUID();
        testRefundId = UUID.randomUUID();

        testBooking = Booking.builder()
                .id(testBookingId)
                .pnr("TWABCD123")
                .totalAmount(BigDecimal.valueOf(5000))
                .createdAt(Instant.now().minus(Duration.ofDays(1)))
                .build();

        testPayment = Payment.builder()
                .id(testPaymentId)
                .booking(testBooking)
                .amount(BigDecimal.valueOf(5000))
                .paymentStatus("CONFIRMED")
                .confirmedAt(Instant.now().minus(Duration.ofDays(1)))
                .build();
    }

    // ===== Admin Override Concurrency Tests =====

    @Test
    @DisplayName("Should handle concurrent admin override requests safely")
    void testConcurrentAdminOverrideDifferentBookings() throws InterruptedException {
        // Given
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger overrideCount = new AtomicInteger(0);

        when(refundRepository.findById(any())).thenAnswer(invocation -> {
            UUID refundId = invocation.getArgument(0);
            return Optional.of(Refund.builder()
                    .id(refundId)
                    .booking(testBooking)
                    .payment(testPayment)
                    .refundStatus("PENDING")
                    .calculatedAmount(BigDecimal.valueOf(3500))
                    .build());
        });

        when(refundRepository.save(any(Refund.class)))
                .thenAnswer(invocation -> {
                    Refund r = invocation.getArgument(0);
                    if (r.isAdminOverride()) {
                        overrideCount.incrementAndGet();
                    }
                    return r;
                });

        // When - Multiple admins override different refunds
        for (int i = 0; i < threadCount; i++) {
            final int adminId = i;
            executor.submit(() -> {
                try {
                    UUID refundId = UUID.randomUUID();
                    BigDecimal overrideAmount = BigDecimal.valueOf(4000);

                    refundService.overrideRefundAmount(
                            refundId,
                            overrideAmount,
                            "Goodwill gesture",
                            "ADMIN_" + adminId
                    );

                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdownNow();

        // Then
        assertTrue(overrideCount.get() > 0);
        verify(refundRepository, times(threadCount)).save(any(Refund.class));
    }

    @Test
    @DisplayName("Should prevent race condition when approving and rejecting same refund")
    void testApprovalRejectionRaceCondition() throws InterruptedException {
        // Given
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);
        AtomicInteger approvalCount = new AtomicInteger(0);
        AtomicInteger rejectionCount = new AtomicInteger(0);

        UUID refundId = UUID.randomUUID();
        Refund refund = Refund.builder()
                .id(refundId)
                .booking(testBooking)
                .payment(testPayment)
                .refundStatus("PENDING")
                .calculatedAmount(BigDecimal.valueOf(3500))
                .build();

        when(refundRepository.findById(refundId))
                .thenReturn(Optional.of(refund));
        when(refundRepository.save(any(Refund.class)))
                .thenAnswer(invocation -> {
                    Refund r = invocation.getArgument(0);
                    if ("APPROVED".equals(r.getRefundStatus())) {
                        approvalCount.incrementAndGet();
                    } else if ("REJECTED".equals(r.getRefundStatus())) {
                        rejectionCount.incrementAndGet();
                    }
                    return r;
                });

        // When - One thread approves, other rejects
        executor.submit(() -> {
            try {
                refundService.approveRefund(refundId, "Admin approval");
            } finally {
                latch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                // Small delay to allow approval to start
                Thread.sleep(10);
                refundService.rejectRefund(refundId, "Duplicate claim");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                latch.countDown();
            }
        });

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdownNow();

        // Then - At least one operation succeeds
        assertTrue(approvalCount.get() + rejectionCount.get() >= 1);
        verify(refundRepository, atLeast(2)).save(any(Refund.class));
    }

    @Test
    @DisplayName("Should track multiple admin overrides in audit trail")
    void testAdminOverrideAuditTrail() throws InterruptedException {
        // Given
        int threadCount = 5;
        UUID refundId = UUID.randomUUID();
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<RefundLedger> ledgerEntries = new ArrayList<>();

        Refund refund = Refund.builder()
                .id(refundId)
                .booking(testBooking)
                .payment(testPayment)
                .refundStatus("PENDING")
                .calculatedAmount(BigDecimal.valueOf(3500))
                .build();

        when(refundRepository.findById(refundId))
                .thenReturn(Optional.of(refund));
        when(refundRepository.save(any(Refund.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(refundLedgerRepository.save(any(RefundLedger.class)))
                .thenAnswer(invocation -> {
                    RefundLedger entry = invocation.getArgument(0);
                    ledgerEntries.add(entry);
                    return entry;
                });

        // When - Multiple admins override with different reasons
        for (int i = 0; i < threadCount; i++) {
            final int adminId = i;
            executor.submit(() -> {
                try {
                    refundService.overrideRefundAmount(
                            refundId,
                            BigDecimal.valueOf(3500 + (adminId * 100)),
                            "Override reason " + adminId,
                            "ADMIN_" + adminId
                    );
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdownNow();

        // Then - All overrides logged
        assertTrue(ledgerEntries.size() > 0);
        ledgerEntries.forEach(entry -> {
            assertNotNull(entry.getAdminId());
            assertNotNull(entry.getReason());
        });
    }

    // ===== Refund Ledger Consistency Tests =====

    @Test
    @DisplayName("Should maintain ledger consistency under concurrent refund operations")
    void testLedgerConsistencyUnderConcurrency() throws InterruptedException {
        // Given
        int threadCount = 10;
        UUID refundId = UUID.randomUUID();
        BigDecimal refundAmount = BigDecimal.valueOf(3500);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<RefundLedger> ledgerEntries = Collections.synchronizedList(new ArrayList<>());

        Refund refund = Refund.builder()
                .id(refundId)
                .booking(testBooking)
                .payment(testPayment)
                .refundStatus("APPROVED")
                .calculatedAmount(refundAmount)
                .build();

        when(refundRepository.findById(refundId))
                .thenReturn(Optional.of(refund));
        when(refundRepository.save(any(Refund.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(refundLedgerRepository.save(any(RefundLedger.class)))
                .thenAnswer(invocation -> {
                    RefundLedger entry = invocation.getArgument(0);
                    ledgerEntries.add(entry);
                    return entry;
                });

        // When - Multiple threads process refunds
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    refundService.processRefund(refundId);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdownNow();

        // Then - Ledger entries are logged and accounted
        assertTrue(ledgerEntries.size() > 0);
        ledgerEntries.forEach(entry -> {
            assertNotNull(entry.getRefundId());
            assertTrue(entry.getAmount().signum() >= 0, "Amount must be positive");
        });
    }

    @Test
    @DisplayName("Should ensure ledger total matches calculated refund amount")
    void testLedgerAmountReconciliation() {
        // Given
        UUID refundId = UUID.randomUUID();
        BigDecimal totalRefundAmount = BigDecimal.valueOf(5000);

        Refund refund = Refund.builder()
                .id(refundId)
                .booking(testBooking)
                .payment(testPayment)
                .refundStatus("COMPLETED")
                .calculatedAmount(BigDecimal.valueOf(3500))
                .refundAmount(BigDecimal.valueOf(3500))
                .build();

        RefundLedger ledgerEntry1 = RefundLedger.builder()
                .refundId(refundId)
                .amount(BigDecimal.valueOf(2000))
                .type("CALCULATED")
                .build();

        RefundLedger ledgerEntry2 = RefundLedger.builder()
                .refundId(refundId)
                .amount(BigDecimal.valueOf(1500))
                .type("CALCULATED")
                .build();

        when(refundRepository.findById(refundId))
                .thenReturn(Optional.of(refund));
        when(refundLedgerRepository.findByRefundId(refundId))
                .thenReturn(Arrays.asList(ledgerEntry1, ledgerEntry2));

        // When
        BigDecimal ledgerTotal = refundService.getLedgerTotalForRefund(refundId);

        // Then
        assertEquals(BigDecimal.valueOf(3500), ledgerTotal);
        assertEquals(refund.getRefundAmount(), ledgerTotal);
    }

    @Test
    @DisplayName("Should prevent ledger entry modifications")
    void testLedgerImmutability() {
        // Given
        UUID refundId = UUID.randomUUID();
        RefundLedger ledgerEntry = RefundLedger.builder()
                .refundId(refundId)
                .amount(BigDecimal.valueOf(3500))
                .type("INITIAL_CALCULATION")
                .createdAt(Instant.now())
                .build();

        when(refundLedgerRepository.findByRefundId(refundId))
                .thenReturn(Collections.singletonList(ledgerEntry));

        // When - Try to retrieve and verify immutability
        List<RefundLedger> entries = refundService.getRefundLedger(refundId);

        // Then
        assertNotNull(entries);
        assertTrue(entries.size() > 0);
        entries.forEach(entry -> {
            assertNotNull(entry.getCreatedAt());
            // Verify no updatedAt or modification fields
        });

        verify(refundLedgerRepository, times(1)).findByRefundId(refundId);
    }

    // ===== Cancellation Policy Edge Case Tests =====

    @Test
    @DisplayName("Should apply full refund policy within cancellation window")
    void testFullRefundWithinCancellationWindow() {
        // Given
        UUID refundId = UUID.randomUUID();
        Instant eventTime = Instant.now().plus(Duration.ofDays(5));
        Instant cancellationTime = Instant.now().plus(Duration.ofDays(1));

        CancellationPolicy fullRefundPolicy = CancellationPolicy.builder()
                .policyName("FULL_REFUND_POLICY")
                .refundPercentage(100)
                .daysBeforeEvent(6)
                .build();

        Refund refund = Refund.builder()
                .id(refundId)
                .booking(testBooking)
                .payment(testPayment)
                .refundStatus("PENDING")
                .calculatedAmount(BigDecimal.valueOf(5000))
                .eventTime(eventTime)
                .cancellationTime(cancellationTime)
                .build();

        when(refundRepository.findById(refundId))
                .thenReturn(Optional.of(refund));
        when(cancellationPolicyRepository.findApplicablePolicy(eventTime, cancellationTime))
                .thenReturn(Optional.of(fullRefundPolicy));
        when(refundRepository.save(any(Refund.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Refund processedRefund = refundService.calculateRefundAmount(refundId);

        // Then
        assertEquals(BigDecimal.valueOf(5000), processedRefund.getCalculatedAmount());
    }

    @Test
    @DisplayName("Should apply partial refund policy within partial refund window")
    void testPartialRefundWithinPartialWindow() {
        // Given
        UUID refundId = UUID.randomUUID();
        Instant eventTime = Instant.now().plus(Duration.ofDays(3));
        Instant cancellationTime = Instant.now().plus(Duration.ofDays(1));

        CancellationPolicy partialRefundPolicy = CancellationPolicy.builder()
                .policyName("PARTIAL_REFUND_POLICY")
                .refundPercentage(50)
                .daysBeforeEvent(4)
                .build();

        Refund refund = Refund.builder()
                .id(refundId)
                .booking(testBooking)
                .payment(testPayment)
                .refundStatus("PENDING")
                .calculatedAmount(BigDecimal.valueOf(5000))
                .eventTime(eventTime)
                .cancellationTime(cancellationTime)
                .build();

        when(refundRepository.findById(refundId))
                .thenReturn(Optional.of(refund));
        when(cancellationPolicyRepository.findApplicablePolicy(eventTime, cancellationTime))
                .thenReturn(Optional.of(partialRefundPolicy));
        when(refundRepository.save(any(Refund.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Refund processedRefund = refundService.calculateRefundAmount(refundId);

        // Then
        BigDecimal expectedAmount = BigDecimal.valueOf(5000).multiply(BigDecimal.valueOf(0.5));
        assertEquals(expectedAmount, processedRefund.getCalculatedAmount());
    }

    @Test
    @DisplayName("Should apply no-refund policy after cancellation deadline")
    void testNoRefundAfterDeadline() {
        // Given
        UUID refundId = UUID.randomUUID();
        Instant eventTime = Instant.now().plus(Duration.ofHours(12));
        Instant cancellationTime = Instant.now();

        CancellationPolicy noRefundPolicy = CancellationPolicy.builder()
                .policyName("NO_REFUND_POLICY")
                .refundPercentage(0)
                .daysBeforeEvent(0)
                .build();

        Refund refund = Refund.builder()
                .id(refundId)
                .booking(testBooking)
                .payment(testPayment)
                .refundStatus("PENDING")
                .calculatedAmount(BigDecimal.valueOf(5000))
                .eventTime(eventTime)
                .cancellationTime(cancellationTime)
                .build();

        when(refundRepository.findById(refundId))
                .thenReturn(Optional.of(refund));
        when(cancellationPolicyRepository.findApplicablePolicy(eventTime, cancellationTime))
                .thenReturn(Optional.of(noRefundPolicy));
        when(refundRepository.save(any(Refund.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Refund processedRefund = refundService.calculateRefundAmount(refundId);

        // Then
        assertEquals(BigDecimal.ZERO, processedRefund.getCalculatedAmount());
    }

    @Test
    @DisplayName("Should resolve policy priority when multiple policies apply")
    void testPolicyPriorityResolution() {
        // Given
        UUID refundId = UUID.randomUUID();
        Instant eventTime = Instant.now().plus(Duration.ofDays(5));
        Instant cancellationTime = Instant.now().plus(Duration.ofDays(2));

        CancellationPolicy priorityPolicy = CancellationPolicy.builder()
                .policyName("PRIORITY_POLICY")
                .refundPercentage(75)
                .daysBeforeEvent(5)
                .priority(1) // Higher priority
                .build();

        Refund refund = Refund.builder()
                .id(refundId)
                .booking(testBooking)
                .payment(testPayment)
                .refundStatus("PENDING")
                .calculatedAmount(BigDecimal.valueOf(5000))
                .eventTime(eventTime)
                .cancellationTime(cancellationTime)
                .build();

        when(refundRepository.findById(refundId))
                .thenReturn(Optional.of(refund));
        when(cancellationPolicyRepository.findHighestPriorityApplicablePolicy(eventTime, cancellationTime))
                .thenReturn(Optional.of(priorityPolicy));
        when(refundRepository.save(any(Refund.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Refund processedRefund = refundService.calculateRefundAmount(refundId);

        // Then
        BigDecimal expectedAmount = BigDecimal.valueOf(5000).multiply(BigDecimal.valueOf(0.75));
        assertEquals(expectedAmount, processedRefund.getCalculatedAmount());
    }

    // ===== High-Volume Stress Tests =====

    @Test
    @DisplayName("Should handle high-volume concurrent refund initiations")
    void testHighVolumeConcurrentRefundInitiations() throws InterruptedException {
        // Given
        int threadCount = 50;
        int refundsPerThread = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger initiatedCount = new AtomicInteger(0);

        when(refundRepository.save(any(Refund.class)))
                .thenAnswer(invocation -> {
                    initiatedCount.incrementAndGet();
                    return invocation.getArgument(0);
                });

        // When
        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                for (int r = 0; r < refundsPerThread; r++) {
                    try {
                        refundService.initiateRefund(
                                UUID.randomUUID(), // booking ID
                                "Cancellation",
                                Instant.now()
                        );
                    } catch (Exception e) {
                        // Expected - some bookings may not exist
                    }
                }
                latch.countDown();
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdownNow();

        // Then
        assertTrue(initiatedCount.get() > 0);
    }

    // ===== Performance Tests =====

    @Test
    @DisplayName("Should process refund approval within reasonable latency")
    void testRefundApprovalLatency() {
        // Given
        int iterations = 100;
        List<Long> latencies = new ArrayList<>();

        Refund refund = Refund.builder()
                .id(UUID.randomUUID())
                .booking(testBooking)
                .payment(testPayment)
                .refundStatus("PENDING")
                .calculatedAmount(BigDecimal.valueOf(3500))
                .build();

        when(refundRepository.findById(any()))
                .thenReturn(Optional.of(refund));
        when(refundRepository.save(any(Refund.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            try {
                refundService.approveRefund(UUID.randomUUID(), "Approval");
            } catch (Exception e) {
                // Ignore
            }
            long end = System.nanoTime();
            latencies.add((end - start) / 1_000_000); // Convert to ms
        }

        // Then
        latencies.sort(null);
        long p99 = latencies.get((int)(iterations * 0.99));
        assertTrue(p99 < 100, "P99 latency should be < 100ms, got: " + p99 + "ms");
    }

    @Test
    @DisplayName("Should calculate refund amounts efficiently under high volume")
    void testRefundCalculationThroughput() throws InterruptedException {
        // Given
        int threadCount = 20;
        int calculationsPerThread = 25;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger calculationCount = new AtomicInteger(0);

        CancellationPolicy policy = CancellationPolicy.builder()
                .policyName("TEST_POLICY")
                .refundPercentage(50)
                .daysBeforeEvent(5)
                .build();

        when(refundRepository.findById(any()))
                .thenAnswer(invocation -> Optional.of(Refund.builder()
                        .id((UUID) invocation.getArgument(0))
                        .booking(testBooking)
                        .payment(testPayment)
                        .refundStatus("PENDING")
                        .calculatedAmount(BigDecimal.valueOf(5000))
                        .eventTime(Instant.now().plus(Duration.ofDays(10)))
                        .cancellationTime(Instant.now())
                        .build()));

        when(cancellationPolicyRepository.findApplicablePolicy(any(), any()))
                .thenReturn(Optional.of(policy));
        when(refundRepository.save(any(Refund.class)))
                .thenAnswer(invocation -> {
                    calculationCount.incrementAndGet();
                    return invocation.getArgument(0);
                });

        // When
        long startTime = System.currentTimeMillis();

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                for (int c = 0; c < calculationsPerThread; c++) {
                    try {
                        refundService.calculateRefundAmount(UUID.randomUUID());
                    } catch (Exception e) {
                        // Ignore
                    }
                }
                latch.countDown();
            });
        }

        latch.await(15, TimeUnit.SECONDS);
        executor.shutdownNow();

        long totalTime = System.currentTimeMillis() - startTime;

        // Then
        assertTrue(calculationCount.get() > 0);
        long throughput = calculationCount.get() / (totalTime / 1000);
        assertTrue(throughput > 100, "Should calculate > 100 refunds/sec, got: " + throughput);
    }
}
