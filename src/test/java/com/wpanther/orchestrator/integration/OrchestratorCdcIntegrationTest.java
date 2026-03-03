package com.wpanther.orchestrator.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.wpanther.orchestrator.application.usecase.SagaApplicationService;
import com.wpanther.orchestrator.domain.model.DocumentMetadata;
import com.wpanther.orchestrator.domain.model.SagaInstance;
import com.wpanther.orchestrator.domain.model.enums.DocumentType;
import com.wpanther.orchestrator.port.out.SagaInstanceRepository;
import com.wpanther.orchestrator.adapter.out.messaging.SagaCommandPublisher;
import com.wpanther.saga.domain.enums.SagaStep;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Full CDC integration tests for orchestrator service.
 * Verifies the complete flow: Saga -> Database -> Outbox -> Debezium CDC -> Kafka.
 * <p>
 * These tests require external infrastructure (PostgreSQL on port 5433, Kafka on port 9093, Debezium on port 8083).
 * They are only enabled when the system property "integration.tests.enabled" is set to "true".
 * <p>
 * To run: mvn test -Dintegration.tests.enabled=true -Dtest=OrchestratorCdcIntegrationTest -Dspring.profiles.active=cdc-test
 * Or: mvn test -Pintegration -Dspring.profiles.active=cdc-test
 */
@DisplayName("Orchestrator CDC Integration Tests")
@Tag("integration")
@EnabledIfSystemProperty(named = "integration.tests.enabled", matches = "true")
class OrchestratorCdcIntegrationTest extends AbstractCdcIntegrationTest {

    @Autowired
    private SagaApplicationService sagaApplicationService;

    @Autowired
    private SagaInstanceRepository sagaInstanceRepository;

    /**
     * Mock command publisher - we only test CDC lifecycle events, not command sending.
     */
    @MockBean
    private SagaCommandPublisher sagaCommandPublisher;

    // ==================== Helper Methods ====================

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
     * Start a saga with mocked command publisher.
     */
    private SagaInstance startTestSaga(DocumentType documentType, String documentId) {
        doAnswer(invocation -> null).when(sagaCommandPublisher).publishCommandForStep(any(), any(), any());
        doAnswer(invocation -> null).when(sagaCommandPublisher).publishCompensationCommand(any(), any(), any());
        DocumentMetadata metadata = createTestMetadata(documentId);
        return sagaApplicationService.startSaga(documentType, documentId, metadata);
    }

    /**
     * Complete all invoice steps: process-invoice -> sign-xml -> generate-invoice-pdf -> sign-pdf -> store-document -> send-ebms
     */
    private void completeAllInvoiceSteps(String sagaId) {
        String[] steps = {"process-invoice", "sign-xml", "generate-invoice-pdf", "sign-pdf", "store-document", "send-ebms"};
        for (String step : steps) {
            sagaApplicationService.handleReply(sagaId, step, true, null);
        }
    }

    /**
     * Complete all tax invoice steps: process-tax-invoice -> sign-xml -> generate-tax-invoice-pdf -> sign-pdf -> store-document -> send-ebms
     */
    private void completeAllTaxInvoiceSteps(String sagaId) {
        String[] steps = {"process-tax-invoice", "sign-xml", "generate-tax-invoice-pdf", "sign-pdf", "store-document", "send-ebms"};
        for (String step : steps) {
            sagaApplicationService.handleReply(sagaId, step, true, null);
        }
    }

    // ==================== Database Write Tests ====================

    @Nested
    @DisplayName("Database Write Tests")
    class DatabaseWriteTests {

        @Test
        @DisplayName("Should save saga instance to database")
        void shouldSaveSagaInstanceToDatabase() {
            // Given
            String documentId = "DB-INV-" + UUID.randomUUID();

            // When
            SagaInstance saga = startTestSaga(DocumentType.INVOICE, documentId);

            // Then - verify via JDBC (bypasses JPA cache)
            Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT * FROM saga_instances WHERE id = ?", saga.getId());

            assertThat(row.get("status")).isEqualTo("IN_PROGRESS");
            assertThat(row.get("document_type")).isEqualTo("INVOICE");
            assertThat(row.get("current_step")).isEqualTo("PROCESS_INVOICE");
            assertThat(row.get("document_id")).isEqualTo(documentId);
        }

        @Test
        @DisplayName("Should create command record in database")
        void shouldCreateCommandRecordInDatabase() {
            // Given
            String documentId = "DB-TAX-" + UUID.randomUUID();

            // When
            SagaInstance saga = startTestSaga(DocumentType.TAX_INVOICE, documentId);

            // Then
            List<Map<String, Object>> commands = jdbcTemplate.queryForList(
                "SELECT * FROM saga_commands WHERE saga_id = ?", saga.getId());

            assertThat(commands).isNotEmpty();
            Map<String, Object> firstCommand = commands.get(0);
            assertThat(firstCommand.get("target_step")).isEqualTo("PROCESS_TAX_INVOICE");
            assertThat(firstCommand.get("status")).isEqualTo("SENT");
        }

        @Test
        @DisplayName("Should create outbox events in same transaction")
        void shouldCreateOutboxEventsInSameTransaction() {
            // Given
            String documentId = "DB-OUTBOX-" + UUID.randomUUID();

            // When
            SagaInstance saga = startTestSaga(DocumentType.INVOICE, documentId);

            // Then
            List<Map<String, Object>> outboxEvents = jdbcTemplate.queryForList(
                "SELECT * FROM outbox_events WHERE aggregate_id = ? ORDER BY created_at",
                saga.getId());

            // Should have at least SagaStartedEvent (+ possibly command outbox events)
            assertThat(outboxEvents).hasSizeGreaterThanOrEqualTo(1);

            // Verify the SagaStartedEvent exists
            boolean hasSagaStartedEvent = outboxEvents.stream()
                .anyMatch(e -> "SagaStartedEvent".equals(e.get("event_type")));
            assertThat(hasSagaStartedEvent).isTrue();
        }

        @Test
        @DisplayName("Should update saga status on completion")
        void shouldUpdateSagaStatusOnCompletion() {
            // Given
            String documentId = "DB-COMPLETE-" + UUID.randomUUID();
            SagaInstance saga = startTestSaga(DocumentType.INVOICE, documentId);

            // When
            completeAllInvoiceSteps(saga.getId());

            // Then
            Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT * FROM saga_instances WHERE id = ?", saga.getId());

            assertThat(row.get("status")).isEqualTo("COMPLETED");
            assertThat(row.get("completed_at")).isNotNull();
        }
    }

    // ==================== Outbox Pattern Tests ====================

    @Nested
    @DisplayName("Outbox Pattern Tests")
    class OutboxPatternTests {

        @Test
        @DisplayName("Should write SagaStartedEvent with correct topic")
        void shouldWriteSagaStartedEventWithCorrectTopic() {
            // Given
            String documentId = "OBX-STARTED-" + UUID.randomUUID();

            // When
            SagaInstance saga = startTestSaga(DocumentType.TAX_INVOICE, documentId);

            // Then
            Map<String, Object> outbox = jdbcTemplate.queryForMap(
                "SELECT * FROM outbox_events WHERE aggregate_id = ? AND event_type = 'SagaStartedEvent'",
                saga.getId());

            assertThat(outbox.get("topic")).isEqualTo("saga.lifecycle.started");
            assertThat(outbox.get("aggregate_type")).isEqualTo("SagaInstance");
            assertThat(outbox.get("status")).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("Should set sagaId as partition key")
        void shouldSetSagaIdAsPartitionKey() {
            // Given
            String documentId = "OBX-PK-" + UUID.randomUUID();

            // When
            SagaInstance saga = startTestSaga(DocumentType.INVOICE, documentId);

            // Then - all lifecycle events should have sagaId as partition key
            List<Map<String, Object>> lifecycleEvents = jdbcTemplate.queryForList(
                "SELECT * FROM outbox_events WHERE aggregate_id = ? AND topic LIKE 'saga.lifecycle.%'",
                saga.getId());

            assertThat(lifecycleEvents).isNotEmpty();
            for (Map<String, Object> event : lifecycleEvents) {
                assertThat(event.get("partition_key")).isEqualTo(saga.getId());
            }
        }

        @Test
        @DisplayName("Should write SagaStepCompletedEvent on reply")
        void shouldWriteStepCompletedEventOnReply() {
            // Given
            String documentId = "OBX-STEP-" + UUID.randomUUID();
            SagaInstance saga = startTestSaga(DocumentType.INVOICE, documentId);

            // When - handle reply for first step
            sagaApplicationService.handleReply(saga.getId(), "process-invoice", true, null);

            // Then
            List<Map<String, Object>> stepEvents = jdbcTemplate.queryForList(
                "SELECT * FROM outbox_events WHERE aggregate_id = ? AND event_type = 'SagaStepCompletedEvent'",
                saga.getId());

            assertThat(stepEvents).isNotEmpty();
            Map<String, Object> stepEvent = stepEvents.get(0);
            assertThat(stepEvent.get("topic")).isEqualTo("saga.lifecycle.step-completed");
            assertThat(stepEvent.get("partition_key")).isEqualTo(saga.getId());
        }

        @Test
        @DisplayName("Should write SagaCompletedEvent on saga completion")
        void shouldWriteCompletedEventOnSagaCompletion() throws Exception {
            // Given
            String documentId = "OBX-DONE-" + UUID.randomUUID();
            SagaInstance saga = startTestSaga(DocumentType.INVOICE, documentId);

            // When
            completeAllInvoiceSteps(saga.getId());

            // Then
            Map<String, Object> completedEvent = jdbcTemplate.queryForMap(
                "SELECT * FROM outbox_events WHERE aggregate_id = ? AND event_type = 'SagaCompletedEvent'",
                saga.getId());

            assertThat(completedEvent.get("topic")).isEqualTo("saga.lifecycle.completed");
            assertThat(completedEvent.get("partition_key")).isEqualTo(saga.getId());

            // Parse payload and verify durationMs field
            String payload = (String) completedEvent.get("payload");
            JsonNode payloadJson = parseJson(payload);
            assertThat(payloadJson.has("durationMs")).isTrue();
            assertThat(payloadJson.get("sagaId").asText()).isEqualTo(saga.getId());
        }
    }

    // ==================== CDC Flow Tests ====================

    @Nested
    @DisplayName("CDC Flow Tests")
    class CdcFlowTests {

        @Test
        @DisplayName("Should publish SagaStartedEvent to Kafka via CDC")
        void shouldPublishSagaStartedEventToKafkaViaCdc() throws Exception {
            // Given
            String documentId = "CDC-START-" + UUID.randomUUID();

            // When
            SagaInstance saga = startTestSaga(DocumentType.INVOICE, documentId);

            // Then - wait for CDC to publish to Kafka
            await().until(() -> hasMessageOnTopic("saga.lifecycle.started", saga.getId()));

            List<ConsumerRecord<String, String>> messages =
                getMessagesFromTopic("saga.lifecycle.started", saga.getId());
            assertThat(messages).hasSize(1);

            JsonNode payload = parseJson(messages.get(0).value());
            assertThat(payload.get("sagaId").asText()).isEqualTo(saga.getId());
            assertThat(payload.get("documentType").asText()).isEqualTo("INVOICE");
            assertThat(payload.get("documentId").asText()).isEqualTo(documentId);
            assertThat(payload.get("eventType").asText()).isEqualTo("SagaStartedEvent");
        }

        @Test
        @DisplayName("Should publish SagaCompletedEvent to Kafka via CDC")
        void shouldPublishSagaCompletedEventToKafkaViaCdc() throws Exception {
            // Given
            String documentId = "CDC-DONE-" + UUID.randomUUID();
            SagaInstance saga = startTestSaga(DocumentType.TAX_INVOICE, documentId);

            // When
            completeAllTaxInvoiceSteps(saga.getId());

            // Then
            await().until(() -> hasMessageOnTopic("saga.lifecycle.completed", saga.getId()));

            List<ConsumerRecord<String, String>> messages =
                getMessagesFromTopic("saga.lifecycle.completed", saga.getId());
            assertThat(messages).hasSize(1);

            JsonNode payload = parseJson(messages.get(0).value());
            assertThat(payload.get("sagaId").asText()).isEqualTo(saga.getId());
            assertThat(payload.get("eventType").asText()).isEqualTo("SagaCompletedEvent");
            assertThat(payload.has("durationMs")).isTrue();
        }

        @Test
        @DisplayName("Should preserve sagaId as Kafka key through CDC")
        void shouldPreserveSagaIdAsKafkaKeyThroughCdc() throws Exception {
            // Given
            String documentId = "CDC-KEY-" + UUID.randomUUID();

            // When
            SagaInstance saga = startTestSaga(DocumentType.INVOICE, documentId);

            // Then
            await().until(() -> hasMessageOnTopic("saga.lifecycle.started", saga.getId()));

            List<ConsumerRecord<String, String>> messages =
                getMessagesFromTopic("saga.lifecycle.started", saga.getId());
            ConsumerRecord<String, String> message = messages.get(0);

            // Kafka key should be sagaId (set as partition_key in outbox)
            assertThat(message.key()).isEqualTo(saga.getId());

            // Payload sagaId should also match
            JsonNode payload = parseJson(message.value());
            assertThat(payload.get("sagaId").asText()).isEqualTo(saga.getId());
        }

        @Test
        @DisplayName("Should publish multiple lifecycle events in order")
        void shouldPublishMultipleLifecycleEventsInOrder() throws Exception {
            // Given
            String documentId = "CDC-MULTI-" + UUID.randomUUID();

            // When - start and complete a saga
            SagaInstance saga = startTestSaga(DocumentType.INVOICE, documentId);
            completeAllInvoiceSteps(saga.getId());

            // Then - wait for both events
            await().until(() -> hasMessageOnTopic("saga.lifecycle.started", saga.getId()));
            await().until(() -> hasMessageOnTopic("saga.lifecycle.completed", saga.getId()));

            List<ConsumerRecord<String, String>> startedMessages =
                getMessagesFromTopic("saga.lifecycle.started", saga.getId());
            List<ConsumerRecord<String, String>> completedMessages =
                getMessagesFromTopic("saga.lifecycle.completed", saga.getId());

            assertThat(startedMessages).hasSize(1);
            assertThat(completedMessages).hasSize(1);
        }
    }

    // ==================== Document Type Tests ====================

    @Nested
    @DisplayName("Document Type Tests")
    class DocumentTypeTests {

        @Test
        @DisplayName("Should handle Invoice document type")
        void shouldHandleInvoiceDocumentType() throws Exception {
            // Given
            String documentId = "TYPE-INV-" + UUID.randomUUID();

            // When
            SagaInstance saga = startTestSaga(DocumentType.INVOICE, documentId);

            // Then - verify domain model
            assertThat(saga.getCurrentStep()).isEqualTo(SagaStep.PROCESS_INVOICE);
            assertThat(saga.getDocumentType()).isEqualTo(DocumentType.INVOICE);

            // Verify outbox payload has correct document type
            Map<String, Object> outbox = jdbcTemplate.queryForMap(
                "SELECT * FROM outbox_events WHERE aggregate_id = ? AND event_type = 'SagaStartedEvent'",
                saga.getId());
            String payload = (String) outbox.get("payload");
            JsonNode payloadJson = parseJson(payload);
            assertThat(payloadJson.get("documentType").asText()).isEqualTo("INVOICE");
        }

        @Test
        @DisplayName("Should handle TaxInvoice document type")
        void shouldHandleTaxInvoiceDocumentType() throws Exception {
            // Given
            String documentId = "TYPE-TAX-" + UUID.randomUUID();

            // When
            SagaInstance saga = startTestSaga(DocumentType.TAX_INVOICE, documentId);

            // Then - verify domain model
            assertThat(saga.getCurrentStep()).isEqualTo(SagaStep.PROCESS_TAX_INVOICE);
            assertThat(saga.getDocumentType()).isEqualTo(DocumentType.TAX_INVOICE);

            // Verify outbox payload has correct document type
            Map<String, Object> outbox = jdbcTemplate.queryForMap(
                "SELECT * FROM outbox_events WHERE aggregate_id = ? AND event_type = 'SagaStartedEvent'",
                saga.getId());
            String payload = (String) outbox.get("payload");
            JsonNode payloadJson = parseJson(payload);
            assertThat(payloadJson.get("documentType").asText()).isEqualTo("TAX_INVOICE");
        }
    }
}
