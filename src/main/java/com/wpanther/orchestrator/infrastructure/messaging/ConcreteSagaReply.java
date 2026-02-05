package com.wpanther.orchestrator.infrastructure.messaging;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.enums.ReplyStatus;
import com.wpanther.saga.domain.model.SagaReply;

import java.time.Instant;
import java.util.UUID;

/**
 * Concrete implementation of {@link SagaReply} for Jackson deserialization.
 * <p>
 * {@code SagaReply} is abstract, so Jackson cannot instantiate it directly.
 * This class provides the concrete type needed by the Kafka {@code JsonDeserializer}.
 */
public class ConcreteSagaReply extends SagaReply {

    private static final long serialVersionUID = 1L;

    @JsonCreator
    public ConcreteSagaReply(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("occurredAt") Instant occurredAt,
            @JsonProperty("eventType") String eventType,
            @JsonProperty("version") int version,
            @JsonProperty("sagaId") String sagaId,
            @JsonProperty("sagaStep") String sagaStep,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("status") ReplyStatus status,
            @JsonProperty("errorMessage") String errorMessage) {
        super(eventId, occurredAt, eventType, version, sagaId, sagaStep, correlationId, status, errorMessage);
    }
}
