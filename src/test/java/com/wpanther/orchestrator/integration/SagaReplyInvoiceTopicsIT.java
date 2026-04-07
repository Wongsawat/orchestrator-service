package com.wpanther.orchestrator.integration;

import com.wpanther.orchestrator.domain.model.enums.DocumentType;
import com.wpanther.saga.domain.enums.SagaStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for {@code SagaReplyConsumer} handling replies on
 * {@code saga.reply.invoice} and {@code saga.reply.tax-invoice} Kafka topics.
 *
 * <p>Covered behaviours per topic:</p>
 * <ul>
 *   <li>Success reply advances the saga to the next step (SIGN_XML)</li>
 *   <li>Command record for the completed step is marked COMPLETED</li>
 *   <li>A new command record for the next step (SIGN_XML) is created</li>
 *   <li>{@code SagaEventPublisher#publishSagaStepCompleted} is invoked on success</li>
 *   <li>{@code SagaCommandPublisher#publishCommandForStep} is invoked for SIGN_XML on success</li>
 *   <li>Failure reply increments {@code retry_count} and keeps the saga at the current step</li>
 *   <li>Failed command record carries the error message</li>
 *   <li>Transient failure followed by success still advances the saga (retry recovery)</li>
 *   <li>Exceeding max retries transitions the saga to COMPENSATING</li>
 *   <li>{@code SagaEventPublisher#publishSagaFailed} is invoked after max retries</li>
 *   <li>Reply for an unknown sagaId is swallowed and the consumer remains healthy</li>
 * </ul>
 *
 * <p><b>Prerequisites:</b></p>
 * <pre>
 *   ./scripts/test-containers-start.sh --with-debezium --auto-deploy-connectors
 * </pre>
 *
 * <p><b>Run:</b></p>
 * <pre>
 *   mvn test -Pintegration \
 *            -Dtest=SagaReplyInvoiceTopicsIT \
 *            -Dintegration.tests.enabled=true \
 *            -Dspring.profiles.active=consumer-test
 * </pre>
 */
@DisplayName("SagaReplyConsumer – saga.reply.invoice and saga.reply.tax-invoice")
@Tag("integration")
@EnabledIfSystemProperty(named = "integration.tests.enabled", matches = "true")
class SagaReplyInvoiceTopicsIT extends AbstractKafkaConsumerTest {

    // =========================================================================
    // saga.reply.invoice  –  PROCESS_INVOICE step
    // =========================================================================

    @Nested
    @DisplayName("saga.reply.invoice – PROCESS_INVOICE reply handling")
    class InvoiceReplyTopicTests {

        /**
         * TC-INV-01: A SUCCESS reply on saga.reply.invoice must advance the saga
         * from PROCESS_INVOICE to SIGN_XML.
         */
        @Test
        @DisplayName("Should advance saga from PROCESS_INVOICE to SIGN_XML on success reply")
        void shouldAdvanceSagaToSignXmlOnSuccessReply() {
            // Given – saga waiting at PROCESS_INVOICE
            String sagaId = startTestSaga(DocumentType.INVOICE, "INV-ADV-" + UUID.randomUUID());
            assertThat(getSagaInstance(sagaId).get("current_step")).isEqualTo("PROCESS_INVOICE");

            // When – invoice-processing-service sends SUCCESS reply
            sendInvoiceReply(sagaId, "process-invoice", true, null);

            // Then – saga advances and remains IN_PROGRESS
            awaitCurrentStep(sagaId, SagaStep.SIGN_XML);
            Map<String, Object> saga = getSagaInstance(sagaId);
            assertThat(saga.get("current_step")).isEqualTo("SIGN_XML");
            assertThat(saga.get("status")).isEqualTo("IN_PROGRESS");
        }

        /**
         * TC-INV-02: On SUCCESS the PROCESS_INVOICE command record must be marked COMPLETED
         * and a new SIGN_XML command record must be created with status SENT.
         */
        @Test
        @DisplayName("Should mark PROCESS_INVOICE command COMPLETED and create SIGN_XML command on success")
        void shouldCompleteProcessInvoiceCommandAndCreateSignXmlCommand() {
            // Given
            String sagaId = startTestSaga(DocumentType.INVOICE, "INV-CMD-" + UUID.randomUUID());

            // When
            sendInvoiceReply(sagaId, "process-invoice", true, null);
            awaitCurrentStep(sagaId, SagaStep.SIGN_XML);

            // Then – PROCESS_INVOICE command completed
            List<Map<String, Object>> commands = getCommandHistory(sagaId);

            Map<String, Object> processCmd = commands.stream()
                    .filter(c -> "PROCESS_INVOICE".equals(c.get("target_step")))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No PROCESS_INVOICE command found"));
            assertThat(processCmd.get("status")).isEqualTo("COMPLETED");
            assertThat(processCmd.get("completed_at")).isNotNull();

            // And a new SIGN_XML command was created
            Map<String, Object> signXmlCmd = commands.stream()
                    .filter(c -> "SIGN_XML".equals(c.get("target_step")))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No SIGN_XML command found"));
            assertThat(signXmlCmd.get("status")).isEqualTo("SENT");
        }

        /**
         * TC-INV-03: On SUCCESS the SagaEventPublisher must be called with
         * publishSagaStepCompleted for the PROCESS_INVOICE step.
         */
        @Test
        @DisplayName("Should invoke SagaEventPublisher.publishSagaStepCompleted for PROCESS_INVOICE on success")
        void shouldPublishStepCompletedEventOnSuccess() {
            // Given
            String sagaId = startTestSaga(DocumentType.INVOICE, "INV-EVT-" + UUID.randomUUID());
            clearInvocations(sagaEventPublisher);   // clear the publishSagaStarted call from setup

            // When
            sendInvoiceReply(sagaId, "process-invoice", true, null);
            awaitCurrentStep(sagaId, SagaStep.SIGN_XML);

            // Then – step-completed event published for PROCESS_INVOICE
            verify(sagaEventPublisher).publishSagaStepCompleted(
                    argThat(s -> sagaId.equals(s.getId())),
                    eq(SagaStep.PROCESS_INVOICE),
                    anyString()
            );
        }

        /**
         * TC-INV-04: On SUCCESS the SagaCommandPublisher must be called to publish
         * the SIGN_XML command (the next step after PROCESS_INVOICE).
         */
        @Test
        @DisplayName("Should invoke SagaCommandPublisher.publishCommandForStep for SIGN_XML on success")
        void shouldPublishSignXmlCommandOnSuccess() {
            // Given
            String sagaId = startTestSaga(DocumentType.INVOICE, "INV-PUB-" + UUID.randomUUID());
            clearInvocations(sagaCommandPublisher);  // clear the initial PROCESS_INVOICE command call

            // When
            sendInvoiceReply(sagaId, "process-invoice", true, null);
            awaitCurrentStep(sagaId, SagaStep.SIGN_XML);

            // Then – SIGN_XML command was published
            verify(sagaCommandPublisher).publishCommandForStep(
                    argThat(s -> sagaId.equals(s.getId())),
                    eq(SagaStep.SIGN_XML),
                    anyString()
            );
        }

        /**
         * TC-INV-05: A FAILURE reply must increment retry_count and keep the saga
         * at PROCESS_INVOICE in IN_PROGRESS status.
         */
        @Test
        @DisplayName("Should increment retry_count and stay at PROCESS_INVOICE on failure reply")
        void shouldIncrementRetryCountOnFailureReply() {
            // Given
            String sagaId = startTestSaga(DocumentType.INVOICE, "INV-RET-" + UUID.randomUUID());
            int initialRetry = (Integer) getSagaInstance(sagaId).getOrDefault("retry_count", 0);

            // When
            sendInvoiceReply(sagaId, "process-invoice", false, "Invoice XML is invalid");

            // Then – retry count incremented, step unchanged, saga still running
            await().until(() -> {
                Integer r = (Integer) getSagaInstance(sagaId).get("retry_count");
                return r != null && r > initialRetry;
            });
            Map<String, Object> saga = getSagaInstance(sagaId);
            assertThat(saga.get("retry_count")).isEqualTo(initialRetry + 1);
            assertThat(saga.get("current_step")).isEqualTo("PROCESS_INVOICE");
            assertThat(saga.get("status")).isEqualTo("IN_PROGRESS");
        }

        /**
         * TC-INV-06: A FAILURE reply must persist the error message on the command record.
         */
        @Test
        @DisplayName("Should mark PROCESS_INVOICE command FAILED with error message on failure reply")
        void shouldMarkCommandFailedWithErrorMessageOnFailure() {
            // Given
            String sagaId = startTestSaga(DocumentType.INVOICE, "INV-FAIL-" + UUID.randomUUID());
            String errorMessage = "Upstream parsing error: invalid tax amount";

            // When
            sendInvoiceReply(sagaId, "process-invoice", false, errorMessage);

            // Then – command record carries the error message
            await().until(() -> getCommandHistory(sagaId).stream()
                    .anyMatch(c -> "PROCESS_INVOICE".equals(c.get("target_step"))
                            && "FAILED".equals(c.get("status"))));

            Map<String, Object> failedCmd = getCommandHistory(sagaId).stream()
                    .filter(c -> "PROCESS_INVOICE".equals(c.get("target_step"))
                            && "FAILED".equals(c.get("status")))
                    .findFirst()
                    .orElseThrow();
            assertThat(failedCmd.get("error_message")).isEqualTo(errorMessage);
        }

        /**
         * TC-INV-07: After a transient failure the saga must recover when a subsequent
         * SUCCESS reply arrives – final state is SIGN_XML / IN_PROGRESS.
         */
        @Test
        @DisplayName("Should advance to SIGN_XML after transient failure then success on saga.reply.invoice")
        void shouldRecoverAndAdvanceAfterTransientFailure() {
            // Given
            String sagaId = startTestSaga(DocumentType.INVOICE, "INV-REC-" + UUID.randomUUID());

            // When – fail, wait for retry increment, then succeed
            sendInvoiceReply(sagaId, "process-invoice", false, "Transient network error");
            await().until(() -> (Integer) getSagaInstance(sagaId).get("retry_count") > 0);

            sendInvoiceReply(sagaId, "process-invoice", true, null);

            // Then – saga advances past PROCESS_INVOICE
            awaitCurrentStep(sagaId, SagaStep.SIGN_XML);
            assertThat(getSagaInstance(sagaId).get("status")).isEqualTo("IN_PROGRESS");
        }

        /**
         * TC-INV-08: After max retries (3) are exceeded the saga must enter COMPENSATING.
         * PROCESS_INVOICE has no compensation step so the saga stays in COMPENSATING.
         */
        @Test
        @DisplayName("Should enter COMPENSATING state after max retries exceeded on saga.reply.invoice")
        void shouldEnterCompensatingAfterMaxRetriesExceeded() {
            // Given – max-retries = 3
            String sagaId = startTestSaga(DocumentType.INVOICE, "INV-MAX-" + UUID.randomUUID());

            // When – send 4 failures (>= maxRetries)
            for (int i = 0; i <= 3; i++) {
                sendInvoiceReply(sagaId, "process-invoice", false, "Persistent failure " + i);
                try { Thread.sleep(150); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }

            // Then
            awaitSagaStatus(sagaId, "COMPENSATING");
            Map<String, Object> saga = getSagaInstance(sagaId);
            assertThat(saga.get("status")).isEqualTo("COMPENSATING");
            assertThat(saga.get("error_message")).isNotNull();
        }

        /**
         * TC-INV-09: When max retries are exceeded, SagaEventPublisher#publishSagaFailed
         * must be invoked with PROCESS_INVOICE as the failing step.
         */
        @Test
        @DisplayName("Should invoke SagaEventPublisher.publishSagaFailed after max retries on saga.reply.invoice")
        void shouldPublishSagaFailedEventAfterMaxRetries() {
            // Given
            String sagaId = startTestSaga(DocumentType.INVOICE, "INV-FEVT-" + UUID.randomUUID());
            clearInvocations(sagaEventPublisher);

            // When – exhaust retries
            for (int i = 0; i <= 3; i++) {
                sendInvoiceReply(sagaId, "process-invoice", false, "Fatal processing error");
                try { Thread.sleep(150); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
            awaitSagaStatus(sagaId, "COMPENSATING");

            // Then – saga-failed event published
            verify(sagaEventPublisher).publishSagaFailed(
                    argThat(s -> sagaId.equals(s.getId())),
                    eq(SagaStep.PROCESS_INVOICE),
                    anyString(),
                    anyString(),
                    any()
            );
        }

        /**
         * TC-INV-10: A reply carrying an unknown sagaId must be acknowledged and swallowed
         * without crashing the consumer. Subsequent valid messages must still be processed.
         */
        @Test
        @DisplayName("Should handle unknown sagaId gracefully and keep consumer healthy")
        void shouldHandleUnknownSagaIdGracefullyOnInvoiceReplyTopic() {
            // Given – a valid-format but non-existent sagaId
            String unknownSagaId = UUID.randomUUID().toString();

            // When – send reply with unknown sagaId to saga.reply.invoice
            testKafkaProducer.send("saga.reply.invoice", unknownSagaId,
                    createSagaReplyJson(unknownSagaId, "process-invoice", true, null));

            try { Thread.sleep(1500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }

            // Then – no saga record created for the unknown ID
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM saga_instances WHERE id = ?", Long.class, unknownSagaId);
            assertThat(count).isEqualTo(0L);

            // Consumer is still healthy – can process a valid subsequent message
            String healthSagaId = startTestSaga(DocumentType.INVOICE, "INV-HEALTH-" + UUID.randomUUID());
            sendInvoiceReply(healthSagaId, "process-invoice", true, null);
            awaitCurrentStep(healthSagaId, SagaStep.SIGN_XML);
        }
    }

    // =========================================================================
    // saga.reply.tax-invoice  –  PROCESS_TAX_INVOICE step
    // =========================================================================

    @Nested
    @DisplayName("saga.reply.tax-invoice – PROCESS_TAX_INVOICE reply handling")
    class TaxInvoiceReplyTopicTests {

        /**
         * TC-TAX-01: A SUCCESS reply on saga.reply.tax-invoice must advance the saga
         * from PROCESS_TAX_INVOICE to SIGN_XML.
         */
        @Test
        @DisplayName("Should advance saga from PROCESS_TAX_INVOICE to SIGN_XML on success reply")
        void shouldAdvanceSagaToSignXmlOnSuccessReply() {
            // Given
            String sagaId = startTestSaga(DocumentType.TAX_INVOICE, "TAX-ADV-" + UUID.randomUUID());
            assertThat(getSagaInstance(sagaId).get("current_step")).isEqualTo("PROCESS_TAX_INVOICE");

            // When
            sendTaxInvoiceReply(sagaId, "process-tax-invoice", true, null);

            // Then
            awaitCurrentStep(sagaId, SagaStep.SIGN_XML);
            Map<String, Object> saga = getSagaInstance(sagaId);
            assertThat(saga.get("current_step")).isEqualTo("SIGN_XML");
            assertThat(saga.get("status")).isEqualTo("IN_PROGRESS");
        }

        /**
         * TC-TAX-02: On SUCCESS the PROCESS_TAX_INVOICE command record must be marked
         * COMPLETED and a new SIGN_XML command record created with status SENT.
         */
        @Test
        @DisplayName("Should mark PROCESS_TAX_INVOICE command COMPLETED and create SIGN_XML command on success")
        void shouldCompleteProcessTaxInvoiceCommandAndCreateSignXmlCommand() {
            // Given
            String sagaId = startTestSaga(DocumentType.TAX_INVOICE, "TAX-CMD-" + UUID.randomUUID());

            // When
            sendTaxInvoiceReply(sagaId, "process-tax-invoice", true, null);
            awaitCurrentStep(sagaId, SagaStep.SIGN_XML);

            // Then – PROCESS_TAX_INVOICE command completed
            List<Map<String, Object>> commands = getCommandHistory(sagaId);

            Map<String, Object> processCmd = commands.stream()
                    .filter(c -> "PROCESS_TAX_INVOICE".equals(c.get("target_step")))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No PROCESS_TAX_INVOICE command found"));
            assertThat(processCmd.get("status")).isEqualTo("COMPLETED");
            assertThat(processCmd.get("completed_at")).isNotNull();

            // And a SIGN_XML command was created
            Map<String, Object> signXmlCmd = commands.stream()
                    .filter(c -> "SIGN_XML".equals(c.get("target_step")))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No SIGN_XML command found"));
            assertThat(signXmlCmd.get("status")).isEqualTo("SENT");
        }

        /**
         * TC-TAX-03: On SUCCESS the SagaEventPublisher must be called with
         * publishSagaStepCompleted for the PROCESS_TAX_INVOICE step.
         */
        @Test
        @DisplayName("Should invoke SagaEventPublisher.publishSagaStepCompleted for PROCESS_TAX_INVOICE on success")
        void shouldPublishStepCompletedEventOnSuccess() {
            // Given
            String sagaId = startTestSaga(DocumentType.TAX_INVOICE, "TAX-EVT-" + UUID.randomUUID());
            clearInvocations(sagaEventPublisher);

            // When
            sendTaxInvoiceReply(sagaId, "process-tax-invoice", true, null);
            awaitCurrentStep(sagaId, SagaStep.SIGN_XML);

            // Then
            verify(sagaEventPublisher).publishSagaStepCompleted(
                    argThat(s -> sagaId.equals(s.getId())),
                    eq(SagaStep.PROCESS_TAX_INVOICE),
                    anyString()
            );
        }

        /**
         * TC-TAX-04: On SUCCESS the SagaCommandPublisher must be called to publish
         * the SIGN_XML command.
         */
        @Test
        @DisplayName("Should invoke SagaCommandPublisher.publishCommandForStep for SIGN_XML on success")
        void shouldPublishSignXmlCommandOnSuccess() {
            // Given
            String sagaId = startTestSaga(DocumentType.TAX_INVOICE, "TAX-PUB-" + UUID.randomUUID());
            clearInvocations(sagaCommandPublisher);

            // When
            sendTaxInvoiceReply(sagaId, "process-tax-invoice", true, null);
            awaitCurrentStep(sagaId, SagaStep.SIGN_XML);

            // Then
            verify(sagaCommandPublisher).publishCommandForStep(
                    argThat(s -> sagaId.equals(s.getId())),
                    eq(SagaStep.SIGN_XML),
                    anyString()
            );
        }

        /**
         * TC-TAX-05: A FAILURE reply must increment retry_count and keep the saga
         * at PROCESS_TAX_INVOICE in IN_PROGRESS status.
         */
        @Test
        @DisplayName("Should increment retry_count and stay at PROCESS_TAX_INVOICE on failure reply")
        void shouldIncrementRetryCountOnFailureReply() {
            // Given
            String sagaId = startTestSaga(DocumentType.TAX_INVOICE, "TAX-RET-" + UUID.randomUUID());
            int initialRetry = (Integer) getSagaInstance(sagaId).getOrDefault("retry_count", 0);

            // When
            sendTaxInvoiceReply(sagaId, "process-tax-invoice", false, "Tax invoice XML schema violation");

            // Then
            await().until(() -> {
                Integer r = (Integer) getSagaInstance(sagaId).get("retry_count");
                return r != null && r > initialRetry;
            });
            Map<String, Object> saga = getSagaInstance(sagaId);
            assertThat(saga.get("retry_count")).isEqualTo(initialRetry + 1);
            assertThat(saga.get("current_step")).isEqualTo("PROCESS_TAX_INVOICE");
            assertThat(saga.get("status")).isEqualTo("IN_PROGRESS");
        }

        /**
         * TC-TAX-06: A FAILURE reply must persist the error message on the command record.
         */
        @Test
        @DisplayName("Should mark PROCESS_TAX_INVOICE command FAILED with error message on failure reply")
        void shouldMarkCommandFailedWithErrorMessageOnFailure() {
            // Given
            String sagaId = startTestSaga(DocumentType.TAX_INVOICE, "TAX-FAIL-" + UUID.randomUUID());
            String errorMessage = "Tax rate calculation mismatch: line item 5";

            // When
            sendTaxInvoiceReply(sagaId, "process-tax-invoice", false, errorMessage);

            // Then
            await().until(() -> getCommandHistory(sagaId).stream()
                    .anyMatch(c -> "PROCESS_TAX_INVOICE".equals(c.get("target_step"))
                            && "FAILED".equals(c.get("status"))));

            Map<String, Object> failedCmd = getCommandHistory(sagaId).stream()
                    .filter(c -> "PROCESS_TAX_INVOICE".equals(c.get("target_step"))
                            && "FAILED".equals(c.get("status")))
                    .findFirst()
                    .orElseThrow();
            assertThat(failedCmd.get("error_message")).isEqualTo(errorMessage);
        }

        /**
         * TC-TAX-07: After a transient failure the saga must recover when a subsequent
         * SUCCESS reply arrives.
         */
        @Test
        @DisplayName("Should advance to SIGN_XML after transient failure then success on saga.reply.tax-invoice")
        void shouldRecoverAndAdvanceAfterTransientFailure() {
            // Given
            String sagaId = startTestSaga(DocumentType.TAX_INVOICE, "TAX-REC-" + UUID.randomUUID());

            // When
            sendTaxInvoiceReply(sagaId, "process-tax-invoice", false, "Transient timeout");
            await().until(() -> (Integer) getSagaInstance(sagaId).get("retry_count") > 0);

            sendTaxInvoiceReply(sagaId, "process-tax-invoice", true, null);

            // Then
            awaitCurrentStep(sagaId, SagaStep.SIGN_XML);
            assertThat(getSagaInstance(sagaId).get("status")).isEqualTo("IN_PROGRESS");
        }

        /**
         * TC-TAX-08: After max retries are exceeded the saga must enter COMPENSATING.
         */
        @Test
        @DisplayName("Should enter COMPENSATING state after max retries exceeded on saga.reply.tax-invoice")
        void shouldEnterCompensatingAfterMaxRetriesExceeded() {
            // Given
            String sagaId = startTestSaga(DocumentType.TAX_INVOICE, "TAX-MAX-" + UUID.randomUUID());

            // When – send maxRetries + 1 failures
            for (int i = 0; i <= 3; i++) {
                sendTaxInvoiceReply(sagaId, "process-tax-invoice", false, "Persistent failure " + i);
                try { Thread.sleep(150); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }

            // Then
            awaitSagaStatus(sagaId, "COMPENSATING");
            Map<String, Object> saga = getSagaInstance(sagaId);
            assertThat(saga.get("status")).isEqualTo("COMPENSATING");
            assertThat(saga.get("error_message")).isNotNull();
        }

        /**
         * TC-TAX-09: When max retries are exceeded, SagaEventPublisher#publishSagaFailed
         * must be invoked with PROCESS_TAX_INVOICE as the failing step.
         */
        @Test
        @DisplayName("Should invoke SagaEventPublisher.publishSagaFailed after max retries on saga.reply.tax-invoice")
        void shouldPublishSagaFailedEventAfterMaxRetries() {
            // Given
            String sagaId = startTestSaga(DocumentType.TAX_INVOICE, "TAX-FEVT-" + UUID.randomUUID());
            clearInvocations(sagaEventPublisher);

            // When
            for (int i = 0; i <= 3; i++) {
                sendTaxInvoiceReply(sagaId, "process-tax-invoice", false, "Fatal error");
                try { Thread.sleep(150); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
            awaitSagaStatus(sagaId, "COMPENSATING");

            // Then
            verify(sagaEventPublisher).publishSagaFailed(
                    argThat(s -> sagaId.equals(s.getId())),
                    eq(SagaStep.PROCESS_TAX_INVOICE),
                    anyString(),
                    anyString(),
                    any()
            );
        }

        /**
         * TC-TAX-10: A reply carrying an unknown sagaId must be swallowed without
         * crashing the consumer. Subsequent valid messages must still be processed.
         */
        @Test
        @DisplayName("Should handle unknown sagaId gracefully and keep consumer healthy")
        void shouldHandleUnknownSagaIdGracefullyOnTaxInvoiceReplyTopic() {
            // Given
            String unknownSagaId = UUID.randomUUID().toString();

            // When
            testKafkaProducer.send("saga.reply.tax-invoice", unknownSagaId,
                    createSagaReplyJson(unknownSagaId, "process-tax-invoice", true, null));

            try { Thread.sleep(1500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }

            // Then – no saga created for the unknown ID
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM saga_instances WHERE id = ?", Long.class, unknownSagaId);
            assertThat(count).isEqualTo(0L);

            // Consumer still processes valid messages
            String healthSagaId = startTestSaga(DocumentType.TAX_INVOICE, "TAX-HEALTH-" + UUID.randomUUID());
            sendTaxInvoiceReply(healthSagaId, "process-tax-invoice", true, null);
            awaitCurrentStep(healthSagaId, SagaStep.SIGN_XML);
        }
    }
}
