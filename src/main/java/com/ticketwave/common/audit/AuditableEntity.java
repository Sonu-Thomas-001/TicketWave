package com.ticketwave.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark entities that should have automatic JPA lifecycle audit logging.
 * Applied to entities to enable pre-update/pre-delete listeners.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditableEntity {

    /**
     * Custom entity type name (if different from class name).
     */
    String entityType() default "";

    /**
     * Whether to exclude certain fields from audit (e.g., password, token).
     */
    String[] excludeFields() default {};
}
