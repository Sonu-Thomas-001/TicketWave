package com.ticketwave.booking.application;

import com.ticketwave.booking.domain.IdempotencyKey;
import com.ticketwave.booking.infrastructure.IdempotencyKeyRepository;
import com.ticketwave.common.exception.ConflictException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Service for managing idempotency keys.
 * 
 * Prevents duplicate booking processing when clients retry requests.
 * Stores request fingerprint and response for safe replay.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IdempotencyKeyService {

    private final IdempotencyKeyRepository idempotencyKeyRepository;

    @Value("${app.idempotency.ttl-hours:24}")
    private int idempotencyTtlHours;

    /**
     * Register an idempotency key before processing.
     * If key exists and not yet processed: Continue processing
     * If key exists and already processed: Throw ConflictException (caller should retry)
     * If key is new: Register it for future retries
     *
     * @param idempotencyKey Client-provided UUID
     * @param requestFingerprint SHA-256 hash of request body
     * @return IdempotencyKey entity for later response caching
     */
    @Transactional
    public IdempotencyKey registerOrGetKey(String idempotencyKey, String requestFingerprint) {
        log.debug("Registering idempotency key: {}", idempotencyKey);

        var existingKey = idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey)
                .orElse(null);

        if (existingKey != null) {
            // Key already exists
            if (existingKey.getProcessed() && !existingKey.isExpired()) {
                log.info("Idempotency cache hit for key: {}", idempotencyKey);
                return existingKey; // Return cached response
            } else if (existingKey.isExpired()) {
                log.warn("Idempotency key expired, allowing reprocessing: {}", idempotencyKey);
                // Expired key: allow reprocessing
                existingKey.setProcessed(false);
                existingKey.setExpiresAt(Instant.now().plusSeconds(3600L * idempotencyTtlHours));
                return idempotencyKeyRepository.save(existingKey);
            }
        }

        // Create new key
        Instant expiresAt = Instant.now().plusSeconds(3600L * idempotencyTtlHours);
        IdempotencyKey newKey = IdempotencyKey.builder()
                .idempotencyKey(idempotencyKey)
                .requestFingerprint(requestFingerprint)
                .processed(false)
                .expiresAt(expiresAt)
                .build();

        return idempotencyKeyRepository.save(newKey);
    }

    /**
     * Mark idempotency key as processed and cache response.
     *
     * @param idempotencyKey Key to mark processed
     * @param responseJson Cached response (for replay)
     * @param statusCode HTTP status code
     */
    @Transactional
    public void markProcessed(String idempotencyKey, String responseJson, Integer statusCode) {
        var key = idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("Idempotency key not found: " + idempotencyKey));

        key.setProcessed(true);
        key.setCachedResponse(responseJson);
        key.setCachedStatusCode(statusCode);
        idempotencyKeyRepository.save(key);

        log.debug("Marked idempotency key as processed: {}", idempotencyKey);
    }

    /**
     * Check if idempotency key has cached response.
     */
    public boolean hasCachedResponse(String idempotencyKey) {
        return idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey)
                .map(key -> key.getProcessed() && !key.isExpired())
                .orElse(false);
    }

    /**
     * Get cached response.
     */
    public IdempotencyKey getCachedKey(String idempotencyKey) {
        return idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("Idempotency key not found: " + idempotencyKey));
    }

    /**
     * Generate SHA-256 fingerprint of request body.
     * Prevents accidentally replaying different requests with same key.
     */
    public String generateFingerprint(String requestBody) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(requestBody.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 not available", e);
            throw new RuntimeException("Failed to generate request fingerprint", e);
        }
    }

    /**
     * Cleanup expired idempotency keys (run as scheduled job).
     */
    @Transactional
    public long cleanupExpiredKeys() {
        Instant cutoff = Instant.now().minusSeconds(3600L * 24 * 7); // 7 days old
        long deleted = idempotencyKeyRepository.deleteByExpiresAtBefore(cutoff);
        log.info("Cleaned up {} expired idempotency keys", deleted);
        return deleted;
    }
}
