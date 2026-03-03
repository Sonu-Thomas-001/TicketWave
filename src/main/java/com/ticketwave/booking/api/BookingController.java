package com.ticketwave.booking.api;

import com.ticketwave.booking.application.BookingServiceEnhanced;
import com.ticketwave.booking.application.IdempotencyKeyService;
import com.ticketwave.booking.domain.SeatHold;
import com.ticketwave.booking.infrastructure.BookingRepository;
import com.ticketwave.catalog.infrastructure.ScheduleRepository;
import com.ticketwave.catalog.infrastructure.SeatRepository;
import com.ticketwave.common.api.ApiResponse;
import com.ticketwave.common.security.JwtTokenProvider;
import com.ticketwave.payment.domain.PaymentIntent;
import com.ticketwave.user.domain.User;
import com.ticketwave.user.infrastructure.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Booking creation endpoint.
 * 
 * Endpoint Flow:
 * 1. Validate idempotency key (prevent duplicates)
 * 2. Validate seats and holds
 * 3. Initiate booking (status: INITIATED → PENDING_PAYMENT)
 * 4. Create payment intent
 * 5. Return booking details and payment link
 * 
 * Client continues with:
 * 6. User completes payment
 * 7. Payment gateway sends webhook to PaymentWebhookController
 * 8. Booking automatically confirmed/failed based on payment
 */
@RestController
@RequestMapping("/api/v1/bookings")
@Slf4j
@RequiredArgsConstructor
public class BookingController {

    private final BookingServiceEnhanced bookingService;
    private final IdempotencyKeyService idempotencyKeyService;
    private final BookingRepository bookingRepository;
    private final ScheduleRepository scheduleRepository;
    private final SeatRepository seatRepository;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;

    /**
     * Create a new booking with payment intent.
     * 
     * Request Example:
     * {
     *   "scheduleId": "550e8400-e29b-41d4-a716-446655440000",
     *   "seatHolds": [
     *     {"seatId": "...", "userId": "...", "holdToken": "...", "heldAt": "...", "expiresAt": "..."}
     *   ],
     *   "passengerBookings": {
     *     "seat-uuid-1": "passenger-uuid-1",
     *     "seat-uuid-2": "passenger-uuid-2"
     *   }
     * }
     * 
     * Response Example:
     * {
     *   "status": "success",
     *   "data": {
     *     "bookingId": "550e8400-e29b-41d4-a716-446655440000",
     *     "bookingStatus": "PENDING_PAYMENT",
     *     "pnr": "TWAB12CD34",
     *     "totalAmount": 3000.00,
     *     "paymentIntent": {
     *       "intentId": "TW-1234567890-abc123",
     *       "status": "PENDING",
     *       "expiresAt": "2024-03-02T11:30:00Z"
     *     },
     *     "paymentLink": "https://payment-gateway.com/checkout?intentId=..."
     *   }
     * }
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> createBooking(
            @Valid @RequestBody CreateBookingRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication) {

        log.info("Creating booking - ScheduleId: {} RequestedSeats: {} IdempotencyKey: {}",
                request.getScheduleId(), request.getSeatHolds().size(), 
                idempotencyKey != null ? "Present" : "Missing");

        try {
            // Generate idempotency key if not provided
            if (idempotencyKey == null) {
                idempotencyKey = UUID.randomUUID().toString();
            }

            // Generate request fingerprint for idempotency safety
            String requestFingerprint = idempotencyKeyService.generateFingerprint(
                    objectMapper.writeValueAsString(request)
            );

            // Register idempotency key (prevents duplicate processing)
            var idempotencyKeyEntity = idempotencyKeyService.registerOrGetKey(
                    idempotencyKey, requestFingerprint
            );

            // Check for cached response (idempotency cache hit)
            if (idempotencyKeyService.hasCachedResponse(idempotencyKey)) {
                log.info("Idempotency cache hit for key: {}", idempotencyKey);
                var cachedKey = idempotencyKeyService.getCachedKey(idempotencyKey);
                
                Map<String, Object> cachedData = objectMapper.readValue(
                        cachedKey.getCachedResponse(), Map.class
                );
                return ResponseEntity.status(cachedKey.getCachedStatusCode())
                        .body(ApiResponse.success("Booking created (cached response)", cachedData));
            }

            // Get current user
            String username = authentication.getName();
            User user = userRepository.findById(UUID.fromString(username))
                    .orElseThrow(() -> new RuntimeException("User not found: " + username));

            // Get schedule
            var schedule = scheduleRepository.findById(request.getScheduleId())
                    .orElseThrow(() -> new RuntimeException("Schedule not found"));

            // Convert DTOs to domain objects
            List<SeatHold> seatHolds = request.getSeatHolds().stream()
                    .map(dto -> SeatHold.builder()
                            .seatId(dto.getSeatId())
                            .userId(dto.getUserId())
                            .holdToken(dto.getHoldToken())
                            .heldAt(dto.getHeldAt())
                            .expiresAt(dto.getExpiresAt())
                            .build())
                    .collect(Collectors.toList());

            Map<UUID, com.ticketwave.user.domain.Passenger> passengerBookings = new HashMap<>();
            for (Map.Entry<String, String> entry : request.getPassengerBookings().entrySet()) {
                UUID seatId = UUID.fromString(entry.getKey());
                UUID passengerId = UUID.fromString(entry.getValue());
                
                var passenger = userRepository.findById(passengerId)
                        .flatMap(u -> u.getPassengers().stream()
                                .filter(p -> p.getId().equals(passengerId))
                                .findFirst())
                        .orElseThrow(() -> new RuntimeException("Passenger not found: " + passengerId));
                
                passengerBookings.put(seatId, passenger);
            }

            // Initiate booking
            var bookingResult = bookingService.initiateBooking(
                    user, schedule, seatHolds, passengerBookings, idempotencyKey
            );

            // Build response
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("bookingId", bookingResult.getBooking().getId());
            responseData.put("bookingStatus", bookingResult.getBooking().getBookingStatus());
            responseData.put("pnr", bookingResult.getBooking().getPnr());
            responseData.put("totalAmount", bookingResult.getBooking().getTotalAmount());

            Map<String, Object> paymentIntentData = new HashMap<>();
            paymentIntentData.put("intentId", bookingResult.getPaymentIntent().getIntentId());
            paymentIntentData.put("status", bookingResult.getPaymentIntent().getStatus());
            paymentIntentData.put("amount", bookingResult.getPaymentIntent().getAmount());
            paymentIntentData.put("expiresAt", bookingResult.getPaymentIntent().getExpiresAt());
            responseData.put("paymentIntent", paymentIntentData);

            // Generate payment link (in production: redirect to payment gateway)
            String paymentLink = buildPaymentLink(bookingResult.getPaymentIntent().getIntentId());
            responseData.put("paymentLink", paymentLink);

            // Cache response for idempotency
            String responseJson = objectMapper.writeValueAsString(responseData);
            idempotencyKeyService.markProcessed(idempotencyKey, responseJson, 201);

            log.info("Booking created successfully - BookingId: {} PNR: {} IntentId: {}",
                    bookingResult.getBooking().getId(), bookingResult.getBooking().getPnr(),
                    bookingResult.getPaymentIntent().getIntentId());

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Booking created, please complete payment", responseData));

        } catch (Exception ex) {
            log.error("Error creating booking", ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.failure("Failed to create booking: " + ex.getMessage()));
        }
    }

    // Helper methods

    private String buildPaymentLink(String intentId) {
        // In production: Redirect to payment gateway (Stripe, PayPal, etc.)
        return "https://payment-gateway.example.com/checkout?intentId=" + intentId;
    }

    // ===== GET Endpoints =====

    /**
     * Get all bookings for the authenticated user.
     */
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getUserBookings(
            Authentication authentication) {

        String username = authentication.getName();
        User user = userRepository.findById(UUID.fromString(username))
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        var bookings = bookingRepository.findAll().stream()
                .filter(b -> b.getUser().getId().equals(user.getId()))
                .map(b -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("bookingId", b.getId());
                    data.put("pnr", b.getPnr());
                    data.put("bookingStatus", b.getBookingStatus());
                    data.put("totalAmount", b.getTotalAmount());
                    data.put("bookedAt", b.getBookedAt());

                    // Schedule info
                    Map<String, Object> scheduleInfo = new HashMap<>();
                    scheduleInfo.put("scheduleId", b.getSchedule().getId());
                    scheduleInfo.put("origin", b.getSchedule().getRoute().getOriginCity());
                    scheduleInfo.put("destination", b.getSchedule().getRoute().getDestinationCity());
                    scheduleInfo.put("transportMode", b.getSchedule().getRoute().getTransportMode());
                    scheduleInfo.put("departureTime", b.getSchedule().getDepartureTime());
                    scheduleInfo.put("arrivalTime", b.getSchedule().getArrivalTime());
                    scheduleInfo.put("vehicleNumber", b.getSchedule().getVehicleNumber());
                    data.put("schedule", scheduleInfo);

                    data.put("itemCount", b.getBookingItems().size());
                    return data;
                })
                .toList();

        return ResponseEntity.ok(ApiResponse.success("Bookings retrieved", bookings));
    }

    /**
     * Get a specific booking by ID.
     */
    @GetMapping("/{bookingId}")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Map<String, Object>>> getBooking(
            @PathVariable UUID bookingId,
            Authentication authentication) {

        String username = authentication.getName();
        User user = userRepository.findById(UUID.fromString(username))
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        var booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found: " + bookingId));

        if (!booking.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.failure("Not authorized to view this booking"));
        }

        Map<String, Object> data = new HashMap<>();
        data.put("bookingId", booking.getId());
        data.put("pnr", booking.getPnr());
        data.put("bookingStatus", booking.getBookingStatus());
        data.put("totalAmount", booking.getTotalAmount());
        data.put("bookedAt", booking.getBookedAt());

        Map<String, Object> scheduleInfo = new HashMap<>();
        scheduleInfo.put("scheduleId", booking.getSchedule().getId());
        scheduleInfo.put("origin", booking.getSchedule().getRoute().getOriginCity());
        scheduleInfo.put("destination", booking.getSchedule().getRoute().getDestinationCity());
        scheduleInfo.put("transportMode", booking.getSchedule().getRoute().getTransportMode());
        scheduleInfo.put("departureTime", booking.getSchedule().getDepartureTime());
        scheduleInfo.put("arrivalTime", booking.getSchedule().getArrivalTime());
        scheduleInfo.put("vehicleNumber", booking.getSchedule().getVehicleNumber());
        data.put("schedule", scheduleInfo);

        List<Map<String, Object>> items = booking.getBookingItems().stream()
                .map(item -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("seatNumber", item.getSeat().getSeatNumber());
                    m.put("seatClass", item.getSeat().getClass_());
                    m.put("fare", item.getFare());
                    return m;
                }).toList();
        data.put("bookingItems", items);

        return ResponseEntity.ok(ApiResponse.success("Booking retrieved", data));
    }

    // ===== Request DTOs =====

    @lombok.Data
    public static class CreateBookingRequest {
        private UUID scheduleId;
        private List<SeatHoldDTO> seatHolds;
        private Map<String, String> passengerBookings; // Map<seatId, passengerId> as strings
    }

    @lombok.Data
    public static class SeatHoldDTO {
        private UUID seatId;
        private UUID userId;
        private String holdToken;
        private java.time.Instant heldAt;
        private java.time.Instant expiresAt;
    }
}
