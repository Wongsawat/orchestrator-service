package com.wpanther.orchestrator.application.usecase;

import com.wpanther.orchestrator.domain.model.SagaInstance;

import java.util.Map;

public interface HandleSagaReplyUseCase {

    SagaInstance handleReply(String sagaId, String step, boolean success, String errorMessage);

    SagaInstance handleReply(String sagaId, String step, boolean success,
                             String errorMessage, Map<String, Object> resultData);

}
