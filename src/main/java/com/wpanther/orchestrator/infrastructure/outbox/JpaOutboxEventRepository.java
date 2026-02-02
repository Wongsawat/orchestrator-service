package com.wpanther.orchestrator.infrastructure.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for OutboxEventEntity.
 */
@Repository
public interface JpaOutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {
}
