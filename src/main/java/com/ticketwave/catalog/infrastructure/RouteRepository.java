package com.ticketwave.catalog.infrastructure;

import com.ticketwave.catalog.domain.Route;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RouteRepository extends JpaRepository<Route, UUID> {
    List<Route> findByOriginCityAndDestinationCity(String originCity, String destinationCity);
    List<Route> findByActiveTrue();
}
