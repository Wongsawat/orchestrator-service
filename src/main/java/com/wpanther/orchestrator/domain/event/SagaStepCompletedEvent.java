package com.wpanther.orchestrator.domain.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.IntegrationEvent;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/**
 * Event published when a saga step completes successfully.
 * Consumed by notification-service and monitoring services.
 */
@Getter
@Builder
@Jacksonized
public class SagaStepCompletedEvent extends IntegrationEvent {

    private static final long serialVersionUID = 1L;

    @JsonProperty("sagaId")
    private final String sagaId;

    @JsonProperty("correlationId")
    private final String correlationId;

    @JsonProperty("documentType")
    private final String documentType;

    @JsonProperty("completedStep")
    private final String completedStep;

    @JsonProperty("nextStep")
    private final String nextStep;

    public SagaStepCompletedEvent() {
        super();
        this.sagaId = null;
        this.correlationId = null;
        this.documentType = null;
        this.completedStep = null;
        this.nextStep = null;
    }

    @Builder
    private SagaStepCompletedEvent(String sagaId, String correlationId, String documentType,
                                   String completedStep, String nextStep) {
        super();
        this.sagaId = sagaId;
        this.correlationId = correlationId;
        this.documentType = documentType;
        this.completedStep = completedStep;
        this.nextStep = nextStep;
    }
}
