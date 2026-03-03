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

        // ── Concert / Event Routes ──
        // For concerts: originCity = event name, destinationCity = city, vehicleNumber = venue
        Route taylorSwift = createRoute("Taylor Swift | The Eras Tour", "New York", "CONCERT",
                "The Eras Tour — a journey through Taylor Swift's musical eras");
        Route coldplay = createRoute("Coldplay | Music of the Spheres", "Mumbai", "CONCERT",
                "Coldplay's spectacular Music of the Spheres World Tour");
        Route arRahman = createRoute("A.R. Rahman Live", "Chennai", "CONCERT",
                "The Mozart of Madras performs his greatest hits live");
        Route arijitSingh = createRoute("Arijit Singh Live in Concert", "Delhi", "CONCERT",
                "India's most soulful voice — live and unplugged");
        Route edSheeran = createRoute("Ed Sheeran | Mathematics Tour", "London", "CONCERT",
                "Ed Sheeran's record-breaking Mathematics World Tour");
        Route weeknd = createRoute("The Weeknd | After Hours", "Los Angeles", "CONCERT",
                "After Hours Til Dawn — a cinematic concert experience");

        // Concerts — Taylor Swift
        createScheduleWithSeats(taylorSwift, "MetLife Stadium",
                LocalDateTime.of(2026, 4, 18, 19, 0),
                LocalDateTime.of(2026, 4, 18, 23, 0),
                60, new BigDecimal("250"));

        createScheduleWithSeats(taylorSwift, "MetLife Stadium",
                LocalDateTime.of(2026, 4, 19, 19, 0),
                LocalDateTime.of(2026, 4, 19, 23, 0),
                60, new BigDecimal("280"));

        // Concerts — Coldplay
        createScheduleWithSeats(coldplay, "DY Patil Stadium",
                LocalDateTime.of(2026, 3, 22, 18, 30),
                LocalDateTime.of(2026, 3, 22, 22, 30),
                80, new BigDecimal("4500"));

        createScheduleWithSeats(coldplay, "DY Patil Stadium",
                LocalDateTime.of(2026, 3, 23, 18, 30),
                LocalDateTime.of(2026, 3, 23, 22, 30),
                80, new BigDecimal("5000"));

        // Concerts — A.R. Rahman
        createScheduleWithSeats(arRahman, "Nehru Indoor Stadium",
                LocalDateTime.of(2026, 4, 5, 18, 0),
                LocalDateTime.of(2026, 4, 5, 22, 0),
                50, new BigDecimal("2000"));

        // Concerts — Arijit Singh
        createScheduleWithSeats(arijitSingh, "Jawaharlal Nehru Stadium",
                LocalDateTime.of(2026, 3, 29, 19, 0),
                LocalDateTime.of(2026, 3, 29, 23, 0),
                70, new BigDecimal("1500"));

        createScheduleWithSeats(arijitSingh, "Jawaharlal Nehru Stadium",
                LocalDateTime.of(2026, 3, 30, 19, 0),
                LocalDateTime.of(2026, 3, 30, 23, 0),
                70, new BigDecimal("1800"));

        // Concerts — Ed Sheeran
        createScheduleWithSeats(edSheeran, "Wembley Stadium",
                LocalDateTime.of(2026, 5, 10, 19, 30),
                LocalDateTime.of(2026, 5, 10, 23, 0),
                100, new BigDecimal("150"));

        createScheduleWithSeats(edSheeran, "Wembley Stadium",
                LocalDateTime.of(2026, 5, 11, 19, 30),
                LocalDateTime.of(2026, 5, 11, 23, 0),
                100, new BigDecimal("175"));

        // Concerts — The Weeknd
        createScheduleWithSeats(weeknd, "SoFi Stadium",
                LocalDateTime.of(2026, 4, 25, 20, 0),
                LocalDateTime.of(2026, 4, 26, 0, 0),
                90, new BigDecimal("200"));

        // ── Sports Events ──
        Route iplFinal = createRoute("IPL 2026 Final", "Mumbai", "SPORTS",
                "The grand final of the Indian Premier League 2026");
        Route premierLeague = createRoute("Manchester United vs Liverpool", "Manchester", "SPORTS",
                "English Premier League — the ultimate rivalry match");
        Route nbaFinals = createRoute("NBA Finals Game 7", "Los Angeles", "SPORTS",
                "NBA Finals 2026 — decisive Game 7");
        Route wimbledon = createRoute("Wimbledon Men's Final", "London", "SPORTS",
                "The Championships, Wimbledon — Men's Singles Final");
        Route cricketWC = createRoute("ICC Cricket World Cup Semi-Final", "Delhi", "SPORTS",
                "India vs Australia — World Cup Semi-Final");

        createScheduleWithSeats(iplFinal, "Wankhede Stadium",
                LocalDateTime.of(2026, 5, 24, 19, 30),
                LocalDateTime.of(2026, 5, 24, 23, 30),
                80, new BigDecimal("3500"));

        createScheduleWithSeats(premierLeague, "Old Trafford",
                LocalDateTime.of(2026, 4, 12, 16, 0),
                LocalDateTime.of(2026, 4, 12, 18, 0),
                70, new BigDecimal("120"));

        createScheduleWithSeats(nbaFinals, "Crypto.com Arena",
                LocalDateTime.of(2026, 6, 21, 20, 0),
                LocalDateTime.of(2026, 6, 21, 23, 0),
                100, new BigDecimal("500"));

        createScheduleWithSeats(wimbledon, "Centre Court",
                LocalDateTime.of(2026, 7, 12, 14, 0),
                LocalDateTime.of(2026, 7, 12, 18, 0),
                60, new BigDecimal("200"));

        createScheduleWithSeats(cricketWC, "Arun Jaitley Stadium",
                LocalDateTime.of(2026, 10, 18, 14, 0),
                LocalDateTime.of(2026, 10, 18, 22, 0),
                90, new BigDecimal("2500"));

        // ── Theatre Events ──
        Route hamilton = createRoute("Hamilton", "New York", "THEATRE",
                "The musical that revolutionized Broadway — Hamilton");
        Route phantomOpera = createRoute("The Phantom of the Opera", "London", "THEATRE",
                "Andrew Lloyd Webber's legendary musical");
        Route lionKing = createRoute("The Lion King", "New York", "THEATRE",
                "Disney's award-winning stage adaptation");
        Route wicked = createRoute("Wicked", "Chicago", "THEATRE",
                "The untold story of the witches of Oz");

        createScheduleWithSeats(hamilton, "Richard Rodgers Theatre",
                LocalDateTime.of(2026, 3, 20, 19, 0),
                LocalDateTime.of(2026, 3, 20, 22, 0),
                50, new BigDecimal("180"));

        createScheduleWithSeats(hamilton, "Richard Rodgers Theatre",
                LocalDateTime.of(2026, 3, 21, 14, 0),
                LocalDateTime.of(2026, 3, 21, 17, 0),
                50, new BigDecimal("200"));

        createScheduleWithSeats(phantomOpera, "Her Majesty's Theatre",
                LocalDateTime.of(2026, 4, 5, 19, 30),
                LocalDateTime.of(2026, 4, 5, 22, 0),
                45, new BigDecimal("95"));

        createScheduleWithSeats(lionKing, "Minskoff Theatre",
                LocalDateTime.of(2026, 3, 28, 19, 0),
                LocalDateTime.of(2026, 3, 28, 22, 0),
                55, new BigDecimal("160"));

        createScheduleWithSeats(wicked, "James M. Nederlander Theatre",
                LocalDateTime.of(2026, 4, 10, 19, 30),
                LocalDateTime.of(2026, 4, 10, 22, 0),
                48, new BigDecimal("140"));

        // ── Festivals ──
        Route coachella = createRoute("Coachella 2026", "Indio", "FESTIVAL",
                "The legendary Coachella Valley Music and Arts Festival");
        Route holi = createRoute("Holi Festival of Colors", "Delhi", "FESTIVAL",
                "Grand celebration of Holi with colors, music and dance");
        Route tomorrowland = createRoute("Tomorrowland 2026", "Boom", "FESTIVAL",
                "The world's most iconic electronic music festival");
        Route sunburn = createRoute("Sunburn Festival 2026", "Goa", "FESTIVAL",
                "Asia's largest electronic dance music festival");

        createScheduleWithSeats(coachella, "Empire Polo Club",
                LocalDateTime.of(2026, 4, 10, 12, 0),
                LocalDateTime.of(2026, 4, 10, 23, 59),
                120, new BigDecimal("450"));

        createScheduleWithSeats(coachella, "Empire Polo Club",
                LocalDateTime.of(2026, 4, 11, 12, 0),
                LocalDateTime.of(2026, 4, 11, 23, 59),
                120, new BigDecimal("450"));

        createScheduleWithSeats(holi, "India Gate Grounds",
                LocalDateTime.of(2026, 3, 14, 10, 0),
                LocalDateTime.of(2026, 3, 14, 18, 0),
                200, new BigDecimal("500"));

        createScheduleWithSeats(tomorrowland, "De Schorre",
                LocalDateTime.of(2026, 7, 17, 12, 0),
                LocalDateTime.of(2026, 7, 17, 23, 59),
                150, new BigDecimal("350"));

        createScheduleWithSeats(sunburn, "Vagator Beach",
                LocalDateTime.of(2026, 12, 28, 14, 0),
                LocalDateTime.of(2026, 12, 28, 23, 59),
                100, new BigDecimal("3000"));

        // ── Shows ──
        Route cirqueSoleil = createRoute("Cirque du Soleil — Kooza", "Las Vegas", "SHOW",
                "Breathtaking acrobatics and dazzling performances");
        Route blueMan = createRoute("Blue Man Group", "New York", "SHOW",
                "The ultimate multimedia sensory experience");
        Route standupKevin = createRoute("Kevin Hart — Reality Check Tour", "Los Angeles", "SHOW",
                "Kevin Hart's hilarious stand-up comedy tour");
        Route magicShow = createRoute("David Copperfield Live", "Las Vegas", "SHOW",
                "World's greatest illusionist — live in Las Vegas");

        createScheduleWithSeats(cirqueSoleil, "MGM Grand Theater",
                LocalDateTime.of(2026, 4, 15, 19, 0),
                LocalDateTime.of(2026, 4, 15, 21, 30),
                60, new BigDecimal("130"));

        createScheduleWithSeats(cirqueSoleil, "MGM Grand Theater",
                LocalDateTime.of(2026, 4, 16, 19, 0),
                LocalDateTime.of(2026, 4, 16, 21, 30),
                60, new BigDecimal("130"));

        createScheduleWithSeats(blueMan, "Astor Place Theatre",
                LocalDateTime.of(2026, 3, 22, 20, 0),
                LocalDateTime.of(2026, 3, 22, 22, 0),
                40, new BigDecimal("85"));

        createScheduleWithSeats(standupKevin, "The Forum",
                LocalDateTime.of(2026, 5, 3, 20, 0),
                LocalDateTime.of(2026, 5, 3, 22, 30),
                80, new BigDecimal("110"));

        createScheduleWithSeats(magicShow, "David Copperfield Theater",
                LocalDateTime.of(2026, 4, 20, 19, 0),
                LocalDateTime.of(2026, 4, 20, 21, 0),
                50, new BigDecimal("150"));

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
