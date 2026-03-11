package com.wpanther.orchestrator.application.usecase;

import com.wpanther.orchestrator.domain.model.DocumentMetadata;
import com.wpanther.orchestrator.domain.model.SagaInstance;
import com.wpanther.orchestrator.domain.model.enums.DocumentType;

public interface StartSagaUseCase {

    /**
     * Starts a new saga with an explicit correlation ID for end-to-end tracing.
     * If {@code correlationId} is null, the implementation generates one.
     */
    SagaInstance startSaga(DocumentType documentType, String documentId,
                           DocumentMetadata metadata, String correlationId);

    /**
     * Convenience overload that generates a new correlation ID.
     */
    default SagaInstance startSaga(DocumentType documentType, String documentId, DocumentMetadata metadata) {
        return startSaga(documentType, documentId, metadata, null);
    }
}
