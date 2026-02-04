package com.wpanther.orchestrator.infrastructure.persistence.outbox;

import com.wpanther.saga.domain.outbox.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for OutboxEventEntity.
 * Provides query methods for outbox event operations.
 */
@Repository
public interface SpringDataOrchestratorOutboxRepository extends JpaRepository<OutboxEventEntity, UUID> {

    /**
     * Find pending events ordered by creation time.
     * Used by polling publisher to find events awaiting publication.
     *
     * @param status   The status to filter by (typically PENDING)
     * @param pageable Pagination parameters
     * @return List of outbox events with the specified status
     */
    List<OutboxEventEntity> findByStatusOrderByCreatedAtAsc(OutboxStatus status, Pageable pageable);

    /**
     * Find failed events ordered by creation time.
     * Used by retry mechanism to find events that failed publication.
     *
     * @param pageable Pagination parameters
     * @return List of failed outbox events
     */
    @Query("SELECT e FROM OutboxEventEntity e WHERE e.status = 'FAILED' ORDER BY e.createdAt ASC")
    List<OutboxEventEntity> findFailedEventsOrderByCreatedAtAsc(Pageable pageable);

    /**
     * Find all events for a specific aggregate.
     * Used for tracing and debugging event flow for specific aggregates.
     *
     * @param aggregateType The aggregate type (e.g., "SagaInstance")
     * @param aggregateId  The ID of the aggregate
     * @return List of outbox events for the aggregate, ordered by creation time
     */
    List<OutboxEventEntity> findByAggregateTypeAndAggregateIdOrderByCreatedAtAsc(
            String aggregateType,
            String aggregateId
    );

    /**
     * Delete published events before a specified time.
     * Used by cleanup service to remove old published events.
     *
     * @param before Delete events published before this time
     * @return Number of events deleted
     */
    @Modifying
    @Query("DELETE FROM OutboxEventEntity e WHERE e.status = 'PUBLISHED' AND e.publishedAt < :before")
    int deletePublishedBefore(@Param("before") Instant before);
}
