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
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;

/**
 * Integration tests for Kafka reply consumption via SagaReplyConsumer.
 * <p>
 * These tests verify that:
 * - SagaReplyConsumer correctly consumes messages from reply topics
 * - Saga state transitions occur on reply processing
 * - Command history is updated
 * - CDC events are published for state changes
 * <p>
 * Prerequisites:
 *   1. Start containers: ./scripts/test-containers-start.sh --with-debezium --auto-deploy-connectors
 *   2. Profile 'cdc-consumption-test' must be active to enable real Kafka producer
 */
@DisplayName("Saga Reply Consumption Integration Tests")
@ActiveProfiles("cdc-consumption-test")
class SagaReplyConsumptionIntegrationTest extends AbstractCdcIntegrationTest {

    @Autowired
    private SagaApplicationService sagaApplicationService;

    @Autowired
    private SagaInstanceRepository sagaInstanceRepository;

    @Autowired
    private SagaCommandRecordRepository commandRecordRepository;

    @MockBean
    private com.wpanther.orchestrator.infrastructure.messaging.producer.SagaCommandProducer sagaCommandProducer;

    @Nested
    @DisplayName("Invoice Reply Consumption Tests")
    class InvoiceReplyConsumptionTests {

        @Test
        @DisplayName("Should consume invoice reply and advance saga to next step")
        void shouldConsumeInvoiceReplyAndAdvanceSaga() throws Exception {
            // Given - start an invoice saga
            String documentId = "INV-" + UUID.randomUUID();
            DocumentMetadata metadata = createTestMetadata(documentId);

            doAnswer(invocation -> null).when(sagaCommandProducer).sendCommand(any(), any(), anyBoolean());

            SagaInstance saga = sagaApplicationService.startSaga(DocumentType.INVOICE, documentId, metadata);
            String sagaId = saga.getId();

            // Clear messages from saga start
            receivedMessages.clear();

            // Verify initial state
            assertThat(saga.getCurrentStep()).isEqualTo(SagaStep.PROCESS_INVOICE);
            assertThat(saga.getStatus()).isEqualTo(SagaStatus.IN_PROGRESS);

            // When - send successful reply for PROCESS_INVOICE step
            sendInvoiceReply(sagaId, "PROCESS_INVOICE");

            // Then - wait for CDC to publish step completed event
            await().until(() -> hasMessageOnTopic("saga.lifecycle.step-completed", sagaId));

            // Verify saga advanced to next step
            SagaInstance updated = sagaInstanceRepository.findById(sagaId).orElseThrow();
            assertThat(updated.getCurrentStep()).isEqualTo(SagaStep.SIGN_XML);
            assertThat(updated.getStatus()).isEqualTo(SagaStatus.IN_PROGRESS);

            // Verify step completed event was published via CDC
            List<ConsumerRecord<String, String>> completedMessages =
                getMessagesFromTopic("saga.lifecycle.step-completed", sagaId);
            assertThat(completedMessages).hasSizeGreaterThanOrEqualTo(1);

            JsonNode payload = parseJson(completedMessages.get(0).value());
            assertThat(payload.get("sagaId").asText()).isEqualTo(sagaId);
            assertThat(payload.get("step").asText()).isEqualTo("PROCESS_INVOICE");
            assertThat(payload.get("status").asText()).isEqualTo("STEP_COMPLETED");
        }

        @Test
        @DisplayName("Should consume tax-invoice reply and advance saga")
        void shouldConsumeTaxInvoiceReplyAndAdvanceSaga() throws Exception {
            // Given - start a tax invoice saga
            String documentId = "TAX-" + UUID.randomUUID();
            DocumentMetadata metadata = createTestMetadata(documentId);

            doAnswer(invocation -> null).when(sagaCommandProducer).sendCommand(any(), any(), anyBoolean());

            SagaInstance saga = sagaApplicationService.startSaga(DocumentType.TAX_INVOICE, documentId, metadata);
            String sagaId = saga.getId();

            receivedMessages.clear();

            // When - send successful reply for PROCESS_TAX_INVOICE step
            sendTaxInvoiceReply(sagaId, "PROCESS_TAX_INVOICE");

            // Then - wait for CDC to publish step completed event
            await().until(() -> hasMessageOnTopic("saga.lifecycle.step-completed", sagaId));

            // Verify saga advanced to next step
            SagaInstance updated = sagaInstanceRepository.findById(sagaId).orElseThrow();
            assertThat(updated.getCurrentStep()).isEqualTo(SagaStep.SIGN_XML);
            assertThat(updated.getStatus()).isEqualTo(SagaStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("Should handle multiple sequential replies")
        void shouldHandleMultipleSequentialReplies() throws Exception {
            // Given - start an invoice saga
            String documentId = "MULTI-" + UUID.randomUUID();
            DocumentMetadata metadata = createTestMetadata(documentId);

            doAnswer(invocation -> null).when(sagaCommandProducer).sendCommand(any(), any(), anyBoolean());

            SagaInstance saga = sagaApplicationService.startSaga(DocumentType.INVOICE, documentId, metadata);
            String sagaId = saga.getId();

            receivedMessages.clear();

            // When - send replies for multiple steps
            sendInvoiceReply(sagaId, "PROCESS_INVOICE");
            await().until(() -> {
                SagaInstance s = sagaInstanceRepository.findById(sagaId).orElse(null);
                return s != null && s.getCurrentStep() == SagaStep.SIGN_XML;
            });

            sendInvoiceReply(sagaId, "SIGN_XML");
            await().until(() -> {
                SagaInstance s = sagaInstanceRepository.findById(sagaId).orElse(null);
                return s != null && s.getCurrentStep() == SagaStep.GENERATE_INVOICE_PDF;
            });

            sendInvoiceReply(sagaId, "GENERATE_INVOICE_PDF");
            await().until(() -> {
                SagaInstance s = sagaInstanceRepository.findById(sagaId).orElse(null);
                return s != null && s.getCurrentStep() == SagaStep.SIGN_PDF;
            });

            // Then - verify final state
            SagaInstance updated = sagaInstanceRepository.findById(sagaId).orElseThrow();
            assertThat(updated.getCurrentStep()).isEqualTo(SagaStep.SIGN_PDF);

            // Verify multiple step completed events were published via CDC
            await().until(() -> {
                pollKafkaMessages();
                List<ConsumerRecord<String, String>> messages = receivedMessages.get("saga.lifecycle.step-completed");
                return messages != null && messages.size() >= 3;
            });

            List<ConsumerRecord<String, String>> completedMessages =
                getAllMessagesFromTopic("saga.lifecycle.step-completed");
            assertThat(completedMessages).hasSizeGreaterThanOrEqualTo(3);
        }

        @Test
        @DisplayName("Should complete saga when all steps succeed")
        void shouldCompleteSagaWhenAllStepsSucceed() throws Exception {
            // Given - start an invoice saga
            String documentId = "COMPLETE-" + UUID.randomUUID();
            DocumentMetadata metadata = createTestMetadata(documentId);

            doAnswer(invocation -> null).when(sagaCommandProducer).sendCommand(any(), any(), anyBoolean());

            SagaInstance saga = sagaApplicationService.startSaga(DocumentType.INVOICE, documentId, metadata);
            String sagaId = saga.getId();

            receivedMessages.clear();

            // When - send replies for all steps in the saga flow
            SagaStep currentStep = saga.getCurrentStep();
            while (currentStep != null) {
                sendInvoiceReply(sagaId, currentStep.getCode());

                SagaStep finalStep = currentStep;
                await().until(() -> {
                    SagaInstance s = sagaInstanceRepository.findById(sagaId).orElse(null);
                    if (s == null) return false;
                    if (s.getStatus() == SagaStatus.COMPLETED) return true;
                    return s.getCurrentStep() != finalStep;
                });

                saga = sagaInstanceRepository.findById(sagaId).orElseThrow();
                currentStep = saga.getStatus() == SagaStatus.COMPLETED ? null : saga.getCurrentStep();
            }

            // Then - verify saga is completed
            SagaInstance finalState = sagaInstanceRepository.findById(sagaId).orElseThrow();
            assertThat(finalState.getStatus()).isEqualTo(SagaStatus.COMPLETED);
            assertThat(finalState.getCompletedAt()).isNotNull();

            // Verify saga completed event was published via CDC
            await().until(() -> hasMessageOnTopic("saga.lifecycle.completed", sagaId));

            List<ConsumerRecord<String, String>> completedMessages =
                getMessagesFromTopic("saga.lifecycle.completed", sagaId);
            assertThat(completedMessages).hasSize(1);

            JsonNode payload = parseJson(completedMessages.get(0).value());
            assertThat(payload.get("sagaId").asText()).isEqualTo(sagaId);
            assertThat(payload.get("status").asText()).isEqualTo("COMPLETED");
        }
    }

    @Nested
    @DisplayName("Failed Reply Tests")
    class FailedReplyTests {

        @Test
        @DisplayName("Should handle failed reply and initiate compensation")
        void shouldHandleFailedReplyAndInitiateCompensation() throws Exception {
            // Given - start an invoice saga
            String documentId = "FAIL-" + UUID.randomUUID();
            DocumentMetadata metadata = createTestMetadata(documentId);

            doAnswer(invocation -> null).when(sagaCommandProducer).sendCommand(any(), any(), anyBoolean());

            SagaInstance saga = sagaApplicationService.startSaga(DocumentType.INVOICE, documentId, metadata);
            String sagaId = saga.getId();

            // Simulate max retries by updating the saga directly
            saga.setRetryCount(3); // Set to max retries
            sagaInstanceRepository.save(saga);

            receivedMessages.clear();

            // When - send failed reply
            sendInvoiceFailureReply(sagaId, "PROCESS_INVOICE", "Processing failed");

            // Then - wait for CDC to publish failed event
            await().until(() -> hasMessageOnTopic("saga.lifecycle.failed", sagaId));

            // Verify saga is marked as failed
            SagaInstance failed = sagaInstanceRepository.findById(sagaId).orElseThrow();
            assertThat(failed.getStatus()).isEqualTo(SagaStatus.FAILED);
            assertThat(failed.getErrorMessage()).contains("Processing failed");

            // Verify failed event was published via CDC
            List<ConsumerRecord<String, String>> failedMessages =
                getMessagesFromTopic("saga.lifecycle.failed", sagaId);
            assertThat(failedMessages).hasSizeGreaterThanOrEqualTo(1);

            JsonNode payload = parseJson(failedMessages.get(0).value());
            assertThat(payload.get("sagaId").asText()).isEqualTo(sagaId);
            assertThat(payload.get("status").asText()).isEqualTo("FAILED");
        }

        @Test
        @DisplayName("Should retry failed step when under max retries")
        void shouldRetryFailedStep() throws Exception {
            // Given - start an invoice saga
            String documentId = "RETRY-" + UUID.randomUUID();
            DocumentMetadata metadata = createTestMetadata(documentId);

            doAnswer(invocation -> null).when(sagaCommandProducer).sendCommand(any(), any(), anyBoolean());

            SagaInstance saga = sagaApplicationService.startSaga(DocumentType.INVOICE, documentId, metadata);
            String sagaId = saga.getId();

            receivedMessages.clear();

            // When - send first failed reply
            sendInvoiceFailureReply(sagaId, "PROCESS_INVOICE", "Temporary error");

            // Wait for retry processing
            await().until(() -> {
                SagaInstance s = sagaInstanceRepository.findById(sagaId).orElse(null);
                return s != null && s.getRetryCount() > 0;
            });

            // Then - verify retry count increased
            SagaInstance retried = sagaInstanceRepository.findById(sagaId).orElseThrow();
            assertThat(retried.getRetryCount()).isEqualTo(1);
            assertThat(retried.getStatus()).isEqualTo(SagaStatus.IN_PROGRESS);
        }
    }

    @Nested
    @DisplayName("Command History Tests")
    class CommandHistoryTests {

        @Test
        @DisplayName("Should update command history on successful reply")
        void shouldUpdateCommandHistoryOnSuccessfulReply() throws Exception {
            // Given - start an invoice saga
            String documentId = "HISTORY-" + UUID.randomUUID();
            DocumentMetadata metadata = createTestMetadata(documentId);

            doAnswer(invocation -> null).when(sagaCommandProducer).sendCommand(any(), any(), anyBoolean());

            SagaInstance saga = sagaApplicationService.startSaga(DocumentType.INVOICE, documentId, metadata);
            String sagaId = saga.getId();

            // Get the initial command
            List<SagaCommandRecord> commands = commandRecordRepository.findBySagaId(sagaId);
            SagaCommandRecord initialCommand = commands.stream()
                .filter(c -> c.getTargetStep() == SagaStep.PROCESS_INVOICE)
                .findFirst()
                .orElseThrow();
            assertThat(initialCommand.getStatus()).isEqualTo(SagaCommandRecord.CommandStatus.SENT);

            receivedMessages.clear();

            // When - send successful reply
            sendInvoiceReply(sagaId, "PROCESS_INVOICE");

            // Wait for processing
            await().until(() -> {
                SagaCommandRecord c = commandRecordRepository.findById(initialCommand.getId()).orElse(null);
                return c != null && c.getStatus() == SagaCommandRecord.CommandStatus.COMPLETED;
            });

            // Then - verify command was marked as completed
            SagaCommandRecord completedCommand = commandRecordRepository.findById(initialCommand.getId()).orElseThrow();
            assertThat(completedCommand.getStatus()).isEqualTo(SagaCommandRecord.CommandStatus.COMPLETED);
            assertThat(completedCommand.getCompletedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should update command history on failed reply")
        void shouldUpdateCommandHistoryOnFailedReply() throws Exception {
            // Given - start an invoice saga
            String documentId = "FAIL-HIST-" + UUID.randomUUID();
            DocumentMetadata metadata = createTestMetadata(documentId);

            doAnswer(invocation -> null).when(sagaCommandProducer).sendCommand(any(), any(), anyBoolean());

            SagaInstance saga = sagaApplicationService.startSaga(DocumentType.INVOICE, documentId, metadata);
            String sagaId = saga.getId();

            // Set to max retries to trigger immediate failure
            saga.setRetryCount(3);
            sagaInstanceRepository.save(saga);

            // Get the initial command
            List<SagaCommandRecord> commands = commandRecordRepository.findBySagaId(sagaId);
            SagaCommandRecord initialCommand = commands.stream()
                .filter(c -> c.getTargetStep() == SagaStep.PROCESS_INVOICE)
                .findFirst()
                .orElseThrow();

            receivedMessages.clear();

            // When - send failed reply
            sendInvoiceFailureReply(sagaId, "PROCESS_INVOICE", "Processing error");

            // Wait for processing
            await().until(() -> {
                SagaCommandRecord c = commandRecordRepository.findById(initialCommand.getId()).orElse(null);
                return c != null && c.getStatus() == SagaCommandRecord.CommandStatus.FAILED;
            });

            // Then - verify command was marked as failed
            SagaCommandRecord failedCommand = commandRecordRepository.findById(initialCommand.getId()).orElseThrow();
            assertThat(failedCommand.getStatus()).isEqualTo(SagaCommandRecord.CommandStatus.FAILED);
            assertThat(failedCommand.getErrorMessage()).contains("Processing error");
            assertThat(failedCommand.getCompletedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should create new command record for each step")
        void shouldCreateNewCommandRecordForEachStep() throws Exception {
            // Given - start an invoice saga
            String documentId = "CMDS-" + UUID.randomUUID();
            DocumentMetadata metadata = createTestMetadata(documentId);

            doAnswer(invocation -> null).when(sagaCommandProducer).sendCommand(any(), any(), anyBoolean());

            SagaInstance saga = sagaApplicationService.startSaga(DocumentType.INVOICE, documentId, metadata);
            String sagaId = saga.getId();

            // Should have one initial command
            List<SagaCommandRecord> initialCommands = commandRecordRepository.findBySagaId(sagaId);
            assertThat(initialCommands).hasSize(1);

            receivedMessages.clear();

            // When - send reply to advance to next step
            sendInvoiceReply(sagaId, "PROCESS_INVOICE");

            // Wait for step advancement
            await().until(() -> {
                SagaInstance s = sagaInstanceRepository.findById(sagaId).orElse(null);
                return s != null && s.getCurrentStep() == SagaStep.SIGN_XML;
            });

            // Then - should have two command records
            List<SagaCommandRecord> commands = commandRecordRepository.findBySagaId(sagaId);
            assertThat(commands).hasSize(2);

            // Verify command types
            assertThat(commands.stream()
                .anyMatch(c -> c.getTargetStep() == SagaStep.PROCESS_INVOICE)).isTrue();
            assertThat(commands.stream()
                .anyMatch(c -> c.getTargetStep() == SagaStep.SIGN_XML)).isTrue();
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
}
