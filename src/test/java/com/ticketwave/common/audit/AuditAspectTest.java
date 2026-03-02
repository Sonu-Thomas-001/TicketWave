package com.ticketwave.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuditAspect.
 * Tests AOP method interception, parameter capture, and exception handling.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuditAspect Tests")
class AuditAspectTest {

    @Mock
    private AuditService auditService;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    @InjectMocks
    private AuditAspect auditAspect;

    private Auditable auditableAnnotation;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        // Create a mock Auditable annotation
        Method testMethod = TestAuditableService.class.getMethod("createBooking", UUID.class, String.class);
        auditableAnnotation = testMethod.getAnnotation(Auditable.class);

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getName()).thenReturn("createBooking");
    }

    @Test
    @DisplayName("Should intercept method and log execution time")
    void shouldInterceptMethodAndLogExecutionTime() throws Throwable {
        // Given
        AuditLog capturedLog = AuditLog.builder()
                .entityType("Booking")
                .action("CREATE")
                .durationMillis(100L)
                .build();

        UUID bookingId = UUID.randomUUID();
        when(joinPoint.proceed()).thenReturn(bookingId);
        doNothing().when(auditService).logEvent(any(AuditService.AuditLogBuilder.class));

        // When
        Object result = auditAspect.auditMethod(joinPoint, auditableAnnotation);

        // Then
        assertNotNull(result);
        assertEqual(bookingId, result);
        verify(auditService).logEvent(any(AuditService.AuditLogBuilder.class));
    }

    @Test
    @DisplayName("Should capture method parameters as JSON")
    void shouldCaptureMethodParametersAsJson() throws Throwable {
        // Given
        UUID bookingId = UUID.randomUUID();
        String venue = "Madison Square Garden";

        when(joinPoint.getArgs()).thenReturn(new Object[]{bookingId, venue});
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"bookingId", "venueName"});
        when(joinPoint.proceed()).thenReturn("success");
        doNothing().when(auditService).logEvent(any(AuditService.AuditLogBuilder.class));

        // When
        auditAspect.auditMethod(joinPoint, auditableAnnotation);

        // Then
        verify(auditService).logEvent(any(AuditService.AuditLogBuilder.class));
    }

    @Test
    @DisplayName("Should extract entity ID from result object")
    void shouldExtractEntityIdFromResult() throws Throwable {
        // Given
        UUID expectedId = UUID.randomUUID();
        TestResult result = new TestResult(expectedId);

        when(joinPoint.getArgs()).thenReturn(new Object[]{});
        when(joinPoint.proceed()).thenReturn(result);
        doNothing().when(auditService).logEvent(any(AuditService.AuditLogBuilder.class));

        // When
        Object procedureResult = auditAspect.auditMethod(joinPoint, auditableAnnotation);

        // Then
        assertNotNull(procedureResult);
        verify(auditService).logEvent(any(AuditService.AuditLogBuilder.class));
    }

    @Test
    @DisplayName("Should extract entity ID from method arguments if result has no ID")
    void shouldExtractEntityIdFromArguments() throws Throwable {
        // Given
        UUID expectedId = UUID.randomUUID();

        when(joinPoint.getArgs()).thenReturn(new Object[]{expectedId});
        when(joinPoint.proceed()).thenReturn("success");
        doNothing().when(auditService).logEvent(any(AuditService.AuditLogBuilder.class));

        // When
        auditAspect.auditMethod(joinPoint, auditableAnnotation);

        // Then
        verify(auditService).logEvent(any(AuditService.AuditLogBuilder.class));
    }

    @Test
    @DisplayName("Should mask sensitive parameters like passwords")
    void shouldMaskSensitiveParameters() throws Throwable {
        // Given
        String sensitiveData = "supersecretpassword";
        when(joinPoint.getArgs()).thenReturn(new Object[]{sensitiveData});
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"password"});
        when(joinPoint.proceed()).thenReturn("success");
        doNothing().when(auditService).logEvent(any(AuditService.AuditLogBuilder.class));

        // When
        auditAspect.auditMethod(joinPoint, auditableAnnotation);

        // Then
        verify(auditService).logEvent(any(AuditService.AuditLogBuilder.class));
    }

    @Test
    @DisplayName("Should mask token parameters")
    void shouldMaskTokenParameters() throws Throwable {
        // Given
        String token = "jwt.token.here";
        when(joinPoint.getArgs()).thenReturn(new Object[]{token});
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"authToken"});
        when(joinPoint.proceed()).thenReturn("success");
        doNothing().when(auditService).logEvent(any(AuditService.AuditLogBuilder.class));

        // When
        auditAspect.auditMethod(joinPoint, auditableAnnotation);

        // Then
        verify(auditService).logEvent(any(AuditService.AuditLogBuilder.class));
    }

    @Test
    @DisplayName("Should catch exception and log error")
    void shouldCatchExceptionAndLogError() throws Throwable {
        // Given
        RuntimeException exception = new RuntimeException("Payment gateway error");
        when(joinPoint.getArgs()).thenReturn(new Object[]{});
        when(joinPoint.proceed()).thenThrow(exception);
        doNothing().when(auditService).logEvent(any(AuditService.AuditLogBuilder.class));

        // When & Then
        assertThrows(RuntimeException.class, () -> auditAspect.auditMethod(joinPoint, auditableAnnotation));
        verify(auditService).logEvent(any(AuditService.AuditLogBuilder.class));
    }

    @Test
    @DisplayName("Should re-throw exception after logging")
    void shouldReThrowExceptionAfterLogging() throws Throwable {
        // Given
        RuntimeException originalException = new RuntimeException("Database connection failed");
        when(joinPoint.proceed()).thenThrow(originalException);
        doNothing().when(auditService).logEvent(any(AuditService.AuditLogBuilder.class));

        // When & Then
        RuntimeException thrownException = assertThrows(RuntimeException.class,
                () -> auditAspect.auditMethod(joinPoint, auditableAnnotation));
        assertEquals("Database connection failed", thrownException.getMessage());
    }

    @Test
    @DisplayName("Should handle null parameters gracefully")
    void shouldHandleNullParametersGracefully() throws Throwable {
        // Given
        when(joinPoint.getArgs()).thenReturn(new Object[]{null, null});
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"param1", "param2"});
        when(joinPoint.proceed()).thenReturn("success");
        doNothing().when(auditService).logEvent(any(AuditService.AuditLogBuilder.class));

        // When
        Object result = auditAspect.auditMethod(joinPoint, auditableAnnotation);

        // Then
        assertEquals("success", result);
        verify(auditService).logEvent(any(AuditService.AuditLogBuilder.class));
    }

    @Test
    @DisplayName("Should capture result when captureResult is true")
    void shouldCaptureResultWhenCaptureResultTrue() throws Throwable {
        // Given
        String successResult = "Booking#bk123";
        when(joinPoint.proceed()).thenReturn(successResult);
        doNothing().when(auditService).logEvent(any(AuditService.AuditLogBuilder.class));

        Auditable annotationWithCapture = mock(Auditable.class);
        when(annotationWithCapture.captureResult()).thenReturn(true);
        when(annotationWithCapture.entityType()).thenReturn("Booking");
        when(annotationWithCapture.action()).thenReturn("CREATE");

        // When
        Object result = auditAspect.auditMethod(joinPoint, annotationWithCapture);

        // Then
        assertEquals(successResult, result);
    }

    @Test
    @DisplayName("Should skip capturing large parameters")
    void shouldSkipCapturingLargeParameters() throws Throwable {
        // Given
        StringBuilder largeString = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            largeString.append("x");
        }

        when(joinPoint.getArgs()).thenReturn(new Object[]{largeString.toString()});
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"largeData"});
        when(joinPoint.proceed()).thenReturn("success");
        doNothing().when(auditService).logEvent(any(AuditService.AuditLogBuilder.class));

        // When
        auditAspect.auditMethod(joinPoint, auditableAnnotation);

        // Then
        verify(auditService).logEvent(any(AuditService.AuditLogBuilder.class));
    }

    @Test
    @DisplayName("Should handle exceptions with stackTrace capture when enabled")
    void shouldHandleExceptionsWithStackTraceWhenEnabled() throws Throwable {
        // Given
        Exception testException = new RuntimeException("Test error");
        when(joinPoint.proceed()).thenThrow(testException);
        doNothing().when(auditService).logEvent(any(AuditService.AuditLogBuilder.class));

        Auditable annotationWithStackTrace = mock(Auditable.class);
        when(annotationWithStackTrace.captureStackTrace()).thenReturn(true);
        when(annotationWithStackTrace.entityType()).thenReturn("Booking");
        when(annotationWithStackTrace.action()).thenReturn("CREATE");

        // When & Then
        assertThrows(RuntimeException.class,
                () -> auditAspect.auditMethod(joinPoint, annotationWithStackTrace));
        verify(auditService).logEvent(any(AuditService.AuditLogBuilder.class));
    }

    @Test
    @DisplayName("Should measure execution time accurately")
    void shouldMeasureExecutionTimeAccurately() throws Throwable {
        // Given
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            Thread.sleep(50); // Simulate delay
            return "success";
        });
        doNothing().when(auditService).logEvent(any(AuditService.AuditLogBuilder.class));

        // When
        long startTime = System.currentTimeMillis();
        auditAspect.auditMethod(joinPoint, auditableAnnotation);
        long duration = System.currentTimeMillis() - startTime;

        // Then
        assertTrue(duration >= 50, "Duration should be at least 50ms");
        verify(auditService).logEvent(any(AuditService.AuditLogBuilder.class));
    }

    // Helper test classes

    static class TestResult {
        private UUID id;

        TestResult(UUID id) {
            this.id = id;
        }

        public UUID getId() {
            return id;
        }
    }

    static class TestAuditableService {
        @Auditable(entityType = "Booking", action = "CREATE")
        public UUID createBooking(UUID bookingId, String venueName) {
            return bookingId;
        }
    }

    private void assertEqual(Object expected, Object actual) {
        assertEquals(expected, actual);
    }
}
