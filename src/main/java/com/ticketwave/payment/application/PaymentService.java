package com.ticketwave.payment.application;

import com.ticketwave.booking.domain.Booking;
import com.ticketwave.common.exception.ResourceNotFoundException;
import com.ticketwave.payment.domain.Payment;
import com.ticketwave.payment.infrastructure.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * PaymentService manages payment operations including creation, confirmation, and failure handling.
 * Handles payment lifecycle independently from booking lifecycle.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentService {

    private final PaymentRepository paymentRepository;

    /**
     * Create a new payment record for a booking.
     * 
     * @param booking Booking entity
     * @param amount Amount to be charged
     * @param transactionId Unique transaction identifier from payment gateway
     * @param paymentMethod Payment method (CARD, UPI, NET_BANKING, WALLET)
     * @return Created Payment entity
     */
    public Payment createPayment(Booking booking, BigDecimal amount, String transactionId, String paymentMethod) {
        log.info("Creating payment for booking: {}, amount: {}, method: {}", booking.getPnr(), amount, paymentMethod);

        Payment payment = Payment.builder()
                .booking(booking)
                .transactionId(transactionId)
                .amount(amount)
                .paymentMethod(paymentMethod)
                .paymentStatus("PENDING")
                .build();

        Payment savedPayment = paymentRepository.save(payment);
        log.info("Payment created with ID: {}", savedPayment.getId());
        return savedPayment;
    }

    /**
     * Confirm a payment (mark as successfully processed).
     * Idempotent: If already confirmed, returns existing payment.
     * 
     * @param paymentId Payment ID to confirm
     * @param gatewayResponse Response from payment gateway
     * @return Confirmed Payment entity
     */
    @Transactional
    public Payment confirmPayment(UUID paymentId, String gatewayResponse) {
        log.info("Confirming payment: {}", paymentId);

        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));

        // Idempotency: If already confirmed, return existing
        if ("CONFIRMED".equals(payment.getPaymentStatus())) {
            log.info("Payment already confirmed: {}", paymentId);
            return payment;
        }

        if (!"PENDING".equals(payment.getPaymentStatus())) {
            log.warn("Cannot confirm payment in status: {} (ID: {})", payment.getPaymentStatus(), paymentId);
            throw new IllegalStateException("Payment must be in PENDING status to confirm");
        }

        payment.setPaymentStatus("CONFIRMED");
        payment.setConfirmedAt(Instant.now());
        payment.setGatewayResponse(gatewayResponse);

        Payment confirmedPayment = paymentRepository.save(payment);
        log.info("Payment confirmed successfully: {}", paymentId);
        return confirmedPayment;
    }

    /**
     * Mark payment as failed (e.g., declined, timeout, insufficient funds).
     * 
     * @param paymentId Payment ID that failed
     * @param failureReason Reason for failure
     * @param gatewayResponse Response from payment gateway
     * @return Failed Payment entity
     */
    @Transactional
    public Payment failPayment(UUID paymentId, String failureReason, String gatewayResponse) {
        log.warn("Failing payment: {} - Reason: {}", paymentId, failureReason);

        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));

        // Can fail from PENDING or CONFIRMED (refund scenario)
        if (!("PENDING".equals(payment.getPaymentStatus()) || "CONFIRMED".equals(payment.getPaymentStatus()))) {
            log.warn("Cannot fail payment in status: {} (ID: {})", payment.getPaymentStatus(), paymentId);
            throw new IllegalStateException("Payment must be in PENDING or CONFIRMED status to fail");
        }

        payment.setPaymentStatus("FAILED");
        payment.setGatewayResponse(failureReason + " - " + gatewayResponse);

        Payment failedPayment = paymentRepository.save(payment);
        log.info("Payment marked as failed: {}", paymentId);
        return failedPayment;
    }

    /**
     * Mark payment as refunded (full or partial).
     * 
     * @param paymentId Payment ID to refund
     * @param refundAmount Amount being refunded (can be less than original)
     * @return Updated Payment entity
     */
    @Transactional
    public Payment refundPayment(UUID paymentId, BigDecimal refundAmount) {
        log.info("Refunding payment: {} - Amount: {}", paymentId, refundAmount);

        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));

        if (!"CONFIRMED".equals(payment.getPaymentStatus())) {
            log.warn("Cannot refund payment not in CONFIRMED status: {} (Current: {})", paymentId, payment.getPaymentStatus());
            throw new IllegalStateException("Payment must be CONFIRMED to refund");
        }

        payment.setPaymentStatus("REFUNDED");
        payment.setGatewayResponse("Refund initiated - Amount: " + refundAmount);

        Payment refundedPayment = paymentRepository.save(payment);
        log.info("Payment refunded successfully: {}", paymentId);
        return refundedPayment;
    }

    /**
     * Get payment by ID.
     * 
     * @param paymentId Payment ID
     * @return Payment entity
     */
    @Transactional(readOnly = true)
    public Payment getPayment(UUID paymentId) {
        return paymentRepository.findById(paymentId)
            .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));
    }

    /**
     * Get payment by transaction ID (from payment gateway).
     * 
     * @param transactionId Transaction ID from gateway
     * @return Payment entity
     */
    @Transactional(readOnly = true)
    public Payment getPaymentByTransactionId(String transactionId) {
        return paymentRepository.findByTransactionId(transactionId)
            .orElseThrow(() -> new ResourceNotFoundException("Payment not found for transaction: " + transactionId));
    }

    /**
     * Get all payments for a booking.
     * 
     * @param booking Booking entity
     * @return List of payments for the booking
     */
    @Transactional(readOnly = true)
    public List<Payment> getPaymentsByBooking(Booking booking) {
        return paymentRepository.findByBooking(booking);
    }

    /**
     * Check if a payment is confirmed.
     * 
     * @param paymentId Payment ID
     * @return true if payment is confirmed, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean isPaymentConfirmed(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .map(p -> "CONFIRMED".equals(p.getPaymentStatus()))
                .orElse(false);
    }

    /**
     * Get total amount paid for a booking.
     * 
     * @param booking Booking entity
     * @return Total amount of all confirmed payments
     */
    @Transactional(readOnly = true)
    public BigDecimal getTotalPaidAmount(Booking booking) {
        List<Payment> payments = paymentRepository.findByBookingAndPaymentStatus(booking, "CONFIRMED");
        return payments.stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
