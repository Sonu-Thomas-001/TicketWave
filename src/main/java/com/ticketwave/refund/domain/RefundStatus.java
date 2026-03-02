package com.ticketwave.refund.domain;

/**
 * Enum representing the status of a refund throughout its lifecycle.
 * 
 * Status flow: INITIATED → APPROVED or REJECTED → PROCESSING → COMPLETED or FAILED
 */
public enum RefundStatus {
    /**
     * Refund request has been initiated and is awaiting approval.
     */
    INITIATED("Refund initiated and pending approval"),

    /**
     * Refund has been approved for processing.
     */
    APPROVED("Refund approved for processing"),

    /**
     * Refund has been rejected (policy/admin decision).
     */
    REJECTED("Refund rejected"),

    /**
     * Refund is being processed with payment gateway.
     */
    PROCESSING("Refund processing with payment provider"),

    /**
     * Refund has been successfully completed.
     */
    COMPLETED("Refund successfully completed"),

    /**
     * Refund processing failed at gateway; manual intervention needed.
     */
    FAILED("Refund processing failed");

    private final String description;

    RefundStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == REJECTED || this == FAILED;
    }

    public boolean canTransitionTo(RefundStatus targetStatus) {
        return switch (this) {
            case INITIATED -> targetStatus == APPROVED || targetStatus == REJECTED;
            case APPROVED -> targetStatus == PROCESSING;
            case PROCESSING -> targetStatus == COMPLETED || targetStatus == FAILED;
            case REJECTED, COMPLETED, FAILED -> false;
        };
    }
}
