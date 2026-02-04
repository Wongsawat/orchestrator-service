package com.wpanther.orchestrator.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.wpanther.orchestrator.application.service.SagaApplicationService;
import com.wpanther.orchestrator.domain.model.DocumentMetadata;
import com.wpanther.orchestrator.domain.model.SagaInstance;
import com.wpanther.orchestrator.domain.model.enums.DocumentType;
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
 * End-to-end integration tests for the orchestrator service.
 * <p>
 * These tests simulate the complete saga flow from start to completion,
 * including:
 * 1. Starting a saga (writes to DB, CDC publishes lifecycle.started)
 * 2. Simulating replies from downstream services (advances state)
 * 3. Verifying state transitions and CDC events at each step
 * 4. Completing the saga and verifying final state
 * <p>
 * Prerequisites:
 *   1. Start containers: ./scripts/test-containers-start.sh --with-debezium --auto-deploy-connectors
 *   2. Profile 'cdc-consumption-test' must be active for reply consumption tests
 */
@DisplayName("Orchestrator End-to-End Integration Tests")
@ActiveProfiles("cdc-consumption-test")
class SagaEndToEndIntegrationTest extends AbstractCdcIntegrationTest {

    @Autowired
    private SagaApplicationService sagaApplicationService;

    @Autowired
    private SagaInstanceRepository sagaInstanceRepository;

    @MockBean
    private com.wpanther.orchestrator.infrastructure.messaging.producer.SagaCommandProducer sagaCommandProducer;

    @Nested
    @DisplayName("Invoice Saga End-to-End Flow")
    class InvoiceSagaEndToEndTests {

        @Test
        @DisplayName("Should complete full invoice saga flow")
        void shouldCompleteFullInvoiceSagaFlow() throws Exception {
            // Given - start an invoice saga
            String documentId = "E2E-INV-" + UUID.randomUUID();
            DocumentMetadata metadata = createTestMetadata(documentId);

            doAnswer(invocation -> null).when(sagaCommandProducer).sendCommand(any(), any(), anyBoolean());

            // Step 1: Start saga and verify initial state
            SagaInstance saga = sagaApplicationService.startSaga(DocumentType.INVOICE, documentId, metadata);
            String sagaId = saga.getId();

            // Verify saga started event published via CDC
            await().until(() -> hasMessageOnTopic("saga.lifecycle.started", sagaId));
            assertThat(saga.getStatus()).isEqualTo(SagaStatus.IN_PROGRESS);
            assertThat(saga.getCurrentStep()).isEqualTo(SagaStep.PROCESS_INVOICE);

            receivedMessages.clear();

            // Step 2: Simulate PROCESS_INVOICE completion
            sendInvoiceReply(sagaId, "PROCESS_INVOICE");
            await().until(() -> {
                SagaInstance s = sagaInstanceRepository.findById(sagaId).orElse(null);
                return s != null && s.getCurrentStep() == SagaStep.SIGN_XML;
            });
            await().until(() -> hasMessageOnTopic("saga.lifecycle.step-completed", sagaId));

            // Step 3: Simulate SIGN_XML completion
            sendInvoiceReply(sagaId, "SIGN_XML");
            await().until(() -> {
                SagaInstance s = sagaInstanceRepository.findById(sagaId).orElse(null);
                return s != null && s.getCurrentStep() == SagaStep.GENERATE_INVOICE_PDF;
            });

            // Step 4: Simulate GENERATE_INVOICE_PDF completion
            sendInvoiceReply(sagaId, "GENERATE_INVOICE_PDF");
            await().until(() -> {
                SagaInstance s = sagaInstanceRepository.findById(sagaId).orElse(null);
                return s != null && s.getCurrentStep() == SagaStep.SIGN_PDF;
            });

            // Step 5: Simulate SIGN_PDF completion
            sendInvoiceReply(sagaId, "SIGN_PDF");
            await().until(() -> {
                SagaInstance s = sagaInstanceRepository.findById(sagaId).orElse(null);
                return s != null && s.getCurrentStep() == SagaStep.STORE_DOCUMENT;
            });

            // Step 6: Simulate STORE_DOCUMENT completion
            sendInvoiceReply(sagaId, "STORE_DOCUMENT");
            await().until(() -> {
                SagaInstance s = sagaInstanceRepository.findById(sagaId).orElse(null);
                return s != null && s.getCurrentStep() == SagaStep.SEND_EBMS;
            });

            // Step 7: Simulate SEND_EBMS completion - saga should complete
            sendInvoiceReply(sagaId, "SEND_EBMS");

            // Verify saga completed
            await().until(() -> {
                SagaInstance s = sagaInstanceRepository.findById(sagaId).orElse(null);
                return s != null && s.getStatus() == SagaStatus.COMPLETED;
            });

            // Verify saga completed event published via CDC
            await().until(() -> hasMessageOnTopic("saga.lifecycle.completed", sagaId));

            // Final assertions
            SagaInstance finalState = sagaInstanceRepository.findById(sagaId).orElseThrow();
            assertThat(finalState.getStatus()).isEqualTo(SagaStatus.COMPLETED);
            assertThat(finalState.getCompletedAt()).isNotNull();
            assertThat(finalState.getCurrentStep()).isNull(); // No current step when completed

            // Verify completed event content
            List<ConsumerRecord<String, String>> completedMessages =
                getMessagesFromTopic("saga.lifecycle.completed", sagaId);
            assertThat(completedMessages).hasSize(1);

            JsonNode payload = parseJson(completedMessages.get(0).value());
            assertThat(payload.get("sagaId").asText()).isEqualTo(sagaId);
            assertThat(payload.get("status").asText()).isEqualTo("COMPLETED");
        }

        @Test
        @DisplayName("Should track state transitions throughout saga lifecycle")
        void shouldTrackStateTransitionsThroughoutSagaLifecycle() throws Exception {
            // Given
            String documentId = "TRANS-" + UUID.randomUUID();
            DocumentMetadata metadata = createTestMetadata(documentId);

            doAnswer(invocation -> null).when(sagaCommandProducer).sendCommand(any(), any(), anyBoolean());

            SagaInstance saga = sagaApplicationService.startSaga(DocumentType.INVOICE, documentId, metadata);
            String sagaId = saga.getId();

            receivedMessages.clear();

            // Track all state transitions
            List<SagaStep> expectedSteps = List.of(
                SagaStep.PROCESS_INVOICE,
                SagaStep.SIGN_XML,
                SagaStep.GENERATE_INVOICE_PDF,
                SagaStep.SIGN_PDF,
                SagaStep.STORE_DOCUMENT,
                SagaStep.SEND_EBMS
            );

            for (SagaStep expectedStep : expectedSteps) {
                // Verify current step
                SagaInstance current = sagaInstanceRepository.findById(sagaId).orElseThrow();
                assertThat(current.getCurrentStep()).isEqualTo(expectedStep);

                // Send reply to advance to next step
                sendInvoiceReply(sagaId, expectedStep.getCode());

                // Wait for step advancement (or completion if last step)
                if (expectedStep != SagaStep.SEND_EBMS) {
                    await().until(() -> {
                        SagaInstance s = sagaInstanceRepository.findById(sagaId).orElse(null);
                        return s != null && s.getCurrentStep() != expectedStep;
                    });
                }
            }

            // Final verification - saga should be completed
            await().until(() -> {
                SagaInstance s = sagaInstanceRepository.findById(sagaId).orElse(null);
                return s != null && s.getStatus() == SagaStatus.COMPLETED;
            });
        }

        @Test
        @DisplayName("Should publish CDC events for each saga step")
        void shouldPublishCdcEventsForEachSagaStep() throws Exception {
            // Given
            String documentId = "CDC-EVENTS-" + UUID.randomUUID();
            DocumentMetadata metadata = createTestMetadata(documentId);

            doAnswer(invocation -> null).when(sagaCommandProducer).sendCommand(any(), any(), anyBoolean());

            SagaInstance saga = sagaApplicationService.startSaga(DocumentType.INVOICE, documentId, metadata);
            String sagaId = saga.getId();

            receivedMessages.clear();

            // Complete all steps
            completeAllInvoiceSteps(sagaId);

            // Wait for completed event
            await().until(() -> hasMessageOnTopic("saga.lifecycle.completed", sagaId));

            // Verify we have multiple step-completed events
            await().until(() -> {
                pollKafkaMessages();
                List<ConsumerRecord<String, String>> messages = receivedMessages.get("saga.lifecycle.step-completed");
                return messages != null && messages.size() >= 6;
            });

            List<ConsumerRecord<String, String>> stepCompletedMessages =
                getAllMessagesFromTopic("saga.lifecycle.step-completed");
            assertThat(stepCompletedMessages.size()).isGreaterThanOrEqualTo(6);

            // Verify all messages have correct saga ID
            for (ConsumerRecord<String, String> message : stepCompletedMessages) {
                JsonNode payload = parseJson(message.value());
                assertThat(payload.get("sagaId").asText()).isEqualTo(sagaId);
            }
        }
    }

    @Nested
    @DisplayName("Tax Invoice Saga End-to-End Flow")
    class TaxInvoiceSagaEndToEndTests {

        @Test
        @DisplayName("Should complete full tax invoice saga flow")
        void shouldCompleteFullTaxInvoiceSagaFlow() throws Exception {
            // Given
            String documentId = "E2E-TAX-" + UUID.randomUUID();
            DocumentMetadata metadata = createTestMetadata(documentId);

            doAnswer(invocation -> null).when(sagaCommandProducer).sendCommand(any(), any(), anyBoolean());

            // Step 1: Start saga
            SagaInstance saga = sagaApplicationService.startSaga(DocumentType.TAX_INVOICE, documentId, metadata);
            String sagaId = saga.getId();

            await().until(() -> hasMessageOnTopic("saga.lifecycle.started", sagaId));
            assertThat(saga.getCurrentStep()).isEqualTo(SagaStep.PROCESS_TAX_INVOICE);

            receivedMessages.clear();

            // Step 2: Simulate PROCESS_TAX_INVOICE completion
            sendTaxInvoiceReply(sagaId, "PROCESS_TAX_INVOICE");
            await().until(() -> {
                SagaInstance s = sagaInstanceRepository.findById(sagaId).orElse(null);
                return s != null && s.getCurrentStep() == SagaStep.SIGN_XML;
            });

            // Step 3: Simulate SIGN_XML completion
            sendTaxInvoiceReply(sagaId, "SIGN_XML");
            await().until(() -> {
                SagaInstance s = sagaInstanceRepository.findById(sagaId).orElse(null);
                return s != null && s.getCurrentStep() == SagaStep.GENERATE_TAX_INVOICE_PDF;
            });

            // Step 4: Simulate GENERATE_TAX_INVOICE_PDF completion
            sendTaxInvoiceReply(sagaId, "GENERATE_TAX_INVOICE_PDF");
            await().until(() -> {
                SagaInstance s = sagaInstanceRepository.findById(sagaId).orElse(null);
                return s != null && s.getCurrentStep() == SagaStep.SIGN_PDF;
            });

            // Step 5: Simulate SIGN_PDF completion
            sendTaxInvoiceReply(sagaId, "SIGN_PDF");
            await().until(() -> {
                SagaInstance s = sagaInstanceRepository.findById(sagaId).orElse(null);
                return s != null && s.getCurrentStep() == SagaStep.STORE_DOCUMENT;
            });

            // Step 6: Simulate STORE_DOCUMENT completion
            sendTaxInvoiceReply(sagaId, "STORE_DOCUMENT");
            await().until(() -> {
                SagaInstance s = sagaInstanceRepository.findById(sagaId).orElse(null);
                return s != null && s.getCurrentStep() == SagaStep.SEND_EBMS;
            });

            // Step 7: Simulate SEND_EBMS completion
            sendTaxInvoiceReply(sagaId, "SEND_EBMS");

            // Verify saga completed
            await().until(() -> {
                SagaInstance s = sagaInstanceRepository.findById(sagaId).orElse(null);
                return s != null && s.getStatus() == SagaStatus.COMPLETED;
            });

            await().until(() -> hasMessageOnTopic("saga.lifecycle.completed", sagaId));

            // Final assertions
            SagaInstance finalState = sagaInstanceRepository.findById(sagaId).orElseThrow();
            assertThat(finalState.getStatus()).isEqualTo(SagaStatus.COMPLETED);
        }
    }

    @Nested
    @DisplayName("Error Recovery End-to-End Tests")
    class ErrorRecoveryEndToEndTests {

        @Test
        @DisplayName("Should handle retry and continue saga")
        void shouldHandleRetryAndContinueSaga() throws Exception {
            // Given
            String documentId = "RETRY-E2E-" + UUID.randomUUID();
            DocumentMetadata metadata = createTestMetadata(documentId);

            doAnswer(invocation -> null).when(sagaCommandProducer).sendCommand(any(), any(), anyBoolean());

            SagaInstance saga = sagaApplicationService.startSaga(DocumentType.INVOICE, documentId, metadata);
            String sagaId = saga.getId();

            receivedMessages.clear();

            // When - send failure (under max retries)
            sendInvoiceFailureReply(sagaId, "PROCESS_INVOICE", "Temporary error");

            // Wait for retry
            await().until(() -> {
                SagaInstance s = sagaInstanceRepository.findById(sagaId).orElse(null);
                return s != null && s.getRetryCount() > 0;
            });

            // Then - send success and continue
            sendInvoiceReply(sagaId, "PROCESS_INVOICE");

            // Wait for advancement to next step
            await().until(() -> {
                SagaInstance s = sagaInstanceRepository.findById(sagaId).orElse(null);
                return s != null && s.getCurrentStep() == SagaStep.SIGN_XML;
            });

            // Verify saga can still complete
            SagaInstance afterRetry = sagaInstanceRepository.findById(sagaId).orElseThrow();
            assertThat(afterRetry.getRetryCount()).isGreaterThan(0);
            assertThat(afterRetry.getStatus()).isEqualTo(SagaStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("Should fail saga after max retries")
        void shouldFailSagaAfterMaxRetries() throws Exception {
            // Given
            String documentId = "FAIL-E2E-" + UUID.randomUUID();
            DocumentMetadata metadata = createTestMetadata(documentId);

            doAnswer(invocation -> null).when(sagaCommandProducer).sendCommand(any(), any(), anyBoolean());

            SagaInstance saga = sagaApplicationService.startSaga(DocumentType.INVOICE, documentId, metadata);
            String sagaId = saga.getId();

            // Set to max retries
            saga.setRetryCount(3);
            sagaInstanceRepository.save(saga);

            receivedMessages.clear();

            // When - send failure
            sendInvoiceFailureReply(sagaId, "PROCESS_INVOICE", "Permanent error");

            // Then - wait for failure
            await().until(() -> hasMessageOnTopic("saga.lifecycle.failed", sagaId));

            // Verify final state
            SagaInstance finalState = sagaInstanceRepository.findById(sagaId).orElseThrow();
            assertThat(finalState.getStatus()).isEqualTo(SagaStatus.FAILED);
            assertThat(finalState.getErrorMessage()).contains("Permanent error");

            // Verify failed event content
            List<ConsumerRecord<String, String>> failedMessages =
                getMessagesFromTopic("saga.lifecycle.failed", sagaId);
            assertThat(failedMessages).isNotEmpty();

            JsonNode payload = parseJson(failedMessages.get(0).value());
            assertThat(payload.get("status").asText()).isEqualTo("FAILED");
        }
    }

    @Nested
    @DisplayName("Database Verification End-to-End Tests")
    class DatabaseVerificationEndToEndTests {

        @Test
        @DisplayName("Should persist all saga state to database")
        void shouldPersistAllSagaStateToDatabase() throws Exception {
            // Given
            String documentId = "DB-E2E-" + UUID.randomUUID();
            DocumentMetadata metadata = createTestMetadata(documentId);

            doAnswer(invocation -> null).when(sagaCommandProducer).sendCommand(any(), any(), anyBoolean());

            // When - start and complete saga
            SagaInstance saga = sagaApplicationService.startSaga(DocumentType.INVOICE, documentId, metadata);
            String sagaId = saga.getId();

            receivedMessages.clear();
            completeAllInvoiceSteps(sagaId);

            await().until(() -> {
                SagaInstance s = sagaInstanceRepository.findById(sagaId).orElse(null);
                return s != null && s.getStatus() == SagaStatus.COMPLETED;
            });

            // Then - verify database state
            Map<String, Object> sagaRow = jdbcTemplate.queryForMap(
                "SELECT * FROM saga_instances WHERE id = ?", sagaId);

            assertThat(sagaRow.get("status")).isEqualTo("COMPLETED");
            assertThat(sagaRow.get("document_type")).isEqualTo("INVOICE");
            assertThat(sagaRow.get("document_id")).isEqualTo(documentId);
            assertThat(sagaRow.get("completed_at")).isNotNull();
            assertThat(sagaRow.get("error_message")).isNull();

            // Verify command history exists
            List<Map<String, Object>> commandRows = jdbcTemplate.queryForList(
                "SELECT * FROM saga_commands WHERE saga_id = ? ORDER BY created_at", sagaId);

            assertThat(commandRows).hasSizeGreaterThanOrEqualTo(6);

            // Verify outbox events exist
            List<Map<String, Object>> outboxRows = jdbcTemplate.queryForList(
                "SELECT * FROM outbox_events WHERE partition_key = ? ORDER BY created_at", sagaId);

            assertThat(outboxRows).isNotEmpty();
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
     * Helper method to complete all invoice saga steps.
     */
    private void completeAllInvoiceSteps(String sagaId) throws Exception {
        SagaStep[] steps = {
            SagaStep.PROCESS_INVOICE,
            SagaStep.SIGN_XML,
            SagaStep.GENERATE_INVOICE_PDF,
            SagaStep.SIGN_PDF,
            SagaStep.STORE_DOCUMENT,
            SagaStep.SEND_EBMS
        };

        for (SagaStep step : steps) {
            sendInvoiceReply(sagaId, step.getCode());

            // Wait for step completion (except last step which results in saga completion)
            if (step != SagaStep.SEND_EBMS) {
                await().until(() -> {
                    SagaInstance s = sagaInstanceRepository.findById(sagaId).orElse(null);
                    return s != null && s.getCurrentStep() != step;
                });
            }
        }
    }
}
