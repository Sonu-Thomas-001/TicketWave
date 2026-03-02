package com.ticketwave.user.infrastructure;

import com.ticketwave.user.domain.Passenger;
import com.ticketwave.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PassengerRepository extends JpaRepository<Passenger, UUID> {
    List<Passenger> findByUser(User user);
    List<Passenger> findByUserAndActiveTrue(User user);
}
