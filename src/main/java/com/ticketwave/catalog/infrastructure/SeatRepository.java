package com.ticketwave.catalog.infrastructure;

import com.ticketwave.catalog.domain.Schedule;
import com.ticketwave.catalog.domain.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SeatRepository extends JpaRepository<Seat, UUID> {
    Optional<Seat> findByScheduleAndSeatNumber(Schedule schedule, String seatNumber);

    List<Seat> findByScheduleAndSeatStatus(Schedule schedule, String seatStatus);

    @Query("SELECT COUNT(s) FROM Seat s WHERE s.schedule = :schedule AND s.seatStatus = :status")
    int countByScheduleAndSeatStatus(@Param("schedule") Schedule schedule, @Param("status") String status);

    List<Seat> findBySchedule(Schedule schedule);

    @Query("SELECT s FROM Seat s WHERE s.id IN :ids")
    List<Seat> findAllByIdWithLock(@Param("ids") List<UUID> ids);
}
