package com.ticketwave.common.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for accessing AuditLog events.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    /**
     * Find all audit logs for a specific entity.
     */
    @Query("SELECT a FROM AuditLog a WHERE a.entityType = :entityType AND a.entityId = :entityId ORDER BY a.timestamp DESC")
    Page<AuditLog> findByEntityTypeAndEntityId(@Param("entityType") String entityType, 
                                                @Param("entityId") String entityId, 
                                                Pageable pageable);

    /**
     * Find audit logs for the same entity within a time range.
     */
    @Query("SELECT a FROM AuditLog a WHERE a.entityType = :entityType AND a.entityId = :entityId " +
           "AND a.timestamp BETWEEN :startTime AND :endTime ORDER BY a.timestamp DESC")
    List<AuditLog> findByEntityWithinTimeRange(@Param("entityType") String entityType,
                                               @Param("entityId") String entityId,
                                               @Param("startTime") Instant startTime,
                                               @Param("endTime") Instant endTime);

    /**
     * Find audit logs by action type.
     */
    @Query("SELECT a FROM AuditLog a WHERE a.action = :action ORDER BY a.timestamp DESC")
    Page<AuditLog> findByAction(@Param("action") String action, Pageable pageable);

    /**
     * Find audit logs by user.
     */
    @Query("SELECT a FROM AuditLog a WHERE a.userId = :userId ORDER BY a.timestamp DESC")
    Page<AuditLog> findByUserId(@Param("userId") String userId, Pageable pageable);

    /**
     * Find audit logs by user and action (useful for admin activity tracking).
     */
    @Query("SELECT a FROM AuditLog a WHERE a.userId = :userId AND a.action = :action ORDER BY a.timestamp DESC")
    Page<AuditLog> findByUserIdAndAction(@Param("userId") String userId, 
                                         @Param("action") String action, 
                                         Pageable pageable);

    /**
     * Find all failed operations (errors).
     */
    @Query("SELECT a FROM AuditLog a WHERE a.errorMessage IS NOT NULL ORDER BY a.timestamp DESC")
    Page<AuditLog> findFailedOperations(Pageable pageable);

    /**
     * Find all admin overrides.
     */
    @Query("SELECT a FROM AuditLog a WHERE a.isAdminOverride = true ORDER BY a.timestamp DESC")
    Page<AuditLog> findAdminOverrides(Pageable pageable);

    /**
     * Find all admin overrides by specific admin.
     */
    @Query("SELECT a FROM AuditLog a WHERE a.isAdminOverride = true AND a.userId = :adminId ORDER BY a.timestamp DESC")
    Page<AuditLog> findAdminOverridesByAdmin(@Param("adminId") String adminId, Pageable pageable);

    /**
     * Find audit logs within a date range (for compliance reports).
     */
    @Query("SELECT a FROM AuditLog a WHERE a.timestamp BETWEEN :startTime AND :endTime ORDER BY a.timestamp DESC")
    Page<AuditLog> findByDateRange(@Param("startTime") Instant startTime, 
                                    @Param("endTime") Instant endTime, 
                                    Pageable pageable);

    /**
     * Find audit logs for multiple entities (useful for transaction audits).
     */
    @Query("SELECT a FROM AuditLog a WHERE a.relatedEntityId = :relatedId AND a.relatedEntityType = :relatedType ORDER BY a.timestamp DESC")
    List<AuditLog> findByRelatedEntity(@Param("relatedType") String relatedType, 
                                       @Param("relatedId") String relatedId);

    /**
     * Count audit logs by entity type (for metrics).
     */
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.entityType = :entityType")
    long countByEntityType(@Param("entityType") String entityType);

    /**
     * Count failed operations.
     */
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.errorMessage IS NOT NULL")
    long countFailedOperations();

    /**
     * Count admin overrides.
     */
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.isAdminOverride = true")
    long countAdminOverrides();
}
