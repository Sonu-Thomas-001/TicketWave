package com.ticketwave.catalog.infrastructure;

import com.ticketwave.catalog.domain.Route;
import com.ticketwave.catalog.domain.Schedule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, UUID> {
    List<Schedule> findByRouteAndActiveTrue(Route route);

    @Query("SELECT s FROM Schedule s WHERE s.route = :route AND s.departureTime BETWEEN :startTime AND :endTime AND s.active = true")
    List<Schedule> findAvailableSchedules(@Param("route") Route route,
                                         @Param("startTime") LocalDateTime startTime,
                                         @Param("endTime") LocalDateTime endTime);

    Page<Schedule> findByActiveTrue(Pageable pageable);

    List<Schedule> findByAvailableSeatsGreaterThan(int availableSeats);

    @Query("SELECT s FROM Schedule s " +
           "JOIN s.route r " +
           "WHERE r.originCity = :originCity " +
           "AND r.destinationCity = :destinationCity " +
           "AND DATE(s.departureTime) = :travelDate " +
           "AND s.active = true " +
           "AND s.availableSeats > 0 " +
           "ORDER BY s.departureTime ASC")
    List<Schedule> searchByOriginDestinationDate(@Param("originCity") String originCity,
                                                 @Param("destinationCity") String destinationCity,
                                                 @Param("travelDate") LocalDateTime travelDate);

    @Query("SELECT s FROM Schedule s " +
           "JOIN s.route r " +
           "WHERE r.originCity = LOWER(:originCity) " +
           "AND r.destinationCity = LOWER(:destinationCity) " +
           "AND s.active = true " +
           "AND s.availableSeats > 0")
    Page<Schedule> searchByOriginDestination(@Param("originCity") String originCity,
                                            @Param("destinationCity") String destinationCity,
                                            Pageable pageable);

    @Query("SELECT s FROM Schedule s WHERE s.id = :id")
    Optional<Schedule> findByIdWithLock(@Param("id") UUID id);

    @Query("SELECT COUNT(s) FROM Schedule s " +
           "WHERE s.id = :scheduleId AND s.availableSeats <= (s.totalSeats * 0.3)")
    int countHighDemandSchedules(@Param("scheduleId") UUID scheduleId);
}
