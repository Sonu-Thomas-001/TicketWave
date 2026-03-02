package com.ticketwave.catalog.infrastructure;

import com.ticketwave.catalog.domain.Schedule;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Enhanced ScheduleRepository with pessimistic locking and optimized queries.
 * 
 * PRODUCTION IMPROVEMENTS:
 * - Pessimistic locking to prevent lost update race conditions
 * - JOIN FETCH for efficient data loading
 */
@Repository
public interface ScheduleRepositoryEnhanced extends JpaRepository<Schedule, UUID> {

    /**
     * Find schedule by ID with pessimistic write lock.
     * 
     * USE CASE: Booking confirmation - update availableSeats atomically.
     * 
     * RACE CONDITION FIX:
     * WITHOUT lock: Read availableSeats → Calculate new value → Write (LOST UPDATE possible)
     * WITH lock: Lock row → Read → Calculate → Write → Unlock (SAFE)
     * 
     * EXAMPLE:
     * Thread 1: Reads availableSeats=10
     * Thread 2: Reads availableSeats=10 (concurrent read)
     * Thread 1: Writes availableSeats=5 (sold 5 seats)
     * Thread 2: Writes availableSeats=8 (sold 2 seats)
     * RESULT: Lost update! Should be 3, not 8.
     * 
     * WITH PESSIMISTIC LOCK:
     * Thread 1: LOCKS row, reads availableSeats=10
     * Thread 2: WAITS for lock
     * Thread 1: Writes availableSeats=5, UNLOCKS
     * Thread 2: LOCKS row, reads availableSeats=5 (correct!)
     * Thread 2: Writes availableSeats=3, UNLOCKS
     * RESULT: Correct value 3!
     * 
     * @param scheduleId schedule ID
     * @return optional schedule with pessimistic write lock
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Schedule s WHERE s.id = :scheduleId")
    Optional<Schedule> findByIdWithLock(@Param("scheduleId") UUID scheduleId);

    /**
     * Find schedule with all related entities eagerly loaded.
     * 
     * USE CASE: Display schedule details with event, route, and vehicle info.
     * 
     * BENEFIT: Avoids lazy load exceptions and N+1 queries.
     * 
     * @param scheduleId schedule ID
     * @return optional schedule with related entities
     */
    @Query("SELECT s FROM Schedule s " +
           "LEFT JOIN FETCH s.route r " +
           "WHERE s.id = :scheduleId")
    Optional<Schedule> findByIdWithDetails(@Param("scheduleId") UUID scheduleId);
}
