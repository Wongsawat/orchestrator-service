package com.wpanther.orchestrator.domain.repository;

import com.wpanther.orchestrator.domain.model.SagaInstance;
import com.wpanther.orchestrator.domain.model.enums.DocumentType;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing saga instances.
 */
public interface SagaInstanceRepository {

    /**
     * Saves a saga instance.
     *
     * @param instance The saga instance to save
     * @return The saved saga instance
     */
    SagaInstance save(SagaInstance instance);

    /**
     * Finds a saga instance by its ID.
     *
     * @param id The saga instance ID
     * @return Optional containing the saga instance if found
     */
    Optional<SagaInstance> findById(String id);

    /**
     * Finds a saga instance by document type and document ID.
     *
     * @param documentType The document type
     * @param documentId   The document ID
     * @return Optional containing the saga instance if found
     */
    Optional<SagaInstance> findByDocumentTypeAndDocumentId(DocumentType documentType, String documentId);

    /**
     * Finds all saga instances with a given status.
     *
     * @param status The saga status
     * @return List of saga instances with the given status
     */
    List<SagaInstance> findByStatus(com.wpanther.saga.domain.enums.SagaStatus status);

    /**
     * Finds all saga instances for a document type.
     *
     * @param documentType The document type
     * @return List of saga instances for the document type
     */
    List<SagaInstance> findByDocumentType(DocumentType documentType);

    /**
     * Finds saga instances that have exceeded their timeout.
     *
     * @param timeoutSeconds The timeout in seconds
     * @return List of saga instances that need attention
     */
    List<SagaInstance> findTimeoutInstances(int timeoutSeconds);

    /**
     * Deletes a saga instance by its ID.
     *
     * @param id The saga instance ID
     */
    void deleteById(String id);

    /**
     * Returns a count of all saga instances.
     *
     * @return The total count
     */
    long count();
}
