package com.ticketwave;

import com.ticketwave.common.security.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
public class TicketWaveApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketWaveApplication.class, args);
    }
}
