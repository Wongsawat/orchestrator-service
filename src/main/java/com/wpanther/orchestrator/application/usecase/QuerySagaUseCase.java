package com.wpanther.orchestrator.application.usecase;

import com.wpanther.orchestrator.domain.model.SagaInstance;
import com.wpanther.orchestrator.domain.model.enums.DocumentType;
import com.wpanther.saga.domain.enums.SagaStatus;

import java.util.List;

public interface QuerySagaUseCase {

    /**
     * Returns the saga instance with the given ID.
     *
     * @throws RuntimeException if no saga exists with that ID
     */
    SagaInstance getSagaInstance(String sagaId);

    /**
     * Returns all sagas currently in {@code IN_PROGRESS} status.
     * Convenience bridge for {@link #getSagasByStatus(SagaStatus)}.
     */
    default List<SagaInstance> getActiveSagas() {
        return getSagasByStatus(SagaStatus.IN_PROGRESS);
    }

    /**
     * Returns all sagas associated with the given document type and document ID.
     */
    List<SagaInstance> getSagasForDocument(DocumentType documentType, String documentId);

    /**
     * Returns all sagas with the given status.
     */
    List<SagaInstance> getSagasByStatus(SagaStatus status);
}
