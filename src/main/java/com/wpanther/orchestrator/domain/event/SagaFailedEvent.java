package com.wpanther.orchestrator.domain.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.IntegrationEvent;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;

/**
 * Event published when a saga fails.
 * Consumed by notification-service and monitoring services.
 */
@Getter
@Builder
@Jacksonized
public class SagaFailedEvent extends IntegrationEvent {

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

    @JsonProperty("failedStep")
    private final String failedStep;

    @JsonProperty("errorMessage")
    private final String errorMessage;

    @JsonProperty("retryCount")
    private final Integer retryCount;

    @JsonProperty("compensationInitiated")
    private final Boolean compensationInitiated;

    @JsonProperty("startedAt")
    private final Instant startedAt;

    @JsonProperty("failedAt")
    private final Instant failedAt;

    @JsonProperty("durationMs")
    private final Long durationMs;

    public SagaFailedEvent() {
        super();
        this.sagaId = null;
        this.correlationId = null;
        this.documentType = null;
        this.documentId = null;
        this.invoiceNumber = null;
        this.failedStep = null;
        this.errorMessage = null;
        this.retryCount = null;
        this.compensationInitiated = null;
        this.startedAt = null;
        this.failedAt = null;
        this.durationMs = null;
    }

    @Builder
    private SagaFailedEvent(String sagaId, String correlationId, String documentType,
                            String documentId, String invoiceNumber, String failedStep,
                            String errorMessage, Integer retryCount, Boolean compensationInitiated,
                            Instant startedAt, Instant failedAt, Long durationMs) {
        super();
        this.sagaId = sagaId;
        this.correlationId = correlationId;
        this.documentType = documentType;
        this.documentId = documentId;
        this.invoiceNumber = invoiceNumber;
        this.failedStep = failedStep;
        this.errorMessage = errorMessage;
        this.retryCount = retryCount;
        this.compensationInitiated = compensationInitiated;
        this.startedAt = startedAt;
        this.failedAt = failedAt;
        this.durationMs = durationMs;
    }
}
