package com.wpanther.orchestrator.domain.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.IntegrationEvent;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;

/**
 * Event published when a saga completes successfully.
 * Consumed by notification-service and monitoring services.
 */
@Getter
@Builder
@Jacksonized
public class SagaCompletedEvent extends IntegrationEvent {

    private static final long serialVersionUID = 1L;

    @JsonProperty("sagaId")
    private final String sagaId;

    @JsonProperty("correlationId")
    private final String correlationId;

    @JsonProperty("documentType")
    private final String documentType;

    @JsonProperty("documentId")
    private final String documentId;

    @JsonProperty("invoiceNumber")
    private final String invoiceNumber;

    @JsonProperty("stepsExecuted")
    private final Integer stepsExecuted;

    @JsonProperty("startedAt")
    private final Instant startedAt;

    @JsonProperty("completedAt")
    private final Instant completedAt;

    @JsonProperty("durationMs")
    private final Long durationMs;

    public SagaCompletedEvent() {
        super();
        this.sagaId = null;
        this.correlationId = null;
        this.documentType = null;
        this.documentId = null;
        this.invoiceNumber = null;
        this.stepsExecuted = null;
        this.startedAt = null;
        this.completedAt = null;
        this.durationMs = null;
    }

    @Builder
    private SagaCompletedEvent(String sagaId, String correlationId, String documentType,
                               String documentId, String invoiceNumber, Integer stepsExecuted,
                               Instant startedAt, Instant completedAt, Long durationMs) {
        super();
        this.sagaId = sagaId;
        this.correlationId = correlationId;
        this.documentType = documentType;
        this.documentId = documentId;
        this.invoiceNumber = invoiceNumber;
        this.stepsExecuted = stepsExecuted;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.durationMs = durationMs;
    }
}
