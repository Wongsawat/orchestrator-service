package com.wpanther.orchestrator.application.usecase;

import com.wpanther.orchestrator.domain.model.SagaInstance;

/**
 * Use case for manual administrative operations on saga instances.
 * These are operator-driven actions invoked via the REST API,
 * distinct from the event-driven reply handling in {@link HandleSagaReplyUseCase}.
 */
public interface SagaManagementUseCase {

    SagaInstance advanceSaga(String sagaId);

    SagaInstance retryStep(String sagaId);
}
