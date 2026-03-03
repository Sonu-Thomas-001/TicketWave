package com.ticketwave.common.config;

import com.ticketwave.catalog.domain.Route;
import com.ticketwave.catalog.domain.Schedule;
import com.ticketwave.catalog.domain.Seat;
import com.ticketwave.catalog.infrastructure.RouteRepository;
import com.ticketwave.catalog.infrastructure.ScheduleRepository;
import com.ticketwave.catalog.infrastructure.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Seeds the database with sample routes, schedules, and seats for development.
 * Only runs when the "dev" profile is active.
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final RouteRepository routeRepository;
    private final ScheduleRepository scheduleRepository;
    private final SeatRepository seatRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (routeRepository.count() > 0) {
            log.info("Seed data already exists — skipping initialization");
            return;
        }

        log.info("Seeding sample routes, schedules, and seats...");

        // Routes
        Route mumbaiDelhi = createRoute("Mumbai", "Delhi", "BUS", "Premium AC bus service");
        Route delhiBangalore = createRoute("Delhi", "Bangalore", "TRAIN", "Superfast express train");
        Route mumbaiGoa = createRoute("Mumbai", "Goa", "BUS", "Luxury sleeper bus");
        Route chennaiHyderabad = createRoute("Chennai", "Hyderabad", "BUS", "AC seater bus");
        Route delhiJaipur = createRoute("Delhi", "Jaipur", "BUS", "Deluxe AC bus");
        Route bangaloreMysore = createRoute("Bangalore", "Mysore", "BUS", "Express bus");
        Route newYorkLA = createRoute("New York", "Los Angeles", "FLIGHT", "Direct flight service");
        Route newYorkChicago = createRoute("New York", "Chicago", "TRAIN", "Express rail");

        // Schedules for Mumbai → Delhi
        createScheduleWithSeats(mumbaiDelhi, "MH-01-AB-1234",
                LocalDateTime.of(2026, 3, 15, 6, 0),
                LocalDateTime.of(2026, 3, 15, 18, 0),
                40, new BigDecimal("1200"));

        createScheduleWithSeats(mumbaiDelhi, "MH-01-CD-5678",
                LocalDateTime.of(2026, 3, 15, 10, 0),
                LocalDateTime.of(2026, 3, 15, 22, 0),
                40, new BigDecimal("1000"));

        createScheduleWithSeats(mumbaiDelhi, "MH-01-EF-9012",
                LocalDateTime.of(2026, 3, 15, 22, 0),
                LocalDateTime.of(2026, 3, 16, 8, 0),
                36, new BigDecimal("1500"));

        // Schedules for Delhi → Bangalore
        createScheduleWithSeats(delhiBangalore, "DL-02-AB-3456",
                LocalDateTime.of(2026, 3, 15, 8, 0),
                LocalDateTime.of(2026, 3, 16, 8, 0),
                48, new BigDecimal("2200"));

        createScheduleWithSeats(delhiBangalore, "DL-02-CD-7890",
                LocalDateTime.of(2026, 3, 15, 18, 0),
                LocalDateTime.of(2026, 3, 16, 18, 0),
                48, new BigDecimal("2000"));

        // Schedules for Mumbai → Goa
        createScheduleWithSeats(mumbaiGoa, "MH-03-AB-1111",
                LocalDateTime.of(2026, 3, 15, 7, 30),
                LocalDateTime.of(2026, 3, 15, 17, 0),
                32, new BigDecimal("800"));

        createScheduleWithSeats(mumbaiGoa, "MH-03-CD-2222",
                LocalDateTime.of(2026, 3, 15, 21, 0),
                LocalDateTime.of(2026, 3, 16, 5, 30),
                32, new BigDecimal("900"));

        // Chennai → Hyderabad
        createScheduleWithSeats(chennaiHyderabad, "TN-04-AB-3333",
                LocalDateTime.of(2026, 3, 15, 9, 0),
                LocalDateTime.of(2026, 3, 15, 18, 0),
                40, new BigDecimal("700"));

        // Delhi → Jaipur
        createScheduleWithSeats(delhiJaipur, "DL-05-AB-4444",
                LocalDateTime.of(2026, 3, 15, 6, 30),
                LocalDateTime.of(2026, 3, 15, 12, 0),
                40, new BigDecimal("500"));

        createScheduleWithSeats(delhiJaipur, "DL-05-CD-5555",
                LocalDateTime.of(2026, 3, 15, 14, 0),
                LocalDateTime.of(2026, 3, 15, 19, 30),
                40, new BigDecimal("600"));

        // Bangalore → Mysore
        createScheduleWithSeats(bangaloreMysore, "KA-06-AB-6666",
                LocalDateTime.of(2026, 3, 15, 8, 0),
                LocalDateTime.of(2026, 3, 15, 11, 0),
                40, new BigDecimal("350"));

        // New York → Los Angeles
        createScheduleWithSeats(newYorkLA, "NY-07-AB-7777",
                LocalDateTime.of(2026, 3, 15, 10, 30),
                LocalDateTime.of(2026, 3, 15, 13, 45),
                180, new BigDecimal("250"));

        createScheduleWithSeats(newYorkLA, "NY-07-CD-8888",
                LocalDateTime.of(2026, 3, 15, 16, 0),
                LocalDateTime.of(2026, 3, 15, 19, 0),
                180, new BigDecimal("195"));

        // New York → Chicago
        createScheduleWithSeats(newYorkChicago, "NY-08-AB-9999",
                LocalDateTime.of(2026, 3, 15, 8, 0),
                LocalDateTime.of(2026, 3, 15, 18, 0),
                120, new BigDecimal("120"));

        log.info("Seed data created successfully: {} routes, {} schedules",
                routeRepository.count(), scheduleRepository.count());
    }

    private Route createRoute(String origin, String destination, String mode, String description) {
        Route route = Route.builder()
                .originCity(origin)
                .destinationCity(destination)
                .transportMode(mode)
                .description(description)
                .active(true)
                .build();
        return routeRepository.save(route);
    }

    private void createScheduleWithSeats(Route route, String vehicleNumber,
                                          LocalDateTime departure, LocalDateTime arrival,
                                          int totalSeats, BigDecimal baseFare) {
        Schedule schedule = Schedule.builder()
                .route(route)
                .vehicleNumber(vehicleNumber)
                .departureTime(departure)
                .arrivalTime(arrival)
                .totalSeats(totalSeats)
                .availableSeats(totalSeats)
                .baseFare(baseFare)
                .active(true)
                .build();
        schedule = scheduleRepository.save(schedule);

        // Create seats in rows: A-Z, with number of seats per row
        int seatsPerRow = Math.min(10, totalSeats);
        int rows = (int) Math.ceil((double) totalSeats / seatsPerRow);
        int seatsCreated = 0;

        for (int r = 0; r < rows && seatsCreated < totalSeats; r++) {
            char rowLetter = (char) ('A' + r);
            for (int s = 1; s <= seatsPerRow && seatsCreated < totalSeats; s++) {
                String seatNumber = String.valueOf(rowLetter) + s;
                String seatClass;
                if (r < 2) {
                    seatClass = "BUSINESS";
                } else if (r < rows - 2) {
                    seatClass = "ECONOMY";
                } else {
                    seatClass = "ECONOMY";
                }

                Seat seat = Seat.builder()
                        .schedule(schedule)
                        .seatNumber(seatNumber)
                        .class_(seatClass)
                        .seatStatus("AVAILABLE")
                        .build();
                seatRepository.save(seat);
                seatsCreated++;
            }
        }
    }
}
