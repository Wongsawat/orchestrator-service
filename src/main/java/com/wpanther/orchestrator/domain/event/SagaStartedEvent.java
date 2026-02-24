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
     * ID of the saga instance.
     */
    @JsonProperty("sagaId")
    private final String sagaId;

    /**
     * Correlation ID for tracing across services.
     */
    @JsonProperty("correlationId")
    private final String correlationId;

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
     * Invoice/tax invoice number for reference.
     */
    @JsonProperty("invoiceNumber")
    private final String invoiceNumber;

    /**
     * Convenience constructor for creating a new SagaStartedEvent.
     */
    public SagaStartedEvent(String sagaId, String correlationId, String documentType,
                            String documentId, String currentStep, String invoiceNumber) {
        super(sagaId, "orchestrator", "SAGA_STARTED");
        this.sagaId = sagaId;
        this.correlationId = correlationId;
        this.documentType = documentType;
        this.documentId = documentId;
        this.currentStep = currentStep;
        this.invoiceNumber = invoiceNumber;
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
            @JsonProperty("source") String source,
            @JsonProperty("traceType") String traceType,
            @JsonProperty("context") String context,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("documentType") String documentType,
            @JsonProperty("documentId") String documentId,
            @JsonProperty("currentStep") String currentStep,
            @JsonProperty("invoiceNumber") String invoiceNumber) {
        super(eventId, occurredAt, eventType, version, sagaId, source, traceType, context);
        this.sagaId = sagaId;
        this.correlationId = correlationId;
        this.documentType = documentType;
        this.documentId = documentId;
        this.currentStep = currentStep;
        this.invoiceNumber = invoiceNumber;
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

    public String getDocumentId() {
        return documentId;
    }

    public String getCurrentStep() {
        return currentStep;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }
}
