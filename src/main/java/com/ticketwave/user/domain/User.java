package com.ticketwave.user.domain;

import com.ticketwave.common.domain.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users", indexes = {
        @jakarta.persistence.Index(name = "idx_users_email", columnList = "email", unique = true)
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User extends AuditedEntity {

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String firstName;

    @Column(nullable = false, length = 50)
    private String lastName;

    @Column(length = 20)
    private String phoneNumber;

    @Column(nullable = false)
    private Boolean active = true;

    @Version
    private long version;

    @Builder.Default
    @OneToMany(mappedBy = "user", fetch = jakarta.persistence.FetchType.LAZY, cascade = jakarta.persistence.CascadeType.REMOVE)
    private Set<Passenger> passengers = new HashSet<>();

    @Builder.Default
    @OneToMany(mappedBy = "user", fetch = jakarta.persistence.FetchType.LAZY, cascade = jakarta.persistence.CascadeType.REMOVE)
    private Set<com.ticketwave.booking.domain.Booking> bookings = new HashSet<>();
}
