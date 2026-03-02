package com.ticketwave.common.audit;

import com.ticketwave.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AuditController provides admin endpoints to query audit logs.
 * All endpoints require ADMIN role.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/audit")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {

    private final AuditService auditService;

    /**
     * GET /api/v1/admin/audit/entity/{entityType}/{entityId}
     * Get audit logs for a specific entity.
     */
    @GetMapping("/entity/{entityType}/{entityId}")
    public ResponseEntity<ApiResponse<Page<AuditLog>>> getEntityAuditLog(
            @PathVariable String entityType,
            @PathVariable String entityId,
            Pageable pageable) {
        log.info("Fetching audit logs - Entity: {}, ID: {}", entityType, entityId);
        
        Page<AuditLog> logs = auditService.getAuditLogsForEntity(entityType, entityId, pageable);
        return ResponseEntity.ok(ApiResponse.success(logs));
    }

    /**
     * GET /api/v1/admin/audit/user/{userId}
     * Get audit logs for all actions by a specific user.
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<Page<AuditLog>>> getUserAuditLog(
            @PathVariable String userId,
            Pageable pageable) {
        log.info("Fetching audit logs for user: {}", userId);
        
        Page<AuditLog> logs = auditService.getAuditLogsForUser(userId, pageable);
        return ResponseEntity.ok(ApiResponse.success(logs));
    }

    /**
     * GET /api/v1/admin/audit/failed-operations
     * Get all failed operations (errors).
     */
    @GetMapping("/failed-operations")
    public ResponseEntity<ApiResponse<Page<AuditLog>>> getFailedOperations(Pageable pageable) {
        log.info("Fetching failed operations");
        
        Page<AuditLog> logs = auditService.getFailedOperations(pageable);
        return ResponseEntity.ok(ApiResponse.success(logs));
    }

    /**
     * GET /api/v1/admin/audit/admin-overrides
     * Get all admin overrides (optionally filtered by admin).
     */
    @GetMapping("/admin-overrides")
    public ResponseEntity<ApiResponse<Page<AuditLog>>> getAdminOverrides(
            @RequestParam(required = false) String adminId,
            Pageable pageable) {
        log.info("Fetching admin overrides" + (adminId != null ? " for admin: " + adminId : ""));
        
        // If no adminId specified, default to current user
        String admin = adminId;
        if (admin == null) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null) {
                admin = authentication.getName();
            }
        }
        
        Page<AuditLog> logs = auditService.getAdminOverrides(admin, pageable);
        return ResponseEntity.ok(ApiResponse.success(logs));
    }

    /**
     * GET /api/v1/admin/audit/my-actions
     * Get audit logs for current user's actions.
     */
    @GetMapping("/my-actions")
    public ResponseEntity<ApiResponse<Page<AuditLog>>> getCurrentUserAuditLog(Pageable pageable) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.failure("401", "No authentication context"));
        }
        
        String userId = authentication.getName();
        log.info("Fetching audit logs for current user: {}", userId);
        
        Page<AuditLog> logs = auditService.getAuditLogsForUser(userId, pageable);
        return ResponseEntity.ok(ApiResponse.success(logs));
    }

    /**
     * GET /api/v1/admin/audit/action/{action}
     * Get all audit logs for a specific action (e.g., "APPROVE", "CANCEL").
     */
    @GetMapping("/action/{action}")
    public ResponseEntity<ApiResponse<Page<AuditLog>>> getByAction(
            @PathVariable String action,
            Pageable pageable) {
        log.info("Fetching audit logs for action: {}", action);
        
        Page<AuditLog> logs = auditService.getAuditLogsByAction(action, pageable);
        return ResponseEntity.ok(ApiResponse.success(logs));
    }

    /**
     * GET /api/v1/admin/audit/{auditId}
     * Get details of a single audit log entry.
     */
    @GetMapping("/{auditId}")
    public ResponseEntity<ApiResponse<AuditLog>> getAuditLogDetail(@PathVariable String auditId) {
        log.info("Fetching audit log detail: {}", auditId);
        
        try {
            AuditLog log = auditService.getAuditLogById(auditId);
            if (log == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(ApiResponse.success(log));
        } catch (Exception e) {
            log.error("Error fetching audit log: {}", e.getMessage());
            return ResponseEntity.status(500)
                    .body(ApiResponse.failure("500", "Error fetching audit log"));
        }
    }

    /**
     * GET /api/v1/admin/audit/stats/overview
     * Get audit statistics overview (counts by entity type, action, etc.).
     */
    @GetMapping("/stats/overview")
    public ResponseEntity<ApiResponse<AuditService.AuditControllerStats>> getAuditStats() {
        log.info("Fetching audit statistics");
        
        AuditService.AuditControllerStats stats = auditService.getAuditStats();
        return ResponseEntity.ok(ApiResponse.success(stats));
    }
}
