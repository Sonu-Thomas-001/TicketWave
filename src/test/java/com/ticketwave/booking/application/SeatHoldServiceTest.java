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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SeatHoldService Unit Tests")
class SeatHoldServiceTest {

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
    @DisplayName("Should create seat hold with valid token and TTL")
    void testHoldSeat_Success() {
        // Arrange
        when(seatLockProvider.tryAcquireSeatLock(testSeatId.toString())).thenReturn(true);
        when(redisTemplate.opsForValue().get(anyString())).thenReturn(null);

        // Act
        SeatHold hold = seatHoldService.holdSeat(testSeatId, testUserId);

        // Assert
        assertNotNull(hold);
        assertEquals(testSeatId, hold.getSeatId());
        assertEquals(testUserId, hold.getUserId());
        assertNotNull(hold.getHoldToken());
        assertFalse(hold.isExpired());
        assertTrue(hold.isOwnedBy(testUserId));

        // Verify lock was released
        verify(seatLockProvider).releaseSeatLock(testSeatId.toString());
    }

    @Test
    @DisplayName("Should throw ConflictException when seat is already held")
    void testHoldSeat_AlreadyHeld() {
        // Arrange
        UUID otherUserId = UUID.randomUUID();
        when(seatLockProvider.tryAcquireSeatLock(testSeatId.toString())).thenReturn(true);
        when(redisTemplate.opsForValue().get(anyString())).thenReturn(otherUserId.toString());

        // Act & Assert
        assertThrows(ConflictException.class, () -> seatHoldService.holdSeat(testSeatId, testUserId));
    }

    @Test
    @DisplayName("Should throw ConflictException when lock cannot be acquired (concurrency)")
    void testHoldSeat_LockAcquisitionFailed() {
        // Arrange
        when(seatLockProvider.tryAcquireSeatLock(testSeatId.toString())).thenReturn(false);

        // Act & Assert
        assertThrows(ConflictException.class, () -> seatHoldService.holdSeat(testSeatId, testUserId));
    }

    @Test
    @DisplayName("Should validate hold is active and owned by correct user")
    void testIsHoldValid_Valid() {
        // Arrange
        String holdToken = "test-token-123";
        String seatKey = "seat:hold:" + testSeatId;
        String tokenKey = "hold:token:" + holdToken;

        when(redisTemplate.opsForValue().get(seatKey)).thenReturn(testUserId.toString());
        when(redisTemplate.opsForValue().get(tokenKey)).thenReturn(testSeatId.toString());

        // Act
        boolean isValid = seatHoldService.isHoldValid(testSeatId, testUserId, holdToken);

        // Assert
        assertTrue(isValid);
    }

    @Test
    @DisplayName("Should return false when hold does not exist")
    void testIsHoldValid_HoldNotFound() {
        // Arrange
        String holdToken = "test-token-123";
        String seatKey = "seat:hold:" + testSeatId;

        when(redisTemplate.opsForValue().get(seatKey)).thenReturn(null);

        // Act
        boolean isValid = seatHoldService.isHoldValid(testSeatId, testUserId, holdToken);

        // Assert
        assertFalse(isValid);
    }

    @Test
    @DisplayName("Should return false when hold is owned by different user")
    void testIsHoldValid_WrongUser() {
        // Arrange
        UUID otherUserId = UUID.randomUUID();
        String holdToken = "test-token-123";
        String seatKey = "seat:hold:" + testSeatId;

        when(redisTemplate.opsForValue().get(seatKey)).thenReturn(otherUserId.toString());

        // Act
        boolean isValid = seatHoldService.isHoldValid(testSeatId, testUserId, holdToken);

        // Assert
        assertFalse(isValid);
    }

    @Test
    @DisplayName("Should return false when hold token is invalid")
    void testIsHoldValid_InvalidToken() {
        // Arrange
        String holdToken = "invalid-token";
        String seatKey = "seat:hold:" + testSeatId;
        String tokenKey = "hold:token:" + holdToken;

        when(redisTemplate.opsForValue().get(seatKey)).thenReturn(testUserId.toString());
        when(redisTemplate.opsForValue().get(tokenKey)).thenReturn(null);

        // Act
        boolean isValid = seatHoldService.isHoldValid(testSeatId, testUserId, holdToken);

        // Assert
        assertFalse(isValid);
    }

    @Test
    @DisplayName("Should release seat hold from Redis")
    void testReleaseSeatHold_Success() {
        // Arrange
        String holdToken = "test-token-123";
        String seatKey = "seat:hold:" + testSeatId;
        String tokenKey = "hold:token:" + holdToken;

        when(redisTemplate.delete(seatKey)).thenReturn(1L);

        // Act
        seatHoldService.releaseSeatHold(testSeatId, holdToken);

        // Assert
        verify(redisTemplate).delete(seatKey);
        verify(redisTemplate).delete(tokenKey);
    }

    @Test
    @DisplayName("Should check if seat is held")
    void testIsSeatHeld() {
        // Arrange
        String seatKey = "seat:hold:" + testSeatId;
        when(redisTemplate.hasKey(seatKey)).thenReturn(true);

        // Act
        boolean held = seatHoldService.isSeatHeld(testSeatId);

        // Assert
        assertTrue(held);
    }

    @Test
    @DisplayName("Should get holding user ID")
    void testGetHoldingUser() {
        // Arrange
        String seatKey = "seat:hold:" + testSeatId;
        when(redisTemplate.opsForValue().get(seatKey)).thenReturn(testUserId.toString());

        // Act
        UUID holdingUser = seatHoldService.getHoldingUser(testSeatId);

        // Assert
        assertEquals(testUserId, holdingUser);
    }

    @Test
    @DisplayName("Should return null when no user holds seat")
    void testGetHoldingUser_NoHold() {
        // Arrange
        String seatKey = "seat:hold:" + testSeatId;
        when(redisTemplate.opsForValue().get(seatKey)).thenReturn(null);

        // Act
        UUID holdingUser = seatHoldService.getHoldingUser(testSeatId);

        // Assert
        assertNull(holdingUser);
    }

    @Test
    @DisplayName("SeatHold should report expiration correctly")
    void testSeatHold_Expiration() {
        // Arrange
        Instant past = Instant.now().minusSeconds(100);
        Instant future = Instant.now().plusSeconds(100);

        SeatHold expiredHold = SeatHold.builder()
                .seatId(testSeatId)
                .userId(testUserId)
                .holdToken("token")
                .heldAt(past)
                .expiresAt(past)
                .build();

        SeatHold activeHold = SeatHold.builder()
                .seatId(testSeatId)
                .userId(testUserId)
                .holdToken("token")
                .heldAt(Instant.now())
                .expiresAt(future)
                .build();

        // Act & Assert
        assertTrue(expiredHold.isExpired());
        assertFalse(activeHold.isExpired());
    }

    @Test
    @DisplayName("SeatHold ownership should be validated correctly")
    void testSeatHold_Ownership() {
        // Arrange
        UUID otherUserId = UUID.randomUUID();
        SeatHold hold = SeatHold.builder()
                .seatId(testSeatId)
                .userId(testUserId)
                .holdToken("token")
                .heldAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .build();

        // Act & Assert
        assertTrue(hold.isOwnedBy(testUserId));
        assertFalse(hold.isOwnedBy(otherUserId));
    }
}
