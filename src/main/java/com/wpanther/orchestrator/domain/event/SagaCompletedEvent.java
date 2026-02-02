package com.wpanther.orchestrator.domain.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published when a saga completes successfully.
 * Consumed by notification-service and monitoring services.
 */
@Getter
@Builder
public class SagaCompletedEvent {

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
     * Type of document that was processed.
     */
    @JsonProperty("documentType")
    private final String documentType;

    /**
     * Document ID that was processed.
     */
    @JsonProperty("documentId")
    private final String documentId;

    /**
     * Invoice/tax invoice number for reference.
     */
    @JsonProperty("invoiceNumber")
    private final String invoiceNumber;

    /**
     * Number of steps executed in this saga.
     */
    @JsonProperty("stepsExecuted")
    private final Integer stepsExecuted;

    /**
     * Timestamp when the saga started.
     */
    @JsonProperty("startedAt")
    private final Instant startedAt;

    /**
     * Timestamp when the saga completed.
     */
    @JsonProperty("completedAt")
    private final Instant completedAt;

    /**
     * Duration in milliseconds.
     */
    @JsonProperty("durationMs")
    private final Long durationMs;
}
