package com.wpanther.orchestrator.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.TraceEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published when a saga completes successfully.
 * Consumed by notification-service and monitoring services.
 */
public class SagaCompletedEvent extends TraceEvent {

    private static final long serialVersionUID = 1L;

    @JsonProperty("documentType")
    private final String documentType;

    @JsonProperty("documentId")
    private final String documentId;

    @JsonProperty("documentNumber")
    private final String documentNumber;

    @JsonProperty("stepsExecuted")
    private final Integer stepsExecuted;

    @JsonProperty("startedAt")
    private final Instant startedAt;

    @JsonProperty("completedAt")
    private final Instant completedAt;

    @JsonProperty("durationMs")
    private final Long durationMs;

    /**
     * Convenience constructor for creating a new SagaCompletedEvent.
     */
    public SagaCompletedEvent(String sagaId, String correlationId, String documentType,
                              String documentId, String documentNumber, Integer stepsExecuted,
                              Instant startedAt, Instant completedAt, Long durationMs) {
        super(sagaId, correlationId, "orchestrator", "SAGA_COMPLETED", null);
        this.documentType = documentType;
        this.documentId = documentId;
        this.documentNumber = documentNumber;
        this.stepsExecuted = stepsExecuted;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.durationMs = durationMs;
    }

    /**
     * Full constructor for Jackson deserialization.
     */
    @JsonCreator
    public SagaCompletedEvent(
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
            @JsonProperty("documentNumber") String documentNumber,
            @JsonProperty("stepsExecuted") Integer stepsExecuted,
            @JsonProperty("startedAt") Instant startedAt,
            @JsonProperty("completedAt") Instant completedAt,
            @JsonProperty("durationMs") Long durationMs) {
        super(eventId, occurredAt, eventType, version, sagaId, correlationId, source, traceType, context);
        this.documentType = documentType;
        this.documentId = documentId;
        this.documentNumber = documentNumber;
        this.stepsExecuted = stepsExecuted;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.durationMs = durationMs;
    }

    // Getters for additional fields
    public String getDocumentType() {
        return documentType;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getDocumentNumber() {
        return documentNumber;
    }

    public Integer getStepsExecuted() {
        return stepsExecuted;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Long getDurationMs() {
        return durationMs;
    }
}
