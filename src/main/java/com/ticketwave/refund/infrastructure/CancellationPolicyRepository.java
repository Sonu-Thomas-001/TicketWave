package com.ticketwave.refund.infrastructure;

import com.ticketwave.refund.domain.CancellationPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing CancellationPolicy persistence.
 */
@Repository
public interface CancellationPolicyRepository extends JpaRepository<CancellationPolicy, UUID> {

    /**
     * Find policy by policy ID.
     */
    Optional<CancellationPolicy> findByPolicyId(String policyId);

    /**
     * Find all active policies for a specific event type.
     */
    @Query("SELECT p FROM CancellationPolicy p " +
           "WHERE p.eventType = :eventType AND p.isActive = true " +
           "AND (p.effectiveFrom IS NULL OR p.effectiveFrom <= :now) " +
           "AND (p.effectiveTo IS NULL OR p.effectiveTo >= :now) " +
           "ORDER BY p.createdAt DESC")
    List<CancellationPolicy> findActiveByEventType(@Param("eventType") String eventType, @Param("now") Instant now);

    /**
     * Find all active policies for an event type at a specific time.
     */
    List<CancellationPolicy> findByEventTypeAndIsActiveTrue(String eventType);

    /**
     * Count active policies by event type.
     */
    long countByEventTypeAndIsActiveTrue(String eventType);
}
