package com.wpanther.orchestrator.infrastructure.messaging;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.enums.ReplyStatus;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.domain.model.SagaReply;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Concrete implementation of {@link SagaReply} for Jackson deserialization.
 * <p>
 * {@code SagaReply} is abstract, so Jackson cannot instantiate it directly.
 * This class provides the concrete type needed by the Kafka {@code JsonDeserializer}.
 * <p>
 * Extra fields from service-specific reply events (e.g., {@code signedPdfUrl} from
 * PdfSigningReplyEvent) are captured in {@code additionalData} via {@code @JsonAnySetter},
 * enabling the orchestrator to propagate step result data to subsequent steps.
 */
public class ConcreteSagaReply extends SagaReply {

    private static final long serialVersionUID = 1L;

    private final Map<String, Object> additionalData = new HashMap<>();

    @JsonCreator
    public ConcreteSagaReply(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("occurredAt") Instant occurredAt,
            @JsonProperty("eventType") String eventType,
            @JsonProperty("version") int version,
            @JsonProperty("sagaId") String sagaId,
            @JsonProperty("sagaStep") SagaStep sagaStep,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("status") ReplyStatus status,
            @JsonProperty("errorMessage") String errorMessage) {
        super(eventId, occurredAt, eventType, version, sagaId, sagaStep, correlationId, status, errorMessage);
    }

    @JsonAnySetter
    public void setAdditionalData(String key, Object value) {
        this.additionalData.put(key, value);
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalData() {
        return additionalData;
    }
}
