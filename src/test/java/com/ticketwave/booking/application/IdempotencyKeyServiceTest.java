package com.ticketwave.booking.application;

import com.ticketwave.booking.domain.IdempotencyKey;
import com.ticketwave.booking.infrastructure.IdempotencyKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("IdempotencyKeyService Tests")
class IdempotencyKeyServiceTest {

    @Mock
    private IdempotencyKeyRepository repository;

    private IdempotencyKeyService service;
    private String testKey;
    private String fingerprint;

    @BeforeEach
    void setUp() {
        service = new IdempotencyKeyService(repository);
        testKey = "idempotency-key-123";
        fingerprint = "sha256hash";
    }

    @Test
    @DisplayName("Should register new idempotency key")
    void testRegisterOrGetKey_NewKey() {
        // Arrange
        when(repository.findByIdempotencyKey(testKey)).thenReturn(Optional.empty());
        when(repository.save(any(IdempotencyKey.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        IdempotencyKey result = service.registerOrGetKey(testKey, fingerprint);

        // Assert
        assertNotNull(result);
        assertEquals(testKey, result.getIdempotencyKey());
        assertEquals(fingerprint, result.getRequestFingerprint());
        assertFalse(result.getProcessed());
        verify(repository, times(1)).save(any(IdempotencyKey.class));
    }

    @Test
    @DisplayName("Should return existing unprocessed key")
    void testRegisterOrGetKey_ExistingUnprocessedKey() {
        // Arrange
        IdempotencyKey existingKey = IdempotencyKey.builder()
                .idempotencyKey(testKey)
                .requestFingerprint(fingerprint)
                .processed(false)
                .expiresAt(Instant.now().plusSeconds(86400))
                .build();

        when(repository.findByIdempotencyKey(testKey)).thenReturn(Optional.of(existingKey));

        // Act
        IdempotencyKey result = service.registerOrGetKey(testKey, fingerprint);

        // Assert
        assertEquals(existingKey, result);
        assertFalse(result.getProcessed());
    }

    @Test
    @DisplayName("Should return cached response for processed key")
    void testRegisterOrGetKey_ProcessedKey() {
        // Arrange
        IdempotencyKey processedKey = IdempotencyKey.builder()
                .idempotencyKey(testKey)
                .requestFingerprint(fingerprint)
                .processed(true)
                .cachedResponse("{\"data\": \"cached\"}")
                .cachedStatusCode(201)
                .expiresAt(Instant.now().plusSeconds(86400))
                .build();

        when(repository.findByIdempotencyKey(testKey)).thenReturn(Optional.of(processedKey));

        // Act
        IdempotencyKey result = service.registerOrGetKey(testKey, fingerprint);

        // Assert
        assertTrue(result.getProcessed());
        assertEquals("{\"data\": \"cached\"}", result.getCachedResponse());
        assertEquals(201, result.getCachedStatusCode());
    }

    @Test
    @DisplayName("Should mark key as processed with cached response")
    void testMarkProcessed() {
        // Arrange
        IdempotencyKey key = IdempotencyKey.builder()
                .idempotencyKey(testKey)
                .processed(false)
                .build();

        when(repository.findByIdempotencyKey(testKey)).thenReturn(Optional.of(key));
        when(repository.save(any(IdempotencyKey.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        service.markProcessed(testKey, "{\"booking\": 123}", 201);

        // Assert
        verify(repository, times(1)).save(argThat(savedKey ->
                savedKey.getProcessed() &&
                        "{\"booking\": 123}".equals(savedKey.getCachedResponse()) &&
                        201 == savedKey.getCachedStatusCode()
        ));
    }

    @Test
    @DisplayName("Should detect cached response")
    void testHasCachedResponse_True() {
        // Arrange
        IdempotencyKey cachedKey = IdempotencyKey.builder()
                .idempotencyKey(testKey)
                .processed(true)
                .expiresAt(Instant.now().plusSeconds(86400))
                .build();

        when(repository.findByIdempotencyKey(testKey)).thenReturn(Optional.of(cachedKey));

        // Act
        boolean result = service.hasCachedResponse(testKey);

        // Assert
        assertTrue(result);
    }

    @Test
    @DisplayName("Should return false for expired key")
    void testHasCachedResponse_Expired() {
        // Arrange
        IdempotencyKey expiredKey = IdempotencyKey.builder()
                .idempotencyKey(testKey)
                .processed(true)
                .expiresAt(Instant.now().minusSeconds(1))
                .build();

        when(repository.findByIdempotencyKey(testKey)).thenReturn(Optional.of(expiredKey));

        // Act
        boolean result = service.hasCachedResponse(testKey);

        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("Should generate valid SHA-256 fingerprint")
    void testGenerateFingerprint() {
        // Arrange
        String requestBody = "{\"schedule\": \"123\", \"seats\": [\"1A\", \"2B\"]}";

        // Act
        String fingerprint1 = service.generateFingerprint(requestBody);
        String fingerprint2 = service.generateFingerprint(requestBody);

        // Assert
        assertEquals(64, fingerprint1.length(), "SHA-256 produces 64 hex characters");
        assertEquals(fingerprint1, fingerprint2, "Same input produces same fingerprint");
    }

    @Test
    @DisplayName("Should cleanup expired keys")
    void testCleanupExpiredKeys() {
        // Arrange
        when(repository.deleteByExpiresAtBefore(any(Instant.class))).thenReturn(5L);

        // Act
        long deleted = service.cleanupExpiredKeys();

        // Assert
        assertEquals(5L, deleted);
        verify(repository, times(1)).deleteByExpiresAtBefore(any(Instant.class));
    }
}
