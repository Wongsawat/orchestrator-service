package com.wpanther.orchestrator.infrastructure.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Service for writing events to the outbox table.
 * Uses MANDATORY transaction propagation to ensure outbox writes are part of the same transaction
 * as the domain state changes, providing atomicity guarantees.
 *
 * Debezium CDC connector reads the outbox_events table and publishes events to Kafka topics
 * based on the 'topic' column value.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxService {

    private final JpaOutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    /**
     * Write event to outbox within current transaction.
     * Debezium CDC will read and publish to Kafka.
     *
     * @param aggregateType Type of the aggregate (e.g., "SagaInstance")
     * @param aggregateId ID of the aggregate instance
     * @param eventType Type of event (e.g., "SagaStartedEvent", "ProcessInvoiceCommand")
     * @param topic Kafka topic to publish to
     * @param payload Event payload (will be serialized to JSON)
     * @param headers Additional metadata headers for Kafka message
     * @throws RuntimeException if payload serialization fails
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void writeEvent(String aggregateType, String aggregateId,
                          String eventType, String topic, Object payload,
                          Map<String, String> headers) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            String headersJson = headers != null ? objectMapper.writeValueAsString(headers) : null;

            OutboxEventEntity event = OutboxEventEntity.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .topic(topic)
                .partitionKey(headers != null ? headers.get("correlationId") : aggregateId)
                .payload(payloadJson)
                .headers(headersJson)
                .status("PENDING")
                .build();

            repository.save(event);
            log.debug("Outbox event written: type={}, topic={}, aggregateId={}",
                eventType, topic, aggregateId);

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outbox event: type={}, aggregateId={}",
                eventType, aggregateId, e);
            throw new RuntimeException("Failed to serialize outbox event", e);
        }
    }
}
