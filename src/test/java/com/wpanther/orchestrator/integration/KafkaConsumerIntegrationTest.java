package com.wpanther.orchestrator.integration;

import com.wpanther.orchestrator.infrastructure.adapter.in.messaging.StartSagaCommand;
import com.wpanther.orchestrator.domain.model.enums.DocumentType;
import com.wpanther.orchestrator.domain.model.SagaInstance;
import com.wpanther.saga.domain.enums.SagaStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Kafka consumers in orchestrator-service.
 * <p>
 * Tests verify:
 * - StartSagaCommandConsumer: Consumes commands from saga.commands.orchestrator
 * - SagaReplyConsumer: Consumes replies from saga.reply.invoice and saga.reply.tax-invoice
 * <p>
 * These tests require external infrastructure:
 * - PostgreSQL on localhost:5433
 * - Kafka on localhost:9093
 * - Debezium on localhost:8083 (optional, for CDC verification)
 * <p>
 * To run: mvn test -Dintegration.tests.enabled=true -Dtest=KafkaConsumerIntegrationTest -Dspring.profiles.active=consumer-test
 */
@DisplayName("Kafka Consumer Integration Tests")
@Tag("integration")
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "integration.tests.enabled", matches = "true")
class KafkaConsumerIntegrationTest extends AbstractKafkaConsumerTest {

    // ==================== StartSagaCommandConsumer Tests ====================

    @Nested
    @DisplayName("StartSagaCommandConsumer Tests")
    class StartSagaCommandConsumerTests {

        @Test
        @DisplayName("Should consume StartSagaCommand and create saga instance")
        void shouldConsumeStartSagaCommandAndCreateSaga() {
            // Given
            String documentId = "TEST-INV-" + UUID.randomUUID();
            StartSagaCommand command = createStartSagaCommand(DocumentType.INVOICE, documentId);

            // When - send command to Kafka
            sendStartSagaCommand(command);

            // Then - verify saga was created in database
            String sagaId = awaitSagaByDocumentId(DocumentType.INVOICE, documentId);

            // Verify saga state
            Map<String, Object> sagaData = getSagaInstance(sagaId);
            assertThat(sagaData.get("document_type")).isEqualTo("INVOICE");
            assertThat(sagaData.get("document_id")).isEqualTo(documentId);
            assertThat(sagaData.get("current_step")).isEqualTo("PROCESS_INVOICE");
            assertThat(sagaData.get("status")).isEqualTo("IN_PROGRESS");
            assertThat(sagaData.get("created_at")).isNotNull();
        }

        @Test
        @DisplayName("Should route Invoice to PROCESS_INVOICE step")
        void shouldRouteInvoiceToProcessInvoiceStep() {
            // Given
            String documentId = "ROUTE-INV-" + UUID.randomUUID();
            StartSagaCommand command = createStartSagaCommand(DocumentType.INVOICE, documentId);

            // When
            sendStartSagaCommand(command);

            // Then
            String sagaId = awaitSagaByDocumentId(DocumentType.INVOICE, documentId);
            assertThat(SagaStep.valueOf((String) getSagaInstance(sagaId).get("current_step"))).isEqualTo(SagaStep.PROCESS_INVOICE);
        }

        @Test
        @DisplayName("Should route TaxInvoice to PROCESS_TAX_INVOICE step")
        void shouldRouteTaxInvoiceToProcessTaxInvoiceStep() {
            // Given
            String documentId = "ROUTE-TAX-" + UUID.randomUUID();
            StartSagaCommand command = createStartSagaCommand(DocumentType.TAX_INVOICE, documentId);

            // When
            sendStartSagaCommand(command);

            // Then
            String sagaId = awaitSagaByDocumentId(DocumentType.TAX_INVOICE, documentId);
            assertThat(SagaStep.valueOf((String) getSagaInstance(sagaId).get("current_step"))).isEqualTo(SagaStep.PROCESS_TAX_INVOICE);
        }

        @Test
        @DisplayName("Should create outbox events for SagaStartedEvent")
        void shouldCreateOutboxEvents() {
            // Given
            String documentId = "OUTBOX-" + UUID.randomUUID();
            StartSagaCommand command = createStartSagaCommand(DocumentType.INVOICE, documentId);

            // When
            sendStartSagaCommand(command);

            // Then - wait for saga to be created first
            String sagaId = awaitSagaByDocumentId(DocumentType.INVOICE, documentId);

            // Now wait for outbox event
            awaitOutboxEvent(sagaId, "SagaStartedEvent");

            // Verify event details
            List<Map<String, Object>> events = getOutboxEvents(sagaId);
            Map<String, Object> startedEvent = events.stream()
                .filter(e -> "SagaStartedEvent".equals(e.get("event_type")))
                .findFirst()
                .orElseThrow();

            assertThat(startedEvent.get("aggregate_type")).isEqualTo("SagaInstance");
            assertThat(startedEvent.get("aggregate_id")).isEqualTo(sagaId);
            assertThat(startedEvent.get("topic")).isEqualTo("saga.lifecycle.started");
            assertThat(startedEvent.get("status")).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("Should create command record in database")
        void shouldCreateCommandRecord() {
            // Given
            String documentId = "CMD-" + UUID.randomUUID();
            StartSagaCommand command = createStartSagaCommand(DocumentType.TAX_INVOICE, documentId);

            // When
            sendStartSagaCommand(command);

            // Then
            String sagaId = awaitSagaByDocumentId(DocumentType.TAX_INVOICE, documentId);

            List<Map<String, Object>> commands = getCommandHistory(sagaId);
            assertThat(commands).isNotEmpty();

            Map<String, Object> firstCommand = commands.get(0);
            assertThat(firstCommand.get("target_step")).isEqualTo("PROCESS_TAX_INVOICE");
            assertThat(firstCommand.get("status")).isEqualTo("SENT");
        }

        @Test
        @DisplayName("Should reuse existing active saga (idempotency)")
        void shouldReuseExistingActiveSaga() {
            // Given - create an existing IN_PROGRESS saga
            String documentId = "IDEM-" + UUID.randomUUID();
            String existingSagaId = startTestSaga(DocumentType.INVOICE, documentId);

            // When - send duplicate StartSagaCommand
            StartSagaCommand command = createStartSagaCommand(DocumentType.INVOICE, documentId);
            sendStartSagaCommand(command);

            // Then - should reuse existing saga, not create duplicate
            String retrievedSagaId = awaitSagaByDocumentId(DocumentType.INVOICE, documentId);
            assertThat(retrievedSagaId).isEqualTo(existingSagaId);

            // Verify only one saga exists
            Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM saga_instances WHERE document_id = ?", Long.class, documentId);
            assertThat(count).isEqualTo(1L);
        }

        @Test
        @DisplayName("Should skip invalid document type")
        void shouldSkipInvalidDocumentType() {
            // Given - command with invalid document type (but valid JSON)
            String documentId = "INVALID-" + UUID.randomUUID();
            String commandJson = String.format(
                "{\"documentId\":\"%s\",\"documentType\":\"INVALID_TYPE\",\"documentNumber\":\"INV-123\","
                + "\"xmlContent\":\"<test/>\",\"correlationId\":\"%s\",\"source\":\"TEST\"}",
                documentId, UUID.randomUUID());

            // When - send invalid command
            testKafkaProducer.send("saga.commands.orchestrator", documentId, commandJson);

            // Then - wait a bit for processing
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Verify no saga was created (IllegalArgumentException should be logged)
            Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM saga_instances WHERE document_id = ?", Long.class, documentId);
            assertThat(count).isEqualTo(0L);
        }
    }

    // ==================== SagaReplyConsumer Tests - Invoice ====================

    @Nested
    @DisplayName("SagaReplyConsumer Tests - Invoice Flow")
    class InvoiceReplyConsumerTests {

        @Test
        @DisplayName("Should consume success reply and advance saga")
        void shouldConsumeSuccessReplyAndAdvanceSaga() {
            // Given - saga in PROCESS_INVOICE step
            String sagaId = startTestSaga(DocumentType.INVOICE, "REPLY-TEST");
            assertThat(SagaStep.valueOf((String) getSagaInstance(sagaId).get("current_step"))).isEqualTo(SagaStep.PROCESS_INVOICE);

            // When - send success reply for process-invoice
            sendInvoiceReply(sagaId, "process-invoice", true, null);

            // Then - saga should advance to next step
            awaitCurrentStep(sagaId, SagaStep.SIGN_XML);

            // Verify previous command marked completed
            List<Map<String, Object>> commands = getCommandHistory(sagaId);
            Map<String, Object> processInvoiceCommand = commands.stream()
                .filter(c -> "PROCESS_INVOICE".equals(c.get("target_step")))
                .findFirst()
                .orElseThrow();

            assertThat(processInvoiceCommand.get("status")).isEqualTo("COMPLETED");
            assertThat(processInvoiceCommand.get("completed_at")).isNotNull();
        }

        @Test
        @DisplayName("Should publish SagaStepCompletedEvent")
        void shouldPublishStepCompletedEvent() {
            // Given
            String sagaId = startTestSaga(DocumentType.INVOICE, "STEP-EVENT");

            // When
            sendInvoiceReply(sagaId, "process-invoice", true, null);

            // Then
            awaitOutboxEvent(sagaId, "SagaStepCompletedEvent");

            // Verify event details
            List<Map<String, Object>> events = getOutboxEvents(sagaId);
            Map<String, Object> stepEvent = events.stream()
                .filter(e -> "SagaStepCompletedEvent".equals(e.get("event_type")))
                .findFirst()
                .orElseThrow();

            assertThat(stepEvent.get("topic")).isEqualTo("saga.lifecycle.step-completed");
        }

        @Test
        @DisplayName("Should complete saga on final step")
        void shouldCompleteSagaOnFinalStep() {
            // Given
            String sagaId = startTestSaga(DocumentType.INVOICE, "COMPLETE-TEST");

            // When - complete all 6 steps
            completeAllInvoiceSteps(sagaId);

            // Then
            awaitSagaStatus(sagaId, "COMPLETED");

            Map<String, Object> completedSaga = getSagaInstance(sagaId);
            assertThat(completedSaga.get("status")).isEqualTo("COMPLETED");
            assertThat(completedSaga.get("completed_at")).isNotNull();

            // Verify all commands completed
            List<Map<String, Object>> commands = getCommandHistory(sagaId);
            assertThat(commands).hasSize(6);
            assertThat(commands).allMatch(c -> "COMPLETED".equals(c.get("status")));
        }

        @Test
        @DisplayName("Should publish SagaCompletedEvent on completion")
        void shouldPublishSagaCompletedEventOnCompletion() {
            // Given
            String sagaId = startTestSaga(DocumentType.INVOICE, "DONE-EVENT");

            // When
            completeAllInvoiceSteps(sagaId);

            // Then
            awaitSagaStatus(sagaId, "COMPLETED");
            awaitOutboxEvent(sagaId, "SagaCompletedEvent");

            // Verify event has durationMs
            List<Map<String, Object>> events = getOutboxEvents(sagaId);
            Map<String, Object> completedEvent = events.stream()
                .filter(e -> "SagaCompletedEvent".equals(e.get("event_type")))
                .findFirst()
                .orElseThrow();

            assertThat(completedEvent.get("topic")).isEqualTo("saga.lifecycle.completed");

            // Parse payload to verify durationMs field
            String payload = (String) completedEvent.get("payload");
            // JSON parsing shows durationMs is present
            assertThat(payload).contains("durationMs");
        }

        @Test
        @DisplayName("Should increment retry count on failure")
        void shouldIncrementRetryCountOnFailure() {
            // Given
            String sagaId = startTestSaga(DocumentType.INVOICE, "RETRY-TEST");
            Integer initialRetry = (Integer) getSagaInstance(sagaId).get("retry_count");

            // When - send failure reply
            sendInvoiceReply(sagaId, "process-invoice", false, "Test failure");

            // Then
            await().until(() -> {
                Integer currentRetry = (Integer) getSagaInstance(sagaId).get("retry_count");
                return currentRetry != null && currentRetry > initialRetry;
            });

            Integer finalRetry = (Integer) getSagaInstance(sagaId).get("retry_count");
            assertThat(finalRetry).isEqualTo(initialRetry + 1);

            // Verify command marked as failed
            List<Map<String, Object>> commands = getCommandHistory(sagaId);
            Map<String, Object> failedCommand = commands.stream()
                .filter(c -> "PROCESS_INVOICE".equals(c.get("target_step")))
                .findFirst()
                .orElseThrow();

            assertThat(failedCommand.get("status")).isEqualTo("FAILED");
            assertThat(failedCommand.get("error_message")).isEqualTo("Test failure");
        }

        @Test
        @DisplayName("Should retry failed step")
        void shouldRetryFailedStep() {
            // Given
            String sagaId = startTestSaga(DocumentType.INVOICE, "RETRY-STEP");

            // When - fail then succeed
            sendInvoiceReply(sagaId, "process-invoice", false, "Transient error");
            try {
                Thread.sleep(200);  // Wait for retry logic to process
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            sendInvoiceReply(sagaId, "process-invoice", true, null);

            // Then - saga should still be in PROCESS_INVOICE (retry happened, same step)
            // After success, should advance to next step
            awaitCurrentStep(sagaId, SagaStep.SIGN_XML);

            // Verify we have 2 command records for PROCESS_INVOICE (original + retry)
            List<Map<String, Object>> commands = getCommandHistory(sagaId);
            long processInvoiceCount = commands.stream()
                .filter(c -> "PROCESS_INVOICE".equals(c.get("target_step")))
                .count();
            assertThat(processInvoiceCount).isGreaterThan(1);  // At least retry created
        }

        @Test
        @DisplayName("Should fail saga after max retries")
        void shouldFailSagaAfterMaxRetries() {
            // Given - saga with max retries = 3
            String sagaId = startTestSaga(DocumentType.INVOICE, "MAX-RETRY");

            // When - send maxRetries + 1 failures (4 failures to exceed maxRetries=3)
            for (int i = 0; i <= 3; i++) {
                sendInvoiceReply(sagaId, "process-invoice", false, "Persistent error");
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            // Then - saga should enter COMPENSATING state (no compensation steps available for first step)
            // Note: Saga stays in COMPENSATING when there are no compensation steps
            awaitSagaStatus(sagaId, "COMPENSATING");

            Map<String, Object> failedSaga = getSagaInstance(sagaId);
            assertThat(failedSaga.get("status")).isEqualTo("COMPENSATING");
            assertThat(failedSaga.get("error_message")).isNotNull();
        }
    }

    // ==================== SagaReplyConsumer Tests - Tax Invoice ====================

    @Nested
    @DisplayName("SagaReplyConsumer Tests - Tax Invoice Flow")
    class TaxInvoiceReplyConsumerTests {

        @Test
        @DisplayName("Should consume success reply and advance tax invoice saga")
        void shouldConsumeSuccessReplyAndAdvanceTaxInvoiceSaga() {
            // Given - saga in PROCESS_TAX_INVOICE step
            String sagaId = startTestSaga(DocumentType.TAX_INVOICE, "TAX-REPLY-TEST");
            assertThat(SagaStep.valueOf((String) getSagaInstance(sagaId).get("current_step"))).isEqualTo(SagaStep.PROCESS_TAX_INVOICE);

            // When - send success reply for process-tax-invoice
            sendTaxInvoiceReply(sagaId, "process-tax-invoice", true, null);

            // Then - saga should advance to next step
            awaitCurrentStep(sagaId, SagaStep.SIGN_XML);

            // Verify previous command marked completed
            List<Map<String, Object>> commands = getCommandHistory(sagaId);
            Map<String, Object> processTaxInvoiceCommand = commands.stream()
                .filter(c -> "PROCESS_TAX_INVOICE".equals(c.get("target_step")))
                .findFirst()
                .orElseThrow();

            assertThat(processTaxInvoiceCommand.get("status")).isEqualTo("COMPLETED");
            assertThat(processTaxInvoiceCommand.get("completed_at")).isNotNull();
        }

        @Test
        @DisplayName("Should route to GENERATE_TAX_INVOICE_PDF step")
        void shouldRouteToGenerateTaxInvoicePdfStep() {
            // Given
            String sagaId = startTestSaga(DocumentType.TAX_INVOICE, "TAX-PDF-TEST");

            // When - complete first two steps to reach PDF generation
            sendTaxInvoiceReply(sagaId, "process-tax-invoice", true, null);
            sendTaxInvoiceReply(sagaId, "sign-xml", true, null);

            // Then - should be at GENERATE_TAX_INVOICE_PDF
            awaitCurrentStep(sagaId, SagaStep.GENERATE_TAX_INVOICE_PDF);
        }

        @Test
        @DisplayName("Should complete full tax invoice workflow")
        void shouldCompleteFullTaxInvoiceWorkflow() {
            // Given
            String sagaId = startTestSaga(DocumentType.TAX_INVOICE, "TAX-COMPLETE");

            // When - complete all 6 steps
            completeAllTaxInvoiceSteps(sagaId);

            // Then
            awaitSagaStatus(sagaId, "COMPLETED");

            Map<String, Object> completedSaga = getSagaInstance(sagaId);
            assertThat(completedSaga.get("status")).isEqualTo("COMPLETED");

            // Verify all commands completed
            List<Map<String, Object>> commands = getCommandHistory(sagaId);
            assertThat(commands).hasSize(6);
            assertThat(commands).allMatch(c -> "COMPLETED".equals(c.get("status")));
        }

        @Test
        @DisplayName("Should publish SagaStepCompletedEvent for tax invoice")
        void shouldPublishStepCompletedEventForTaxInvoice() {
            // Given
            String sagaId = startTestSaga(DocumentType.TAX_INVOICE, "TAX-EVENT");

            // When
            sendTaxInvoiceReply(sagaId, "process-tax-invoice", true, null);

            // Then
            awaitOutboxEvent(sagaId, "SagaStepCompletedEvent");

            // Verify event details
            List<Map<String, Object>> events = getOutboxEvents(sagaId);
            Map<String, Object> stepEvent = events.stream()
                .filter(e -> "SagaStepCompletedEvent".equals(e.get("event_type")))
                .findFirst()
                .orElseThrow();

            assertThat(stepEvent.get("topic")).isEqualTo("saga.lifecycle.step-completed");
        }

        @Test
        @DisplayName("Should increment retry count on tax invoice failure")
        void shouldIncrementRetryCountOnTaxInvoiceFailure() {
            // Given
            String sagaId = startTestSaga(DocumentType.TAX_INVOICE, "TAX-RETRY");
            Integer initialRetry = (Integer) getSagaInstance(sagaId).get("retry_count");

            // When - send failure reply
            sendTaxInvoiceReply(sagaId, "process-tax-invoice", false, "Test failure");

            // Then
            await().until(() -> {
                Integer currentRetry = (Integer) getSagaInstance(sagaId).get("retry_count");
                return currentRetry != null && currentRetry > initialRetry;
            });

            Integer finalRetry = (Integer) getSagaInstance(sagaId).get("retry_count");
            assertThat(finalRetry).isEqualTo(initialRetry + 1);
        }

        @Test
        @DisplayName("Should fail tax invoice saga after max retries")
        void shouldFailTaxInvoiceSagaAfterMaxRetries() {
            // Given
            String sagaId = startTestSaga(DocumentType.TAX_INVOICE, "TAX-MAX-RETRY");

            // When - send maxRetries + 1 failures
            for (int i = 0; i <= 3; i++) {
                sendTaxInvoiceReply(sagaId, "process-tax-invoice", false, "Persistent error");
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            // Then - saga should enter COMPENSATING state (no compensation steps available for first step)
            awaitSagaStatus(sagaId, "COMPENSATING");
        }
    }

    // ==================== End-to-End Flow Tests ====================

    @Nested
    @DisplayName("End-to-End Flow Tests")
    class EndToEndFlowTests {

        @Test
        @DisplayName("Should complete full invoice workflow")
        void shouldCompleteFullInvoiceWorkflow() {
            // Given - StartSagaCommand
            String documentId = "E2E-INV-" + UUID.randomUUID();
            sendStartSagaCommand(createStartSagaCommand(DocumentType.INVOICE, documentId));

            // Wait for saga creation
            String sagaId = awaitSagaByDocumentId(DocumentType.INVOICE, documentId);

            // Verify initial state
            assertThat(SagaStep.valueOf((String) getSagaInstance(sagaId).get("current_step"))).isEqualTo(SagaStep.PROCESS_INVOICE);

            // When - complete all 6 steps via replies
            completeAllInvoiceSteps(sagaId);

            // Then
            awaitSagaStatus(sagaId, "COMPLETED");

            // Verify all commands marked completed
            List<Map<String, Object>> commands = getCommandHistory(sagaId);
            assertThat(commands).hasSize(6);
            assertThat(commands).allMatch(c -> "COMPLETED".equals(c.get("status")));

            // Verify outbox events
            List<Map<String, Object>> events = getOutboxEvents(sagaId);

            // Should have: SagaStartedEvent + 6 SagaStepCompletedEvent + SagaCompletedEvent
            assertThat(events).anyMatch(e -> "SagaStartedEvent".equals(e.get("event_type")));
            assertThat(events).anyMatch(e -> "SagaCompletedEvent".equals(e.get("event_type")));

            long stepCompletedCount = events.stream()
                .filter(e -> "SagaStepCompletedEvent".equals(e.get("event_type")))
                .count();
            assertThat(stepCompletedCount).isEqualTo(6);
        }

        @Test
        @DisplayName("Should recover from transient failure")
        void shouldRecoverFromTransientFailure() {
            // Given
            String sagaId = startTestSaga(DocumentType.INVOICE, "RECOVER-TEST");

            // When - fail then succeed
            sendInvoiceReply(sagaId, "process-invoice", false, "Transient error");

            // Wait for retry processing
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            sendInvoiceReply(sagaId, "process-invoice", true, null);

            // Then - should advance to next step
            awaitCurrentStep(sagaId, SagaStep.SIGN_XML);

            // Verify saga is still healthy
            Map<String, Object> sagaData = getSagaInstance(sagaId);
            assertThat(sagaData.get("status")).isEqualTo("IN_PROGRESS");
        }

        @Test
        @DisplayName("Should maintain state consistency across all steps")
        void shouldMaintainStateConsistency() {
            // Given
            String documentId = "STATE-" + UUID.randomUUID();
            sendStartSagaCommand(createStartSagaCommand(DocumentType.INVOICE, documentId));
            String sagaId = awaitSagaByDocumentId(DocumentType.INVOICE, documentId);

            // Verify initial state
            Map<String, Object> initialState = getSagaInstance(sagaId);
            assertThat(initialState.get("status")).isEqualTo("IN_PROGRESS");
            assertThat(initialState.get("current_step")).isEqualTo("PROCESS_INVOICE");

            // When - complete first step
            sendInvoiceReply(sagaId, "process-invoice", true, null);

            // Then - verify step transition
            awaitCurrentStep(sagaId, SagaStep.SIGN_XML);
            Map<String, Object> afterStep1 = getSagaInstance(sagaId);
            assertThat(afterStep1.get("status")).isEqualTo("IN_PROGRESS");

            // When - complete all remaining steps
            completeAllInvoiceSteps(sagaId);

            // Then - verify final state
            awaitSagaStatus(sagaId, "COMPLETED");
            Map<String, Object> finalState = getSagaInstance(sagaId);
            assertThat(finalState.get("status")).isEqualTo("COMPLETED");
            assertThat(finalState.get("completed_at")).isNotNull();
        }
    }
}