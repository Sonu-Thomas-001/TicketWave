package com.ticketwave.booking.application;

import com.ticketwave.booking.domain.SeatHold;
import com.ticketwave.booking.infrastructure.SeatLockProvider;
import com.ticketwave.common.exception.ConflictException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Slf4j
@ExtendWith(MockitoExtension.class)
@DisplayName("SeatHoldService Concurrency Tests")
class SeatHoldServiceConcurrencyTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SeatLockProvider seatLockProvider;

    private SeatHoldService seatHoldService;

    private UUID testSeatId;

    @BeforeEach
    void setUp() {
        seatHoldService = new SeatHoldService(redisTemplate, seatLockProvider);
        testSeatId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Should prevent double booking under concurrent lock contention")
    void testConcurrentSeatHolds_OnlyOneSucceeds() {
        // Simulate lock behavior: first acquirer succeeds, rest get timeout
        AtomicInteger lockAttempts = new AtomicInteger(0);
        
        when(seatLockProvider.tryAcquireSeatLock(testSeatId.toString()))
                .thenAnswer(invocation -> {
                    int attempt = lockAttempts.incrementAndGet();
                    return attempt == 1; // Only first thread gets lock
                });

        when(redisTemplate.opsForValue().get(anyString())).thenReturn(null);

        // Create multiple concurrent threads attempting to hold same seat
        int threadCount = 5;
        List<Thread> threads = new ArrayList<>();
        List<Exception> exceptions = new CopyOnWriteArrayList<>();
        List<SeatHold> successfulHolds = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            UUID userId = UUID.randomUUID();
            Thread thread = new Thread(() -> {
                try {
                    SeatHold hold = seatHoldService.holdSeat(testSeatId, userId);
                    successfulHolds.add(hold);
                    log.info("Thread successfully held seat");
                } catch (ConflictException ex) {
                    exceptions.add(ex);
                    log.info("Thread failed to hold seat (expected): {}", ex.getMessage());
                }
            });
            threads.add(thread);
        }

        // Start all threads
        threads.forEach(Thread::start);

        // Wait for all threads to complete
        threads.forEach(t -> {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Assert
        assertEquals(1, successfulHolds.size(), "Only one thread should successfully hold the seat");
        assertEquals(threadCount - 1, exceptions.size(), "Other threads should get ConflictException");
        assertTrue(exceptions.stream().allMatch(ex -> ex instanceof ConflictException),
                "All exceptions should be ConflictException");
    }

    @Test
    @DisplayName("Should handle rapid fire release and re-hold")
    void testRapidReleaseAndReHold() {
        // Arrange
        when(seatLockProvider.tryAcquireSeatLock(testSeatId.toString())).thenReturn(true);
        when(redisTemplate.opsForValue().get(anyString())).thenReturn(null);
        when(redisTemplate.delete(anyString())).thenReturn(1L);

        // Act & Assert
        for (int i = 0; i < 10; i++) {
            UUID userId = UUID.randomUUID();
            
            // Hold
            SeatHold hold = seatHoldService.holdSeat(testSeatId, userId);
            assertNotNull(hold);
            
            // Release
            seatHoldService.releaseSeatHold(testSeatId, hold.getHoldToken());
            
            log.debug("Iteration {}: Hold and release successful", i);
        }

        verify(seatLockProvider, times(10)).tryAcquireSeatLock(testSeatId.toString());
        verify(seatLockProvider, times(10)).releaseSeatLock(testSeatId.toString());
    }

    @Test
    @DisplayName("Should handle lock timeout gracefully")
    void testLockAcquisitionTimeout() {
        // Arrange: simulate lock timeout
        when(seatLockProvider.tryAcquireSeatLock(testSeatId.toString())).thenReturn(false);

        // Act & Assert
        assertThrows(ConflictException.class, () -> seatHoldService.holdSeat(testSeatId, UUID.randomUUID()));
        
        verify(seatLockProvider).tryAcquireSeatLock(testSeatId.toString());
        verify(seatLockProvider, never()).releaseSeatLock(anyString());
    }

    @Test
    @DisplayName("Should validate hold after concurrent holds from different users")
    void testValidateHoldAfterConcurrentOperations() {
        // Arrange
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        String holdToken1 = "token-1";
        String seatKey = "seat:hold:" + testSeatId;
        String tokenKey1 = "hold:token:" + holdToken1;

        // Simulate user1 holds seat
        when(redisTemplate.opsForValue().get(seatKey)).thenReturn(user1.toString());
        when(redisTemplate.opsForValue().get(tokenKey1)).thenReturn(testSeatId.toString());

        // Act
        boolean isUser1HoldValid = seatHoldService.isHoldValid(testSeatId, user1, holdToken1);
        boolean isUser2HoldValid = seatHoldService.isHoldValid(testSeatId, user2, holdToken1);

        // Assert
        assertTrue(isUser1HoldValid, "User1 should have valid hold");
        assertFalse(isUser2HoldValid, "User2 should not have valid hold");
    }

    @Test
    @DisplayName("Should handle interrupted thread during lock acquisition")
    void testInterruptedThreadDuringLockAcquisition() {
        // Arrange: simulate thread interruption
        when(seatLockProvider.tryAcquireSeatLock(testSeatId.toString()))
                .thenThrow(new RuntimeException("Lock acquisition failed - thread interrupted"));

        UUID userId = UUID.randomUUID();

        // Act & Assert
        assertThrows(Exception.class, () -> seatHoldService.holdSeat(testSeatId, userId));
    }

    @Test
    @DisplayName("Should ensure hold TTL prevents stale holds from blocking seats")
    void testHoldTTLEnforcement() {
        // Arrange
        when(seatLockProvider.tryAcquireSeatLock(testSeatId.toString())).thenReturn(true);
        when(redisTemplate.opsForValue().get(anyString())).thenReturn(null);

        // Act
        SeatHold hold = seatHoldService.holdSeat(testSeatId, UUID.randomUUID());

        // Assert: hold should expire after configured duration
        long remainingSeconds = seatHoldService.getHoldRemainingSeconds(testSeatId);
        assertTrue(remainingSeconds > 0, "Hold should have positive TTL");
        assertTrue(remainingSeconds <= 600, "Hold TTL should not exceed 10 minutes (600s)");
    }
}
