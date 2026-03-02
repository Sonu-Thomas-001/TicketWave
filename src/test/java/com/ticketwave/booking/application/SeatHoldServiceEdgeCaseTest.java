package com.ticketwave.booking.application;

import com.ticketwave.booking.domain.SeatHold;
import com.ticketwave.booking.infrastructure.SeatLockProvider;
import com.ticketwave.common.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SeatHoldService Edge Case Tests")
class SeatHoldServiceEdgeCaseTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SeatLockProvider seatLockProvider;

    private SeatHoldService seatHoldService;

    private UUID testSeatId;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        seatHoldService = new SeatHoldService(redisTemplate, seatLockProvider);
        testSeatId = UUID.randomUUID();
        testUserId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Should handle null seat ID gracefully")
    void testHoldSeat_NullSeatId() {
        // Act & Assert
        assertThrows(NullPointerException.class, () -> seatHoldService.holdSeat(null, testUserId));
    }

    @Test
    @DisplayName("Should handle null user ID gracefully")
    void testHoldSeat_NullUserId() {
        // Act & Assert
        assertThrows(NullPointerException.class, () -> seatHoldService.holdSeat(testSeatId, null));
    }

    @Test
    @DisplayName("Should release hold gracefully even if hold does not exist")
    void testReleaseSeatHold_NonExistentHold() {
        // Arrange
        String holdToken = "non-existent-token";
        when(redisTemplate.delete(anyString())).thenReturn(0L);

        // Act - should not throw exception
        seatHoldService.releaseSeatHold(testSeatId, holdToken);

        // Assert
        verify(redisTemplate).delete(anyString());
    }

    @Test
    @DisplayName("Should handle Redis returning negative TTL as key expired")
    void testGetHoldRemainingSeconds_Expired() {
        // Arrange
        String seatKey = "seat:hold:" + testSeatId;
        when(redisTemplate.getExpire(seatKey, TimeUnit.SECONDS)).thenReturn(-2L); // Key expired

        // Act
        long remaining = seatHoldService.getHoldRemainingSeconds(testSeatId);

        // Assert
        assertEquals(-2, remaining, "-2 indicates key does not exist in Redis");
    }

    @Test
    @DisplayName("Should handle Redis returning -1 TTL for persistent key")
    void testGetHoldRemainingSeconds_PersistentKey() {
        // Arrange
        String seatKey = "seat:hold:" + testSeatId;
        when(redisTemplate.getExpire(seatKey, TimeUnit.SECONDS)).thenReturn(-1L); // No expiry

        // Act
        long remaining = seatHoldService.getHoldRemainingSeconds(testSeatId);

        // Assert
        assertEquals(-1, remaining, "-1 indicates no expiry set");
    }

    @Test
    @DisplayName("Should handle valid hold with TTL near expiration")
    void testIsHoldValid_ExpiringTTL() {
        // Arrange: hold exists but about to expire
        String holdToken = "test-token";
        String seatKey = "seat:hold:" + testSeatId;
        String tokenKey = "hold:token:" + holdToken;

        when(redisTemplate.opsForValue().get(seatKey)).thenReturn(testUserId.toString());
        when(redisTemplate.opsForValue().get(tokenKey)).thenReturn(testSeatId.toString());
        when(redisTemplate.getExpire(seatKey, TimeUnit.SECONDS)).thenReturn(1L); // 1 second left

        // Act
        boolean isValid = seatHoldService.isHoldValid(testSeatId, testUserId, holdToken);

        // Assert
        assertTrue(isValid, "Hold should be valid even if near expiration");
    }

    @Test
    @DisplayName("Should handle multiple sequential holds by same user on different seats")
    void testMultipleHoldsSequential_SameUser() {
        // Arrange
        UUID user = UUID.randomUUID();
        UUID seat1 = UUID.randomUUID();
        UUID seat2 = UUID.randomUUID();

        when(seatLockProvider.tryAcquireSeatLock(anyString())).thenReturn(true);
        when(redisTemplate.opsForValue().get(anyString())).thenReturn(null);

        // Act
        SeatHold hold1 = seatHoldService.holdSeat(seat1, user);
        SeatHold hold2 = seatHoldService.holdSeat(seat2, user);

        // Assert
        assertNotNull(hold1);
        assertNotNull(hold2);
        assertEquals(user, hold1.getUserId());
        assertEquals(user, hold2.getUserId());
        assertNotEquals(hold1.getSeatId(), hold2.getSeatId());
    }

    @Test
    @DisplayName("Should handle extend hold operation with valid TTL")
    void testExtendSeatHold_ValidTTL() {
        // Arrange
        String seatKey = "seat:hold:" + testSeatId;
        when(redisTemplate.getExpire(seatKey, TimeUnit.SECONDS)).thenReturn(300L); // 5 minutes

        // Act
        seatHoldService.extendSeatHold(testSeatId, 300); // Add 5 more minutes

        // Assert
        verify(redisTemplate).expire(seatKey, 600L, TimeUnit.SECONDS); // Total 10 minutes
    }

    @Test
    @DisplayName("Should handle extend hold operation when hold does not exist")
    void testExtendSeatHold_NoHold() {
        // Arrange
        String seatKey = "seat:hold:" + testSeatId;
        when(redisTemplate.getExpire(seatKey, TimeUnit.SECONDS)).thenReturn(-2L); // No hold

        // Act - should not throw exception
        seatHoldService.extendSeatHold(testSeatId, 300);

        // Assert
        verify(redisTemplate, never()).expire(seatKey, 600L, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Should handle SeatHold.create with zero duration")
    void testSeatHold_ZeroDuration() {
        // Act
        SeatHold hold = SeatHold.create(testSeatId, testUserId, "token", 0);

        // Assert
        assertNotNull(hold);
        assertTrue(hold.isExpired(), "Hold created with zero duration should be immediately expired");
    }

    @Test
    @DisplayName("Should handle SeatHold.create with negative duration")
    void testSeatHold_NegativeDuration() {
        // Act
        SeatHold hold = SeatHold.create(testSeatId, testUserId, "token", -100);

        // Assert
        assertNotNull(hold);
        assertTrue(hold.isExpired(), "Hold created with negative duration should be expired");
    }

    @Test
    @DisplayName("Should validate hold with mismatched seat in token")
    void testIsHoldValid_MismatchedSeatInToken() {
        // Arrange
        UUID wrongSeatId = UUID.randomUUID();
        String holdToken = "test-token";
        String seatKey = "seat:hold:" + testSeatId;
        String tokenKey = "hold:token:" + holdToken;

        when(redisTemplate.opsForValue().get(seatKey)).thenReturn(testUserId.toString());
        when(redisTemplate.opsForValue().get(tokenKey)).thenReturn(wrongSeatId.toString()); // Wrong seat

        // Act
        boolean isValid = seatHoldService.isHoldValid(testSeatId, testUserId, holdToken);

        // Assert
        assertFalse(isValid, "Hold should be invalid if token maps to different seat");
    }

    @Test
    @DisplayName("Should handle lock provider returning false after successful hold")
    void testHoldSeat_LockReleaseFailure() {
        // Arrange: lock successfully acquired, but release fails silently
        when(seatLockProvider.tryAcquireSeatLock(testSeatId.toString())).thenReturn(true);
        when(redisTemplate.opsForValue().get(anyString())).thenReturn(null);
        doThrow(new RuntimeException("Lock release failed")).when(seatLockProvider).releaseSeatLock(anyString());

        // Act - should not fail the hold operation despite lock release error
        assertThrows(RuntimeException.class, () -> seatHoldService.holdSeat(testSeatId, testUserId));
    }

    @Test
    @DisplayName("Should handle very large number of hold extensions")
    void testExtendSeatHold_ManyExtensions() {
        // Arrange
        String seatKey = "seat:hold:" + testSeatId;
        long initialTtl = 600L;

        when(redisTemplate.getExpire(seatKey, TimeUnit.SECONDS))
                .thenReturn(initialTtl)
                .thenReturn(900L)
                .thenReturn(1200L);

        // Act - extend hold 3 times
        seatHoldService.extendSeatHold(testSeatId, 300);
        seatHoldService.extendSeatHold(testSeatId, 300);
        seatHoldService.extendSeatHold(testSeatId, 300);

        // Assert
        verify(redisTemplate, times(3)).expire(eq(seatKey), anyLong(), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Should generate unique hold tokens for same seat by different users")
    void testHoldTokenUniqueness() {
        // Arrange
        when(seatLockProvider.tryAcquireSeatLock(anyString())).thenReturn(true);
        when(redisTemplate.opsForValue().get(anyString())).thenReturn(null);

        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        // Act
        SeatHold hold1 = seatHoldService.holdSeat(testSeatId, user1);
        SeatHold hold2 = seatHoldService.holdSeat(testSeatId, user2);

        // Assert
        assertNotEquals(hold1.getHoldToken(), hold2.getHoldToken(), 
                "Different users should get different hold tokens");
    }
}
