package com.ticketwave.booking.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class SeatLockProvider {

    private static final String LOCK_PREFIX = "seat:lock:";
    private static final long LOCK_WAIT_TIME = 2; // seconds
    private static final long LOCK_LEASE_TIME = 5; // seconds

    private final RedissonClient redissonClient;

    public SeatLockProvider(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * Atomically acquire a distributed lock for a seat.
     * Returns true if lock was acquired, false otherwise.
     */
    public boolean tryAcquireSeatLock(String seatId) {
        RLock lock = redissonClient.getLock(LOCK_PREFIX + seatId);
        try {
            boolean acquired = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);
            if (acquired) {
                log.debug("Acquired lock for seat: {}", seatId);
            } else {
                log.warn("Failed to acquire lock for seat: {} (timeout)", seatId);
            }
            return acquired;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while acquiring lock for seat: {}", seatId, ex);
            return false;
        }
    }

    /**
     * Release a distributed lock for a seat.
     */
    public void releaseSeatLock(String seatId) {
        RLock lock = redissonClient.getLock(LOCK_PREFIX + seatId);
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("Released lock for seat: {}", seatId);
            }
        } catch (Exception ex) {
            log.error("Error releasing lock for seat: {}", seatId, ex);
        }
    }

    /**
     * Check if a seat is currently locked.
     */
    public boolean isSeatLocked(String seatId) {
        RLock lock = redissonClient.getLock(LOCK_PREFIX + seatId);
        return lock.isLocked();
    }

    /**
     * Forcefully unlock a seat (admin/override).
     */
    public void forciblyUnlockSeat(String seatId) {
        RLock lock = redissonClient.getLock(LOCK_PREFIX + seatId);
        try {
            lock.forceUnlock();
            log.warn("Forcibly unlocked seat: {}", seatId);
        } catch (Exception ex) {
            log.error("Error forcibly unlocking seat: {}", seatId, ex);
        }
    }
}
