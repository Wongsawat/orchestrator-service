package com.wpanther.orchestrator.infrastructure.persistence;

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
}
