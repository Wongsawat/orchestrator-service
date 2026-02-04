package com.wpanther.orchestrator.infrastructure.persistence.outbox;

import com.wpanther.saga.domain.outbox.OutboxEvent;
import com.wpanther.saga.domain.outbox.OutboxEventRepository;
import com.wpanther.saga.domain.outbox.OutboxStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA implementation of saga-commons OutboxEventRepository interface.
 * Bridges between domain OutboxEvent and JPA OutboxEventEntity.
 * <p>
 * Each service provides its own implementation to ensure the outbox table
 * resides in the service's own database for transactional consistency.
 */
@Component
public class JpaOrchestratorOutboxRepository implements OutboxEventRepository {

    private static final Logger log = LoggerFactory.getLogger(JpaOrchestratorOutboxRepository.class);

    private final SpringDataOrchestratorOutboxRepository springRepository;

    public JpaOrchestratorOutboxRepository(SpringDataOrchestratorOutboxRepository springRepository) {
        this.springRepository = springRepository;
    }

    @Override
    public OutboxEvent save(OutboxEvent event) {
        log.debug("Saving outbox event: {} for aggregate: {}/{}",
                event.getId(), event.getAggregateType(), event.getAggregateId());

        OutboxEventEntity entity = OutboxEventEntity.fromDomain(event);
        OutboxEventEntity savedEntity = springRepository.save(entity);

        return savedEntity.toDomain();
    }

    @Override
    public Optional<OutboxEvent> findById(UUID id) {
        return springRepository.findById(id)
                .map(OutboxEventEntity::toDomain);
    }

    @Override
    public List<OutboxEvent> findPendingEvents(int limit) {
        log.debug("Finding pending events with limit: {}", limit);

        return springRepository.findByStatusOrderByCreatedAtAsc(
                        OutboxStatus.PENDING,
                        Pageable.ofSize(limit)
                ).stream()
                .map(OutboxEventEntity::toDomain)
                .toList();
    }

    @Override
    public List<OutboxEvent> findFailedEvents(int limit) {
        log.debug("Finding failed events with limit: {}", limit);

        return springRepository.findFailedEventsOrderByCreatedAtAsc(
                        Pageable.ofSize(limit)
                ).stream()
                .map(OutboxEventEntity::toDomain)
                .toList();
    }

    @Override
    public int deletePublishedBefore(java.time.Instant before) {
        log.debug("Deleting published events before: {}", before);

        int deletedCount = springRepository.deletePublishedBefore(before);
        log.info("Deleted {} published events before: {}", deletedCount, before);

        return deletedCount;
    }

    @Override
    public List<OutboxEvent> findByAggregate(String aggregateType, String aggregateId) {
        log.debug("Finding events for aggregate: {}/{}", aggregateType, aggregateId);

        return springRepository.findByAggregateTypeAndAggregateIdOrderByCreatedAtAsc(
                        aggregateType,
                        aggregateId
                ).stream()
                .map(OutboxEventEntity::toDomain)
                .toList();
    }
}
