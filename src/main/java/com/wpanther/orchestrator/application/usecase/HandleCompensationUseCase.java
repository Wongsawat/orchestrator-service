package com.wpanther.orchestrator.application.usecase;

import com.wpanther.orchestrator.domain.model.SagaInstance;

public interface HandleCompensationUseCase {

    SagaInstance initiateCompensation(String sagaId, String errorMessage);
}
