package com.ticketwave.booking.mapper;

import com.ticketwave.booking.api.dto.BookingConfirmationResponse;
import com.ticketwave.booking.domain.Booking;
import com.ticketwave.booking.domain.BookingItem;
import com.ticketwave.payment.domain.Payment;
import com.ticketwave.user.domain.Passenger;
import com.ticketwave.user.domain.User;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Mapper to convert domain entities to API DTOs.
 * 
 * BENEFITS:
 * - Centralized mapping logic
 * - Testable transformation
 * - Type-safe conversion
 * - Easy to modify API contract without touching domain
 * 
 * ALTERNATIVE: Use MapStruct for compile-time type-safe mapping:
 * 
 * @Mapper(componentModel = "spring")
 * public interface BookingMapper {
 *     BookingConfirmationResponse toResponse(Booking booking);
 * }
 * 
 * MapStruct generates implementation at compile time - faster and type-safe!
 */
@Component
public class BookingMapper {

    /**
     * Convert Booking entity to BookingConfirmationResponse DTO.
     *
     * @param booking domain entity
     * @return API response DTO
     */
    public BookingConfirmationResponse toResponse(Booking booking) {
        if (booking == null) {
            return null;
        }

        return BookingConfirmationResponse.builder()
                .bookingId(booking.getId())
                .pnr(booking.getPnr())
                .bookingStatus(booking.getBookingStatus())
                .totalAmount(booking.getTotalAmount())
                .bookedAt(booking.getBookedAt())
                .user(mapUser(booking.getUser()))
                .schedule(mapSchedule(booking))
                .bookingItems(mapBookingItems(booking.getBookingItems() != null ? new java.util.ArrayList<>(booking.getBookingItems()) : null))
                .payments(mapPayments(booking.getPayments() != null ? new java.util.ArrayList<>(booking.getPayments()) : null))
                .build();
    }

    private BookingConfirmationResponse.UserSummary mapUser(User user) {
        if (user == null) {
            return null;
        }

        return BookingConfirmationResponse.UserSummary.builder()
                .userId(user.getId())
                .fullName(user.getFirstName() + " " + user.getLastName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .build();
    }

    private BookingConfirmationResponse.ScheduleSummary mapSchedule(Booking booking) {
        if (booking.getSchedule() == null) {
            return null;
        }

        var schedule = booking.getSchedule();

        return BookingConfirmationResponse.ScheduleSummary.builder()
                .scheduleId(schedule.getId())
                .eventName(schedule.getVehicleNumber())
                .eventType(schedule.getRoute() != null ? schedule.getRoute().getTransportMode() : "N/A")
                .departureTime(schedule.getDepartureTime())
                .arrivalTime(schedule.getArrivalTime())
                .origin(schedule.getRoute() != null ? schedule.getRoute().getOriginCity() : "N/A")
                .destination(schedule.getRoute() != null ? schedule.getRoute().getDestinationCity() : "N/A")
                .baseFare(schedule.getBaseFare())
                .build();
    }

    private List<BookingConfirmationResponse.BookingItemSummary> mapBookingItems(
            List<BookingItem> items) {
        if (items == null) {
            return List.of();
        }

        return items.stream()
                .map(this::mapBookingItem)
                .collect(Collectors.toList());
    }

    private BookingConfirmationResponse.BookingItemSummary mapBookingItem(BookingItem item) {
        return BookingConfirmationResponse.BookingItemSummary.builder()
                .bookingItemId(item.getId())
                .seatNumber(item.getSeat() != null ? item.getSeat().getSeatNumber() : "N/A")
                .seatType(item.getSeat() != null ? item.getSeat().getClass_() : "N/A")
                .fare(item.getFare())
                .itemStatus(item.getItemStatus())
                .passenger(mapPassenger(item.getPassenger()))
                .build();
    }

    private BookingConfirmationResponse.PassengerSummary mapPassenger(Passenger passenger) {
        if (passenger == null) {
            return null;
        }

        return BookingConfirmationResponse.PassengerSummary.builder()
                .passengerId(passenger.getId())
                .firstName(passenger.getFirstName())
                .lastName(passenger.getLastName())
                .age(passenger.getDateOfBirth() != null ? java.time.Period.between(passenger.getDateOfBirth(), java.time.LocalDate.now()).getYears() : null)
                .gender(passenger.getDocumentType())
                .build();
    }

    private List<BookingConfirmationResponse.PaymentSummary> mapPayments(List<Payment> payments) {
        if (payments == null) {
            return List.of();
        }

        return payments.stream()
                .map(this::mapPayment)
                .collect(Collectors.toList());
    }

    private BookingConfirmationResponse.PaymentSummary mapPayment(Payment payment) {
        return BookingConfirmationResponse.PaymentSummary.builder()
                .paymentId(payment.getId())
                .amount(payment.getAmount())
                .paymentStatus(payment.getPaymentStatus())
                .paymentMethod(payment.getPaymentMethod())
                .transactionId(payment.getTransactionId())
                .paymentDate(payment.getConfirmedAt() != null ? java.time.LocalDateTime.ofInstant(payment.getConfirmedAt(), java.time.ZoneId.systemDefault()) : null)
                .build();
    }
}
