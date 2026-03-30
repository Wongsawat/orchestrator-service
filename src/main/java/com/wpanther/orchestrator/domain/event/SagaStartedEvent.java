package com.wpanther.orchestrator.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.TraceEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published when a saga is started.
 * Consumed by notification-service and other monitoring services.
 */
public class SagaStartedEvent extends TraceEvent {

    private static final long serialVersionUID = 1L;

    /**
     * Type of document being processed.
     */
    @JsonProperty("documentType")
    private final String documentType;

    /**
     * Document ID being processed.
     */
    @JsonProperty("documentId")
    private final String documentId;

    /**
     * The current step the saga is starting on.
     */
    @JsonProperty("currentStep")
    private final String currentStep;

    /**
     * Document number for reference.
     */
    @JsonProperty("documentNumber")
    private final String documentNumber;

    /**
     * Convenience constructor for creating a new SagaStartedEvent.
     */
    public SagaStartedEvent(String sagaId, String correlationId, String documentType,
                            String documentId, String currentStep, String documentNumber) {
        super(sagaId, correlationId, "orchestrator", "SAGA_STARTED", null);
        this.documentType = documentType;
        this.documentId = documentId;
        this.currentStep = currentStep;
        this.documentNumber = documentNumber;
    }

    /**
     * Full constructor for Jackson deserialization.
     */
    @JsonCreator
    public SagaStartedEvent(
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
            @JsonProperty("currentStep") String currentStep,
            @JsonProperty("documentNumber") String documentNumber) {
        super(eventId, occurredAt, eventType, version, sagaId, correlationId, source, traceType, context);
        this.documentType = documentType;
        this.documentId = documentId;
        this.currentStep = currentStep;
        this.documentNumber = documentNumber;
    }

    // Getters for additional fields
    public String getDocumentType() {
        return documentType;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getCurrentStep() {
        return currentStep;
    }

    public String getDocumentNumber() {
        return documentNumber;
    }
}
