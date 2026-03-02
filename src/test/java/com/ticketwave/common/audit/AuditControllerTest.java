package com.ticketwave.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketwave.common.api.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for AuditController.
 * Tests REST endpoints for audit log queries and admin-only access control.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("AuditController Tests")
class AuditControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
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
                .ipAddress("192.168.1.1")
                .httpMethod("POST")
                .endpoint("/api/v1/bookings")
                .timestamp(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Should deny audit endpoint access for unauthenticated user")
    void shouldDenyAccessForUnauthenticated() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/admin/audit/entity/Booking/bk123"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should deny audit endpoint access for non-admin user")
    @WithMockUser(username = "user1", roles = "USER")
    void shouldDenyAccessForNonAdmin() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/admin/audit/entity/Booking/bk123")
                .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should allow audit endpoint access for admin user")
    @WithMockUser(username = "admin1", roles = "ADMIN")
    void shouldAllowAccessForAdmin() throws Exception {
        // Given
        UUID entityId = UUID.randomUUID();
        Page<AuditLog> page = new PageImpl<>(List.of(mockAuditLog), PageRequest.of(0, 10), 1);
        when(auditService.getAuditLogsForEntity("Booking", entityId.toString(), PageRequest.of(0, 10)))
                .thenReturn(page);

        // When & Then
        mockMvc.perform(get("/api/v1/admin/audit/entity/Booking/" + entityId)
                .param("page", "0")
                .param("size", "10")
                .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should get audit logs for entity")
    @WithMockUser(username = "admin1", roles = "ADMIN")
    void shouldGetAuditLogsForEntity() throws Exception {
        // Given
        UUID entityId = UUID.randomUUID();
        Page<AuditLog> page = new PageImpl<>(List.of(mockAuditLog), PageRequest.of(0, 10), 1);
        when(auditService.getAuditLogsForEntity("Booking", entityId.toString(), PageRequest.of(0, 10)))
                .thenReturn(page);

        // When & Then
        mockMvc.perform(get("/api/v1/admin/audit/entity/Booking/" + entityId)
                .param("page", "0")
                .param("size", "10")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].entityType", equalTo("Booking")))
                .andExpect(jsonPath("$.data.content[0].action", equalTo("CREATE")));
    }

    @Test
    @DisplayName("Should get audit logs for user")
    @WithMockUser(username = "admin1", roles = "ADMIN")
    void shouldGetAuditLogsForUser() throws Exception {
        // Given
        Page<AuditLog> page = new PageImpl<>(List.of(mockAuditLog), PageRequest.of(0, 10), 1);
        when(auditService.getAuditLogsForUser("user1", PageRequest.of(0, 10)))
                .thenReturn(page);

        // When & Then
        mockMvc.perform(get("/api/v1/admin/audit/user/user1")
                .param("page", "0")
                .param("size", "10")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].userId", equalTo("user1")));
    }

    @Test
    @DisplayName("Should get failed operations")
    @WithMockUser(username = "admin1", roles = "ADMIN")
    void shouldGetFailedOperations() throws Exception {
        // Given
        mockAuditLog.setErrorMessage("Payment gateway error");
        Page<AuditLog> page = new PageImpl<>(List.of(mockAuditLog), PageRequest.of(0, 10), 1);
        when(auditService.getFailedOperations(PageRequest.of(0, 10)))
                .thenReturn(page);

        // When & Then
        mockMvc.perform(get("/api/v1/admin/audit/failed-operations")
                .param("page", "0")
                .param("size", "10")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].errorMessage", notNullValue()));
    }

    @Test
    @DisplayName("Should get admin overrides")
    @WithMockUser(username = "admin1", roles = "ADMIN")
    void shouldGetAdminOverrides() throws Exception {
        // Given
        mockAuditLog.setIsAdminOverride(true);
        Page<AuditLog> page = new PageImpl<>(List.of(mockAuditLog), PageRequest.of(0, 10), 1);
        when(auditService.getAdminOverrides(null, PageRequest.of(0, 10)))
                .thenReturn(page);

        // When & Then
        mockMvc.perform(get("/api/v1/admin/audit/admin-overrides")
                .param("page", "0")
                .param("size", "10")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].isAdminOverride", is(true)));
    }

    @Test
    @DisplayName("Should get admin overrides for specific admin")
    @WithMockUser(username = "admin1", roles = "ADMIN")
    void shouldGetAdminOverridesForSpecificAdmin() throws Exception {
        // Given
        mockAuditLog.setIsAdminOverride(true);
        mockAuditLog.setUserId("admin2");
        Page<AuditLog> page = new PageImpl<>(List.of(mockAuditLog), PageRequest.of(0, 10), 1);
        when(auditService.getAdminOverrides("admin2", PageRequest.of(0, 10)))
                .thenReturn(page);

        // When & Then
        mockMvc.perform(get("/api/v1/admin/audit/admin-overrides")
                .param("adminId", "admin2")
                .param("page", "0")
                .param("size", "10")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content[0].userId", equalTo("admin2")));
    }

    @Test
    @DisplayName("Should get current user's actions")
    @WithMockUser(username = "user1", roles = "ADMIN")
    void shouldGetCurrentUserActions() throws Exception {
        // Given
        Page<AuditLog> page = new PageImpl<>(List.of(mockAuditLog), PageRequest.of(0, 10), 1);
        when(auditService.getAuditLogsForUser("user1", PageRequest.of(0, 10)))
                .thenReturn(page);

        // When & Then
        mockMvc.perform(get("/api/v1/admin/audit/my-actions")
                .param("page", "0")
                .param("size", "10")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content", hasSize(1)));
    }

    @Test
    @DisplayName("Should get audit logs by action type")
    @WithMockUser(username = "admin1", roles = "ADMIN")
    void shouldGetAuditLogsByActionType() throws Exception {
        // Given
        Page<AuditLog> page = new PageImpl<>(List.of(mockAuditLog), PageRequest.of(0, 10), 1);
        when(auditService.getAuditLogsByAction("CREATE", PageRequest.of(0, 10)))
                .thenReturn(page);

        // When & Then
        mockMvc.perform(get("/api/v1/admin/audit/action/CREATE")
                .param("page", "0")
                .param("size", "10")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].action", equalTo("CREATE")));
    }

    @Test
    @DisplayName("Should get single audit log by ID")
    @WithMockUser(username = "admin1", roles = "ADMIN")
    void shouldGetSingleAuditLogById() throws Exception {
        // Given
        when(auditService.getAuditLogById(auditId.toString()))
                .thenReturn(mockAuditLog);

        // When & Then
        mockMvc.perform(get("/api/v1/admin/audit/" + auditId)
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", equalTo(auditId.toString())))
                .andExpect(jsonPath("$.data.entityType", equalTo("Booking")));
    }

    @Test
    @DisplayName("Should return 404 for non-existent audit log")
    @WithMockUser(username = "admin1", roles = "ADMIN")
    void shouldReturn404ForNonExistentAuditLog() throws Exception {
        // Given
        UUID nonExistentId = UUID.randomUUID();
        when(auditService.getAuditLogById(nonExistentId.toString()))
                .thenReturn(null);

        // When & Then
        mockMvc.perform(get("/api/v1/admin/audit/" + nonExistentId)
                .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should get audit statistics overview")
    @WithMockUser(username = "admin1", roles = "ADMIN")
    void shouldGetAuditStatistics() throws Exception {
        // Given
        AuditService.AuditControllerStats stats = new AuditService.AuditControllerStats();
        stats.totalEvents = 1000L;
        stats.failedOperations = 15L;
        stats.adminOverrides = 5L;

        when(auditService.getAuditStats()).thenReturn(stats);

        // When & Then
        mockMvc.perform(get("/api/v1/admin/audit/stats/overview")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.totalEvents", equalTo(1000)))
                .andExpect(jsonPath("$.data.failedOperations", equalTo(15)))
                .andExpect(jsonPath("$.data.adminOverrides", equalTo(5)));
    }

    @Test
    @DisplayName("Should support pagination with page parameter")
    @WithMockUser(username = "admin1", roles = "ADMIN")
    void shouldSupportPaginationWithPageParameter() throws Exception {
        // Given
        Page<AuditLog> page = new PageImpl<>(List.of(mockAuditLog), PageRequest.of(2, 10), 500);
        when(auditService.getAuditLogsForUser("user1", PageRequest.of(2, 10)))
                .thenReturn(page);

        // When & Then
        mockMvc.perform(get("/api/v1/admin/audit/user/user1")
                .param("page", "2")
                .param("size", "10")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.number", equalTo(2)))
                .andExpect(jsonPath("$.data.size", equalTo(10)))
                .andExpect(jsonPath("$.data.totalElements", equalTo(500)));
    }

    @Test
    @DisplayName("Should support sorting parameter")
    @WithMockUser(username = "admin1", roles = "ADMIN")
    void shouldSupportSortingParameter() throws Exception {
        // Given
        Page<AuditLog> page = new PageImpl<>(List.of(mockAuditLog), PageRequest.of(0, 10), 1);
        when(auditService.getAuditLogsForEntity(
                eq("Booking"),
                anyString(),
                argThat(pr -> pr.getPageNumber() == 0 && pr.getPageSize() == 10)))
                .thenReturn(page);

        // When & Then
        mockMvc.perform(get("/api/v1/admin/audit/entity/Booking/bk123")
                .param("page", "0")
                .param("size", "10")
                .param("sort", "timestamp,desc")
                .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should include metadata in audit log response")
    @WithMockUser(username = "admin1", roles = "ADMIN")
    void shouldIncludeMetadataInResponse() throws Exception {
        // Given
        mockAuditLog.setMetadata("{\"customField\": \"value\"}");
        Page<AuditLog> page = new PageImpl<>(List.of(mockAuditLog), PageRequest.of(0, 10), 1);
        when(auditService.getAuditLogsForEntity("Booking", "bk123", PageRequest.of(0, 10)))
                .thenReturn(page);

        // When & Then
        mockMvc.perform(get("/api/v1/admin/audit/entity/Booking/bk123")
                .param("page", "0")
                .param("size", "10")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].metadata", notNullValue()));
    }

    @Test
    @DisplayName("Should include IP address in audit log response")
    @WithMockUser(username = "admin1", roles = "ADMIN")
    void shouldIncludeIpAddressInResponse() throws Exception {
        // Given
        mockAuditLog.setIpAddress("203.0.113.42");
        Page<AuditLog> page = new PageImpl<>(List.of(mockAuditLog), PageRequest.of(0, 10), 1);
        when(auditService.getAuditLogsForEntity("Booking", "bk123", PageRequest.of(0, 10)))
                .thenReturn(page);

        // When & Then
        mockMvc.perform(get("/api/v1/admin/audit/entity/Booking/bk123")
                .param("page", "0")
                .param("size", "10")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].ipAddress", equalTo("203.0.113.42")));
    }

    @Test
    @DisplayName("Should handle empty audit logs gracefully")
    @WithMockUser(username = "admin1", roles = "ADMIN")
    void shouldHandleEmptyAuditLogs() throws Exception {
        // Given
        Page<AuditLog> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(auditService.getAuditLogsForUser("nonexistent", PageRequest.of(0, 10)))
                .thenReturn(emptyPage);

        // When & Then
        mockMvc.perform(get("/api/v1/admin/audit/user/nonexistent")
                .param("page", "0")
                .param("size", "10")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content", hasSize(0)))
                .andExpect(jsonPath("$.data.totalElements", equalTo(0)));
    }
}
