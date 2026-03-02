package com.ticketwave.booking.domain;

/**
 * Booking lifecycle event types for audit logging.
 */
public enum BookingEventType {
    BOOKING_INITIATED("Booking created, awaiting seat hold validation"),
    SEAT_HOLD_CREATED("Seat hold acquired in Redis"),
    SEAT_HOLD_VALIDATED("Seat hold verified still active and owned by user"),
    PAYMENT_INTENT_CREATED("Payment intent created, awaiting payment"),
    PAYMENT_CONFIRMED("Payment confirmed by gateway webhook"),
    PAYMENT_FAILED("Payment rejected or timed out"),
    BOOKING_CONFIRMED("Booking confirmed, seats booked, PNR generated"),
    BOOKING_FAILED("Booking failed, seats released, hold removed"),
    HOLD_RELEASED("Seat hold released before booking"),
    IDEMPOTENCY_CACHE_HIT("Duplicate request detected, returning cached response"),
    RETRY_ATTEMPT("Operation retried after failure"),
    BOOKING_CANCELLED("Booking cancelled after confirmation");

    private final String description;

    BookingEventType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
