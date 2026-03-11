package com.wpanther.orchestrator.application.usecase;

import com.wpanther.orchestrator.domain.model.SagaInstance;

/**
 * Use case for manual administrative operations on saga instances.
 * These are operator-driven actions invoked via the REST API,
 * distinct from the event-driven reply handling in {@link HandleSagaReplyUseCase}.
 */
public interface SagaManagementUseCase {

    /**
     * Manually advances the saga to its next step.
     * Intended for operator use when a saga is stuck in a completed step
     * but the automatic transition did not fire.
     *
     * @throws RuntimeException if no saga exists with that ID
     */
    SagaInstance advanceSaga(String sagaId);

    /**
     * Retries the current failed step of the saga.
     * Resets the retry counter and re-publishes the last command.
     *
     * @throws RuntimeException if no saga exists with that ID
     */
    SagaInstance retryStep(String sagaId);
}
