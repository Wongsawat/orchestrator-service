package com.wpanther.orchestrator.infrastructure.adapter.out.persistence;

import com.wpanther.orchestrator.domain.model.enums.DocumentType;
import com.wpanther.saga.domain.enums.SagaStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for SagaInstanceEntity.
 */
interface SpringDataSagaInstanceRepository extends JpaRepository<SagaInstanceEntity, String> {

    /**
     * Finds a saga instance by document type and document ID.
     */
    Optional<SagaInstanceEntity> findByDocumentTypeAndDocumentId(DocumentType documentType, String documentId);

    /**
     * Finds all saga instances with a given status.
     */
    List<SagaInstanceEntity> findByStatus(SagaStatus status);

    /**
     * Finds all saga instances for a document type.
     */
    List<SagaInstanceEntity> findByDocumentType(DocumentType documentType);

    /**
     * Finds saga instances that have not been updated since a given time.
     */
    List<SagaInstanceEntity> findByStatusInAndUpdatedAtBefore(
            List<SagaStatus> statuses,
            Instant timeout
    );

    /**
     * Finds saga instances with status in a list of values.
     */
    List<SagaInstanceEntity> findByStatusIn(List<SagaStatus> statuses);

    /**
     * Counts saga instances by status.
     */
    long countByStatus(SagaStatus status);

    /**
     * Finds saga instances that need timeout processing.
     */
    @Query("SELECT s FROM SagaInstanceEntity s WHERE s.status IN :statuses AND s.updatedAt < :timeout")
    List<SagaInstanceEntity> findTimeoutInstances(
            @Param("statuses") List<SagaStatus> statuses,
            @Param("timeout") Instant timeout
    );

    /**
     * Finds a saga instance by ID using a projection that excludes CLOB columns.
     * This avoids PostgreSQL JDBC LOB API issues when loading large TEXT columns
     * (xml_content, metadata) by not selecting them at all.
     */
    @Query("SELECT s.id, s.documentType, s.documentId, s.currentStep, s.status, "
            + "s.createdAt, s.updatedAt, s.completedAt, s.errorMessage, "
            + "s.correlationId, s.retryCount, s.maxRetries "
            + "FROM SagaInstanceEntity s WHERE s.id = :id")
    Optional<SagaInstanceSimple> findByIdWithoutClob(@Param("id") String id);
}
