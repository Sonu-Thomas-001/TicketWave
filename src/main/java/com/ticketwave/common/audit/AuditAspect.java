package com.ticketwave.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * AuditAspect provides AOP-based audit logging for methods marked with @Auditable.
 * Automatically captures method execution context and logs before/after behavior.
 * 
 * Usage:
 * @Auditable(entityType = "Booking", action = "CREATE")
 * public Booking createBooking(...) { ... }
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    /**
     * Intercept methods marked with @Auditable annotation.
     */
    @Around("@annotation(auditable)")
    public Object auditMethodExecution(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        String entityType = auditable.entityType();
        String action = auditable.action();
        
        long startTime = System.currentTimeMillis();
        Object result = null;
        Throwable exception = null;

        try {
            log.debug("Executing auditable method - Entity: {}, Action: {}, Method: {}", 
                    entityType, action, methodName);

            // Execute the actual method
            result = joinPoint.proceed();

            long duration = System.currentTimeMillis() - startTime;
            
            // Extract entity ID from result
            String entityId = extractEntityId(result);

            // Build and log audit event
            AuditService.AuditLogBuilder builder = AuditService.AuditLogBuilder.builder()
                    .entityType(entityType)
                    .entityId(entityId)
                    .action(action)
                    .description("Method: " + methodName + " executed successfully")
                    .durationMillis(duration)
                    .source("METHOD_EXECUTION");

            if (auditable.captureParameters()) {
                builder.metadata(captureParameters(joinPoint));
            }

            auditService.logEvent(builder.build());

            return result;

        } catch (Throwable e) {
            exception = e;
            long duration = System.currentTimeMillis() - startTime;

            // Extract entity ID from method arguments if possible
            String entityId = extractEntityIdFromArguments(joinPoint);

            auditService.logError(
                    entityType,
                    entityId,
                    action,
                    "Method execution failed: " + e.getClass().getSimpleName() + " - " + e.getMessage(),
                    auditable.captureStackTrace() ? e : null
            );

            throw e;
        }
    }

    /**
     * Extract entity ID from method result.
     */
    private String extractEntityId(Object result) {
        if (result == null) {
            return null;
        }

        try {
            // Try to get ID field from result object
            if (result.getClass().getName().contains("com.ticketwave")) {
                Object id = result.getClass().getMethod("getId").invoke(result);
                if (id instanceof UUID) {
                    return ((UUID) id).toString();
                }
                return String.valueOf(id);
            }
        } catch (Exception e) {
            log.debug("Could not extract ID from result: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Extract entity ID from method arguments (first UUID or Long argument).
     */
    private String extractEntityIdFromArguments(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        for (Object arg : args) {
            if (arg instanceof UUID) {
                return ((UUID) arg).toString();
            }
            if (arg instanceof Long) {
                return String.valueOf(arg);
            }
            if (arg instanceof String) {
                try {
                    UUID.fromString((String) arg);
                    return (String) arg;
                } catch (IllegalArgumentException e) {
                    // Not a UUID, skip
                }
            }
        }
        return null;
    }

    /**
     * Capture method parameters as JSON for audit trail.
     */
    private String captureParameters(ProceedingJoinPoint joinPoint) {
        try {
            Object[] args = joinPoint.getArgs();
            if (args.length == 0) {
                return null;
            }

            // Don't serialize sensitive parameters like passwords
            StringBuilder params = new StringBuilder();
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                if (arg != null && !isSensitiveParameter(arg)) {
                    params.append("arg").append(i).append("=");
                    try {
                        params.append(objectMapper.writeValueAsString(arg).substring(0, 500)); // Limit size
                    } catch (Exception e) {
                        params.append(arg.getClass().getSimpleName());
                    }
                    params.append("; ");
                }
            }
            return params.toString();
        } catch (Exception e) {
            log.debug("Could not capture parameters: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Check if parameter is sensitive (e.g., contains password, token, credit card).
     */
    private boolean isSensitiveParameter(Object arg) {
        if (arg == null) {
            return true;
        }
        
        String className = arg.getClass().getSimpleName().toLowerCase();
        return className.contains("password") || 
               className.contains("token") || 
               className.contains("credential") ||
               className.contains("secret");
    }
}
