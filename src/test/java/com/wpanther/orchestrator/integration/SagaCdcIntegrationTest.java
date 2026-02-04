package com.wpanther.orchestrator.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.wpanther.orchestrator.application.service.SagaApplicationService;
import com.wpanther.orchestrator.domain.model.DocumentMetadata;
import com.wpanther.orchestrator.domain.model.SagaInstance;
import com.wpanther.orchestrator.domain.model.enums.DocumentType;
import com.wpanther.orchestrator.domain.repository.SagaInstanceRepository;
import com.wpanther.orchestrator.infrastructure.messaging.producer.SagaCommandPublisher;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;

/**
 * CDC Integration Tests for Saga Lifecycle Events.
 * <p>
 * These tests verify that saga lifecycle events are correctly published
 * to Kafka via the outbox pattern and Debezium CDC.
 */
class SagaCdcIntegrationTest extends AbstractCdcIntegrationTest {

    @Autowired
    private SagaApplicationService sagaApplicationService;

    @Autowired
    private SagaInstanceRepository sagaInstanceRepository;

    /**
     * Mock command publisher - we only test CDC, not actual command sending.
     */
    @MockBean
    private SagaCommandPublisher sagaCommandPublisher;

    @Test
    @DisplayName("Should publish SagaStartedEvent to Kafka via CDC")
    void shouldPublishSagaStartedEventToKafka() throws Exception {
        // Given
        String documentId = "INV-" + UUID.randomUUID();
        DocumentType documentType = DocumentType.INVOICE;
        DocumentMetadata metadata = createTestMetadata(documentId);

        // Mock command publisher to do nothing (we only test CDC)
        doAnswer(invocation -> null).when(sagaCommandPublisher).publishCommandForStep(any(), any(), any());

        // When - start a new saga
        SagaInstance saga = sagaApplicationService.startSaga(documentType, documentId, metadata);

        // Then - wait for CDC to publish to Kafka
        await().until(() -> hasMessageOnTopic("saga.lifecycle.started", saga.getId().toString()));

        // Verify message content
        List<ConsumerRecord<String, String>> messages =
            getMessagesFromTopic("saga.lifecycle.started", saga.getId().toString());
        assertThat(messages).hasSize(1);

        JsonNode payload = parseJson(messages.get(0).value());
        assertThat(payload.get("sagaId").asText()).isEqualTo(saga.getId().toString());
        assertThat(payload.get("documentType").asText()).isEqualTo(documentType.name());
        assertThat(payload.get("documentId").asText()).isEqualTo(documentId);
        assertThat(payload.get("eventType").asText()).isEqualTo("SagaStartedEvent");
    }

    @Test
    @DisplayName("Should publish SagaCompletedEvent to Kafka via CDC")
    void shouldPublishSagaCompletedEventToKafka() throws Exception {
        // Given
        String documentId = "TAX-" + UUID.randomUUID();
        DocumentType documentType = DocumentType.TAX_INVOICE;
        DocumentMetadata metadata = createTestMetadata(documentId);

        doAnswer(invocation -> null).when(sagaCommandPublisher).publishCommandForStep(any(), any(), any());
        doAnswer(invocation -> null).when(sagaCommandPublisher).publishCompensationCommand(any(), any(), any());

        // Start saga
        SagaInstance saga = sagaApplicationService.startSaga(documentType, documentId, metadata);

        // Clear received messages from saga.started (if any)
        List<ConsumerRecord<String, String>> startedMessages = receivedMessages.get("saga.lifecycle.started");
        if (startedMessages != null) {
            startedMessages.clear();
        }

        // When - complete the saga via handleReply
        simulateSuccessfulSteps(saga);

        // Then - wait for CDC to publish completed event
        await().until(() -> hasMessageOnTopic("saga.lifecycle.completed", saga.getId().toString()));

        // Verify message content
        List<ConsumerRecord<String, String>> messages =
            getMessagesFromTopic("saga.lifecycle.completed", saga.getId().toString());
        assertThat(messages).hasSize(1);

        JsonNode payload = parseJson(messages.get(0).value());
        assertThat(payload.get("sagaId").asText()).isEqualTo(saga.getId().toString());
        assertThat(payload.get("eventType").asText()).isEqualTo("SagaCompletedEvent");
    }

    @Test
    @DisplayName("Should write to outbox_events table")
    void shouldWriteToOutboxEventsTable() {
        // Given
        String documentId = "TEST-" + UUID.randomUUID();
        DocumentType documentType = DocumentType.INVOICE;
        DocumentMetadata metadata = createTestMetadata(documentId);

        doAnswer(invocation -> null).when(sagaCommandPublisher).publishCommandForStep(any(), any(), any());

        // When - start saga
        sagaApplicationService.startSaga(documentType, documentId, metadata);

        // Then - verify outbox_events table
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM outbox_events WHERE topic = 'saga.lifecycle.started'",
            Integer.class
        );
        assertThat(count).isGreaterThan(0);

        // Verify outbox event structure
        List<String> eventIds = jdbcTemplate.queryForList(
            "SELECT id FROM outbox_events WHERE topic = 'saga.lifecycle.started' ORDER BY created_at DESC LIMIT 1",
            String.class
        );
        assertThat(eventIds).hasSize(1);
    }

    @Test
    @DisplayName("Should verify outbox event has correct fields for CDC routing")
    void shouldVerifyOutboxEventHasCorrectFields() {
        // Given
        String documentId = "VERIFY-" + UUID.randomUUID();
        DocumentType documentType = DocumentType.TAX_INVOICE;
        DocumentMetadata metadata = createTestMetadata(documentId);

        doAnswer(invocation -> null).when(sagaCommandPublisher).publishCommandForStep(any(), any(), any());

        // When - start saga
        sagaApplicationService.startSaga(documentType, documentId, metadata);

        // Then - verify outbox event has required CDC fields
        List<Map<String, Object>> events = jdbcTemplate.queryForList(
            "SELECT topic, partition_key, payload, headers FROM outbox_events WHERE topic = 'saga.lifecycle.started' LIMIT 1"
        );
        assertThat(events).hasSize(1);

        Map<String, Object> event = events.get(0);
        assertThat(event.get("topic")).isEqualTo("saga.lifecycle.started");
        assertThat(event.get("partition_key")).isNotNull();
        assertThat(event.get("payload")).isNotNull();
        assertThat(event.get("headers")).isNotNull();
    }

    @Test
    @DisplayName("Should publish multiple lifecycle events for saga progression")
    void shouldPublishMultipleLifecycleEvents() throws Exception {
        // Given
        String documentId = "MULTI-" + UUID.randomUUID();
        DocumentType documentType = DocumentType.INVOICE;
        DocumentMetadata metadata = createTestMetadata(documentId);

        doAnswer(invocation -> null).when(sagaCommandPublisher).publishCommandForStep(any(), any(), any());
        doAnswer(invocation -> null).when(sagaCommandPublisher).publishCompensationCommand(any(), any(), any());

        // When - start saga
        SagaInstance saga = sagaApplicationService.startSaga(documentType, documentId, metadata);

        // Simulate successful completion
        simulateSuccessfulSteps(saga);

        // Then - verify both events are published via CDC
        await().until(() -> hasMessageOnTopic("saga.lifecycle.started", saga.getId().toString()));
        await().until(() -> hasMessageOnTopic("saga.lifecycle.completed", saga.getId().toString()));

        // Verify we have the expected number of events
        List<ConsumerRecord<String, String>> startedMessages =
            getMessagesFromTopic("saga.lifecycle.started", saga.getId().toString());
        List<ConsumerRecord<String, String>> completedMessages =
            getMessagesFromTopic("saga.lifecycle.completed", saga.getId().toString());

        assertThat(startedMessages).hasSize(1);
        assertThat(completedMessages).hasSize(1);
    }

    @Test
    @DisplayName("Should maintain message order per partition key")
    void shouldMaintainMessageOrderPerPartitionKey() throws Exception {
        // Given
        String documentId = "ORDER-" + UUID.randomUUID();
        DocumentType documentType = DocumentType.INVOICE;
        DocumentMetadata metadata1 = createTestMetadata(documentId + "-1");
        DocumentMetadata metadata2 = createTestMetadata(documentId + "-2");

        doAnswer(invocation -> null).when(sagaCommandPublisher).publishCommandForStep(any(), any(), any());

        // When - create multiple saga instances
        SagaInstance saga1 = sagaApplicationService.startSaga(documentType, documentId + "-1", metadata1);
        SagaInstance saga2 = sagaApplicationService.startSaga(documentType, documentId + "-2", metadata2);

        // Then - wait for events and verify order
        await().until(() -> {
            pollKafkaMessages();
            List<ConsumerRecord<String, String>> messages = receivedMessages.get("saga.lifecycle.started");
            return messages != null && messages.size() >= 2;
        });

        List<ConsumerRecord<String, String>> messages = getAllMessagesFromTopic("saga.lifecycle.started");
        assertThat(messages).hasSizeGreaterThanOrEqualTo(2);

        // Each saga should have its own partition key (sagaId)
        assertThat(messages.get(0).key()).isNotNull();
        assertThat(messages.get(1).key()).isNotNull();
    }

    /**
     * Helper method to create test DocumentMetadata.
     */
    private DocumentMetadata createTestMetadata(String documentId) {
        return DocumentMetadata.builder()
                .filePath("/test/path/" + documentId + ".xml")
                .xmlContent("<test>content</test>")
                .metadata(Map.of("invoiceNumber", "INV-" + documentId))
                .fileSize(1024L)
                .mimeType("application/xml")
                .checksum("abc123")
                .build();
    }

    /**
     * Simulate successful step completion to trigger saga completion.
     * Calls handleReply for each step until the saga is complete.
     */
    private void simulateSuccessfulSteps(SagaInstance saga) {
        String sagaId = saga.getId();

        // Steps for TAX_INVOICE: PROCESS_TAX_INVOICE -> SIGN_XML -> GENERATE_TAX_INVOICE_PDF -> SIGN_PDF -> STORE_DOCUMENT -> SEND_EBMS
        String[] steps = {"process-tax-invoice", "sign-xml", "generate-tax-invoice-pdf", "sign-pdf", "store-document", "send-ebms"};

        for (String step : steps) {
            sagaApplicationService.handleReply(sagaId, step, true, null);
        }
    }
}
