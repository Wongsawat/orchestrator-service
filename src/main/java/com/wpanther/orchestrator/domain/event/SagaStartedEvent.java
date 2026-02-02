package com.wpanther.orchestrator.domain.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.IntegrationEvent;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;

/**
 * Event published when a saga is started.
 * Consumed by notification-service and other monitoring services.
 */
@Getter
@Builder
@Jacksonized  // Enable Jackson builder deserialization
public class SagaStartedEvent extends IntegrationEvent {

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
     * Constructor for creating a new SagaStartedEvent.
     */
    public SagaStartedEvent() {
        super();
        this.sagaId = null;
        this.correlationId = null;
        this.documentType = null;
        this.documentId = null;
        this.currentStep = null;
        this.invoiceNumber = null;
    }

    /**
     * Full constructor for Builder.
     */
    @Builder
    private SagaStartedEvent(String sagaId, String correlationId, String documentType,
                             String documentId, String currentStep, String invoiceNumber) {
        super();
        this.sagaId = sagaId;
        this.correlationId = correlationId;
        this.documentType = documentType;
        this.documentId = documentId;
        this.currentStep = currentStep;
        this.invoiceNumber = invoiceNumber;
    }
}
