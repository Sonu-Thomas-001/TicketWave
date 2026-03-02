package com.ticketwave.payment.application;

import com.ticketwave.booking.domain.Booking;
import com.ticketwave.common.exception.ResourceNotFoundException;
import com.ticketwave.payment.domain.Payment;
import com.ticketwave.payment.infrastructure.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PaymentService.
 * Tests payment lifecycle operations including creation, confirmation, and refunds.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService Tests")
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentService paymentService;

    private Booking mockBooking;
    private Payment mockPayment;

    @BeforeEach
    void setUp() {
        mockBooking = Booking.builder()
                .id(UUID.randomUUID())
                .pnr("TW123456")
                .build();

        mockPayment = Payment.builder()
                .id(UUID.randomUUID())
                .transactionId("TXN-123")
                .amount(BigDecimal.valueOf(1000))
                .paymentMethod("CARD")
                .paymentStatus("PENDING")
                .booking(mockBooking)
                .build();
    }

    @Test
    @DisplayName("Should create payment successfully")
    void shouldCreatePaymentSuccessfully() {
        // Given
        when(paymentRepository.save(any(Payment.class))).thenReturn(mockPayment);

        // When
        Payment result = paymentService.createPayment(
                mockBooking,
                BigDecimal.valueOf(1000),
                "TXN-123",
                "CARD"
        );

        // Then
        assertNotNull(result);
        assertEquals("TXN-123", result.getTransactionId());
        assertEquals("PENDING", result.getPaymentStatus());
        assertEquals(BigDecimal.valueOf(1000), result.getAmount());
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    @DisplayName("Should confirm payment successfully")
    void shouldConfirmPaymentSuccessfully() {
        // Given
        when(paymentRepository.findById(mockPayment.getId())).thenReturn(Optional.of(mockPayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        Payment result = paymentService.confirmPayment(mockPayment.getId(), "Gateway: Success");

        // Then
        assertEquals("CONFIRMED", result.getPaymentStatus());
        assertNotNull(result.getConfirmedAt());
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    @DisplayName("Should be idempotent when confirming already confirmed payment")
    void shouldBeIdempotentOnConfirm() {
        // Given: Payment already confirmed
        mockPayment.setPaymentStatus("CONFIRMED");
        mockPayment.setConfirmedAt(Instant.now());

        when(paymentRepository.findById(mockPayment.getId())).thenReturn(Optional.of(mockPayment));

        // When
        Payment result = paymentService.confirmPayment(mockPayment.getId(), "New response");

        // Then
        assertEquals("CONFIRMED", result.getPaymentStatus());
        // Should not update the response
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("Should throw exception when confirming non-existent payment")
    void shouldThrowExceptionWhenConfirmingNonExistentPayment() {
        // Given
        when(paymentRepository.findById(any())).thenReturn(Optional.empty());

        // When & Then
        assertThrows(ResourceNotFoundException.class, () -> 
                paymentService.confirmPayment(UUID.randomUUID(), "Gateway: Success")
        );
    }

    @Test
    @DisplayName("Should fail payment successfully")
    void shouldFailPaymentSuccessfully() {
        // Given
        when(paymentRepository.findById(mockPayment.getId())).thenReturn(Optional.of(mockPayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        Payment result = paymentService.failPayment(
                mockPayment.getId(),
                "Insufficient funds",
                "Gateway error response"
        );

        // Then
        assertEquals("FAILED", result.getPaymentStatus());
        assertTrue(result.getGatewayResponse().contains("Insufficient funds"));
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    @DisplayName("Should allow failing a confirmed payment (for refund scenarios)")
    void shouldAllowFailingConfirmedPayment() {
        // Given: Payment is confirmed
        mockPayment.setPaymentStatus("CONFIRMED");
        mockPayment.setConfirmedAt(Instant.now());

        when(paymentRepository.findById(mockPayment.getId())).thenReturn(Optional.of(mockPayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        Payment result = paymentService.failPayment(
                mockPayment.getId(),
                "Chargeback initiated",
                "Customer dispute"
        );

        // Then
        assertEquals("FAILED", result.getPaymentStatus());
    }

    @Test
    @DisplayName("Should refund confirmed payment")
    void shouldRefundPaymentSuccessfully() {
        // Given
        mockPayment.setPaymentStatus("CONFIRMED");

        when(paymentRepository.findById(mockPayment.getId())).thenReturn(Optional.of(mockPayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        Payment result = paymentService.refundPayment(
                mockPayment.getId(),
                BigDecimal.valueOf(950) // Partial refund
        );

        // Then
        assertEquals("REFUNDED", result.getPaymentStatus());
        assertTrue(result.getGatewayResponse().contains("Refund initiated"));
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    @DisplayName("Should throw exception when refunding non-confirmed payment")
    void shouldThrowExceptionWhenRefundingNonConfirmedPayment() {
        // Given: Payment is PENDING
        mockPayment.setPaymentStatus("PENDING");

        when(paymentRepository.findById(mockPayment.getId())).thenReturn(Optional.of(mockPayment));

        // When & Then
        assertThrows(IllegalStateException.class, () -> 
                paymentService.refundPayment(mockPayment.getId(), BigDecimal.valueOf(950))
        );
    }

    @Test
    @DisplayName("Should retrieve payment by ID")
    void shouldGetPaymentById() {
        // Given
        when(paymentRepository.findById(mockPayment.getId())).thenReturn(Optional.of(mockPayment));

        // When
        Payment result = paymentService.getPayment(mockPayment.getId());

        // Then
        assertNotNull(result);
        assertEquals(mockPayment.getTransactionId(), result.getTransactionId());
    }

    @Test
    @DisplayName("Should throw NotFoundException when payment not found")
    void shouldThrowNotFoundExceptionWhenPaymentNotFound() {
        // Given
        when(paymentRepository.findById(any())).thenReturn(Optional.empty());

        // When & Then
        assertThrows(ResourceNotFoundException.class, () -> 
                paymentService.getPayment(UUID.randomUUID())
        );
    }

    @Test
    @DisplayName("Should retrieve payment by transaction ID")
    void shouldGetPaymentByTransactionId() {
        // Given
        when(paymentRepository.findByTransactionId("TXN-123")).thenReturn(Optional.of(mockPayment));

        // When
        Payment result = paymentService.getPaymentByTransactionId("TXN-123");

        // Then
        assertNotNull(result);
        assertEquals("TXN-123", result.getTransactionId());
    }

    @Test
    @DisplayName("Should get all payments for a booking")
    void shouldGetPaymentsByBooking() {
        // Given
        List<Payment> payments = List.of(mockPayment);
        when(paymentRepository.findByBooking(mockBooking)).thenReturn(payments);

        // When
        List<Payment> result = paymentService.getPaymentsByBooking(mockBooking);

        // Then
        assertEquals(1, result.size());
        assertEquals(mockPayment.getTransactionId(), result.get(0).getTransactionId());
    }

    @Test
    @DisplayName("Should check if payment is confirmed")
    void shouldCheckIfPaymentConfirmed() {
        // Given: Payment is confirmed
        mockPayment.setPaymentStatus("CONFIRMED");
        when(paymentRepository.findById(mockPayment.getId())).thenReturn(Optional.of(mockPayment));

        // When
        boolean result = paymentService.isPaymentConfirmed(mockPayment.getId());

        // Then
        assertTrue(result);
    }

    @Test
    @DisplayName("Should return false when payment is not confirmed")
    void shouldReturnFalseWhenPaymentNotConfirmed() {
        // Given: Payment is PENDING
        mockPayment.setPaymentStatus("PENDING");
        when(paymentRepository.findById(mockPayment.getId())).thenReturn(Optional.of(mockPayment));

        // When
        boolean result = paymentService.isPaymentConfirmed(mockPayment.getId());

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("Should calculate total paid amount for booking")
    void shouldCalculateTotalPaidAmount() {
        // Given
        Payment payment1 = Payment.builder()
                .amount(BigDecimal.valueOf(500))
                .paymentStatus("CONFIRMED")
                .build();

        Payment payment2 = Payment.builder()
                .amount(BigDecimal.valueOf(300))
                .paymentStatus("CONFIRMED")
                .build();

        Payment failedPayment = Payment.builder()
                .amount(BigDecimal.valueOf(200))
                .paymentStatus("FAILED")
                .build();

        List<Payment> confirmedPayments = List.of(payment1, payment2);
        when(paymentRepository.findByBookingAndPaymentStatus(mockBooking, "CONFIRMED"))
                .thenReturn(confirmedPayments);

        // When
        BigDecimal total = paymentService.getTotalPaidAmount(mockBooking);

        // Then
        assertEquals(BigDecimal.valueOf(800), total);
    }

    @Test
    @DisplayName("Should handle invalid state transition on confirm")
    void shouldPreventConfirmingFailedPayment() {
        // Given: Payment already failed
        mockPayment.setPaymentStatus("FAILED");

        when(paymentRepository.findById(mockPayment.getId())).thenReturn(Optional.of(mockPayment));

        // When & Then
        assertThrows(IllegalStateException.class, () -> 
                paymentService.confirmPayment(mockPayment.getId(), "Gateway: Success")
        );
    }

    @Test
    @DisplayName("Should succeed with multiple payment operations in sequence")
    void shouldHandleMultipleOperationsSequence() {
        // Given
        when(paymentRepository.findById(mockPayment.getId()))
                .thenReturn(Optional.of(mockPayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        // When: Create -> Confirm -> Refund
        paymentService.confirmPayment(mockPayment.getId(), "Success");
        mockPayment.setPaymentStatus("CONFIRMED");
        
        Payment refundedPayment = paymentService.refundPayment(
                mockPayment.getId(),
                BigDecimal.valueOf(950)
        );

        // Then
        assertEquals("REFUNDED", refundedPayment.getPaymentStatus());
        verify(paymentRepository, times(2)).save(any(Payment.class));
    }
}
