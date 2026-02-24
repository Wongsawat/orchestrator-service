package com.wpanther.orchestrator.infrastructure.messaging.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.orchestrator.domain.event.SagaCompletedEvent;
import com.wpanther.orchestrator.domain.event.SagaFailedEvent;
import com.wpanther.orchestrator.domain.event.SagaStartedEvent;
import com.wpanther.orchestrator.domain.event.SagaStepCompletedEvent;
import com.wpanther.orchestrator.domain.model.SagaInstance;
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

    @Value("${app.kafka.topics.saga-lifecycle-started:saga.lifecycle.started}")
    private String sagaStartedTopic;

    @Value("${app.kafka.topics.saga-lifecycle-step-completed:saga.lifecycle.step-completed}")
    private String sagaStepCompletedTopic;

    @Value("${app.kafka.topics.saga-lifecycle-completed:saga.lifecycle.completed}")
    private String sagaCompletedTopic;

    @Value("${app.kafka.topics.saga-lifecycle-failed:saga.lifecycle.failed}")
    private String sagaFailedTopic;

    /**
     * Publishes a SagaStartedEvent when a new saga is created.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishSagaStarted(SagaInstance saga, String correlationId, String invoiceNumber) {
        SagaStartedEvent event = new SagaStartedEvent(
            saga.getId(),
            correlationId,
            saga.getDocumentType().name(),
            saga.getDocumentId(),
            saga.getCurrentStep() != null ? saga.getCurrentStep().getCode() : null,
            invoiceNumber
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
    public void publishSagaCompleted(SagaInstance saga, String correlationId, String invoiceNumber) {
        long durationMs = java.time.Duration.between(saga.getCreatedAt(), saga.getCompletedAt()).toMillis();

        SagaCompletedEvent event = new SagaCompletedEvent(
            saga.getId(),
            correlationId,
            saga.getDocumentType().name(),
            saga.getDocumentId(),
            invoiceNumber,
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

        log.info("Published SagaCompletedEvent for saga {} with duration {}ms", saga.getId(), durationMs);
    }

    /**
     * Publishes a SagaFailedEvent when a saga fails.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishSagaFailed(SagaInstance saga, SagaStep failedStep, String errorMessage,
                                  String correlationId, String invoiceNumber) {
        long durationMs = java.time.Duration.between(saga.getCreatedAt(), saga.getUpdatedAt()).toMillis();
        boolean compensating = saga.getStatus() == SagaStatus.COMPENSATING;

        SagaFailedEvent event = new SagaFailedEvent(
            saga.getId(),
            correlationId,
            saga.getDocumentType().name(),
            saga.getDocumentId(),
            invoiceNumber,
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
