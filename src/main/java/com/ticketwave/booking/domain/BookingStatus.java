package com.ticketwave.booking.domain;

/**
 * Represents the lifecycle states of a booking.
 * 
 * State transitions:
 * INITIATED → PENDING_PAYMENT → CONFIRMED → (optionally CANCELLED)
 * INITIATED → FAILED
 * PENDING_PAYMENT → FAILED
 * CONFIRMED → CANCELLED (after completion)
 */
public enum BookingStatus {
    /**
     * Initial state: Booking created, awaiting seat validation and hold confirmation.
     */
    INITIATED,

    /**
     * Seats held successfully, payment intent created, awaiting payment confirmation.
     */
    PENDING_PAYMENT,

    /**
     * Payment confirmed, seats booked, PNR generated. Booking complete.
     */
    CONFIRMED,

    /**
     * Booking cancelled by user (after initial confirmation) or payment timeout.
     */
    CANCELLED,

    /**
     * Booking failed due to seat unavailability, payment rejection, or system error.
     * Seats not booked, holds released.
     */
    FAILED;

    public boolean isTerminal() {
        return this == CONFIRMED || this == CANCELLED || this == FAILED;
    }

    public boolean canTransitionTo(BookingStatus target) {
        return switch (this) {
            case INITIATED -> target == PENDING_PAYMENT || target == FAILED;
            case PENDING_PAYMENT -> target == CONFIRMED || target == FAILED;
            case CONFIRMED -> target == CANCELLED;
            case CANCELLED, FAILED -> false; // Terminal states
        };
    }
}
