package com.wpanther.orchestrator.domain.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published when a saga step completes successfully.
 * Consumed by notification-service and monitoring services.
 */
@Getter
@Builder
public class SagaStepCompletedEvent {

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
     * The step that was completed.
     */
    @JsonProperty("completedStep")
    private final String completedStep;

    /**
     * The next step to be executed, if any.
     */
    @JsonProperty("nextStep")
    private final String nextStep;
}
