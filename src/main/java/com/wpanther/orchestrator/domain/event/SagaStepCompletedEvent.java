package com.wpanther.orchestrator.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.TraceEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published when a saga step completes successfully.
 * Consumed by notification-service and monitoring services.
 */
public class SagaStepCompletedEvent extends TraceEvent {

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

    /**
     * Convenience constructor for creating a new SagaStepCompletedEvent.
     */
    public SagaStepCompletedEvent(String sagaId, String correlationId, String documentType,
                                   String completedStep, String nextStep) {
        super(sagaId, "orchestrator", "STEP_COMPLETED");
        this.sagaId = sagaId;
        this.correlationId = correlationId;
        this.documentType = documentType;
        this.completedStep = completedStep;
        this.nextStep = nextStep;
    }

    /**
     * Full constructor for Jackson deserialization.
     */
    @JsonCreator
    public SagaStepCompletedEvent(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("occurredAt") Instant occurredAt,
            @JsonProperty("eventType") String eventType,
            @JsonProperty("version") int version,
            @JsonProperty("sagaId") String sagaId,
            @JsonProperty("source") String source,
            @JsonProperty("traceType") String traceType,
            @JsonProperty("context") String context,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("documentType") String documentType,
            @JsonProperty("completedStep") String completedStep,
            @JsonProperty("nextStep") String nextStep) {
        super(eventId, occurredAt, eventType, version, sagaId, source, traceType, context);
        this.sagaId = sagaId;
        this.correlationId = correlationId;
        this.documentType = documentType;
        this.completedStep = completedStep;
        this.nextStep = nextStep;
    }

    // Getters for additional fields
    public String getSagaId() {
        return sagaId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getDocumentType() {
        return documentType;
    }

    public String getCompletedStep() {
        return completedStep;
    }

    public String getNextStep() {
        return nextStep;
    }
}
