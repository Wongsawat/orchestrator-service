package com.wpanther.orchestrator.domain.service;

import com.wpanther.orchestrator.domain.model.DocumentMetadata;
import com.wpanther.orchestrator.domain.model.SagaInstance;
import com.wpanther.orchestrator.domain.model.enums.DocumentType;

/**
 * Domain service interface for saga orchestration operations.
 * Defines the core business operations for managing saga lifecycles.
 */
public interface SagaOrchestrationService {

    /**
     * Starts a new saga instance for document processing.
     *
     * @param documentType The type of document to process
     * @param documentId   The external document identifier
     * @param metadata     Additional metadata about the document
     * @return The created saga instance
     */
    SagaInstance startSaga(DocumentType documentType, String documentId, DocumentMetadata metadata);

    /**
     * Handles a reply from a service in response to a saga command.
     *
     * @param sagaId       The saga instance ID
     * @param step         The step that is replying
     * @param success      Whether the operation was successful
     * @param errorMessage Error message if not successful
     * @return The updated saga instance
     */
    SagaInstance handleReply(String sagaId, String step, boolean success, String errorMessage);

    /**
     * Retrieves a saga instance by its ID.
     *
     * @param sagaId The saga instance ID
     * @return The saga instance if found
     */
    SagaInstance getSagaInstance(String sagaId);

    /**
     * Advances a saga to the next step.
     *
     * @param sagaId The saga instance ID
     * @return The updated saga instance
     */
    SagaInstance advanceSaga(String sagaId);

    /**
     * Initiates compensation for a failed saga.
     *
     * @param sagaId       The saga instance ID
     * @param errorMessage The reason for compensation
     * @return The updated saga instance
     */
    SagaInstance initiateCompensation(String sagaId, String errorMessage);

    /**
     * Retries a failed saga step.
     *
     * @param sagaId The saga instance ID
     * @return The updated saga instance
     */
    SagaInstance retryStep(String sagaId);

    /**
     * Returns all active sagas.
     *
     * @return List of active saga instances
     */
    java.util.List<SagaInstance> getActiveSagas();

    /**
     * Returns all sagas for a specific document.
     *
     * @param documentType The document type
     * @param documentId   The document ID
     * @return List of saga instances for the document
     */
    java.util.List<SagaInstance> getSagasForDocument(DocumentType documentType, String documentId);
}
