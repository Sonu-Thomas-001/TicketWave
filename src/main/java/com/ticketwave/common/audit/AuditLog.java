package com.ticketwave.common.audit;

import com.ticketwave.common.domain.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * AuditLog records all significant events in the system for compliance and debugging.
 * Captures user actions, entity changes, admin operations, and security events.
 */
@Entity
@Table(
        name = "audit_logs",
        indexes = {
                @Index(name = "idx_audit_logs_entity_id", columnList = "entity_id"),
                @Index(name = "idx_audit_logs_entity_type", columnList = "entity_type"),
                @Index(name = "idx_audit_logs_user_id", columnList = "user_id"),
                @Index(name = "idx_audit_logs_action", columnList = "action"),
                @Index(name = "idx_audit_logs_created_at", columnList = "created_at"),
                @Index(name = "idx_audit_logs_timestamp", columnList = "timestamp")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog extends AuditedEntity {

    /**
     * Entity type being audited (e.g., "Booking", "Payment", "Refund", "User").
     */
    @Column(nullable = false, length = 50)
    private String entityType;

    /**
     * ID of the entity being modified/accessed.
     */
    @Column(nullable = false, length = 50)
    private String entityId;

    /**
     * Action performed (CREATE, UPDATE, DELETE, APPROVE, REJECT, PROCESS, CANCEL, etc.).
     */
    @Column(nullable = false, length = 50)
    private String action;

    /**
     * User ID who performed the action (from JWT principal or security context).
     */
    @Column(length = 50)
    private String userId;

    /**
     * Username/email of the user (for readability).
     */
    @Column(length = 100)
    private String username;

    /**
     * User's role when action was performed (ADMIN, USER, CUSTOMER, etc.).
     */
    @Column(length = 50)
    private String userRole;

    /**
     * Status before change (for UPDATE operations).
     * Example: "PENDING" → "CONFIRMED"
     */
    @Column(length = 200)
    private String previousValue;

    /**
     * Status/value after change (for UPDATE operations).
     */
    @Column(length = 200)
    private String newValue;

    /**
     * Detailed description of what happened.
     * Example: "Booking cancelled due to customer request"
     */
    @Column(length = 1000)
    private String description;

    /**
     * Additional metadata (JSON format for complex data).
     * Can include request parameters, calculation details, etc.
     */
    @Column(columnDefinition = "TEXT")
    private String metadata;

    /**
     * Source of the action (API, BATCH_JOB, SCHEDULED, ADMIN_CONSOLE, etc.).
     */
    @Column(length = 50)
    private String source;

    /**
     * IP address of the requester (if API call).
     */
    @Column(length = 50)
    private String ipAddress;

    /**
     * Request ID/correlation ID for tracking requests across services.
     */
    @Column(length = 100)
    private String correlationId;

    /**
     * HTTP method if API (GET, POST, PUT, DELETE, etc.).
     */
    @Column(length = 10)
    private String httpMethod;

    /**
     * HTTP endpoint/path that was called.
     */
    @Column(length = 500)
    private String endpoint;

    /**
     * HTTP status code of the response.
     */
    @Column(length = 5)
    private String httpStatus;

    /**
     * Time the action was performed (can differ from created_at for async operations).
     */
    @Column(nullable = false)
    private Instant timestamp;

    /**
     * Only set if action failed - error message/reason.
     */
    @Column(length = 500)
    private String errorMessage;

    /**
     * Stack trace if exception occurred (truncated to prevent huge storage).
     */
    @Column(columnDefinition = "TEXT")
    private String stackTrace;

    /**
     * Duration in milliseconds (for performance monitoring).
     */
    @Column
    private Long durationMillis;

    /**
     * Whether this operation had admin override applied.
     */
    @Column(nullable = false)
    private Boolean isAdminOverride = false;

    /**
     * Related entity ID (e.g., booking ID when auditing a refund).
     */
    @Column(length = 50)
    private String relatedEntityId;

    /**
     * Related entity type (e.g., "Booking" when auditing refund for a booking).
     */
    @Column(length = 50)
    private String relatedEntityType;
}
