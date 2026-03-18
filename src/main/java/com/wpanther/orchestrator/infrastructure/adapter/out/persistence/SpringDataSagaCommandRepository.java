package com.wpanther.orchestrator.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Spring Data JPA repository for SagaCommandEntity.
 */
interface SpringDataSagaCommandRepository extends JpaRepository<SagaCommandEntity, String> {

    /**
     * Finds all commands for a saga instance ordered by creation time.
     */
    List<SagaCommandEntity> findBySagaIdOrderByCreatedAtAsc(String sagaId);

    /**
     * Counts commands for a saga instance.
     */
    long countBySagaId(String sagaId);

    /**
     * Deletes all commands for a saga instance.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM SagaCommandEntity c WHERE c.sagaId = :sagaId")
    void deleteBySagaId(@Param("sagaId") String sagaId);

    /**
     * Finds the latest command for a saga instance.
     */
    @Query("SELECT c FROM SagaCommandEntity c WHERE c.sagaId = :sagaId ORDER BY c.createdAt DESC LIMIT 1")
    List<SagaCommandEntity> findLatestCommandBySagaId(@Param("sagaId") String sagaId);

    /**
     * Finds all commands for multiple saga instances, ordered by creation time.
     * Used for batch loading to avoid N+1 queries.
     */
    @Query("SELECT c FROM SagaCommandEntity c WHERE c.sagaId IN :sagaIds ORDER BY c.sagaId, c.createdAt ASC")
    List<SagaCommandEntity> findBySagaIdInOrderByCreatedAtAsc(@Param("sagaIds") List<String> sagaIds);
}
