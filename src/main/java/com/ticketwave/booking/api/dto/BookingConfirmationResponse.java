package com.ticketwave.booking.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for booking confirmation.
 * 
 * CLEAN ARCHITECTURE FIX:
 * - Separates API contract from domain entities
 * - Prevents Jackson lazy load exceptions
 * - Enables API versioning without breaking persistence layer
 * - Hides internal entity structure
 * 
 * BEFORE (returning JPA entity directly):
 * @GetMapping("/bookings/{id}")
 * public Booking getBooking(@PathVariable UUID id) {
 *     return bookingService.getBooking(id);  // ⚠️ Exposes entity, lazy load risk
 * }
 * 
 * AFTER (using DTO):
 * @GetMapping("/bookings/{id}")
 * public BookingConfirmationResponse getBooking(@PathVariable UUID id) {
 *     Booking booking = bookingService.getBooking(id);
 *     return bookingMapper.toResponse(booking);  // ✅ Clean separation
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingConfirmationResponse {

    @JsonProperty("booking_id")
    private UUID bookingId;

    @JsonProperty("pnr")
    private String pnr;

    @JsonProperty("booking_status")
    private String bookingStatus;

    @JsonProperty("total_amount")
    private BigDecimal totalAmount;

    @JsonProperty("booked_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime bookedAt;

    @JsonProperty("user")
    private UserSummary user;

    @JsonProperty("schedule")
    private ScheduleSummary schedule;

    @JsonProperty("booking_items")
    private List<BookingItemSummary> bookingItems;

    @JsonProperty("payments")
    private List<PaymentSummary> payments;

    /**
     * Nested DTO for user summary.
     * Only exposes minimal user info needed for booking confirmation.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserSummary {
        @JsonProperty("user_id")
        private UUID userId;

        @JsonProperty("full_name")
        private String fullName;

        @JsonProperty("email")
        private String email;

        @JsonProperty("phone_number")
        private String phoneNumber;
    }

    /**
     * Nested DTO for schedule summary.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScheduleSummary {
        @JsonProperty("schedule_id")
        private UUID scheduleId;

        @JsonProperty("event_name")
        private String eventName;

        @JsonProperty("event_type")
        private String eventType;

        @JsonProperty("departure_time")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime departureTime;

        @JsonProperty("arrival_time")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime arrivalTime;

        @JsonProperty("origin")
        private String origin;

        @JsonProperty("destination")
        private String destination;

        @JsonProperty("base_fare")
        private BigDecimal baseFare;
    }

    /**
     * Nested DTO for booking item.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BookingItemSummary {
        @JsonProperty("booking_item_id")
        private UUID bookingItemId;

        @JsonProperty("seat_number")
        private String seatNumber;

        @JsonProperty("seat_type")
        private String seatType;

        @JsonProperty("fare")
        private BigDecimal fare;

        @JsonProperty("item_status")
        private String itemStatus;

        @JsonProperty("passenger")
        private PassengerSummary passenger;
    }

    /**
     * Nested DTO for passenger.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PassengerSummary {
        @JsonProperty("passenger_id")
        private UUID passengerId;

        @JsonProperty("first_name")
        private String firstName;

        @JsonProperty("last_name")
        private String lastName;

        @JsonProperty("age")
        private Integer age;

        @JsonProperty("gender")
        private String gender;
    }

    /**
     * Nested DTO for payment summary.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentSummary {
        @JsonProperty("payment_id")
        private UUID paymentId;

        @JsonProperty("amount")
        private BigDecimal amount;

        @JsonProperty("payment_status")
        private String paymentStatus;

        @JsonProperty("payment_method")
        private String paymentMethod;

        @JsonProperty("transaction_id")
        private String transactionId;

        @JsonProperty("payment_date")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime paymentDate;
    }
}
