package com.ticketwave.refund.api;

import com.ticketwave.common.api.ApiResponse;
import com.ticketwave.refund.application.RefundService;
import com.ticketwave.refund.domain.Refund;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * RefundController provides REST endpoints for refund management.
 * All endpoints require ADMIN role for security.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/refunds")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class RefundController {

    private final RefundService refundService;

    /**
     * GET /api/v1/refunds/{refundId}
     * Get refund details by ID.
     */
    @GetMapping("/{refundId}")
    public ResponseEntity<ApiResponse<RefundResponse>> getRefund(@PathVariable UUID refundId) {
        log.info("Getting refund: {}", refundId);
        Refund refund = refundService.getRefund(refundId);
        return ResponseEntity.ok(ApiResponse.success(toResponse(refund)));
    }

    /**
     * GET /api/v1/refunds/booking/{bookingId}
     * Get all refunds for a booking.
     */
    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<ApiResponse<List<RefundResponse>>> getRefundsForBooking(@PathVariable UUID bookingId) {
        log.info("Getting refunds for booking: {}", bookingId);
        List<Refund> refunds = refundService.getRefundsForBooking(bookingId);
        return ResponseEntity.ok(ApiResponse.success(toResponseList(refunds)));
    }

    /**
     * GET /api/v1/refunds/pending
     * Get all pending refunds (for batch processing).
     */
    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<RefundResponse>>> getPendingRefunds() {
        log.info("Getting pending refunds");
        List<Refund> refunds = refundService.getPendingRefunds();
        return ResponseEntity.ok(ApiResponse.success(toResponseList(refunds)));
    }

    /**
     * POST /api/v1/refunds/{refundId}/approve
     * Approve a refund request (transition from INITIATED to APPROVED).
     */
    @PostMapping("/{refundId}/approve")
    public ResponseEntity<ApiResponse<RefundResponse>> approveRefund(@PathVariable UUID refundId) {
        log.info("Approving refund: {}", refundId);
        try {
            Refund refund = refundService.approveRefund(refundId);
            return ResponseEntity.ok(ApiResponse.success(toResponse(refund)));
        } catch (IllegalStateException e) {
            log.warn("Cannot approve refund: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.failure("409", e.getMessage()));
        }
    }

    /**
     * POST /api/v1/refunds/{refundId}/reject
     * Reject a refund request.
     */
    @PostMapping("/{refundId}/reject")
    public ResponseEntity<ApiResponse<RefundResponse>> rejectRefund(
            @PathVariable UUID refundId,
            @RequestBody RejectRefundRequest request) {
        log.info("Rejecting refund: {} - Reason: {}", refundId, request.getReason());
        try {
            Refund refund = refundService.rejectRefund(refundId, request.getReason());
            return ResponseEntity.ok(ApiResponse.success(toResponse(refund)));
        } catch (IllegalStateException e) {
            log.warn("Cannot reject refund: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.failure("409", e.getMessage()));
        }
    }

    /**
     * POST /api/v1/refunds/{refundId}/process
     * Process a refund (trigger payment gateway refund).
     */
    @PostMapping("/{refundId}/process")
    public ResponseEntity<ApiResponse<RefundResponse>> processRefund(@PathVariable UUID refundId) {
        log.info("Processing refund: {}", refundId);
        try {
            Refund refund = refundService.processRefund(refundId);
            return ResponseEntity.ok(ApiResponse.success(toResponse(refund)));
        } catch (IllegalStateException e) {
            log.warn("Cannot process refund: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.failure("409", e.getMessage()));
        }
    }

    /**
     * POST /api/v1/refunds/{refundId}/complete
     * Mark refund as completed (after successful payment reversal).
     * Internal endpoint called by webhook handler.
     */
    @PostMapping("/{refundId}/complete")
    public ResponseEntity<ApiResponse<RefundResponse>> completeRefund(
            @PathVariable UUID refundId,
            @RequestBody CompleteRefundRequest request) {
        log.info("Completing refund: {} - Gateway ID: {}", refundId, request.getGatewayRefundId());
        try {
            Refund refund = refundService.completeRefund(refundId, request.getGatewayRefundId());
            return ResponseEntity.ok(ApiResponse.success(toResponse(refund)));
        } catch (IllegalStateException e) {
            log.warn("Cannot complete refund: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.failure("409", e.getMessage()));
        }
    }

    /**
     * POST /api/v1/refunds/{refundId}/fail
     * Mark refund as failed (payment gateway rejection).
     * Internal endpoint called by webhook handler.
     */
    @PostMapping("/{refundId}/fail")
    public ResponseEntity<ApiResponse<RefundResponse>> failRefund(
            @PathVariable UUID refundId,
            @RequestBody FailRefundRequest request) {
        log.warn("Failing refund: {} - Error: {}", refundId, request.getErrorMessage());
        try {
            Refund refund = refundService.failRefund(refundId, request.getErrorMessage());
            return ResponseEntity.ok(ApiResponse.success(toResponse(refund)));
        } catch (IllegalStateException e) {
            log.warn("Cannot fail refund: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.failure("409", e.getMessage()));
        }
    }

    /**
     * POST /api/v1/refunds/{refundId}/override-amount
     * Admin override: Manually adjust refund amount.
     * This creates an ADJUSTMENT ledger entry for audit trail.
     */
    @PostMapping("/{refundId}/override-amount")
    public ResponseEntity<ApiResponse<RefundResponse>> overrideRefundAmount(
            @PathVariable UUID refundId,
            @RequestBody OverrideRefundRequest request) {
        log.warn("Admin override refund amount - ID: {}, Adjustment: {}, Reason: {}",
                refundId, request.getAdjustmentAmount(), request.getReason());
        try {
            String adminId = request.getAdminId(); // Should come from JWT principal
            Refund refund = refundService.overrideRefundAmount(
                    refundId,
                    request.getAdjustmentAmount(),
                    request.getReason(),
                    adminId
            );
            return ResponseEntity.ok(ApiResponse.success(toResponse(refund)));
        } catch (IllegalArgumentException e) {
            log.warn("Cannot override refund: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.failure("400", e.getMessage()));
        }
    }

    private List<RefundResponse> toResponseList(List<Refund> refunds) {
        return refunds.stream().map(this::toResponse).toList();
    }

    private RefundResponse toResponse(Refund refund) {
        RefundResponse response = new RefundResponse();
        response.id = refund.getId();
        response.refundId = refund.getRefundId();
        response.bookingId = refund.getBooking() != null ? refund.getBooking().getId() : null;
        response.refundAmount = refund.getRefundAmount();
        response.refundStatus = refund.getRefundStatus();
        response.reason = refund.getReason();
        response.gatewayResponse = refund.getGatewayResponse();
        return response;
    }

    // Request DTOs

    public static class RejectRefundRequest {
        private String reason;

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class CompleteRefundRequest {
        private String gatewayRefundId;

        public String getGatewayRefundId() { return gatewayRefundId; }
        public void setGatewayRefundId(String gatewayRefundId) { this.gatewayRefundId = gatewayRefundId; }
    }

    public static class FailRefundRequest {
        private String errorMessage;

        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }

    public static class OverrideRefundRequest {
        private BigDecimal adjustmentAmount;
        private String reason;
        private String adminId; // Should come from JWT principal in production

        public BigDecimal getAdjustmentAmount() { return adjustmentAmount; }
        public void setAdjustmentAmount(BigDecimal adjustmentAmount) { this.adjustmentAmount = adjustmentAmount; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }

        public String getAdminId() { return adminId; }
        public void setAdminId(String adminId) { this.adminId = adminId; }
    }

    // Response DTO (to be mapped via RefundMapper)
    public static class RefundResponse {
        public UUID id;
        public String refundId;
        public UUID bookingId;
        public BigDecimal refundAmount;
        public String refundStatus;
        public String reason;
        public String gatewayResponse;

        // Getters/setters...
    }
}
