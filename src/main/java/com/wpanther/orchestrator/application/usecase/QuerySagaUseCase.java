package com.wpanther.orchestrator.application.usecase;

import com.wpanther.orchestrator.domain.model.SagaInstance;
import com.wpanther.orchestrator.domain.model.enums.DocumentType;

import java.util.List;

public interface QuerySagaUseCase {

    SagaInstance getSagaInstance(String sagaId);

    List<SagaInstance> getActiveSagas();

    List<SagaInstance> getSagasForDocument(DocumentType documentType, String documentId);
}
