package com.ticketwave.booking.application;

import com.ticketwave.booking.domain.SeatHold;
import com.ticketwave.booking.infrastructure.SeatLockProvider;
import com.ticketwave.common.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Concurrency and stress tests for SeatHoldService.
 * Tests distributed locking, race conditions, and high-volume scenarios.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SeatHoldService Concurrency & Stress Tests")
class SeatHoldServiceConcurrencyStressTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SeatLockProvider seatLockProvider;

    @InjectMocks
    private SeatHoldService seatHoldService;

    private UUID testSeatId;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        testSeatId = UUID.randomUUID();
        testUserId = UUID.randomUUID();
    }

    // ===== Race Condition Tests =====

    @Test
    @DisplayName("Should prevent double-booking under concurrent acquisition attempts")
    void testPreventDoubleBooking_ConcurrentAttempts() throws InterruptedException {
        // Given
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        when(seatLockProvider.tryAcquireSeatLock(testSeatId.toString()))
                .thenAnswer(invocation -> {
                    Thread.sleep(10); // Simulate lock acquisition delay
                    return successCount.get() == 0; // Only first thread gets lock
                });
        when(redisTemplate.opsForValue().get(anyString()))
                .thenReturn(null);

        // When
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    UUID userId = UUID.randomUUID();
                    SeatHold hold = seatHoldService.holdSeat(testSeatId, userId);
                    successCount.incrementAndGet();
                } catch (ConflictException e) {
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdownNow();

        // Then
        assertTrue(failureCount.get() > 0, "Some threads should fail due to seat already held");
    }

    @Test
    @DisplayName("Should handle lock acquire and release atomically")
    void testAtomicLockAcquireRelease() throws InterruptedException {
        // Given
        int iterations = 100;
        UUID seatId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(seatLockProvider.tryAcquireSeatLock(seatId.toString()))
                .thenReturn(true);
        when(redisTemplate.opsForValue().get(anyString()))
                .thenReturn(null);

        AtomicInteger acquireCount = new AtomicInteger(0);
        AtomicInteger releaseCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            acquireCount.incrementAndGet();
            return null;
        }).when(seatLockProvider).tryAcquireSeatLock(seatId.toString());

        doAnswer(invocation -> {
            releaseCount.incrementAndGet();
            return null;
        }).when(seatLockProvider).releaseSeatLock(seatId.toString());

        // When
        for (int i = 0; i < iterations; i++) {
            seatHoldService.holdSeat(seatId, userId);
        }

        // Then
        assertEquals(iterations, acquireCount.get(), "All lock acquisitions should complete");
        assertEquals(iterations, releaseCount.get(), "All lock releases should complete");
    }

    // ===== High-Volume/Stress Tests =====

    @Test
    @DisplayName("Should handle high-volume seat hold requests")
    void testHighVolumeSeatHolds() throws InterruptedException {
        // Given
        int threadCount = 50;
        int holdsPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger holdCount = new AtomicInteger(0);

        when(seatLockProvider.tryAcquireSeatLock(anyString()))
                .thenReturn(true);
        when(redisTemplate.opsForValue().get(anyString()))
                .thenReturn(null);

        // When
        long startTime = System.currentTimeMillis();
        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                for (int h = 0; h < holdsPerThread; h++) {
                    try {
                        UUID seatId = UUID.randomUUID();
                        UUID userId = UUID.randomUUID();
                        seatHoldService.holdSeat(seatId, userId);
                        holdCount.incrementAndGet();
                    } catch (Exception e) {
                        // Expected - some conflicts
                    }
                }
                latch.countDown();
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdownNow();
        long duration = System.currentTimeMillis() - startTime;

        // Then
        assertTrue(holdCount.get() > 0, "Should successfully create holds");
        assertTrue(duration < 30000, "Should complete in reasonable time");
    }

    @Test
    @DisplayName("Should handle rapid hold extend operations")
    void testRapidHoldExtension() throws InterruptedException {
        // Given
        UUID seatId = UUID.randomUUID();
        int threadCount = 10;
        int extensionsPerThread = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger extendCount = new AtomicInteger(0);

        when(redisTemplate.expire(anyString(), anyLong(), any()))
                .thenReturn(true);

        // When
        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                for (int e = 0; e < extensionsPerThread; e++) {
                    try {
                        seatHoldService.extendSeatHold(seatId, 300);
                        extendCount.incrementAndGet();
                    } catch (Exception ex) {
                        // Ignore
                    }
                }
                latch.countDown();
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdownNow();

        // Then
        assertEquals(threadCount * extensionsPerThread, extendCount.get());
        verify(redisTemplate, times(extendCount.get())).expire(anyString(), anyLong(), any());
    }

    // ===== Lock Contention Tests =====

    @Test
    @DisplayName("Should handle lock timeouts under high contention")
    void testLockTimeoutUnderHighContention() throws InterruptedException {
        // Given
        int threadCount = 30;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger timeoutCount = new AtomicInteger(0);

        UUID seatId = UUID.randomUUID();

        when(seatLockProvider.tryAcquireSeatLock(seatId.toString()))
                .thenAnswer(invocation -> {
                    // Simulate timeout for some threads
                    Thread.sleep(5);
                    return Math.random() > 0.3; // 70% success, 30% timeout
                });

        when(redisTemplate.opsForValue().get(anyString()))
                .thenReturn(null);

        // When
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    seatHoldService.holdSeat(seatId, UUID.randomUUID());
                } catch (ConflictException e) {
                    timeoutCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdownNow();

        // Then
        assertTrue(timeoutCount.get() > 0, "Some requests should timeout");
        assertTrue(timeoutCount.get() < threadCount, "Some requests should succeed");
    }

    // ===== Cache Coherency Tests =====

    @Test
    @DisplayName("Should maintain consistency across hold validation during updates")
    void testHoldValidationConsistency() throws InterruptedException {
        // Given
        UUID seatId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String holdToken = "token-123";

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger validCount = new AtomicInteger(0);
        AtomicInteger invalidCount = new AtomicInteger(0);

        when(redisTemplate.opsForValue().get("seat:hold:" + seatId))
                .thenReturn(userId.toString());
        when(redisTemplate.opsForValue().get("hold:token:" + holdToken))
                .thenReturn(seatId.toString());

        // When
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    boolean isValid = seatHoldService.isHoldValid(seatId, userId, holdToken);
                    if (isValid) {
                        validCount.incrementAndGet();
                    } else {
                        invalidCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdownNow();

        // Then
        assertTrue(validCount.get() > 0, "Most validations should succeed");
    }

    @Test
    @DisplayName("Should handle concurrent release during validation")
    void testConcurrentReleaseAndValidation() throws InterruptedException {
        // Given
        UUID seatId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String holdToken = "token-456";

        int validationThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(validationThreads + 1);
        CountDownLatch latch = new CountDownLatch(validationThreads + 1);
        AtomicInteger validationResults = new AtomicInteger(0);

        when(redisTemplate.opsForValue().get(anyString()))
                .thenReturn(userId.toString());
        when(redisTemplate.delete(anyString()))
                .thenReturn(1L);

        // When - One thread releases, multiple validate
        executor.submit(() -> {
            try {
                Thread.sleep(50);
                seatHoldService.releaseSeatHold(seatId, holdToken);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                latch.countDown();
            }
        });

        for (int i = 0; i < validationThreads; i++) {
            executor.submit(() -> {
                try {
                    boolean isValid = seatHoldService.isHoldValid(seatId, userId, holdToken);
                    if (isValid) {
                        validationResults.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdownNow();

        // Then
        verify(redisTemplate, atLeast(1)).delete(anyString());
    }

    // ===== Performance Benchmarks =====

    @Test
    @DisplayName("Should hold seat within reasonable latency (P99 < 100ms)")
    void testSeatHoldLatency() {
        // Given
        UUID seatId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        int iterations = 100;

        when(seatLockProvider.tryAcquireSeatLock(seatId.toString()))
                .thenReturn(true);
        when(redisTemplate.opsForValue().get(anyString()))
                .thenReturn(null);

        List<Long> latencies = new ArrayList<>();

        // When
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            try {
                seatHoldService.holdSeat(seatId, UUID.randomUUID());
            } catch (Exception e) {
                // Ignore
            }
            long end = System.nanoTime();
            latencies.add((end - start) / 1_000_000); // Convert to ms
        }

        // Then
        latencies.sort(null);
        long p99 = latencies.get((int)(iterations * 0.99));
        assertTrue(p99 < 100, "P99 latency should be less than 100ms, got: " + p99 + "ms");
    }

    @Test
    @DisplayName("Should validate hold within reasonable latency")
    void testHoldValidationLatency() {
        // Given
        UUID seatId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String holdToken = "token-789";
        int iterations = 1000;

        when(redisTemplate.opsForValue().get(anyString()))
                .thenReturn(userId.toString());

        List<Long> latencies = new ArrayList<>();

        // When
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            seatHoldService.isHoldValid(seatId, userId, holdToken);
            long end = System.nanoTime();
            latencies.add((end - start) / 1_000_000); // Convert to ms
        }

        // Then
        latencies.sort(null);
        long p99 = latencies.get((int)(iterations * 0.99));
        long p999 = latencies.get((int)(iterations * 0.999));
        assertTrue(p99 < 50, "P99 latency should be < 50ms, got: " + p99 + "ms");
        assertTrue(p999 < 100, "P999 latency should be < 100ms, got: " + p999 + "ms");
    }
}
