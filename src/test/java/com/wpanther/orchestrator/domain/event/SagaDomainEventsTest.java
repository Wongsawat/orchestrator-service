package com.wpanther.orchestrator.domain.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for saga domain event classes.
 * Covers the convenience constructors and getters that are exercised during
 * saga lifecycle event publishing (via SagaEventPublisher → OutboxService).
 */
@DisplayName("Saga Domain Events Tests")
class SagaDomainEventsTest {

    @Nested
    @DisplayName("SagaStartedEvent")
    class SagaStartedEventTests {

        @Test
        @DisplayName("creates event with correct fields")
        void createsEventWithFields() {
            SagaStartedEvent event = new SagaStartedEvent(
                    "saga-001", "corr-001", "INVOICE", "doc-001", "process-invoice", "INV-001"
            );

            assertThat(event.getSagaId()).isEqualTo("saga-001");
            assertThat(event.getCorrelationId()).isEqualTo("corr-001");
            assertThat(event.getDocumentType()).isEqualTo("INVOICE");
            assertThat(event.getDocumentId()).isEqualTo("doc-001");
            assertThat(event.getCurrentStep()).isEqualTo("process-invoice");
            assertThat(event.getInvoiceNumber()).isEqualTo("INV-001");
        }

        @Test
        @DisplayName("creates event with null optional fields")
        void createsEventWithNullFields() {
            SagaStartedEvent event = new SagaStartedEvent(
                    "saga-002", "corr-002", "TAX_INVOICE", "doc-002", null, null
            );

            assertThat(event.getSagaId()).isEqualTo("saga-002");
            assertThat(event.getCurrentStep()).isNull();
            assertThat(event.getInvoiceNumber()).isNull();
        }
    }

    @Nested
    @DisplayName("SagaStepCompletedEvent")
    class SagaStepCompletedEventTests {

        @Test
        @DisplayName("creates event with correct fields")
        void createsEventWithFields() {
            SagaStepCompletedEvent event = new SagaStepCompletedEvent(
                    "saga-001", "corr-001", "INVOICE", "process-invoice", "sign-xml"
            );

            assertThat(event.getSagaId()).isEqualTo("saga-001");
            assertThat(event.getCorrelationId()).isEqualTo("corr-001");
            assertThat(event.getDocumentType()).isEqualTo("INVOICE");
            assertThat(event.getCompletedStep()).isEqualTo("process-invoice");
            assertThat(event.getNextStep()).isEqualTo("sign-xml");
        }

        @Test
        @DisplayName("creates event with null next step (last step)")
        void createsEventWithNullNextStep() {
            SagaStepCompletedEvent event = new SagaStepCompletedEvent(
                    "saga-001", "corr-001", "INVOICE", "send-ebms", null
            );

            assertThat(event.getNextStep()).isNull();
            assertThat(event.getCompletedStep()).isEqualTo("send-ebms");
        }
    }

    @Nested
    @DisplayName("SagaCompletedEvent")
    class SagaCompletedEventTests {

        @Test
        @DisplayName("creates event with all fields")
        void createsEventWithFields() {
            Instant startedAt = Instant.now().minusSeconds(60);
            Instant completedAt = Instant.now();

            SagaCompletedEvent event = new SagaCompletedEvent(
                    "saga-001", "corr-001", "INVOICE", "doc-001", "INV-001",
                    7, startedAt, completedAt, 60000L
            );

            assertThat(event.getSagaId()).isEqualTo("saga-001");
            assertThat(event.getCorrelationId()).isEqualTo("corr-001");
            assertThat(event.getDocumentType()).isEqualTo("INVOICE");
            assertThat(event.getDocumentId()).isEqualTo("doc-001");
            assertThat(event.getInvoiceNumber()).isEqualTo("INV-001");
            assertThat(event.getStepsExecuted()).isEqualTo(7);
            assertThat(event.getStartedAt()).isEqualTo(startedAt);
            assertThat(event.getCompletedAt()).isEqualTo(completedAt);
            assertThat(event.getDurationMs()).isEqualTo(60000L);
        }
    }

    @Nested
    @DisplayName("SagaFailedEvent")
    class SagaFailedEventTests {

        @Test
        @DisplayName("creates event with all fields")
        void createsEventWithFields() {
            Instant startedAt = Instant.now().minusSeconds(30);
            Instant failedAt = Instant.now();

            SagaFailedEvent event = new SagaFailedEvent(
                    "saga-001", "corr-001", "INVOICE", "doc-001", "INV-001",
                    "sign-xml", "Signing service unavailable", 3, true,
                    startedAt, failedAt, 30000L
            );

            assertThat(event.getSagaId()).isEqualTo("saga-001");
            assertThat(event.getCorrelationId()).isEqualTo("corr-001");
            assertThat(event.getDocumentType()).isEqualTo("INVOICE");
            assertThat(event.getDocumentId()).isEqualTo("doc-001");
            assertThat(event.getInvoiceNumber()).isEqualTo("INV-001");
            assertThat(event.getFailedStep()).isEqualTo("sign-xml");
            assertThat(event.getErrorMessage()).isEqualTo("Signing service unavailable");
            assertThat(event.getRetryCount()).isEqualTo(3);
            assertThat(event.getCompensationInitiated()).isTrue();
            assertThat(event.getStartedAt()).isEqualTo(startedAt);
            assertThat(event.getFailedAt()).isEqualTo(failedAt);
            assertThat(event.getDurationMs()).isEqualTo(30000L);
        }

        @Test
        @DisplayName("creates event with null optional fields")
        void createsEventWithNullOptionals() {
            Instant startedAt = Instant.now();

            SagaFailedEvent event = new SagaFailedEvent(
                    "saga-001", null, "TAX_INVOICE", "doc-002", null,
                    null, "Generic error", 0, false,
                    startedAt, startedAt, 0L
            );

            assertThat(event.getFailedStep()).isNull();
            assertThat(event.getInvoiceNumber()).isNull();
            assertThat(event.getCompensationInitiated()).isFalse();
        }
    }

    @Nested
    @DisplayName("StartSagaCommand")
    class StartSagaCommandTests {

        @Test
        @DisplayName("creates command with required fields using convenience constructor")
        void createsCommandWithFields() {
            StartSagaCommand command = new StartSagaCommand(
                    "doc-001", "TAX_INVOICE", "TAX-001", "<xml/>", "corr-001", "document-intake"
            );

            assertThat(command.getDocumentId()).isEqualTo("doc-001");
            assertThat(command.getDocumentType()).isEqualTo("TAX_INVOICE");
            assertThat(command.getInvoiceNumber()).isEqualTo("TAX-001");
            assertThat(command.getXmlContent()).isEqualTo("<xml/>");
            assertThat(command.getCorrelationId()).isEqualTo("corr-001");
            assertThat(command.getSource()).isEqualTo("document-intake");
        }
    }
}
