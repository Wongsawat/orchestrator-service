package com.wpanther.orchestrator.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.TraceEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published when a saga fails.
 * Consumed by notification-service and monitoring services.
 */
public class SagaFailedEvent extends TraceEvent {

    private static final long serialVersionUID = 1L;

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

    /**
     * Convenience constructor for creating a new SagaFailedEvent.
     */
    public SagaFailedEvent(String sagaId, String correlationId, String documentType,
                           String documentId, String invoiceNumber, String failedStep,
                           String errorMessage, Integer retryCount, Boolean compensationInitiated,
                           Instant startedAt, Instant failedAt, Long durationMs) {
        super(sagaId, correlationId, "orchestrator", "SAGA_FAILED", null);
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

    /**
     * Full constructor for Jackson deserialization.
     */
    @JsonCreator
    public SagaFailedEvent(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("occurredAt") Instant occurredAt,
            @JsonProperty("eventType") String eventType,
            @JsonProperty("version") int version,
            @JsonProperty("sagaId") String sagaId,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("source") String source,
            @JsonProperty("traceType") String traceType,
            @JsonProperty("context") String context,
            @JsonProperty("documentType") String documentType,
            @JsonProperty("documentId") String documentId,
            @JsonProperty("invoiceNumber") String invoiceNumber,
            @JsonProperty("failedStep") String failedStep,
            @JsonProperty("errorMessage") String errorMessage,
            @JsonProperty("retryCount") Integer retryCount,
            @JsonProperty("compensationInitiated") Boolean compensationInitiated,
            @JsonProperty("startedAt") Instant startedAt,
            @JsonProperty("failedAt") Instant failedAt,
            @JsonProperty("durationMs") Long durationMs) {
        super(eventId, occurredAt, eventType, version, sagaId, correlationId, source, traceType, context);
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

    // Getters for additional fields
    public String getDocumentType() {
        return documentType;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public String getFailedStep() {
        return failedStep;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public Boolean getCompensationInitiated() {
        return compensationInitiated;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFailedAt() {
        return failedAt;
    }

    public Long getDurationMs() {
        return durationMs;
    }
}
