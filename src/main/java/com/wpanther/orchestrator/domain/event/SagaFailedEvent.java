package com.wpanther.orchestrator.domain.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published when a saga fails.
 * Consumed by notification-service and monitoring services.
 */
@Getter
@Builder
public class SagaFailedEvent {

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
     * Type of document that failed processing.
     */
    @JsonProperty("documentType")
    private final String documentType;

    /**
     * Document ID that failed processing.
     */
    @JsonProperty("documentId")
    private final String documentId;

    /**
     * Invoice/tax invoice number for reference.
     */
    @JsonProperty("invoiceNumber")
    private final String invoiceNumber;

    /**
     * The step that failed.
     */
    @JsonProperty("failedStep")
    private final String failedStep;

    /**
     * Error message describing the failure.
     */
    @JsonProperty("errorMessage")
    private final String errorMessage;

    /**
     * Number of retry attempts made.
     */
    @JsonProperty("retryCount")
    private final Integer retryCount;

    /**
     * Whether compensation was initiated.
     */
    @JsonProperty("compensationInitiated")
    private final Boolean compensationInitiated;

    /**
     * Timestamp when the saga started.
     */
    @JsonProperty("startedAt")
    private final Instant startedAt;

    /**
     * Timestamp when the saga failed.
     */
    @JsonProperty("failedAt")
    private final Instant failedAt;

    /**
     * Duration in milliseconds before failure.
     */
    @JsonProperty("durationMs")
    private final Long durationMs;
}
