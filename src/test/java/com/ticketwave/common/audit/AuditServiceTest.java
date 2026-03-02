package com.ticketwave.common.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuditService.
 * Tests audit log creation, context extraction, and query operations.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuditService Tests")
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditService auditService;

    private AuditLog mockAuditLog;
    private UUID auditId;

    @BeforeEach
    void setUp() {
        auditId = UUID.randomUUID();
        mockAuditLog = AuditLog.builder()
                .id(auditId)
                .entityType("Booking")
                .entityId("bk123")
                .action("CREATE")
                .userId("user1")
                .username("testuser")
                .userRole("ADMIN")
                .description("Booking created successfully")
                .timestamp(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Should log basic event successfully")
    void shouldLogBasicEventSuccessfully() {
        // Given
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(mockAuditLog);

        // When
        AuditLog result = auditService.logEvent("Booking", "bk123", "CREATE", "Booking created");

        // Then
        assertNotNull(result);
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("Should log status change with before/after values")
    void shouldLogStatusChangeWithValues() {
        // Given
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(mockAuditLog);

        // When
        AuditLog result = auditService.logStatusChange(
                "Booking", "bk123", "CONFIRM",
                "PENDING", "CONFIRMED", "Booking confirmed"
        );

        // Then
        assertNotNull(result);
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("Should log admin action with override flag")
    void shouldLogAdminActionWithOverride() {
        // Given
        mockAuditLog.setIsAdminOverride(true);
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(mockAuditLog);

        // When
        AuditLog result = auditService.logAdminAction(
                "Refund", "ref123", "OVERRIDE_AMOUNT",
                "Admin override applied", true
        );

        // Then
        assertNotNull(result);
        assertTrue(result.getIsAdminOverride());
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("Should log error with exception details")
    void shouldLogErrorWithException() {
        // Given
        Exception exception = new RuntimeException("Test error");
        mockAuditLog.setErrorMessage("Payment gateway timeout");
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(mockAuditLog);

        // When
        AuditLog result = auditService.logError(
                "Payment", "pay123", "CONFIRM",
                "Payment confirmation failed", exception
        );

        // Then
        assertNotNull(result);
        assertNotNull(result.getErrorMessage());
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("Should query audit logs for entity")
    void shouldQueryAuditLogsForEntity() {
        // Given
        Page<AuditLog> page = new PageImpl<>(List.of(mockAuditLog), PageRequest.of(0, 10), 1);
        when(auditLogRepository.findByEntityTypeAndEntityId("Booking", "bk123", PageRequest.of(0, 10)))
                .thenReturn(page);

        // When
        Page<AuditLog> result = auditService.getAuditLogsForEntity("Booking", "bk123", PageRequest.of(0, 10));

        // Then
        assertEquals(1, result.getTotalElements());
        assertEquals("Booking", result.getContent().get(0).getEntityType());
    }

    @Test
    @DisplayName("Should query audit logs by user ID")
    void shouldQueryAuditLogsByUser() {
        // Given
        Page<AuditLog> page = new PageImpl<>(List.of(mockAuditLog), PageRequest.of(0, 10), 1);
        when(auditLogRepository.findByUserId("user1", PageRequest.of(0, 10)))
                .thenReturn(page);

        // When
        Page<AuditLog> result = auditService.getAuditLogsForUser("user1", PageRequest.of(0, 10));

        // Then
        assertEquals(1, result.getTotalElements());
        assertEquals("user1", result.getContent().get(0).getUserId());
    }

    @Test
    @DisplayName("Should query failed operations")
    void shouldQueryFailedOperations() {
        // Given
        mockAuditLog.setErrorMessage("Gateway error");
        Page<AuditLog> page = new PageImpl<>(List.of(mockAuditLog), PageRequest.of(0, 10), 1);
        when(auditLogRepository.findFailedOperations(PageRequest.of(0, 10)))
                .thenReturn(page);

        // When
        Page<AuditLog> result = auditService.getFailedOperations(PageRequest.of(0, 10));

        // Then
        assertEquals(1, result.getTotalElements());
        assertNotNull(result.getContent().get(0).getErrorMessage());
    }

    @Test
    @DisplayName("Should query admin overrides")
    void shouldQueryAdminOverrides() {
        // Given
        mockAuditLog.setIsAdminOverride(true);
        Page<AuditLog> page = new PageImpl<>(List.of(mockAuditLog), PageRequest.of(0, 10), 1);
        when(auditLogRepository.findAdminOverrides(PageRequest.of(0, 10)))
                .thenReturn(page);

        // When
        Page<AuditLog> result = auditService.getAdminOverrides(null, PageRequest.of(0, 10));

        // Then
        assertEquals(1, result.getTotalElements());
        assertTrue(result.getContent().get(0).getIsAdminOverride());
    }

    @Test
    @DisplayName("Should query admin overrides for specific admin")
    void shouldQueryAdminOverridesForSpecificAdmin() {
        // Given
        String adminId = "admin1";
        mockAuditLog.setIsAdminOverride(true);
        mockAuditLog.setUserId(adminId);
        Page<AuditLog> page = new PageImpl<>(List.of(mockAuditLog), PageRequest.of(0, 10), 1);
        when(auditLogRepository.findAdminOverridesByAdmin(adminId, PageRequest.of(0, 10)))
                .thenReturn(page);

        // When
        Page<AuditLog> result = auditService.getAdminOverrides(adminId, PageRequest.of(0, 10));

        // Then
        assertEquals(1, result.getTotalElements());
        assertEquals(adminId, result.getContent().get(0).getUserId());
    }

    @Test
    @DisplayName("Should query audit logs by action")
    void shouldQueryAuditLogsByAction() {
        // Given
        Page<AuditLog> page = new PageImpl<>(List.of(mockAuditLog), PageRequest.of(0, 10), 1);
        when(auditLogRepository.findByAction("CREATE", PageRequest.of(0, 10)))
                .thenReturn(page);

        // When
        Page<AuditLog> result = auditService.getAuditLogsByAction("CREATE", PageRequest.of(0, 10));

        // Then
        assertEquals(1, result.getTotalElements());
        assertEquals("CREATE", result.getContent().get(0).getAction());
    }

    @Test
    @DisplayName("Should get audit log by ID")
    void shouldGetAuditLogById() {
        // Given
        when(auditLogRepository.findById(auditId)).thenReturn(Optional.of(mockAuditLog));

        // When
        AuditLog result = auditService.getAuditLogById(auditId.toString());

        // Then
        assertNotNull(result);
        assertEquals(auditId, result.getId());
    }

    @Test
    @DisplayName("Should return null for invalid ID format")
    void shouldReturnNullForInvalidId() {
        // When
        AuditLog result = auditService.getAuditLogById("invalid-id");

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("Should calculate audit statistics")
    void shouldCalculateAuditStatistics() {
        // Given
        when(auditLogRepository.count()).thenReturn(100L);
        when(auditLogRepository.countFailedOperations()).thenReturn(5L);
        when(auditLogRepository.countAdminOverrides()).thenReturn(3L);

        // When
        AuditService.AuditControllerStats stats = auditService.getAuditStats();

        // Then
        assertEquals(100L, stats.totalEvents);
        assertEquals(5L, stats.failedOperations);
        assertEquals(3L, stats.adminOverrides);
    }

    @Test
    @DisplayName("Should create audit log with builder pattern")
    void shouldCreateAuditLogWithBuilder() {
        // Given
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(mockAuditLog);

        AuditService.AuditLogBuilder builder = AuditService.AuditLogBuilder.builder()
                .entityType("Payment")
                .entityId("pay123")
                .action("CONFIRM")
                .description("Payment confirmed")
                .ipAddress("192.168.1.1")
                .httpMethod("POST")
                .endpoint("/api/v1/payment/confirm");

        // When
        AuditLog result = auditService.logEvent(builder.build());

        // Then
        assertNotNull(result);
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("Should capture duration of operation")
    void shouldCaptureDuration() {
        // Given
        mockAuditLog.setDurationMillis(250L);
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(mockAuditLog);

        // When
        AuditService.AuditLogBuilder builder = AuditService.AuditLogBuilder.builder()
                .entityType("Booking")
                .entityId("bk123")
                .action("CREATE")
                .durationMillis(250L);

        AuditLog result = auditService.logEvent(builder.build());

        // Then
        assertNotNull(result);
        assertEquals(250L, result.getDurationMillis());
    }

    @Test
    @DisplayName("Should handle null error message gracefully")
    void shouldHandleNullErrorMessage() {
        // Given
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(mockAuditLog);

        // When
        AuditLog result = auditService.logError("Booking", "bk123", "CREATE", null, null);

        // Then
        assertNotNull(result);
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("Should set related entity information")
    void shouldSetRelatedEntityInformation() {
        // Given
        mockAuditLog.setRelatedEntityType("Booking");
        mockAuditLog.setRelatedEntityId("bk123");
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(mockAuditLog);

        // When
        AuditService.AuditLogBuilder builder = AuditService.AuditLogBuilder.builder()
                .entityType("Refund")
                .entityId("ref123")
                .relatedEntityType("Booking")
                .relatedEntityId("bk123");

        AuditLog result = auditService.logEvent(builder.build());

        // Then
        assertNotNull(result);
        assertEquals("Booking", result.getRelatedEntityType());
    }
}
