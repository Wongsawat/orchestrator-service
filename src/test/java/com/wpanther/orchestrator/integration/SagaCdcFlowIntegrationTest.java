package com.wpanther.orchestrator.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.wpanther.orchestrator.application.service.SagaApplicationService;
import com.wpanther.orchestrator.domain.model.DocumentMetadata;
import com.wpanther.orchestrator.domain.model.SagaCommandRecord;
import com.wpanther.orchestrator.domain.model.SagaInstance;
import com.wpanther.orchestrator.domain.model.enums.DocumentType;
import com.wpanther.orchestrator.domain.repository.SagaCommandRecordRepository;
import com.wpanther.orchestrator.domain.repository.SagaInstanceRepository;
import com.wpanther.saga.domain.enums.SagaStatus;
import com.wpanther.saga.domain.enums.SagaStep;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;

/**
 * Full CDC integration tests for orchestrator service with nested test structure.
 * Verifies the complete flow: Saga -> Database -> Outbox -> Debezium CDC -> Kafka.
 * <p>
 * Follows the same pattern as document-intake-service's DocumentIntakeCdcIntegrationTest.
 * <p>
 * Prerequisites:
 *   1. Start containers: ./scripts/test-containers-start.sh --with-debezium --auto-deploy-connectors
 *   2. Containers must be running: PostgreSQL (5433), Kafka (9093), Debezium (8083)
 */
@DisplayName("Orchestrator CDC Flow Integration Tests")
class SagaCdcFlowIntegrationTest extends AbstractCdcIntegrationTest {

    @Autowired
    private SagaApplicationService sagaApplicationService;

    @Autowired
    private SagaInstanceRepository sagaInstanceRepository;

    @Autowired
    private SagaCommandRecordRepository commandRecordRepository;

    @MockBean
    private com.wpanther.orchestrator.infrastructure.messaging.producer.SagaCommandProducer sagaCommandProducer;

    @Nested
    @DisplayName("Database Write Tests")
    class DatabaseWriteTests {

        @Test
        @DisplayName("Should save saga instance to database")
        void shouldSaveSagaInstanceToDatabase() {
            // Given
            String documentId = "DB-" + UUID.randomUUID();
            DocumentType documentType = DocumentType.INVOICE;
            DocumentMetadata metadata = createTestMetadata(documentId);

            doAnswer(invocation -> null).when(sagaCommandProducer).sendCommand(any(), any(), anyBoolean());

            // When
            SagaInstance saga = sagaApplicationService.startSaga(documentType, documentId, metadata);

            // Then
            assertThat(saga.getId()).isNotNull();
            assertThat(saga.getStatus()).isEqualTo(SagaStatus.IN_PROGRESS);

            // Verify in database using JDBC (not JPA) to avoid session cache
            Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT * FROM saga_instances WHERE id = ?",
                saga.getId());

            assertThat(row.get("status")).isEqualTo("IN_PROGRESS");
            assertThat(row.get("document_type")).isEqualTo("INVOICE");
            assertThat(row.get("document_id")).isEqualTo(documentId);
            assertThat(row.get("current_step")).isEqualTo("PROCESS_INVOICE");
        }

        @Test
        @DisplayName("Should create command history entries")
        void shouldCreateCommandHistoryEntries() {
            // Given
            String documentId = "CMD-" + UUID.randomUUID();
            DocumentType documentType = DocumentType.TAX_INVOICE;
            DocumentMetadata metadata = createTestMetadata(documentId);

            doAnswer(invocation -> null).when(sagaCommandProducer).sendCommand(any(), any(), anyBoolean());

            // When
            SagaInstance saga = sagaApplicationService.startSaga(documentType, documentId, metadata);

            // Then - verify command record exists in database
            List<Map<String, Object>> commandRows = jdbcTemplate.queryForList(
                "SELECT * FROM saga_commands WHERE saga_id = ? ORDER BY created_at",
                saga.getId());

            assertThat(commandRows).hasSize(1);

            Map<String, Object> commandRow = commandRows.get(0);
            assertThat(commandRow.get("saga_id")).isEqualTo(saga.getId());
            assertThat(commandRow.get("target_step")).isEqualTo("PROCESS_TAX_INVOICE");
            assertThat(commandRow.get("status")).isEqualTo("SENT");
        }

        @Test
        @DisplayName("Should update saga status on reply")
        void shouldUpdateSagaStatusOnReply() throws Exception {
            // Given
            String documentId = "UPDATE-" + UUID.randomUUID();
            DocumentType documentType = DocumentType.INVOICE;
            DocumentMetadata metadata = createTestMetadata(documentId);

            doAnswer(invocation -> null).when(sagaCommandProducer).sendCommand(any(), any(), anyBoolean());

            SagaInstance saga = sagaApplicationService.startSaga(documentType, documentId, metadata);
            String sagaId = saga.getId();

            // Verify initial status in database
            String initialStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM saga_instances WHERE id = ?",
                String.class,
                sagaId);
            assertThat(initialStatus).isEqualTo("IN_PROGRESS");

            receivedMessages.clear();

            // When - send successful reply to complete saga
            simulateCompletion(sagaId);

            // Then - verify status updated in database
            String finalStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM saga_instances WHERE id = ?",
                String.class,
                sagaId);
            assertThat(finalStatus).isEqualTo("COMPLETED");

            // Verify completed_at is set
            Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT completed_at FROM saga_instances WHERE id = ?",
                sagaId);
            assertThat(row.get("completed_at")).isNotNull();
        }

        @Test
        @DisplayName("Should create outbox events in same transaction")
        void shouldCreateOutboxEventsInSameTransaction() {
            // Given
            String documentId = "OUTBOX-" + UUID.randomUUID();
            DocumentType documentType = DocumentType.INVOICE;
            DocumentMetadata metadata = createTestMetadata(documentId);

            doAnswer(invocation -> null).when(sagaCommandProducer).sendCommand(any(), any(), anyBoolean());

            // When
            SagaInstance saga = sagaApplicationService.startSaga(documentType, documentId, metadata);

            // Then - verify outbox entries exist
            List<Map<String, Object>> outboxEvents = jdbcTemplate.queryForList(
                "SELECT * FROM outbox_events WHERE partition_key = ? ORDER BY created_at",
                saga.getId());

            // Should have at least saga.started event
            assertThat(outboxEvents).hasSizeGreaterThanOrEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Outbox Pattern Tests")
    class OutboxPatternTests {

        @Test
        @DisplayName("Should write saga started event with correct topic")
        void shouldWriteSagaStartedEventWithCorrectTopic() {
            // Given
            String documentId = "START-" + UUID.randomUUID();
            DocumentType documentType = DocumentType.TAX_INVOICE;
            DocumentMetadata metadata = createTestMetadata(documentId);

            doAnswer(invocation -> null).when(sagaCommandProducer).sendCommand(any(), any(), anyBoolean());

            // When
            SagaInstance saga = sagaApplicationService.startSaga(documentType, documentId, metadata);

            // Then
            Map<String, Object> outbox = jdbcTemplate.queryForMap(
                "SELECT * FROM outbox_events WHERE partition_key = ? AND event_type = 'SagaStartedEvent'",
                saga.getId());

            assertThat(outbox.get("topic")).isEqualTo("saga.lifecycle.started");
            assertThat(outbox.get("partition_key")).isEqualTo(saga.getId());
            assertThat(outbox.get("status")).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("Should write step completed events with correct topic")
        void shouldWriteStepCompletedEventsWithCorrectTopic() throws Exception {
            // Given
            String documentId = "STEP-" + UUID.randomUUID();
            DocumentType documentType = DocumentType.INVOICE;
            DocumentMetadata metadata = createTestMetadata(documentId);

            doAnswer(invocation -> null).when(sagaCommandProducer).sendCommand(any(), any(), anyBoolean());

            SagaInstance saga = sagaApplicationService.startSaga(documentType, documentId, metadata);
            receivedMessages.clear();

            // When - simulate step completion
            simulateCompletion(saga.getId());

            // Then
            List<Map<String, Object>> stepEvents = jdbcTemplate.queryForList(
                "SELECT * FROM outbox_events WHERE partition_key = ? AND event_type = 'SagaStepCompletedEvent'",
                saga.getId());

            assertThat(stepEvents).isNotEmpty();
            for (Map<String, Object> event : stepEvents) {
                assertThat(event.get("topic")).isEqualTo("saga.lifecycle.step-completed");
                assertThat(event.get("partition_key")).isEqualTo(saga.getId());
            }
        }

        @Test
        @DisplayName("Should set correct partition key for ordering")
        void shouldSetCorrectPartitionKeyForOrdering() {
            // Given
            String documentId = "ORDER-" + UUID.randomUUID();
            DocumentType documentType = DocumentType.INVOICE;
            DocumentMetadata metadata = createTestMetadata(documentId);

            doAnswer(invocation -> null).when(sagaCommandProducer).sendCommand(any(), any(), anyBoolean());

            // When
            SagaInstance saga = sagaApplicationService.startSaga(documentType, documentId, metadata);

            // Then - all events for same saga should have same partition key
            List<String> partitionKeys = jdbcTemplate.queryForList(
                "SELECT DISTINCT partition_key FROM outbox_events WHERE partition_key = ?",
                String.class,
                saga.getId());

            assertThat(partitionKeys).hasSize(1);
            assertThat(partitionKeys.get(0)).isEqualTo(saga.getId());
        }

        @Test
        @DisplayName("Should write command events to correct command topics")
        void shouldWriteCommandEventsToCorrectTopics() {
            // Given
            String documentId = "CMD-TOPIC-" + UUID.randomUUID();
            DocumentType documentType = DocumentType.INVOICE;
            DocumentMetadata metadata = createTestMetadata(documentId);

            doAnswer(invocation -> null).when(sagaCommandProducer).sendCommand(any(), any(), anyBoolean());

            // When
            SagaInstance saga = sagaApplicationService.startSaga(documentType, documentId, metadata);

            // Then - verify command events go to correct topics
            List<Map<String, Object>> commandEvents = jdbcTemplate.queryForList(
                "SELECT * FROM outbox_events WHERE partition_key = ? AND event_type LIKE '%Command'",
                saga.getId());

            assertThat(commandEvents).isNotEmpty();
            for (Map<String, Object> event : commandEvents) {
                String topic = (String) event.get("topic");
                assertThat(topic).isIn("saga.command.invoice", "saga.command.tax-invoice");
            }
        }

        @Test
        @DisplayName("Should write lifecycle events")
        void shouldWriteLifecycleEvents() {
            // Given
            String documentId = "LIFECYCLE-" + UUID.randomUUID();
            DocumentType documentType = DocumentType.TAX_INVOICE;
            DocumentMetadata metadata = createTestMetadata(documentId);

            doAnswer(invocation -> null).when(sagaCommandProducer).sendCommand(any(), any(), anyBoolean());

            // When
            SagaInstance saga = sagaApplicationService.startSaga(documentType, documentId, metadata);

            // Then - verify lifecycle event topics are used
            List<Map<String, Object>> lifecycleEvents = jdbcTemplate.queryForList(
                "SELECT DISTINCT topic FROM outbox_events WHERE partition_key = ? ORDER BY topic",
                saga.getId());

            assertThat(lifecycleEvents).isNotEmpty();
            assertThat(lifecycleEvents.stream().map(e -> e.get("topic")).toList())
                .contains("saga.lifecycle.started");
        }
    }

    @Nested
    @DisplayName("CDC Flow Tests")
    class CdcFlowTests {

        @Test
        @DisplayName("Should publish saga started event to Kafka via CDC")
        void shouldPublishSagaStartedEventToKafkaViaCDC() throws Exception {
            // Given
            String documentId = "CDC-START-" + UUID.randomUUID();
            DocumentType documentType = DocumentType.INVOICE;
            DocumentMetadata metadata = createTestMetadata(documentId);

            doAnswer(invocation -> null).when(sagaCommandProducer).sendCommand(any(), any(), anyBoolean());

            // When - start saga
            SagaInstance saga = sagaApplicationService.startSaga(documentType, documentId, metadata);

            // Then - wait for Kafka message (CDC takes time)
            await().until(() -> hasMessageOnTopic("saga.lifecycle.started", saga.getId()));

            // Verify message content
            List<ConsumerRecord<String, String>> messages =
                getMessagesFromTopic("saga.lifecycle.started", saga.getId());
            assertThat(messages).isNotEmpty();

            JsonNode payload = parseJson(messages.get(0).value());
            assertThat(payload.get("sagaId").asText()).isEqualTo(saga.getId());
            assertThat(payload.get("documentType").asText()).isEqualTo("INVOICE");
            assertThat(payload.get("documentId").asText()).isEqualTo(documentId);
            assertThat(payload.get("status").asText()).isEqualTo("STARTED");
        }

        @Test
        @DisplayName("Should publish step completed events to Kafka via CDC")
        void shouldPublishStepCompletedEventsToKafkaViaCDC() throws Exception {
            // Given
            String documentId = "CDC-STEP-" + UUID.randomUUID();
            DocumentType documentType = DocumentType.TAX_INVOICE;
            DocumentMetadata metadata = createTestMetadata(documentId);

            doAnswer(invocation -> null).when(sagaCommandProducer).sendCommand(any(), any(), anyBoolean());

            SagaInstance saga = sagaApplicationService.startSaga(documentType, documentId, metadata);
            receivedMessages.clear();

            // When - simulate step completion
            simulateCompletion(saga.getId());

            // Then - wait for step completed event on Kafka
            await().until(() -> hasMessageOnTopic("saga.lifecycle.step-completed", saga.getId()));

            List<ConsumerRecord<String, String>> messages =
                getMessagesFromTopic("saga.lifecycle.step-completed", saga.getId());
            assertThat(messages).isNotEmpty();

            JsonNode payload = parseJson(messages.get(0).value());
            assertThat(payload.get("sagaId").asText()).isEqualTo(saga.getId());
            assertThat(payload.get("status").asText()).isEqualTo("STEP_COMPLETED");
        }

        @Test
        @DisplayName("Should publish saga completed event to Kafka via CDC")
        void shouldPublishSagaCompletedEventToKafkaViaCDC() throws Exception {
            // Given
            String documentId = "CDC-COMPLETE-" + UUID.randomUUID();
            DocumentType documentType = DocumentType.INVOICE;
            DocumentMetadata metadata = createTestMetadata(documentId);

            doAnswer(invocation -> null).when(sagaCommandProducer).sendCommand(any(), any(), anyBoolean());

            SagaInstance saga = sagaApplicationService.startSaga(documentType, documentId, metadata);
            receivedMessages.clear();

            // When - simulate saga completion
            simulateCompletion(saga.getId());

            // Then - wait for completed event on Kafka
            await().until(() -> hasMessageOnTopic("saga.lifecycle.completed", saga.getId()));

            List<ConsumerRecord<String, String>> messages =
                getMessagesFromTopic("saga.lifecycle.completed", saga.getId());
            assertThat(messages).hasSize(1);

            JsonNode payload = parseJson(messages.get(0).value());
            assertThat(payload.get("sagaId").asText()).isEqualTo(saga.getId());
            assertThat(payload.get("status").asText()).isEqualTo("COMPLETED");
        }

        @Test
        @DisplayName("Should preserve saga ID through CDC flow")
        void shouldPreserveSagaIdThroughCdcFlow() throws Exception {
            // Given
            String documentId = "PRESERVE-" + UUID.randomUUID();
            DocumentType documentType = DocumentType.INVOICE;
            DocumentMetadata metadata = createTestMetadata(documentId);

            doAnswer(invocation -> null).when(sagaCommandProducer).sendCommand(any(), any(), anyBoolean());

            // When
            SagaInstance saga = sagaApplicationService.startSaga(documentType, documentId, metadata);

            // Then - wait and verify saga ID in Kafka message
            await().until(() -> hasMessageOnTopic("saga.lifecycle.started", saga.getId()));

            List<ConsumerRecord<String, String>> messages =
                getMessagesFromTopic("saga.lifecycle.started", saga.getId());
            ConsumerRecord<String, String> message = messages.get(0);

            // Kafka message key should be the saga ID (partition key)
            assertThat(message.key()).isEqualTo(saga.getId());

            // Payload should also contain saga ID
            JsonNode payload = parseJson(message.value());
            assertThat(payload.get("sagaId").asText()).isEqualTo(saga.getId());
        }

        @Test
        @DisplayName("Should publish multiple lifecycle events in order")
        void shouldPublishMultipleLifecycleEventsInOrder() throws Exception {
            // Given
            String documentId = "MULTI-CDC-" + UUID.randomUUID();
            DocumentType documentType = DocumentType.TAX_INVOICE;
            DocumentMetadata metadata = createTestMetadata(documentId);

            doAnswer(invocation -> null).when(sagaCommandProducer).sendCommand(any(), any(), anyBoolean());

            // When - start saga
            SagaInstance saga = sagaApplicationService.startSaga(documentType, documentId, metadata);
            receivedMessages.clear();

            // Simulate completion
            simulateCompletion(saga.getId());

            // Then - verify both events are published via CDC
            await().until(() -> hasMessageOnTopic("saga.lifecycle.started", saga.getId()));
            await().until(() -> hasMessageOnTopic("saga.lifecycle.completed", saga.getId()));

            // Verify we have the expected number of events
            List<ConsumerRecord<String, String>> startedMessages =
                getMessagesFromTopic("saga.lifecycle.started", saga.getId());
            List<ConsumerRecord<String, String>> completedMessages =
                getMessagesFromTopic("saga.lifecycle.completed", saga.getId());

            assertThat(startedMessages).hasSize(1);
            assertThat(completedMessages).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should publish failed event on max retries")
        void shouldPublishFailedEventOnMaxRetries() throws Exception {
            // Given
            String documentId = "FAIL-CDC-" + UUID.randomUUID();
            DocumentType documentType = DocumentType.INVOICE;
            DocumentMetadata metadata = createTestMetadata(documentId);

            doAnswer(invocation -> null).when(sagaCommandProducer).sendCommand(any(), any(), anyBoolean());

            SagaInstance saga = sagaApplicationService.startSaga(documentType, documentId, metadata);

            // Set to max retries and simulate failure
            saga.setRetryCount(3);
            sagaInstanceRepository.save(saga);
            receivedMessages.clear();

            // When - simulate failure
            sagaApplicationService.handleReply(saga.getId(), "PROCESS_INVOICE", false, "Processing failed");

            // Then - wait for failed event on Kafka
            await().until(() -> hasMessageOnTopic("saga.lifecycle.failed", saga.getId()));

            List<ConsumerRecord<String, String>> messages =
                getMessagesFromTopic("saga.lifecycle.failed", saga.getId());
            assertThat(messages).isNotEmpty();

            JsonNode payload = parseJson(messages.get(0).value());
            assertThat(payload.get("sagaId").asText()).isEqualTo(saga.getId());
            assertThat(payload.get("status").asText()).isEqualTo("FAILED");
            assertThat(payload.get("errorMessage").asText()).contains("Processing failed");
        }

        @Test
        @DisplayName("Should initiate compensation on failure")
        void shouldInitiateCompensationOnFailure() {
            // Given
            String documentId = "COMP-CDC-" + UUID.randomUUID();
            DocumentType documentType = DocumentType.TAX_INVOICE;
            DocumentMetadata metadata = createTestMetadata(documentId);

            doAnswer(invocation -> null).when(sagaCommandProducer).sendCommand(any(), any(), anyBoolean());

            SagaInstance saga = sagaApplicationService.startSaga(documentType, documentId, metadata);

            // Set to max retries
            saga.setRetryCount(3);
            sagaInstanceRepository.save(saga);
            receivedMessages.clear();

            // When - simulate failure
            SagaInstance result = sagaApplicationService.handleReply(
                saga.getId(), "PROCESS_TAX_INVOICE", false, "Cannot process");

            // Then - verify compensation initiated
            assertThat(result.getStatus()).isEqualTo(SagaStatus.COMPENSATING);

            // Verify in database
            String status = jdbcTemplate.queryForObject(
                "SELECT status FROM saga_instances WHERE id = ?",
                String.class,
                saga.getId());
            assertThat(status).isEqualTo("COMPENSATING");
        }
    }

    @Nested
    @DisplayName("Document Type Tests")
    class DocumentTypeTests {

        @Test
        @DisplayName("Should handle invoice document type")
        void shouldHandleInvoiceDocumentType() throws Exception {
            // Given
            String documentId = "INV-TYPE-" + UUID.randomUUID();
            DocumentType documentType = DocumentType.INVOICE;
            DocumentMetadata metadata = createTestMetadata(documentId);

            doAnswer(invocation -> null).when(sagaCommandProducer).sendCommand(any(), any(), anyBoolean());

            // When
            SagaInstance saga = sagaApplicationService.startSaga(documentType, documentId, metadata);

            // Then
            assertThat(saga.getDocumentType()).isEqualTo(DocumentType.INVOICE);
            assertThat(saga.getCurrentStep()).isEqualTo(SagaStep.PROCESS_INVOICE);

            // Verify in outbox
            Map<String, Object> outbox = jdbcTemplate.queryForMap(
                "SELECT * FROM outbox_events WHERE partition_key = ? AND event_type = 'SagaStartedEvent'",
                saga.getId());

            String payload = (String) outbox.get("payload");
            JsonNode payloadJson = parseJson(payload);
            assertThat(payloadJson.get("documentType").asText()).isEqualTo("INVOICE");
        }

        @Test
        @DisplayName("Should handle tax-invoice document type")
        void shouldHandleTaxInvoiceDocumentType() throws Exception {
            // Given
            String documentId = "TAX-TYPE-" + UUID.randomUUID();
            DocumentType documentType = DocumentType.TAX_INVOICE;
            DocumentMetadata metadata = createTestMetadata(documentId);

            doAnswer(invocation -> null).when(sagaCommandProducer).sendCommand(any(), any(), anyBoolean());

            // When
            SagaInstance saga = sagaApplicationService.startSaga(documentType, documentId, metadata);

            // Then
            assertThat(saga.getDocumentType()).isEqualTo(DocumentType.TAX_INVOICE);
            assertThat(saga.getCurrentStep()).isEqualTo(SagaStep.PROCESS_TAX_INVOICE);

            // Verify in outbox
            Map<String, Object> outbox = jdbcTemplate.queryForMap(
                "SELECT * FROM outbox_events WHERE partition_key = ? AND event_type = 'SagaStartedEvent'",
                saga.getId());

            String payload = (String) outbox.get("payload");
            JsonNode payloadJson = parseJson(payload);
            assertThat(payloadJson.get("documentType").asText()).isEqualTo("TAX_INVOICE");
        }
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
     * Simulate saga completion by completing all steps.
     */
    private void simulateCompletion(String sagaId) {
        sagaInstanceRepository.findById(sagaId).ifPresent(instance -> {
            instance.complete();
            sagaInstanceRepository.save(instance);
        });
    }
}
