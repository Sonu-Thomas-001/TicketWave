package com.ticketwave.common.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.UUID;

/**
 * AuditService provides centralized audit logging functionality.
 * Captures user context, request details, and stores audit events.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Log an audit event with full context capture.
     * 
     * @param entityType Type of entity being audited
     * @param entityId ID of the entity
     * @param action Action performed
     * @param description Description of what happened
     * @return Created AuditLog
     */
    public AuditLog logEvent(String entityType, String entityId, String action, String description) {
        return logEvent(AuditLogBuilder.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .description(description)
                .build());
    }

    /**
     * Log an audit event with status change tracking.
     */
    public AuditLog logStatusChange(String entityType, String entityId, String action,
                                    String previousValue, String newValue, String description) {
        return logEvent(AuditLogBuilder.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .previousValue(previousValue)
                .newValue(newValue)
                .description(description)
                .build());
    }

    /**
     * Log an admin action with override flag.
     */
    public AuditLog logAdminAction(String entityType, String entityId, String action,
                                   String description, boolean isOverride) {
        return logEvent(AuditLogBuilder.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .description(description)
                .isAdminOverride(isOverride)
                .build());
    }

    /**
     * Log a failed operation (with error tracking).
     */
    public AuditLog logError(String entityType, String entityId, String action,
                             String errorMessage, Throwable exception) {
        AuditLogBuilder builder = AuditLogBuilder.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .errorMessage(errorMessage);

        if (exception != null) {
            builder.stackTrace(getStackTrace(exception).substring(0, Math.min(5000, getStackTrace(exception).length())));
        }

        return logEvent(builder.build());
    }

    /**
     * Core method to create and save audit log with extracted context.
     */
    public AuditLog logEvent(AuditLogBuilder builder) {
        log.info("Logging audit event - Entity: {}, Action: {}, Entity ID: {}", 
                builder.entityType, builder.action, builder.entityId);

        // Extract user context
        extractUserContext(builder);

        // Extract request context (if in HTTP request)
        extractRequestContext(builder);

        // Set timestamp if not already set
        if (builder.timestamp == null) {
            builder.timestamp = Instant.now();
        }

        AuditLog auditLog = AuditLog.builder()
                .entityType(builder.entityType)
                .entityId(builder.entityId)
                .action(builder.action)
                .userId(builder.userId)
                .username(builder.username)
                .userRole(builder.userRole)
                .previousValue(builder.previousValue)
                .newValue(builder.newValue)
                .description(builder.description)
                .metadata(builder.metadata)
                .source(builder.source)
                .ipAddress(builder.ipAddress)
                .correlationId(builder.correlationId)
                .httpMethod(builder.httpMethod)
                .endpoint(builder.endpoint)
                .httpStatus(builder.httpStatus)
                .timestamp(builder.timestamp)
                .errorMessage(builder.errorMessage)
                .stackTrace(builder.stackTrace)
                .durationMillis(builder.durationMillis)
                .isAdminOverride(builder.isAdminOverride)
                .relatedEntityId(builder.relatedEntityId)
                .relatedEntityType(builder.relatedEntityType)
                .build();

        AuditLog savedLog = auditLogRepository.save(auditLog);
        log.debug("Audit event logged successfully - ID: {}", savedLog.getId());
        return savedLog;
    }

    /**
     * Extract user context from Spring Security.
     */
    private void extractUserContext(AuditLogBuilder builder) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                builder.userId(authentication.getName());
                builder.username(authentication.getName());
                
                // Extract user role from authorities
                if (authentication.getAuthorities() != null && !authentication.getAuthorities().isEmpty()) {
                    String role = authentication.getAuthorities().stream()
                            .findFirst()
                            .map(auth -> auth.getAuthority().replace("ROLE_", ""))
                            .orElse("USER");
                    builder.userRole(role);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract user context: {}", e.getMessage());
        }
    }

    /**
     * Extract HTTP request context if available.
     */
    private void extractRequestContext(AuditLogBuilder builder) {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                
                builder.httpMethod(request.getMethod());
                builder.endpoint(request.getRequestURI());
                builder.ipAddress(getClientIpAddress(request));
                builder.source("API");
                
                // Try to get correlation ID from headers
                String correlationId = request.getHeader("X-Correlation-ID");
                if (correlationId != null) {
                    builder.correlationId(correlationId);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract request context: {}", e.getMessage());
        }
    }

    /**
     * Get client IP address from request (accounting for proxies).
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String[] headers = {"X-Forwarded-For", "Proxy-Client-IP", "WL-Proxy-Client-IP", "HTTP_CLIENT_IP", "HTTP_X_FORWARDED_FOR"};
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    /**
     * Convert exception to string stack trace.
     */
    private String getStackTrace(Throwable exception) {
        StringBuilder sb = new StringBuilder();
        sb.append(exception.getClass().getName()).append(": ").append(exception.getMessage()).append("\n");
        for (StackTraceElement element : exception.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
        }
        return sb.toString();
    }

    /**
     * Query audit logs for an entity.
     */
    public org.springframework.data.domain.Page<AuditLog> getAuditLogsForEntity(String entityType, String entityId, 
                                                                                 org.springframework.data.domain.Pageable pageable) {
        return auditLogRepository.findByEntityTypeAndEntityId(entityType, entityId, pageable);
    }

    /**
     * Query audit logs for a specific user.
     */
    public org.springframework.data.domain.Page<AuditLog> getAuditLogsForUser(String userId, 
                                                                              org.springframework.data.domain.Pageable pageable) {
        return auditLogRepository.findByUserId(userId, pageable);
    }

    /**
     * Get all failed operations (errors).
     */
    public org.springframework.data.domain.Page<AuditLog> getFailedOperations(org.springframework.data.domain.Pageable pageable) {
        return auditLogRepository.findFailedOperations(pageable);
    }

    /**
     * Get all admin overrides with optional filter by admin.
     */
    public org.springframework.data.domain.Page<AuditLog> getAdminOverrides(String adminId, 
                                                                            org.springframework.data.domain.Pageable pageable) {
        if (adminId != null) {
            return auditLogRepository.findAdminOverridesByAdmin(adminId, pageable);
        }
        return auditLogRepository.findAdminOverrides(pageable);
    }

    /**
     * Get audit logs by action type.
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<AuditLog> getAuditLogsByAction(String action, 
                                                                               org.springframework.data.domain.Pageable pageable) {
        return auditLogRepository.findByAction(action, pageable);
    }

    /**
     * Get a single audit log by ID.
     */
    @Transactional(readOnly = true)
    public AuditLog getAuditLogById(String id) {
        try {
            return auditLogRepository.findById(UUID.fromString(id)).orElse(null);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Get audit statistics (counts and summaries).
     */
    @Transactional(readOnly = true)
    public AuditControllerStats getAuditStats() {
        long totalEvents = auditLogRepository.count();
        long failedOps = auditLogRepository.countFailedOperations();
        long adminOverrides = auditLogRepository.countAdminOverrides();

        // TODO: Implement additional statistics queries as needed
        java.util.Map<String, Long> byEntity = new java.util.HashMap<>();
        java.util.Map<String, Long> byAction = new java.util.HashMap<>();

        return new AuditControllerStats(totalEvents, failedOps, adminOverrides, byEntity, byAction);
    }

    /**
     * DTO for audit statistics.
     */
    public static class AuditControllerStats {
        public long totalEvents;
        public long failedOperations;
        public long adminOverrides;
        public java.util.Map<String, Long> eventsByEntity;
        public java.util.Map<String, Long> eventsByAction;

        public AuditControllerStats(long totalEvents, long failedOperations, long adminOverrides,
                                   java.util.Map<String, Long> eventsByEntity, java.util.Map<String, Long> eventsByAction) {
            this.totalEvents = totalEvents;
            this.failedOperations = failedOperations;
            this.adminOverrides = adminOverrides;
            this.eventsByEntity = eventsByEntity;
            this.eventsByAction = eventsByAction;
        }
    }

    /**
     * Builder pattern for flexible audit log creation.
     */
    public static class AuditLogBuilder {
        private String entityType;
        private String entityId;
        private String action;
        private String userId;
        private String username;
        private String userRole;
        private String previousValue;
        private String newValue;
        private String description;
        private String metadata;
        private String source;
        private String ipAddress;
        private String correlationId;
        private String httpMethod;
        private String endpoint;
        private String httpStatus;
        private Instant timestamp;
        private String errorMessage;
        private String stackTrace;
        private Long durationMillis;
        private Boolean isAdminOverride = false;
        private String relatedEntityId;
        private String relatedEntityType;

        private AuditLogBuilder() {}

        public static AuditLogBuilder builder() {
            return new AuditLogBuilder();
        }

        public AuditLogBuilder entityType(String entityType) { this.entityType = entityType; return this; }
        public AuditLogBuilder entityId(String entityId) { this.entityId = entityId; return this; }
        public AuditLogBuilder action(String action) { this.action = action; return this; }
        public AuditLogBuilder userId(String userId) { this.userId = userId; return this; }
        public AuditLogBuilder username(String username) { this.username = username; return this; }
        public AuditLogBuilder userRole(String userRole) { this.userRole = userRole; return this; }
        public AuditLogBuilder previousValue(String previousValue) { this.previousValue = previousValue; return this; }
        public AuditLogBuilder newValue(String newValue) { this.newValue = newValue; return this; }
        public AuditLogBuilder description(String description) { this.description = description; return this; }
        public AuditLogBuilder metadata(String metadata) { this.metadata = metadata; return this; }
        public AuditLogBuilder source(String source) { this.source = source; return this; }
        public AuditLogBuilder ipAddress(String ipAddress) { this.ipAddress = ipAddress; return this; }
        public AuditLogBuilder correlationId(String correlationId) { this.correlationId = correlationId; return this; }
        public AuditLogBuilder httpMethod(String httpMethod) { this.httpMethod = httpMethod; return this; }
        public AuditLogBuilder endpoint(String endpoint) { this.endpoint = endpoint; return this; }
        public AuditLogBuilder httpStatus(String httpStatus) { this.httpStatus = httpStatus; return this; }
        public AuditLogBuilder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
        public AuditLogBuilder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }
        public AuditLogBuilder stackTrace(String stackTrace) { this.stackTrace = stackTrace; return this; }
        public AuditLogBuilder durationMillis(Long durationMillis) { this.durationMillis = durationMillis; return this; }
        public AuditLogBuilder isAdminOverride(Boolean isAdminOverride) { this.isAdminOverride = isAdminOverride; return this; }
        public AuditLogBuilder relatedEntityId(String relatedEntityId) { this.relatedEntityId = relatedEntityId; return this; }
        public AuditLogBuilder relatedEntityType(String relatedEntityType) { this.relatedEntityType = relatedEntityType; return this; }

        public AuditLogBuilder build() {
            return this;
        }
    }
}
