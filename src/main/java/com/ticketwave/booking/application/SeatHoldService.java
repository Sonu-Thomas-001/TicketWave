package com.ticketwave.booking.application;

import com.ticketwave.booking.domain.SeatHold;
import com.ticketwave.booking.infrastructure.SeatLockProvider;
import com.ticketwave.common.exception.ConflictException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SeatHoldService {

    private static final String SEAT_HOLD_PREFIX = "seat:hold:";
    private static final String HOLD_TOKEN_PREFIX = "hold:token:";

    private final StringRedisTemplate redisTemplate;
    private final SeatLockProvider seatLockProvider;

    @Value("${app.seat-hold.duration-seconds:600}")
    private long holdDurationSeconds;

    public SeatHoldService(StringRedisTemplate redisTemplate, SeatLockProvider seatLockProvider) {
        this.redisTemplate = redisTemplate;
        this.seatLockProvider = seatLockProvider;
    }

    /**
     * Create a 10-minute hold on a seat for a user.
     * Atomically prevents double booking via distributed lock.
     *
     * @param seatId seat UUID
     * @param userId user UUID
     * @return SeatHold object with hold token
     * @throws ConflictException if seat is already held
     */
    public SeatHold holdSeat(UUID seatId, UUID userId) {
        String seatKey = SEAT_HOLD_PREFIX + seatId;
        String holdToken = generateHoldToken(seatId, userId);

        // Acquire distributed lock to prevent race conditions
        if (!seatLockProvider.tryAcquireSeatLock(seatId.toString())) {
            log.warn("Cannot acquire lock for seat: {} (already under contention)", seatId);
            throw new ConflictException("Seat is currently being processed by another transaction");
        }

        try {
            // Check if seat already held (atomic with lock)
            String existingHold = redisTemplate.opsForValue().get(seatKey);
            if (existingHold != null) {
                log.info("Seat {} already held by: {}", seatId, existingHold);
                throw new ConflictException("Seat is already held by another user");
            }

            // Set hold in Redis with TTL (10 minutes)
            String holdData = userId.toString();
            redisTemplate.opsForValue().set(seatKey, holdData, holdDurationSeconds, TimeUnit.SECONDS);

            // Also store hold token mapping for quick lookup
            redisTemplate.opsForValue().set(HOLD_TOKEN_PREFIX + holdToken, seatId.toString(), 
                    holdDurationSeconds, TimeUnit.SECONDS);

            SeatHold hold = SeatHold.create(seatId, userId, holdToken, holdDurationSeconds);
            log.info("Seat hold created: seatId={}, userId={}, token={}, expiresAt={}", 
                    seatId, userId, holdToken, hold.getExpiresAt());

            return hold;

        } finally {
            seatLockProvider.releaseSeatLock(seatId.toString());
        }
    }

    /**
     * Validate that a hold is still active and owned by the user.
     *
     * @param seatId seat UUID
     * @param userId user UUID
     * @param holdToken hold token
     * @return true if hold is valid, false if expired or not owned
     */
    public boolean isHoldValid(UUID seatId, UUID userId, String holdToken) {
        String seatKey = SEAT_HOLD_PREFIX + seatId;
        String holdUserId = redisTemplate.opsForValue().get(seatKey);

        if (holdUserId == null) {
            log.warn("Seat hold not found for seatId: {}", seatId);
            return false;
        }

        if (!holdUserId.equals(userId.toString())) {
            log.warn("Seat hold mismatch: seatId={}, expected userId={}, actual userId={}", 
                    seatId, userId, holdUserId);
            return false;
        }

        // Verify token matches
        String tokenSeatId = redisTemplate.opsForValue().get(HOLD_TOKEN_PREFIX + holdToken);
        if (tokenSeatId == null || !tokenSeatId.equals(seatId.toString())) {
            log.warn("Hold token invalid or expired: token={}", holdToken);
            return false;
        }

        return true;
    }

    /**
     * Get remaining hold time in seconds.
     *
     * @param seatId seat UUID
     * @return remaining TTL in seconds, -1 if no hold exists
     */
    public long getHoldRemainingSeconds(UUID seatId) {
        String seatKey = SEAT_HOLD_PREFIX + seatId;
        Long ttl = redisTemplate.getExpire(seatKey, TimeUnit.SECONDS);
        return ttl != null ? ttl : -1;
    }

    /**
     * Release a seat hold (on cancellation or booking confirmation).
     *
     * @param seatId seat UUID
     * @param holdToken hold token
     */
    public void releaseSeatHold(UUID seatId, String holdToken) {
        String seatKey = SEAT_HOLD_PREFIX + seatId;
        String tokenKey = HOLD_TOKEN_PREFIX + holdToken;

        Boolean deleted = redisTemplate.delete(seatKey);
        redisTemplate.delete(tokenKey);

        if (Boolean.TRUE.equals(deleted)) {
            log.info("Seat hold released: seatId={}, token={}", seatId, holdToken);
        } else {
            log.warn("Attempted to release non-existent hold for seatId={}", seatId);
        }
    }

    /**
     * Release all holds owned by a user (fallback cleanup).
     *
     * @param userId user UUID
     */
    public void releaseAllUserHolds(UUID userId) {
        log.debug("Releasing all holds for user: {}", userId);
        // In production, use Redis scan for large datasets
        // For now, relies on TTL expiration; consider background cleanup job
    }

    /**
     * Check if a seat is currently held.
     *
     * @param seatId seat UUID
     * @return true if seat is held
     */
    public boolean isSeatHeld(UUID seatId) {
        String seatKey = SEAT_HOLD_PREFIX + seatId;
        return redisTemplate.hasKey(seatKey);
    }

    /**
     * Get the user who currently holds a seat.
     *
     * @param seatId seat UUID
     * @return user UUID or null if not held
     */
    public UUID getHoldingUser(UUID seatId) {
        String seatKey = SEAT_HOLD_PREFIX + seatId;
        String userId = redisTemplate.opsForValue().get(seatKey);
        return userId != null ? UUID.fromString(userId) : null;
    }

    /**
     * Refresh hold expiry (extend hold time - typically for pending confirmation).
     *
     * @param seatId seat UUID
     * @param additionalSeconds additional seconds to hold
     */
    public void extendSeatHold(UUID seatId, long additionalSeconds) {
        String seatKey = SEAT_HOLD_PREFIX + seatId;
        Long currentTtl = redisTemplate.getExpire(seatKey, TimeUnit.SECONDS);

        if (currentTtl != null && currentTtl > 0) {
            long newTtl = currentTtl + additionalSeconds;
            redisTemplate.expire(seatKey, newTtl, TimeUnit.SECONDS);
            log.debug("Seat hold extended: seatId={}, newTtl={}s", seatId, newTtl);
        } else {
            log.warn("Cannot extend hold for seatId: {} (no active hold)", seatId);
        }
    }

    private String generateHoldToken(UUID seatId, UUID userId) {
        return userId.toString().substring(0, 8) + "-" + seatId.toString().substring(0, 8) + 
               "-" + System.nanoTime() % 10000;
    }
}
