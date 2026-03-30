package com.wpanther.orchestrator.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.orchestrator.domain.event.SagaCompletedEvent;
import com.wpanther.orchestrator.domain.event.SagaFailedEvent;
import com.wpanther.orchestrator.domain.event.SagaStartedEvent;
import com.wpanther.orchestrator.domain.event.SagaStepCompletedEvent;
import com.wpanther.orchestrator.domain.model.SagaInstance;
import com.wpanther.orchestrator.infrastructure.metrics.SagaMetrics;
import com.wpanther.saga.domain.enums.SagaStatus;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * Publisher for saga lifecycle events via the outbox pattern.
 * All events are written to the outbox table within the same transaction
 * as the domain state changes, ensuring atomicity.
 * Debezium CDC reads the outbox table and publishes events to Kafka.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SagaEventPublisher {

    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;
    private final SagaMetrics sagaMetrics;

    @Value("${app.kafka.topics.saga-lifecycle-started:saga.lifecycle.started}")
    private String sagaStartedTopic;

    @Value("${app.kafka.topics.saga-lifecycle-step-completed:saga.lifecycle.step-completed}")
    private String sagaStepCompletedTopic;

    @Value("${app.kafka.topics.saga-lifecycle-completed:saga.lifecycle.completed}")
    private String sagaCompletedTopic;

    @Value("${app.kafka.topics.saga-lifecycle-failed:saga.lifecycle.failed}")
    private String sagaFailedTopic;

    /**
     * Expected maximum duration for a saga to complete (in milliseconds).
     * Used for logging warnings when sagas take longer than expected.
     * Default: 5 minutes (300,000 ms).
     * Configurable via app.saga.expected-max-duration-ms property.
     */
    @Value("${app.saga.expected-max-duration-ms:300000}")
    private long expectedMaxDurationMs;

    /**
     * Publishes a SagaStartedEvent when a new saga is created.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishSagaStarted(SagaInstance saga, String correlationId, String documentNumber) {
        // Record metric
        sagaMetrics.recordSagaStarted(saga.getDocumentType());

        SagaStartedEvent event = new SagaStartedEvent(
            saga.getId(),
            correlationId,
            saga.getDocumentType().name(),
            saga.getDocumentId(),
            saga.getCurrentStep() != null ? saga.getCurrentStep().getCode() : null,
            documentNumber
        );

        Map<String, String> headers = new HashMap<>();
        headers.put("sagaId", saga.getId());
        headers.put("correlationId", correlationId);
        headers.put("documentType", saga.getDocumentType().name());

        String headersJson = toJson(headers);

        outboxService.saveWithRouting(
            event,
            "SagaInstance",
            saga.getId(),
            sagaStartedTopic,
            saga.getId(),  // partition key - use sagaId for consistent ordering
            headersJson
        );

        log.debug("Published SagaStartedEvent for saga {}", saga.getId());
    }

    /**
     * Publishes a SagaStepCompletedEvent when a step completes successfully.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishSagaStepCompleted(SagaInstance saga, SagaStep completedStep,
                                         String correlationId) {
        // Record metric
        sagaMetrics.recordStepCompleted(saga.getDocumentType(), completedStep);

        SagaStep nextStep = saga.getNextStep();

        SagaStepCompletedEvent event = new SagaStepCompletedEvent(
            saga.getId(),
            correlationId,
            saga.getDocumentType().name(),
            completedStep.getCode(),
            nextStep != null ? nextStep.getCode() : null
        );

        Map<String, String> headers = new HashMap<>();
        headers.put("sagaId", saga.getId());
        headers.put("correlationId", correlationId);
        headers.put("documentType", saga.getDocumentType().name());

        String headersJson = toJson(headers);

        outboxService.saveWithRouting(
            event,
            "SagaInstance",
            saga.getId(),
            sagaStepCompletedTopic,
            saga.getId(),  // partition key - use sagaId for consistent ordering
            headersJson
        );

        log.debug("Published SagaStepCompletedEvent for saga {}, step {}", saga.getId(), completedStep);
    }

    /**
     * Publishes a SagaCompletedEvent when a saga completes successfully.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishSagaCompleted(SagaInstance saga, String correlationId, String documentNumber) {
        long durationMs = java.time.Duration.between(saga.getCreatedAt(), saga.getCompletedAt()).toMillis();

        // Record metric
        sagaMetrics.recordSagaCompleted(saga.getDocumentType(), durationMs);

        SagaCompletedEvent event = new SagaCompletedEvent(
            saga.getId(),
            correlationId,
            saga.getDocumentType().name(),
            saga.getDocumentId(),
            documentNumber,
            saga.getCommandHistory().size(),
            saga.getCreatedAt(),
            saga.getCompletedAt(),
            durationMs
        );

        Map<String, String> headers = new HashMap<>();
        headers.put("sagaId", saga.getId());
        headers.put("correlationId", correlationId);
        headers.put("documentType", saga.getDocumentType().name());

        String headersJson = toJson(headers);

        outboxService.saveWithRouting(
            event,
            "SagaInstance",
            saga.getId(),
            sagaCompletedTopic,
            saga.getId(),  // partition key - use sagaId for consistent ordering
            headersJson
        );

        // Log warning if saga exceeded expected duration
        if (durationMs > expectedMaxDurationMs) {
            log.warn("Saga {} completed with duration {}ms, exceeding expected max of {}ms",
                saga.getId(), durationMs, expectedMaxDurationMs);
        } else {
            log.info("Published SagaCompletedEvent for saga {} with duration {}ms", saga.getId(), durationMs);
        }
    }

    /**
     * Publishes a SagaFailedEvent when a saga fails.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishSagaFailed(SagaInstance saga, SagaStep failedStep, String errorMessage,
                                  String correlationId, String documentNumber) {
        long durationMs = java.time.Duration.between(saga.getCreatedAt(), saga.getUpdatedAt()).toMillis();
        boolean compensating = SagaStatus.COMPENSATING.equals(saga.getStatus());

        // Record metrics
        sagaMetrics.recordSagaFailed(saga.getDocumentType(), durationMs, compensating);
        if (failedStep != null) {
            sagaMetrics.recordStepFailed(saga.getDocumentType(), failedStep);
        }

        SagaFailedEvent event = new SagaFailedEvent(
            saga.getId(),
            correlationId,
            saga.getDocumentType().name(),
            saga.getDocumentId(),
            documentNumber,
            failedStep != null ? failedStep.getCode() : null,
            errorMessage,
            saga.getRetryCount(),
            compensating,
            saga.getCreatedAt(),
            saga.getUpdatedAt(),
            durationMs
        );

        Map<String, String> headers = new HashMap<>();
        headers.put("sagaId", saga.getId());
        headers.put("correlationId", correlationId);
        headers.put("documentType", saga.getDocumentType().name());

        String headersJson = toJson(headers);

        outboxService.saveWithRouting(
            event,
            "SagaInstance",
            saga.getId(),
            sagaFailedTopic,
            saga.getId(),  // partition key - use sagaId for consistent ordering
            headersJson
        );

        log.warn("Published SagaFailedEvent for saga {} at step {}, compensating: {}",
            saga.getId(), failedStep, compensating);
    }

    private String toJson(Map<String, String> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize headers to JSON", e);
            return null;
        }
    }
}
