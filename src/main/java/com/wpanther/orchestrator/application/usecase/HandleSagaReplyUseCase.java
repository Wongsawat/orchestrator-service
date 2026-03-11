package com.wpanther.orchestrator.application.usecase;

import com.wpanther.orchestrator.domain.model.SagaInstance;

import java.util.Map;

public interface HandleSagaReplyUseCase {

    /**
     * Handles a step reply with optional step-specific result data.
     * If {@code resultData} is non-empty and the step succeeded, values are merged
     * into the saga's metadata map for use by subsequent steps.
     */
    SagaInstance handleReply(String sagaId, String step, boolean success,
                             String errorMessage, Map<String, Object> resultData);

    /**
     * Convenience overload for replies that carry no result data.
     */
    default SagaInstance handleReply(String sagaId, String step, boolean success, String errorMessage) {
        return handleReply(sagaId, step, success, errorMessage, null);
    }
}
