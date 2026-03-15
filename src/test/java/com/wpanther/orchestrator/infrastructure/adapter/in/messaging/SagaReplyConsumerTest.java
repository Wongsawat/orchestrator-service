package com.wpanther.orchestrator.infrastructure.adapter.in.messaging;

import com.wpanther.orchestrator.application.usecase.HandleSagaReplyUseCase;
import com.wpanther.saga.domain.enums.ReplyStatus;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.domain.model.SagaReply;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SagaReplyConsumerTest {

    @Mock private HandleSagaReplyUseCase handleSagaReplyUseCase;
    @Mock private Acknowledgment acknowledgment;
    @Mock private SagaReply mockReply;

    private SagaReplyConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new SagaReplyConsumer(handleSagaReplyUseCase);
    }

    @Nested
    @DisplayName("handleSagaReply()")
    class HandleSagaReplyTests {

        @Test
        @DisplayName("delegates to application service with success status")
        void success_delegatesToApplicationService() {
            when(mockReply.getSagaId()).thenReturn("saga-001");
            when(mockReply.getSagaStep()).thenReturn(SagaStep.PROCESS_INVOICE);
            when(mockReply.isSuccess()).thenReturn(true);
            when(mockReply.isFailure()).thenReturn(false);

            consumer.handleSagaReply(mockReply, "saga.reply.invoice", 0, 0L, acknowledgment);

            verify(handleSagaReplyUseCase).handleReply("saga-001", "process-invoice", true, null, null);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("delegates to application service with failure status")
        void failure_delegatesToApplicationService() {
            when(mockReply.getSagaId()).thenReturn("saga-001");
            when(mockReply.getSagaStep()).thenReturn(SagaStep.PROCESS_INVOICE);
            when(mockReply.isSuccess()).thenReturn(false);
            when(mockReply.isFailure()).thenReturn(true);
            when(mockReply.getErrorMessage()).thenReturn("Processing failed");

            consumer.handleSagaReply(mockReply, "saga.reply.invoice", 0, 0L, acknowledgment);

            verify(handleSagaReplyUseCase).handleReply("saga-001", "process-invoice", false, "Processing failed", null);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("with null reply logs warning and acknowledges")
        void withNullReply_logsWarningAndAcknowledges() {
            consumer.handleSagaReply(null, "saga.reply.invoice", 0, 0L, acknowledgment);

            verifyNoInteractions(handleSagaReplyUseCase);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("with null sagaId logs warning and acknowledges")
        void withNullSagaId_logsWarningAndAcknowledges() {
            when(mockReply.getSagaId()).thenReturn(null);

            consumer.handleSagaReply(mockReply, "saga.reply.invoice", 0, 0L, acknowledgment);

            verifyNoInteractions(handleSagaReplyUseCase);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("with empty sagaId logs warning and acknowledges")
        void withEmptySagaId_logsWarningAndAcknowledges() {
            when(mockReply.getSagaId()).thenReturn("   ");

            consumer.handleSagaReply(mockReply, "saga.reply.invoice", 0, 0L, acknowledgment);

            verifyNoInteractions(handleSagaReplyUseCase);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("with exception still acknowledges to avoid retry loop")
        void withException_stillAcknowledges() {
            when(mockReply.getSagaId()).thenReturn("saga-001");
            when(mockReply.getSagaStep()).thenReturn(SagaStep.PROCESS_INVOICE);
            when(mockReply.isSuccess()).thenReturn(true);
            doThrow(new RuntimeException("Unexpected error"))
                    .when(handleSagaReplyUseCase).handleReply(any(), any(), anyBoolean(), any(), any());

            consumer.handleSagaReply(mockReply, "saga.reply.invoice", 0, 0L, acknowledgment);

            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("handles tax invoice reply")
        void handlesTaxInvoiceReply() {
            when(mockReply.getSagaId()).thenReturn("saga-002");
            when(mockReply.getSagaStep()).thenReturn(SagaStep.PROCESS_TAX_INVOICE);
            when(mockReply.isSuccess()).thenReturn(true);

            consumer.handleSagaReply(mockReply, "saga.reply.tax-invoice", 0, 0L, acknowledgment);

            verify(handleSagaReplyUseCase).handleReply("saga-002", "process-tax-invoice", true, null, null);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("handles document storage reply")
        void handlesDocumentStorageReply() {
            when(mockReply.getSagaId()).thenReturn("saga-003");
            when(mockReply.getSagaStep()).thenReturn(SagaStep.STORE_DOCUMENT);
            when(mockReply.isSuccess()).thenReturn(true);

            consumer.handleSagaReply(mockReply, "saga.reply.document-storage", 0, 0L, acknowledgment);

            verify(handleSagaReplyUseCase).handleReply("saga-003", "store-document", true, null, null);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("handles XML signing reply")
        void handlesXmlSigningReply() {
            when(mockReply.getSagaId()).thenReturn("saga-004");
            when(mockReply.getSagaStep()).thenReturn(SagaStep.SIGN_XML);
            when(mockReply.isSuccess()).thenReturn(true);

            consumer.handleSagaReply(mockReply, "saga.reply.xml-signing", 0, 0L, acknowledgment);

            verify(handleSagaReplyUseCase).handleReply("saga-004", "sign-xml", true, null, null);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("handles signed XML storage reply")
        void handlesSignedXmlStorageReply() {
            when(mockReply.getSagaId()).thenReturn("saga-005");
            when(mockReply.getSagaStep()).thenReturn(SagaStep.SIGNEDXML_STORAGE);
            when(mockReply.isSuccess()).thenReturn(true);

            consumer.handleSagaReply(mockReply, "saga.reply.signedxml-storage", 0, 0L, acknowledgment);

            verify(handleSagaReplyUseCase).handleReply("saga-005", "signedxml-storage", true, null, null);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("handles invoice PDF reply")
        void handlesInvoicePdfReply() {
            when(mockReply.getSagaId()).thenReturn("saga-006");
            when(mockReply.getSagaStep()).thenReturn(SagaStep.GENERATE_INVOICE_PDF);
            when(mockReply.isSuccess()).thenReturn(true);

            consumer.handleSagaReply(mockReply, "saga.reply.invoice-pdf", 0, 0L, acknowledgment);

            verify(handleSagaReplyUseCase).handleReply("saga-006", "generate-invoice-pdf", true, null, null);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("handles tax invoice PDF reply")
        void handlesTaxInvoicePdfReply() {
            when(mockReply.getSagaId()).thenReturn("saga-007");
            when(mockReply.getSagaStep()).thenReturn(SagaStep.GENERATE_TAX_INVOICE_PDF);
            when(mockReply.isSuccess()).thenReturn(true);

            consumer.handleSagaReply(mockReply, "saga.reply.tax-invoice-pdf", 0, 0L, acknowledgment);

            verify(handleSagaReplyUseCase).handleReply("saga-007", "generate-tax-invoice-pdf", true, null, null);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("handles PDF storage reply")
        void handlesPdfStorageReply() {
            when(mockReply.getSagaId()).thenReturn("saga-008");
            when(mockReply.getSagaStep()).thenReturn(SagaStep.PDF_STORAGE);
            when(mockReply.isSuccess()).thenReturn(true);

            consumer.handleSagaReply(mockReply, "saga.reply.pdf-storage", 0, 0L, acknowledgment);

            verify(handleSagaReplyUseCase).handleReply("saga-008", "pdf-storage", true, null, null);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("handles PDF signing reply")
        void handlesPdfSigningReply() {
            when(mockReply.getSagaId()).thenReturn("saga-009");
            when(mockReply.getSagaStep()).thenReturn(SagaStep.SIGN_PDF);
            when(mockReply.isSuccess()).thenReturn(true);

            consumer.handleSagaReply(mockReply, "saga.reply.pdf-signing", 0, 0L, acknowledgment);

            verify(handleSagaReplyUseCase).handleReply("saga-009", "sign-pdf", true, null, null);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("handles ebMS sending reply")
        void handlesEbmsSendingReply() {
            when(mockReply.getSagaId()).thenReturn("saga-010");
            when(mockReply.getSagaStep()).thenReturn(SagaStep.SEND_EBMS);
            when(mockReply.isSuccess()).thenReturn(true);

            consumer.handleSagaReply(mockReply, "saga.reply.ebms-sending", 0, 0L, acknowledgment);

            verify(handleSagaReplyUseCase).handleReply("saga-010", "send-ebms", true, null, null);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("passes additional data from ConcreteSagaReply to application service")
        void passesAdditionalData_fromConcreteSagaReply() {
            ConcreteSagaReply concreteReply = new ConcreteSagaReply(
                    UUID.randomUUID(),
                    Instant.now(),
                    "saga.step-completed",
                    1,
                    "saga-011",
                    SagaStep.SIGN_PDF,
                    UUID.randomUUID().toString(),
                    ReplyStatus.SUCCESS,
                    null
            );
            // Add additional data via @JsonAnySetter method
            concreteReply.setAdditionalData("signedPdfUrl", "http://example.com/signed.pdf");
            concreteReply.setAdditionalData("signatureLevel", "PAdES-BASELINE-T");

            consumer.handleSagaReply(concreteReply, "saga.reply.pdf-signing", 0, 0L, acknowledgment);

            verify(handleSagaReplyUseCase).handleReply(
                    eq("saga-011"),
                    eq("sign-pdf"),
                    eq(true),
                    isNull(),
                    argThat(data -> data != null
                            && data.size() == 2
                            && "http://example.com/signed.pdf".equals(data.get("signedPdfUrl"))
                            && "PAdES-BASELINE-T".equals(data.get("signatureLevel"))
                    )
            );
        }

        @Test
        @DisplayName("with null acknowledgment does not throw NPE")
        void withNullAcknowledgment_doesNotThrow() {
            when(mockReply.getSagaId()).thenReturn("saga-012");
            when(mockReply.getSagaStep()).thenReturn(SagaStep.PROCESS_INVOICE);
            when(mockReply.isSuccess()).thenReturn(true);

            assertThatCode(() -> {
                consumer.handleSagaReply(mockReply, "saga.reply.invoice", 0, 0L, null);
            }).doesNotThrowAnyException();

            verify(handleSagaReplyUseCase).handleReply("saga-012", "process-invoice", true, null, null);
        }
    }
}
