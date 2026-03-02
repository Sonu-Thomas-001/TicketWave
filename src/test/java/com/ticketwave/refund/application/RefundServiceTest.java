package com.ticketwave.refund.application;

import com.ticketwave.booking.domain.Booking;
import com.ticketwave.booking.infrastructure.BookingRepository;
import com.ticketwave.catalog.domain.Schedule;
import com.ticketwave.common.exception.ResourceNotFoundException;
import com.ticketwave.payment.application.PaymentService;
import com.ticketwave.payment.domain.Payment;
import com.ticketwave.refund.domain.CancellationPolicy;
import com.ticketwave.refund.domain.Refund;
import com.ticketwave.refund.domain.RefundStatus;
import com.ticketwave.refund.infrastructure.CancellationPolicyRepository;
import com.ticketwave.refund.infrastructure.RefundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RefundService.
 * Tests refund business logic including initiation, approval, processing, and admin overrides.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RefundService Tests")
class RefundServiceTest {

    @Mock
    private RefundRepository refundRepository;
    
    @Mock
    private CancellationPolicyRepository policyRepository;
    
    @Mock
    private RefundLedgerService refundLedgerService;
    
    @Mock
    private PaymentService paymentService;
    
    @Mock
    private CancellationPolicyEngine policyEngine;
    
    @Mock
    private BookingRepository bookingRepository;

    @InjectMocks
    private RefundService refundService;

    private Booking mockBooking;
    private Payment mockPayment;
    private CancellationPolicy mockPolicy;
    private Refund mockRefund;
    private Schedule mockSchedule;

    @BeforeEach
    void setUp() {
        // Setup mock objects
        mockSchedule = Schedule.builder()
                .id(UUID.randomUUID())
                .eventType("MOVIE")
                .departureTime(Instant.now().plus(96, ChronoUnit.HOURS))
                .build();

        mockBooking = Booking.builder()
                .id(UUID.randomUUID())
                .pnr("TW123456")
                .totalAmount(BigDecimal.valueOf(1000))
                .schedule(mockSchedule)
                .build();

        mockPayment = Payment.builder()
                .id(UUID.randomUUID())
                .transactionId("TXN-123")
                .amount(BigDecimal.valueOf(1000))
                .paymentStatus("CONFIRMED")
                .booking(mockBooking)
                .build();

        mockPolicy = CancellationPolicy.builder()
                .id(UUID.randomUUID())
                .policyId("POL-STANDARD")
                .eventType("MOVIE")
                .fullRefundWindowHours(72)
                .partialRefundWindowHours(24)
                .partialRefundPercentage(BigDecimal.valueOf(50))
                .processingFeePercentage(BigDecimal.valueOf(2.5))
                .isActive(true)
                .build();

        mockRefund = Refund.builder()
                .id(UUID.randomUUID())
                .refundId("REF-123")
                .booking(mockBooking)
                .payment(mockPayment)
                .refundAmount(BigDecimal.valueOf(975))
                .refundStatus(RefundStatus.INITIATED.name())
                .build();
    }

    @Test
    @DisplayName("Should successfully initiate refund when booking has payment")
    void shouldInitiateRefundSuccessfully() {
        // Given
        mockBooking.getPayments().add(mockPayment);
        CancellationPolicyEngine.RefundCalculation calculation = createMockCalculation();

        when(bookingRepository.findById(mockBooking.getId())).thenReturn(Optional.of(mockBooking));
        when(policyRepository.findActiveByEventType("MOVIE", Instant.now()))
                .thenReturn(List.of(mockPolicy));
        when(policyEngine.calculateRefund(any(), any(), any(), any()))
                .thenReturn(calculation);
        when(refundRepository.save(any(Refund.class))).thenReturn(mockRefund);

        // When
        Refund result = refundService.initiateRefund(mockBooking.getId(), "Customer request");

        // Then
        assertNotNull(result);
        assertEquals(RefundStatus.INITIATED.name(), result.getRefundStatus());
        assertEquals(BigDecimal.valueOf(975), result.getRefundAmount());
        verify(refundRepository).save(any(Refund.class));
        verify(refundLedgerService, times(4)).createLedgerEntry(any()); // 4 entries min
    }

    @Test
    @DisplayName("Should throw NotFoundException when booking not found")
    void shouldThrowNotFoundExceptionWhenBookingNotFound() {
        // Given
        when(bookingRepository.findById(any())).thenReturn(Optional.empty());

        // When & Then
        assertThrows(ResourceNotFoundException.class, () -> 
                refundService.initiateRefund(UUID.randomUUID(), "Test"));
    }

    @Test
    @DisplayName("Should throw exception when no payment found for booking")
    void shouldThrowExceptionWhenNoPayment() {
        // Given
        when(bookingRepository.findById(mockBooking.getId())).thenReturn(Optional.of(mockBooking));

        // When & Then
        assertThrows(IllegalStateException.class, () -> 
                refundService.initiateRefund(mockBooking.getId(), "Test"));
    }

    @Test
    @DisplayName("Should approve refund when in INITIATED status")
    void shouldApproveRefundFromInitiated() {
        // Given
        Refund refundToApprove = Refund.builder()
                .id(UUID.randomUUID())
                .refundStatus(RefundStatus.INITIATED.name())
                .refundAmount(BigDecimal.valueOf(500))
                .build();

        when(refundRepository.findById(refundToApprove.getId())).thenReturn(Optional.of(refundToApprove));
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        Refund result = refundService.approveRefund(refundToApprove.getId());

        // Then
        assertEquals(RefundStatus.APPROVED.name(), result.getRefundStatus());
    }

    @Test
    @DisplayName("Should reject refund when in INITIATED status")
    void shouldRejectRefundFromInitiated() {
        // Given
        Refund refundToReject = Refund.builder()
                .id(UUID.randomUUID())
                .refundStatus(RefundStatus.INITIATED.name())
                .build();

        when(refundRepository.findById(refundToReject.getId())).thenReturn(Optional.of(refundToReject));
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        Refund result = refundService.rejectRefund(refundToReject.getId(), "Policy violation");

        // Then
        assertEquals(RefundStatus.REJECTED.name(), result.getRefundStatus());
    }

    @Test
    @DisplayName("Should process refund and transition to PROCESSING status")
    void shouldProcessRefundSuccessfully() {
        // Given
        Refund refundToProcess = Refund.builder()
                .id(UUID.randomUUID())
                .refundStatus(RefundStatus.APPROVED.name())
                .build();

        when(refundRepository.findById(refundToProcess.getId())).thenReturn(Optional.of(refundToProcess));
        when(refundRepository.countByRefundStatus(RefundStatus.PROCESSING.name())).thenReturn(50L);
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        Refund result = refundService.processRefund(refundToProcess.getId());

        // Then
        assertEquals(RefundStatus.PROCESSING.name(), result.getRefundStatus());
    }

    @Test
    @DisplayName("Should complete refund and mark payment as refunded")
    void shouldCompleteRefundSuccessfully() {
        // Given
        Refund refundToComplete = Refund.builder()
                .id(UUID.randomUUID())
                .refundStatus(RefundStatus.PROCESSING.name())
                .payment(mockPayment)
                .refundAmount(BigDecimal.valueOf(975))
                .build();

        when(refundRepository.findById(refundToComplete.getId()))
                .thenReturn(Optional.of(refundToComplete));
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        Refund result = refundService.completeRefund(refundToComplete.getId(), "GW-REF-12345");

        // Then
        assertEquals(RefundStatus.COMPLETED.name(), result.getRefundStatus());
        assertNotNull(result.getProcessedAt());
        verify(paymentService).refundPayment(mockPayment.getId(), BigDecimal.valueOf(975));
    }

    @Test
    @DisplayName("Should fail refund when in PROCESSING status")
    void shouldFailRefundFromProcessing() {
        // Given
        Refund refundToFail = Refund.builder()
                .id(UUID.randomUUID())
                .refundStatus(RefundStatus.PROCESSING.name())
                .build();

        when(refundRepository.findById(refundToFail.getId())).thenReturn(Optional.of(refundToFail));
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        Refund result = refundService.failRefund(refundToFail.getId(), "Insufficient funds");

        // Then
        assertEquals(RefundStatus.FAILED.name(), result.getRefundStatus());
    }

    @Test
    @DisplayName("Should allow admin override of refund amount")
    void shouldOverrideRefundAmount() {
        // Given
        Refund refundToAdjust = Refund.builder()
                .id(UUID.randomUUID())
                .refundAmount(BigDecimal.valueOf(500))
                .booking(mockBooking)
                .build();

        BigDecimal adjustmentAmount = BigDecimal.valueOf(100); // Increase by 100

        when(refundRepository.findById(refundToAdjust.getId()))
                .thenReturn(Optional.of(refundToAdjust));
        when(refundLedgerService.getLedgerEntriesForRefund(refundToAdjust.getId()))
                .thenReturn(new ArrayList<>()); // Empty list
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        Refund result = refundService.overrideRefundAmount(
                refundToAdjust.getId(),
                adjustmentAmount,
                "Goodwill gesture",
                "ADMIN-001"
        );

        // Then
        assertEquals(BigDecimal.valueOf(600), result.getRefundAmount());
        verify(refundLedgerService).createAdjustmentEntry(any(), any(), eq(adjustmentAmount), any(), any(), any());
    }

    @Test
    @DisplayName("Should prevent negative refund amounts in override")
    void shouldPreventNegativeRefundAmount() {
        // Given
        Refund refundToAdjust = Refund.builder()
                .id(UUID.randomUUID())
                .refundAmount(BigDecimal.valueOf(100))
                .build();

        BigDecimal negativeAdjustment = BigDecimal.valueOf(-200); // Would make it negative

        when(refundRepository.findById(refundToAdjust.getId()))
                .thenReturn(Optional.of(refundToAdjust));

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> 
                refundService.overrideRefundAmount(
                        refundToAdjust.getId(),
                        negativeAdjustment,
                        "Error",
                        "ADMIN-001"
                )
        );
    }

    @Test
    @DisplayName("Should retrieve refund by ID")
    void shouldGetRefundById() {
        // Given
        when(refundRepository.findById(mockRefund.getId())).thenReturn(Optional.of(mockRefund));

        // When
        Refund result = refundService.getRefund(mockRefund.getId());

        // Then
        assertNotNull(result);
        assertEquals(mockRefund.getRefundId(), result.getRefundId());
    }

    @Test
    @DisplayName("Should retrieve all refunds for a booking")
    void shouldGetRefundsForBooking() {
        // Given
        List<Refund> refunds = List.of(mockRefund);
        when(refundRepository.findByBookingIdOrderByCreatedAtDesc(mockBooking.getId()))
                .thenReturn(refunds);

        // When
        List<Refund> result = refundService.getRefundsForBooking(mockBooking.getId());

        // Then
        assertEquals(1, result.size());
        assertEquals(mockRefund.getRefundId(), result.get(0).getRefundId());
    }

    @Test
    @DisplayName("Should retrieve pending refunds for batch processing")
    void shouldGetPendingRefunds() {
        // Given
        List<Refund> pending = List.of(mockRefund);
        when(refundRepository.findPendingRefunds()).thenReturn(pending);

        // When
        List<Refund> result = refundService.getPendingRefunds();

        // Then
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("Should prevent invalid state transitions")
    void shouldPreventInvalidStateTransition() {
        // Given: Refund in COMPLETED status
        Refund completedRefund = Refund.builder()
                .id(UUID.randomUUID())
                .refundStatus(RefundStatus.COMPLETED.name())
                .build();

        when(refundRepository.findById(completedRefund.getId()))
                .thenReturn(Optional.of(completedRefund));

        // When & Then: Cannot approve a completed refund
        assertThrows(IllegalStateException.class, () -> 
                refundService.approveRefund(completedRefund.getId())
        );
    }

    // Helper method
    private CancellationPolicyEngine.RefundCalculation createMockCalculation() {
        return CancellationPolicyEngine.RefundCalculation.builder()
                .policy(mockPolicy)
                .originalAmount(BigDecimal.valueOf(1000))
                .refundType("FULL_REFUND")
                .refundPercentage(BigDecimal.valueOf(100))
                .refundAmount(BigDecimal.valueOf(975))
                .hoursUntilEvent(96)
                .reason("Full refund window applicable")
                .build();
    }
}
