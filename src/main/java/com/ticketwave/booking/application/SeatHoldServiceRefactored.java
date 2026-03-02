package com.ticketwave.booking.application;

import com.ticketwave.booking.domain.SeatHold;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * REFACTORED SeatHoldService with Redis race condition fixes.
 * 
 * KEY IMPROVEMENTS:
 * 1. Atomic extend operation using Lua script (eliminates TOCTOU race)
 * 2. Proper null validation
 * 3. Structured logging with context
 * 4. Returns Optional instead of nullable values
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatHoldServiceRefactored {

    private final StringRedisTemplate redisTemplate;
    private final RedissonClient redissonClient;

    @Value("${ticketwave.seat-hold.ttl-seconds:600}")
    private int seatHoldTtlSeconds;

    @Value("${ticketwave.seat-hold.lock-wait-seconds:2}")
    private int lockWaitSeconds;

    @Value("${ticketwave.seat-hold.lock-lease-seconds:5}")
    private int lockLeaseSeconds;

    private static final String SEAT_HOLD_KEY_PREFIX = "seat:hold:";
    private static final String SEAT_LOCK_KEY_PREFIX = "seat:lock:";
    
    /**
     * Lua script for atomic TTL extension.
     * 
     * RACE CONDITION FIX:
     * WITHOUT Lua script (Java code):
     * 1. Long currentTtl = redisTemplate.getExpire(key)  // Read
     * 2. // Network delay, TTL might expire here!
     * 3. redisTemplate.expire(key, newTtl)  // Write on possibly expired key
     * PROBLEM: Key might expire between read and write (TOCTOU)
     * 
     * WITH Lua script (Redis atomic operation):
     * 1. Single atomic operation on Redis server
     * 2. No network round-trip between read and write
     * 3. Key cannot expire during operation
     * 
     * SCRIPT LOGIC:
     * - Check if key exists
     * - If exists and value matches holdToken, extend TTL
     * - Return 1 if extended, 0 if key not found or token mismatch
     */
    private static final String LUA_EXTEND_HOLD = """
            local key = KEYS[1]
            local expectedToken = ARGV[1]
            local extensionSeconds = tonumber(ARGV[2])
            
            if redis.call('EXISTS', key) == 1 then
                local currentToken = redis.call('GET', key)
                if currentToken == expectedToken then
                    redis.call('EXPIRE', key, extensionSeconds)
                    return 1
                else
                    return 0  -- Token mismatch
                end
            else
                return 0  -- Key does not exist
            end
            """;

    /**
     * Hold a seat for a user with distributed locking.
     *
     * @param seatId seat ID to hold
     * @param userId user ID claiming the hold
     * @return seat hold with token
     * @throws IllegalArgumentException if parameters are null
     * @throws IllegalStateException if seat already held
     */
    public SeatHold holdSeat(UUID seatId, UUID userId) {
        // Null validation
        if (seatId == null) {
            throw new IllegalArgumentException("Seat ID cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        String lockKey = SEAT_LOCK_KEY_PREFIX + seatId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean isLocked = lock.tryLock(lockWaitSeconds, lockLeaseSeconds, TimeUnit.SECONDS);
            
            if (!isLocked) {
                log.warn("Failed to acquire lock for seat: {} by user: {}", seatId, userId);
                throw new IllegalStateException("Seat is being held by another user. Please try again.");
            }

            // Check if already held
            String holdKey = SEAT_HOLD_KEY_PREFIX + seatId;
            String existingHold = redisTemplate.opsForValue().get(holdKey);
            
            if (existingHold != null) {
                log.warn("Seat already held: seatId={}, holdToken={}", seatId, existingHold);
                throw new IllegalStateException("Seat is already on hold");
            }

            // Create new hold
            String holdToken = UUID.randomUUID().toString();
            String holdValue = userId + ":" + holdToken;
            
            redisTemplate.opsForValue().set(holdKey, holdValue, Duration.ofSeconds(seatHoldTtlSeconds));
            
            log.info("Seat hold created: seatId={}, userId={}, ttl={}s", 
                    seatId, userId, seatHoldTtlSeconds);
            
            return SeatHold.builder()
                    .seatId(seatId)
                    .userId(userId)
                    .holdToken(holdToken)
                    .expiresAt(java.time.Instant.now().plusSeconds(seatHoldTtlSeconds))
                    .build();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while acquiring seat hold lock: seatId={}", seatId, e);
            throw new IllegalStateException("Failed to acquire seat hold due to interruption", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * Extend an existing seat hold atomically using Lua script.
     * 
     * RACE CONDITION FIX: Uses Lua script for atomic read-modify-write.
     *
     * @param seatId seat ID
     * @param userId user ID owning the hold
     * @param holdToken hold token to verify ownership
     * @param extensionSeconds additional seconds to extend
     * @return true if extended successfully
     * @throws IllegalArgumentException if parameters are null or invalid
     */
    public boolean extendSeatHold(UUID seatId, UUID userId, String holdToken, int extensionSeconds) {
        // Validation
        if (seatId == null) {
            throw new IllegalArgumentException("Seat ID cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (holdToken == null || holdToken.isBlank()) {
            throw new IllegalArgumentException("Hold token cannot be null or empty");
        }
        if (extensionSeconds <= 0) {
            throw new IllegalArgumentException("Extension seconds must be positive");
        }

        String holdKey = SEAT_HOLD_KEY_PREFIX + seatId;
        String expectedValue = userId + ":" + holdToken;
        
        // Execute Lua script atomically
        RedisScript<Long> script = RedisScript.of(LUA_EXTEND_HOLD, Long.class);
        Long result = redisTemplate.execute(
                script, 
                Collections.singletonList(holdKey), 
                expectedValue, 
                String.valueOf(extensionSeconds)
        );
        
        boolean extended = result != null && result == 1L;
        
        if (extended) {
            log.info("Seat hold extended: seatId={}, userId={}, extensionSeconds={}", 
                    seatId, userId, extensionSeconds);
        } else {
            log.warn("Failed to extend seat hold: seatId={}, userId={}, reason={}",
                    seatId, userId, (result == null ? "script_error" : "token_mismatch_or_expired"));
        }
        
        return extended;
    }

    /**
     * Validate if a seat hold is still valid.
     *
     * @param seatId seat ID
     * @param userId user ID
     * @param holdToken hold token
     * @return true if hold is valid and not expired
     */
    public boolean isHoldValid(UUID seatId, UUID userId, String holdToken) {
        if (seatId == null || userId == null || holdToken == null) {
            return false;
        }

        String holdKey = SEAT_HOLD_KEY_PREFIX + seatId;
        String expectedValue = userId + ":" + holdToken;
        String actualValue = redisTemplate.opsForValue().get(holdKey);
        
        boolean valid = expectedValue.equals(actualValue);
        
        if (!valid) {
            log.debug("Seat hold validation failed: seatId={}, userId={}, expected={}, actual={}", 
                    seatId, userId, expectedValue, actualValue);
        }
        
        return valid;
    }

    /**
     * Release a seat hold.
     *
     * @param seatId seat ID
     * @param holdToken hold token
     * @return true if released successfully
     */
    public boolean releaseSeatHold(UUID seatId, String holdToken) {
        if (seatId == null || holdToken == null) {
            log.warn("Cannot release seat hold with null parameters");
            return false;
        }

        String holdKey = SEAT_HOLD_KEY_PREFIX + seatId;
        Boolean deleted = redisTemplate.delete(holdKey);
        
        if (Boolean.TRUE.equals(deleted)) {
            log.info("Seat hold released: seatId={}", seatId);
        } else {
            log.warn("Seat hold not found during release: seatId={}", seatId);
        }
        
        return Boolean.TRUE.equals(deleted);
    }

    /**
     * Get the user currently holding a seat.
     * 
     * CLEAN CODE FIX: Returns Optional instead of nullable String.
     *
     * @param seatId seat ID
     * @return optional user ID
     */
    public Optional<UUID> getHoldingUser(UUID seatId) {
        if (seatId == null) {
            return Optional.empty();
        }

        String holdKey = SEAT_HOLD_KEY_PREFIX + seatId;
        String holdValue = redisTemplate.opsForValue().get(holdKey);
        
        if (holdValue == null) {
            return Optional.empty();
        }

        try {
            String userId = holdValue.split(":")[0];
            return Optional.of(UUID.fromString(userId));
        } catch (Exception e) {
            log.error("Failed to parse holding user from hold value: {}", holdValue, e);
            return Optional.empty();
        }
    }

    /**
     * Get remaining TTL for a seat hold.
     *
     * @param seatId seat ID
     * @return remaining seconds, or empty if hold doesn't exist
     */
    public Optional<Long> getRemainingTtl(UUID seatId) {
        if (seatId == null) {
            return Optional.empty();
        }

        String holdKey = SEAT_HOLD_KEY_PREFIX + seatId;
        Long ttl = redisTemplate.getExpire(holdKey, TimeUnit.SECONDS);
        
        if (ttl == null || ttl < 0) {
            return Optional.empty();
        }
        
        return Optional.of(ttl);
    }
}
