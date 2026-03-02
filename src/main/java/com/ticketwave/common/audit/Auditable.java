package com.ticketwave.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark methods/operations that should be audit logged.
 * Used by AuditAspect to automatically capture operation details.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    /**
     * Entity type being operated on (e.g., "Booking", "Payment", "Refund").
     */
    String entityType();

    /**
     * Action being performed (e.g., "CREATE", "APPROVE", "PROCESS").
     */
    String action();

    /**
     * Whether to capture result (useful for query operations).
     */
    boolean captureResult() default true;

    /**
     * Whether to capture parameters (useful for CRUD operations).
     */
    boolean captureParameters() default true;

    /**
     * Whether to capture stack trace on exceptions.
     */
    boolean captureStackTrace() default true;
}
