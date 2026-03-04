package com.wpanther.orchestrator.adapter.out.messaging;

import com.wpanther.orchestrator.adapter.out.messaging.SagaEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.orchestrator.domain.model.DocumentMetadata;
import com.wpanther.orchestrator.domain.model.SagaInstance;
import com.wpanther.orchestrator.domain.model.enums.DocumentType;
import com.wpanther.saga.domain.enums.SagaStatus;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SagaEventPublisher Tests")
class SagaEventPublisherTest {

    @Mock private OutboxService outboxService;

    private SagaEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new SagaEventPublisher(outboxService, new ObjectMapper());
        ReflectionTestUtils.setField(publisher, "sagaStartedTopic", "saga.lifecycle.started");
        ReflectionTestUtils.setField(publisher, "sagaStepCompletedTopic", "saga.lifecycle.step-completed");
        ReflectionTestUtils.setField(publisher, "sagaCompletedTopic", "saga.lifecycle.completed");
        ReflectionTestUtils.setField(publisher, "sagaFailedTopic", "saga.lifecycle.failed");
    }

    private SagaInstance createSaga(SagaStatus status) {
        SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-001",
                DocumentMetadata.builder().xmlContent("<xml/>").build());
        saga.start();
        if (status == SagaStatus.COMPLETED) {
            saga.advanceTo(SagaStep.PROCESS_INVOICE);
            saga.complete();
        } else if (status == SagaStatus.FAILED) {
            saga.fail("Test failure");
        } else if (status == SagaStatus.COMPENSATING) {
            saga.fail("Test failure");
            saga.startCompensation();
        }
        return saga;
    }

    @Nested
    @DisplayName("publishSagaStarted()")
    class PublishSagaStartedTests {

        @Test
        @DisplayName("publishes event to outbox with correct topic")
        void publishesToOutbox() {
            SagaInstance saga = createSaga(SagaStatus.IN_PROGRESS);

            publisher.publishSagaStarted(saga, "corr-001", "INV-001");

            verify(outboxService).saveWithRouting(
                    any(), eq("SagaInstance"), eq(saga.getId()),
                    eq("saga.lifecycle.started"), eq(saga.getId()), any());
        }

        @Test
        @DisplayName("publishes event with null invoice number")
        void publishesWithNullInvoiceNumber() {
            SagaInstance saga = createSaga(SagaStatus.IN_PROGRESS);

            publisher.publishSagaStarted(saga, "corr-001", null);

            verify(outboxService).saveWithRouting(any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("publishSagaStepCompleted()")
    class PublishSagaStepCompletedTests {

        @Test
        @DisplayName("publishes step completed event to outbox")
        void publishesToOutbox() {
            SagaInstance saga = createSaga(SagaStatus.IN_PROGRESS);

            publisher.publishSagaStepCompleted(saga, SagaStep.PROCESS_INVOICE, "corr-001");

            verify(outboxService).saveWithRouting(
                    any(), eq("SagaInstance"), eq(saga.getId()),
                    eq("saga.lifecycle.step-completed"), eq(saga.getId()), any());
        }

        @Test
        @DisplayName("handles step with no next step (last step)")
        void handlesLastStep() {
            SagaInstance saga = createSaga(SagaStatus.IN_PROGRESS);
            saga.advanceTo(SagaStep.SEND_EBMS);

            publisher.publishSagaStepCompleted(saga, SagaStep.SEND_EBMS, "corr-001");

            verify(outboxService).saveWithRouting(any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("publishSagaCompleted()")
    class PublishSagaCompletedTests {

        @Test
        @DisplayName("publishes completed event to outbox")
        void publishesToOutbox() {
            SagaInstance saga = createSaga(SagaStatus.COMPLETED);

            publisher.publishSagaCompleted(saga, "corr-001", "INV-001");

            verify(outboxService).saveWithRouting(
                    any(), eq("SagaInstance"), eq(saga.getId()),
                    eq("saga.lifecycle.completed"), eq(saga.getId()), any());
        }

        @Test
        @DisplayName("publishes with null invoice number")
        void publishesWithNullInvoiceNumber() {
            SagaInstance saga = createSaga(SagaStatus.COMPLETED);

            publisher.publishSagaCompleted(saga, "corr-001", null);

            verify(outboxService).saveWithRouting(any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("publishSagaFailed()")
    class PublishSagaFailedTests {

        @Test
        @DisplayName("publishes failed event to outbox")
        void publishesToOutbox() {
            SagaInstance saga = createSaga(SagaStatus.FAILED);

            publisher.publishSagaFailed(saga, SagaStep.PROCESS_INVOICE, "Something failed", "corr-001", "INV-001");

            verify(outboxService).saveWithRouting(
                    any(), eq("SagaInstance"), eq(saga.getId()),
                    eq("saga.lifecycle.failed"), eq(saga.getId()), any());
        }

        @Test
        @DisplayName("publishes with null failed step")
        void publishesWithNullStep() {
            SagaInstance saga = createSaga(SagaStatus.FAILED);

            publisher.publishSagaFailed(saga, null, "Something failed", "corr-001", null);

            verify(outboxService).saveWithRouting(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("publishes with compensating status")
        void publishesWithCompensatingStatus() {
            SagaInstance saga = createSaga(SagaStatus.COMPENSATING);

            publisher.publishSagaFailed(saga, SagaStep.SIGN_XML, "Error", "corr-001", null);

            verify(outboxService).saveWithRouting(any(), any(), any(), any(), any(), any());
        }
    }
}
