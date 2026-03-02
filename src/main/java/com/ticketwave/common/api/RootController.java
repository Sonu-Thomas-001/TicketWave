package com.ticketwave.common.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class RootController {

    @GetMapping("/")
    public Map<String, Object> index() {
        return Map.of(
                "application", "TicketWave",
                "version", "1.0.0",
                "status", "running",
                "timestamp", Instant.now().toString(),
                "endpoints", Map.of(
                        "auth", "/api/v1/auth/**",
                        "health", "/actuator/health"
                )
        );
    }
}
