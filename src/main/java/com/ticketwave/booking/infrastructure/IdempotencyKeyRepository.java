package com.ticketwave.booking.infrastructure;

import com.ticketwave.booking.domain.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {

    /**
     * Find idempotency key by string key value.
     */
    Optional<IdempotencyKey> findByIdempotencyKey(String idempotencyKey);

    /**
     * Count non-expired idempotency keys (for monitoring).
     */
    long countByExpiresAtAfter(Instant instant);

    /**
     * Delete expired keys (cleanup job).
     */
    long deleteByExpiresAtBefore(Instant instant);
}
