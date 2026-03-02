package com.ticketwave.common.audit;

import jakarta.persistence.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Field;
import java.util.*;

/**
 * JPA Entity Lifecycle Listener for automatic audit logging.
 * 
 * Automatically logs entity creation, update, and deletion events.
 * Respects @AuditableEntity annotation on entity classes.
 * Tracks field-level changes via @Version or manual tracking.
 * 
 * Note: This listener must be registered with a persistence provider or
 * EntityListenerRegistry in Spring Data JPA configuration.
 */
@Component
public class EntityAuditListener {

    private static final Logger logger = LoggerFactory.getLogger(EntityAuditListener.class);

    private final AuditService auditService;

    // Store previous state for change tracking
    private final Map<String, Map<String, Object>> entityStateMap = new WeakHashMap<>();

    public EntityAuditListener(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * Called after an entity is inserted into the database.
     * Logs entity creation events.
     */
    @PostPersist
    public void logEntityCreation(Object entity) {
        if (!isAuditable(entity)) {
            return;
        }

        try {
            String entityType = getEntityType(entity);
            String entityId = getEntityId(entity);

            AuditService.AuditLogBuilder builder = AuditService.AuditLogBuilder.builder()
                    .entityType(entityType)
                    .entityId(entityId)
                    .action("CREATE")
                    .description(String.format("%s created", entityType));

            logger.debug("Logging entity creation: {} with ID: {}", entityType, entityId);
            auditService.logEvent(builder.build());
        } catch (Exception e) {
            logger.warn("Failed to log entity creation", e);
            // Non-fatal: don't fail the business operation
        }
    }

    /**
     * Called after an entity is updated in the database.
     * Tracks field-level changes and logs status transitions.
     */
    @PostUpdate
    public void logEntityUpdate(Object entity) {
        if (!isAuditable(entity)) {
            return;
        }

        try {
            String entityType = getEntityType(entity);
            String entityId = getEntityId(entity);

            // Detect if this is a status change
            String previousStatus = getPreviousStatus(entity);
            String currentStatus = getCurrentStatus(entity);

            if (previousStatus != null && currentStatus != null && !previousStatus.equals(currentStatus)) {
                // Status transition - log with specific event
                String action = determineAction(previousStatus, currentStatus);

                AuditService.AuditLogBuilder builder = AuditService.AuditLogBuilder.builder()
                        .entityType(entityType)
                        .entityId(entityId)
                        .action(action)
                        .previousValue(previousStatus)
                        .newValue(currentStatus)
                        .description(String.format("%s status changed from %s to %s",
                                entityType, previousStatus, currentStatus));

                logger.debug("Logging status change: {} {} -> {}", entityType, previousStatus, currentStatus);
                auditService.logEvent(builder.build());
            } else {
                // Generic update
                AuditService.AuditLogBuilder builder = AuditService.AuditLogBuilder.builder()
                        .entityType(entityType)
                        .entityId(entityId)
                        .action("UPDATE")
                        .description(String.format("%s updated", entityType));

                logger.debug("Logging entity update: {} with ID: {}", entityType, entityId);
                auditService.logEvent(builder.build());
            }
        } catch (Exception e) {
            logger.warn("Failed to log entity update", e);
            // Non-fatal: don't fail the business operation
        }
    }

    /**
     * Called before an entity is deleted from the database.
     * Logs entity deletion events.
     */
    @PreRemove
    public void logEntityDeletion(Object entity) {
        if (!isAuditable(entity)) {
            return;
        }

        try {
            String entityType = getEntityType(entity);
            String entityId = getEntityId(entity);

            AuditService.AuditLogBuilder builder = AuditService.AuditLogBuilder.builder()
                    .entityType(entityType)
                    .entityId(entityId)
                    .action("DELETE")
                    .description(String.format("%s deleted", entityType));

            logger.debug("Logging entity deletion: {} with ID: {}", entityType, entityId);
            auditService.logEvent(builder.build());
        } catch (Exception e) {
            logger.warn("Failed to log entity deletion", e);
            // Non-fatal: don't fail the business operation
        }
    }

    /**
     * Determines if an entity should be audited based on @AuditableEntity annotation.
     */
    private boolean isAuditable(Object entity) {
        if (entity == null) {
            return false;
        }
        return entity.getClass().isAnnotationPresent(AuditableEntity.class);
    }

    /**
     * Extracts entity type from @AuditableEntity annotation or class name.
     */
    private String getEntityType(Object entity) {
        AuditableEntity annotation = entity.getClass().getAnnotation(AuditableEntity.class);
        if (annotation != null && !annotation.entityType().isEmpty()) {
            return annotation.entityType();
        }
        return entity.getClass().getSimpleName();
    }

    /**
     * Extracts entity ID (primary key) from the entity.
     * Looks for @Id field or common field names (id, uuid, pk).
     */
    private String getEntityId(Object entity) {
        try {
            // Try @Id annotated field first
            for (Field field : entity.getClass().getDeclaredFields()) {
                if (field.isAnnotationPresent(Id.class)) {
                    field.setAccessible(true);
                    Object value = field.get(entity);
                    return value != null ? value.toString() : null;
                }
            }

            // Fallback to common field names
            for (String fieldName : Arrays.asList("id", "uuid", "pk", "primaryKey")) {
                try {
                    Field field = entity.getClass().getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Object value = field.get(entity);
                    return value != null ? value.toString() : null;
                } catch (NoSuchFieldException e) {
                    // Continue to next fallback
                }
            }

            return null;
        } catch (Exception e) {
            logger.debug("Could not extract entity ID", e);
            return null;
        }
    }

    /**
     * Attempts to extract the previous entity state from context.
     * Only available if state was stored before update.
     */
    private String getPreviousStatus(Object entity) {
        try {
            String entityId = getEntityId(entity);
            if (entityId == null) {
                return null;
            }

            Map<String, Object> state = entityStateMap.get(entityId);
            if (state != null) {
                Object status = state.get("status");
                return status != null ? status.toString() : null;
            }

            return null;
        } catch (Exception e) {
            logger.debug("Could not extract previous status", e);
            return null;
        }
    }

    /**
     * Extracts current status from entity (if it has a status field).
     * Looks for fields named: status, state, statusCode, statusName.
     */
    private String getCurrentStatus(Object entity) {
        try {
            for (String fieldName : Arrays.asList("status", "state", "statusCode", "statusName")) {
                try {
                    Field field = entity.getClass().getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Object value = field.get(entity);
                    return value != null ? value.toString() : null;
                } catch (NoSuchFieldException e) {
                    // Continue to next fallback
                }
            }

            return null;
        } catch (Exception e) {
            logger.debug("Could not extract current status", e);
            return null;
        }
    }

    /**
     * Determines the action name based on status transition.
     * Examples: PENDING->CONFIRMED = "CONFIRM", ACTIVE->CANCELLED = "CANCEL"
     */
    private String determineAction(String previousStatus, String currentStatus) {
        // Map common transitions to action names
        Map<String, Map<String, String>> transitions = new HashMap<>();
        
        // Booking transitions
        Map<String, String> bookingTransitions = new HashMap<>();
        bookingTransitions.put("PENDING", "CONFIRM");
        bookingTransitions.put("CONFIRMED", "CANCEL");
        bookingTransitions.put("CANCELLED", "");
        transitions.put("Booking", bookingTransitions);

        // Payment transitions
        Map<String, String> paymentTransitions = new HashMap<>();
        paymentTransitions.put("PENDING", "PROCESS");
        paymentTransitions.put("PROCESSING", "CONFIRM");
        paymentTransitions.put("CONFIRMED", "COMPLETE");
        transitions.put("Payment", paymentTransitions);

        // Refund transitions
        Map<String, String> refundTransitions = new HashMap<>();
        refundTransitions.put("PENDING", "APPROVE");
        refundTransitions.put("APPROVED", "PROCESS");
        refundTransitions.put("PROCESSED", "COMPLETE");
        transitions.put("Refund", refundTransitions);

        // Generic action: extract from status name
        // e.g., PENDING->CONFIRMED becomes "CONFIRM"
        String action = currentStatus.replaceAll("ED$", "").toUpperCase();
        if (action.length() > 30) {
            action = "TRANSITION";
        }

        return action;
    }

    /**
     * Stores the previous entity state for change tracking.
     * Called before persistence operations to capture current state.
     *
     * Note: This method should be called explicitly as JPA doesn't provide
     * a standard hook for pre-persistence state snapshots.
     */
    public void captureEntityState(Object entity) {
        try {
            String entityId = getEntityId(entity);
            if (entityId != null) {
                Map<String, Object> state = new HashMap<>();
                state.put("status", getCurrentStatus(entity));
                entityStateMap.put(entityId, state);
            }
        } catch (Exception e) {
            logger.debug("Could not capture entity state", e);
        }
    }

    /**
     * Clears captured entity state (for cleanup).
     */
    public void clearEntityState(Object entity) {
        try {
            String entityId = getEntityId(entity);
            if (entityId != null) {
                entityStateMap.remove(entityId);
            }
        } catch (Exception e) {
            logger.debug("Could not clear entity state", e);
        }
    }
}
