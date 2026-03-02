package com.ticketwave.booking.domain;

import com.ticketwave.common.domain.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Idempotency key entity for preventing duplicate booking processing.
 * 
 * Used for:
 * - Booking creation: Ensures same request processed once even with retries
 * - Payment webhook: Ensures same webhook payload processed idempotent
 * 
 * Strategy:
 * 1. Client generates UUID4 idempotency key
 * 2. Server stores key before processing
 * 3. If key exists: Return cached response
 * 4. If key new: Process request, cache response
 * 5. TTL: 24 hours (idempotency key retention)
 */
@Entity
@Table(
        name = "idempotency_keys",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_idempotency_key", columnNames = {"idempotency_key"})
        },
        indexes = {
                @Index(name = "idx_idempotency_key_expiry", columnList = "idempotency_key,expires_at")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyKey extends AuditedEntity {

    /**
     * Unique idempotency key provided by client (typically UUID).
     */
    @Column(nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    /**
     * Request fingerprint: Hash of request body for additional safety.
     * Prevents accidentally replaying different requests with same key.
     */
    @Column(nullable = false, length = 64)
    private String requestFingerprint;

    /**
     * JSON response stored for replay if key is reused.
     */
    @Column(columnDefinition = "TEXT")
    private String cachedResponse;

    /**
     * HTTP status code of cached response (200, 201, 400, 500, etc.).
     */
    private Integer cachedStatusCode;

    /**
     * When this idempotency key expires (TTL: 24 hours).
     */
    @Column(nullable = false)
    private Instant expiresAt;

    /**
     * Whether this key has been processed.
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean processed = false;

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return !isExpired() && processed;
    }
}
