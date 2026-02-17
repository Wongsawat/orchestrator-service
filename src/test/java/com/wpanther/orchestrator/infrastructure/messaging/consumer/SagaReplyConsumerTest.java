package com.wpanther.orchestrator.infrastructure.messaging.consumer;

import com.wpanther.orchestrator.application.service.SagaApplicationService;
import com.wpanther.saga.domain.enums.ReplyStatus;
import com.wpanther.saga.domain.model.SagaReply;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SagaReplyConsumerTest {

    @Mock private SagaApplicationService sagaApplicationService;
    @Mock private Acknowledgment acknowledgment;
    @Mock private SagaReply mockReply;

    private SagaReplyConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new SagaReplyConsumer(sagaApplicationService);
    }

    @Nested
    @DisplayName("handleInvoiceReply()")
    class HandleInvoiceReplyTests {
        @Test
        @DisplayName("delegates to application service with success status")
        void success_delegatesToApplicationService() {
            when(mockReply.getSagaId()).thenReturn("saga-001");
            when(mockReply.getSagaStep()).thenReturn("PROCESS_INVOICE");
            when(mockReply.isSuccess()).thenReturn(true);
            when(mockReply.isFailure()).thenReturn(false);

            consumer.handleInvoiceReply(mockReply, "saga.reply.invoice", 0, 0L, acknowledgment);

            verify(sagaApplicationService).handleReply("saga-001", "PROCESS_INVOICE", true, null, null);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("delegates to application service with failure status")
        void failure_delegatesToApplicationService() {
            when(mockReply.getSagaId()).thenReturn("saga-001");
            when(mockReply.getSagaStep()).thenReturn("PROCESS_INVOICE");
            when(mockReply.isSuccess()).thenReturn(false);
            when(mockReply.isFailure()).thenReturn(true);
            when(mockReply.getErrorMessage()).thenReturn("Processing failed");

            consumer.handleInvoiceReply(mockReply, "saga.reply.invoice", 0, 0L, acknowledgment);

            verify(sagaApplicationService).handleReply("saga-001", "PROCESS_INVOICE", false, "Processing failed", null);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("with null reply logs warning and acknowledges")
        void withNullReply_logsWarningAndAcknowledges() {
            consumer.handleInvoiceReply(null, "saga.reply.invoice", 0, 0L, acknowledgment);

            verifyNoInteractions(sagaApplicationService);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("with null sagaId logs warning and acknowledges")
        void withNullSagaId_logsWarningAndAcknowledges() {
            when(mockReply.getSagaId()).thenReturn(null);
            // getSagaStep() is not called when sagaId is null, so no need to stub it

            consumer.handleInvoiceReply(mockReply, "saga.reply.invoice", 0, 0L, acknowledgment);

            verifyNoInteractions(sagaApplicationService);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("with empty sagaId logs warning and acknowledges")
        void withEmptySagaId_logsWarningAndAcknowledges() {
            when(mockReply.getSagaId()).thenReturn("   ");
            // getSagaStep() is not called when sagaId is blank, so no need to stub it

            consumer.handleInvoiceReply(mockReply, "saga.reply.invoice", 0, 0L, acknowledgment);

            verifyNoInteractions(sagaApplicationService);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("with exception still acknowledges to avoid retry loop")
        void withException_stillAcknowledges() {
            when(mockReply.getSagaId()).thenReturn("saga-001");
            when(mockReply.getSagaStep()).thenReturn("PROCESS_INVOICE");
            when(mockReply.isSuccess()).thenReturn(true);
            when(mockReply.isFailure()).thenReturn(false);
            doThrow(new RuntimeException("DB error"))
                .when(sagaApplicationService).handleReply(any(), any(), anyBoolean(), any(), any());

            consumer.handleInvoiceReply(mockReply, "saga.reply.invoice", 0, 0L, acknowledgment);

            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("with null acknowledgment still processes reply")
        void withNullAcknowledgment_stillProcesses() {
            when(mockReply.getSagaId()).thenReturn("saga-001");
            when(mockReply.getSagaStep()).thenReturn("PROCESS_INVOICE");
            when(mockReply.isSuccess()).thenReturn(true);
            when(mockReply.isFailure()).thenReturn(false);

            consumer.handleInvoiceReply(mockReply, "saga.reply.invoice", 0, 0L, null);

            verify(sagaApplicationService).handleReply("saga-001", "PROCESS_INVOICE", true, null, null);
        }

        @Test
        @DisplayName("handles different saga steps")
        void handlesDifferentSteps() {
            when(mockReply.getSagaId()).thenReturn("saga-001");
            when(mockReply.getSagaStep()).thenReturn("SIGN_XML");
            when(mockReply.isSuccess()).thenReturn(true);
            when(mockReply.isFailure()).thenReturn(false);

            consumer.handleInvoiceReply(mockReply, "saga.reply.invoice", 0, 0L, acknowledgment);

            verify(sagaApplicationService).handleReply("saga-001", "SIGN_XML", true, null, null);
        }
    }

    @Nested
    @DisplayName("handleTaxInvoiceReply()")
    class HandleTaxInvoiceReplyTests {
        @Test
        @DisplayName("delegates to application service with success status")
        void success_delegatesToApplicationService() {
            when(mockReply.getSagaId()).thenReturn("saga-001");
            when(mockReply.getSagaStep()).thenReturn("PROCESS_TAX_INVOICE");
            when(mockReply.isSuccess()).thenReturn(true);
            when(mockReply.isFailure()).thenReturn(false);

            consumer.handleTaxInvoiceReply(mockReply, "saga.reply.tax-invoice", 0, 0L, acknowledgment);

            verify(sagaApplicationService).handleReply("saga-001", "PROCESS_TAX_INVOICE", true, null, null);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("delegates to application service with failure status")
        void failure_delegatesToApplicationService() {
            when(mockReply.getSagaId()).thenReturn("saga-001");
            when(mockReply.getSagaStep()).thenReturn("PROCESS_TAX_INVOICE");
            when(mockReply.isSuccess()).thenReturn(false);
            when(mockReply.isFailure()).thenReturn(true);
            when(mockReply.getErrorMessage()).thenReturn("Tax invoice processing failed");

            consumer.handleTaxInvoiceReply(mockReply, "saga.reply.tax-invoice", 0, 0L, acknowledgment);

            verify(sagaApplicationService).handleReply("saga-001", "PROCESS_TAX_INVOICE", false, "Tax invoice processing failed", null);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("with null reply logs warning and acknowledges")
        void withNullReply_logsWarningAndAcknowledges() {
            consumer.handleTaxInvoiceReply(null, "saga.reply.tax-invoice", 0, 0L, acknowledgment);

            verifyNoInteractions(sagaApplicationService);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("with exception still acknowledges to avoid retry loop")
        void withException_stillAcknowledges() {
            when(mockReply.getSagaId()).thenReturn("saga-001");
            when(mockReply.getSagaStep()).thenReturn("PROCESS_TAX_INVOICE");
            when(mockReply.isSuccess()).thenReturn(true);
            when(mockReply.isFailure()).thenReturn(false);
            doThrow(new RuntimeException("DB error"))
                .when(sagaApplicationService).handleReply(any(), any(), anyBoolean(), any(), any());

            consumer.handleTaxInvoiceReply(mockReply, "saga.reply.tax-invoice", 0, 0L, acknowledgment);

            verify(acknowledgment).acknowledge();
        }
    }

}
