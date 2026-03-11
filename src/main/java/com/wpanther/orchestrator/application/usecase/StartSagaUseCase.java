package com.wpanther.orchestrator.application.usecase;

import com.wpanther.orchestrator.domain.model.DocumentMetadata;
import com.wpanther.orchestrator.domain.model.SagaInstance;
import com.wpanther.orchestrator.domain.model.enums.DocumentType;

public interface StartSagaUseCase {

    SagaInstance startSaga(DocumentType documentType, String documentId, DocumentMetadata metadata);
}
