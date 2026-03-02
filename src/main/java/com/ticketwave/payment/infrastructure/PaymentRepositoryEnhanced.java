package com.ticketwave.payment.infrastructure;

import com.ticketwave.payment.domain.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Enhanced PaymentRepository with pessimistic locking for concurrency control.
 * 
 * PRODUCTION IMPROVEMENTS:
 * - Pessimistic locking to prevent payment status race conditions
 * - Idempotency support via transaction ID lookup
 */
@Repository
public interface PaymentRepositoryEnhanced extends JpaRepository<Payment, UUID> {

    /**
     * Find payment by ID with pessimistic write lock.
     * 
     * USE CASE: Payment confirmation webhook - update status atomically.
     * 
     * RACE CONDITION FIX:
     * WITHOUT lock: Two webhook calls arrive simultaneously
     * Thread 1: Read status=PENDING → Set CONFIRMED
     * Thread 2: Read status=PENDING → Set CONFIRMED (duplicate processing!)
     * 
     * WITH lock:
     * Thread 1: LOCK row, read status=PENDING → Set CONFIRMED, UNLOCK
     * Thread 2: LOCK row (waits), read status=CONFIRMED → Already processed, return
     * 
     * @param paymentId payment ID
     * @return optional payment with pessimistic write lock
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :paymentId")
    Optional<Payment> findByIdWithLock(@Param("paymentId") UUID paymentId);

    /**
     * Find payment by transaction ID for idempotency.
     * 
     * USE CASE: Webhook deduplication - check if transaction already processed.
     * 
     * @param transactionId external payment gateway transaction ID
     * @return optional payment
     */
    Optional<Payment> findByTransactionId(String transactionId);

    /**
     * Find payment by booking ID.
     * 
     * USE CASE: Refund processing - find payment to refund.
     * 
     * @param bookingId booking ID
     * @return optional payment
     */
    Optional<Payment> findByBookingId(UUID bookingId);
}
