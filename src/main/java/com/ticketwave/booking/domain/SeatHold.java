package com.ticketwave.booking.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor(force = true)
@AllArgsConstructor
public class SeatHold {

    private final UUID seatId;
    private final UUID userId;
    private final String holdToken;
    private final Instant heldAt;
    private final Instant expiresAt;

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isOwnedBy(UUID userId) {
        return this.userId.equals(userId);
    }

    public static SeatHold create(UUID seatId, UUID userId, String holdToken, long durationSeconds) {
        Instant now = Instant.now();
        return SeatHold.builder()
                .seatId(seatId)
                .userId(userId)
                .holdToken(holdToken)
                .heldAt(now)
                .expiresAt(now.plusSeconds(durationSeconds))
                .build();
    }
}
