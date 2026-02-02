package com.wpanther.orchestrator.domain.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published when a saga is started.
 * Consumed by notification-service and other monitoring services.
 */
@Getter
@Builder
public class SagaStartedEvent {

    @JsonProperty("eventId")
    @Builder.Default
    private final UUID eventId = UUID.randomUUID();

    @JsonProperty("occurredAt")
    @Builder.Default
    private final Instant occurredAt = Instant.now();

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
}
