package com.wpanther.orchestrator.infrastructure.messaging.consumer;

import com.wpanther.orchestrator.application.service.SagaApplicationService;
import com.wpanther.orchestrator.infrastructure.messaging.ConcreteSagaReply;
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
            when(mockReply.getSagaStep()).thenReturn(SagaStep.PROCESS_INVOICE);
            when(mockReply.isSuccess()).thenReturn(true);
            when(mockReply.isFailure()).thenReturn(false);

            consumer.handleInvoiceReply(mockReply, "saga.reply.invoice", 0, 0L, acknowledgment);

            verify(sagaApplicationService).handleReply("saga-001", "process-invoice", true, null, null);
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

            consumer.handleInvoiceReply(mockReply, "saga.reply.invoice", 0, 0L, acknowledgment);

            verify(sagaApplicationService).handleReply("saga-001", "process-invoice", false, "Processing failed", null);
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
            when(mockReply.getSagaStep()).thenReturn(SagaStep.PROCESS_INVOICE);
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
            when(mockReply.getSagaStep()).thenReturn(SagaStep.PROCESS_INVOICE);
            when(mockReply.isSuccess()).thenReturn(true);
            when(mockReply.isFailure()).thenReturn(false);

            consumer.handleInvoiceReply(mockReply, "saga.reply.invoice", 0, 0L, null);

            verify(sagaApplicationService).handleReply("saga-001", "process-invoice", true, null, null);
        }

        @Test
        @DisplayName("handles different saga steps")
        void handlesDifferentSteps() {
            when(mockReply.getSagaId()).thenReturn("saga-001");
            when(mockReply.getSagaStep()).thenReturn(SagaStep.SIGN_XML);
            when(mockReply.isSuccess()).thenReturn(true);
            when(mockReply.isFailure()).thenReturn(false);

            consumer.handleInvoiceReply(mockReply, "saga.reply.invoice", 0, 0L, acknowledgment);

            verify(sagaApplicationService).handleReply("saga-001", "sign-xml", true, null, null);
        }
    }

    @Nested
    @DisplayName("handleTaxInvoiceReply()")
    class HandleTaxInvoiceReplyTests {
        @Test
        @DisplayName("delegates to application service with success status")
        void success_delegatesToApplicationService() {
            when(mockReply.getSagaId()).thenReturn("saga-001");
            when(mockReply.getSagaStep()).thenReturn(SagaStep.PROCESS_TAX_INVOICE);
            when(mockReply.isSuccess()).thenReturn(true);
            when(mockReply.isFailure()).thenReturn(false);

            consumer.handleTaxInvoiceReply(mockReply, "saga.reply.tax-invoice", 0, 0L, acknowledgment);

            verify(sagaApplicationService).handleReply("saga-001", "process-tax-invoice", true, null, null);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("delegates to application service with failure status")
        void failure_delegatesToApplicationService() {
            when(mockReply.getSagaId()).thenReturn("saga-001");
            when(mockReply.getSagaStep()).thenReturn(SagaStep.PROCESS_TAX_INVOICE);
            when(mockReply.isSuccess()).thenReturn(false);
            when(mockReply.isFailure()).thenReturn(true);
            when(mockReply.getErrorMessage()).thenReturn("Tax invoice processing failed");

            consumer.handleTaxInvoiceReply(mockReply, "saga.reply.tax-invoice", 0, 0L, acknowledgment);

            verify(sagaApplicationService).handleReply("saga-001", "process-tax-invoice", false, "Tax invoice processing failed", null);
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
            when(mockReply.getSagaStep()).thenReturn(SagaStep.PROCESS_TAX_INVOICE);
            when(mockReply.isSuccess()).thenReturn(true);
            when(mockReply.isFailure()).thenReturn(false);
            doThrow(new RuntimeException("DB error"))
                .when(sagaApplicationService).handleReply(any(), any(), anyBoolean(), any(), any());

            consumer.handleTaxInvoiceReply(mockReply, "saga.reply.tax-invoice", 0, 0L, acknowledgment);

            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("handleDocumentStorageReply()")
    class HandleDocumentStorageReplyTests {

        @Test
        @DisplayName("delegates to application service with success")
        void success_delegatesToApplicationService() {
            when(mockReply.getSagaId()).thenReturn("saga-001");
            when(mockReply.getSagaStep()).thenReturn(SagaStep.STORE_DOCUMENT);
            when(mockReply.isSuccess()).thenReturn(true);
            when(mockReply.isFailure()).thenReturn(false);

            consumer.handleDocumentStorageReply(mockReply, "saga.reply.document-storage", 0, 0L, acknowledgment);

            verify(sagaApplicationService).handleReply("saga-001", "store-document", true, null, null);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("delegates with failure status")
        void failure_delegatesToApplicationService() {
            when(mockReply.getSagaId()).thenReturn("saga-001");
            when(mockReply.getSagaStep()).thenReturn(SagaStep.STORE_DOCUMENT);
            when(mockReply.isSuccess()).thenReturn(false);
            when(mockReply.isFailure()).thenReturn(true);
            when(mockReply.getErrorMessage()).thenReturn("Storage failed");

            consumer.handleDocumentStorageReply(mockReply, "saga.reply.document-storage", 0, 0L, acknowledgment);

            verify(sagaApplicationService).handleReply("saga-001", "store-document", false, "Storage failed", null);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("with null reply acknowledges and skips")
        void nullReply_acknowledgesAndSkips() {
            consumer.handleDocumentStorageReply(null, "saga.reply.document-storage", 0, 0L, acknowledgment);

            verifyNoInteractions(sagaApplicationService);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("with exception still acknowledges")
        void withException_stillAcknowledges() {
            when(mockReply.getSagaId()).thenReturn("saga-001");
            when(mockReply.getSagaStep()).thenReturn(SagaStep.STORE_DOCUMENT);
            when(mockReply.isSuccess()).thenReturn(true);
            when(mockReply.isFailure()).thenReturn(false);
            doThrow(new RuntimeException("DB error"))
                .when(sagaApplicationService).handleReply(any(), any(), anyBoolean(), any(), any());

            consumer.handleDocumentStorageReply(mockReply, "saga.reply.document-storage", 0, 0L, acknowledgment);

            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("handleXmlSigningReply()")
    class HandleXmlSigningReplyTests {

        @Test
        @DisplayName("delegates to application service with success")
        void success_delegatesToApplicationService() {
            when(mockReply.getSagaId()).thenReturn("saga-001");
            when(mockReply.getSagaStep()).thenReturn(SagaStep.SIGN_XML);
            when(mockReply.isSuccess()).thenReturn(true);
            when(mockReply.isFailure()).thenReturn(false);

            consumer.handleXmlSigningReply(mockReply, "saga.reply.xml-signing", 0, 0L, acknowledgment);

            verify(sagaApplicationService).handleReply("saga-001", "sign-xml", true, null, null);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("with null reply acknowledges and skips")
        void nullReply_acknowledgesAndSkips() {
            consumer.handleXmlSigningReply(null, "saga.reply.xml-signing", 0, 0L, acknowledgment);

            verifyNoInteractions(sagaApplicationService);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("with exception still acknowledges")
        void withException_stillAcknowledges() {
            when(mockReply.getSagaId()).thenReturn("saga-001");
            when(mockReply.getSagaStep()).thenReturn(SagaStep.SIGN_XML);
            when(mockReply.isSuccess()).thenReturn(true);
            when(mockReply.isFailure()).thenReturn(false);
            doThrow(new RuntimeException("DB error"))
                .when(sagaApplicationService).handleReply(any(), any(), anyBoolean(), any(), any());

            consumer.handleXmlSigningReply(mockReply, "saga.reply.xml-signing", 0, 0L, acknowledgment);

            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("handleSignedXmlStorageReply()")
    class HandleSignedXmlStorageReplyTests {

        @Test
        @DisplayName("delegates to application service with success")
        void success_delegatesToApplicationService() {
            when(mockReply.getSagaId()).thenReturn("saga-001");
            when(mockReply.getSagaStep()).thenReturn(SagaStep.SIGNEDXML_STORAGE);
            when(mockReply.isSuccess()).thenReturn(true);
            when(mockReply.isFailure()).thenReturn(false);

            consumer.handleSignedXmlStorageReply(mockReply, "saga.reply.signedxml-storage", 0, 0L, acknowledgment);

            verify(sagaApplicationService).handleReply("saga-001", "signedxml-storage", true, null, null);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("with null reply acknowledges and skips")
        void nullReply_acknowledgesAndSkips() {
            consumer.handleSignedXmlStorageReply(null, "saga.reply.signedxml-storage", 0, 0L, acknowledgment);

            verifyNoInteractions(sagaApplicationService);
            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("handleInvoicePdfReply()")
    class HandleInvoicePdfReplyTests {

        @Test
        @DisplayName("delegates to application service with success")
        void success_delegatesToApplicationService() {
            when(mockReply.getSagaId()).thenReturn("saga-001");
            when(mockReply.getSagaStep()).thenReturn(SagaStep.GENERATE_INVOICE_PDF);
            when(mockReply.isSuccess()).thenReturn(true);
            when(mockReply.isFailure()).thenReturn(false);

            consumer.handleInvoicePdfReply(mockReply, "saga.reply.invoice-pdf", 0, 0L, acknowledgment);

            verify(sagaApplicationService).handleReply("saga-001", "generate-invoice-pdf", true, null, null);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("with null reply acknowledges and skips")
        void nullReply_acknowledgesAndSkips() {
            consumer.handleInvoicePdfReply(null, "saga.reply.invoice-pdf", 0, 0L, acknowledgment);

            verifyNoInteractions(sagaApplicationService);
            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("handleTaxInvoicePdfReply()")
    class HandleTaxInvoicePdfReplyTests {

        @Test
        @DisplayName("delegates to application service with success")
        void success_delegatesToApplicationService() {
            when(mockReply.getSagaId()).thenReturn("saga-001");
            when(mockReply.getSagaStep()).thenReturn(SagaStep.GENERATE_TAX_INVOICE_PDF);
            when(mockReply.isSuccess()).thenReturn(true);
            when(mockReply.isFailure()).thenReturn(false);

            consumer.handleTaxInvoicePdfReply(mockReply, "saga.reply.tax-invoice-pdf", 0, 0L, acknowledgment);

            verify(sagaApplicationService).handleReply("saga-001", "generate-tax-invoice-pdf", true, null, null);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("with null reply acknowledges and skips")
        void nullReply_acknowledgesAndSkips() {
            consumer.handleTaxInvoicePdfReply(null, "saga.reply.tax-invoice-pdf", 0, 0L, acknowledgment);

            verifyNoInteractions(sagaApplicationService);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("with exception still acknowledges")
        void withException_stillAcknowledges() {
            when(mockReply.getSagaId()).thenReturn("saga-001");
            when(mockReply.getSagaStep()).thenReturn(SagaStep.GENERATE_TAX_INVOICE_PDF);
            when(mockReply.isSuccess()).thenReturn(true);
            when(mockReply.isFailure()).thenReturn(false);
            doThrow(new RuntimeException("Error"))
                .when(sagaApplicationService).handleReply(any(), any(), anyBoolean(), any(), any());

            consumer.handleTaxInvoicePdfReply(mockReply, "saga.reply.tax-invoice-pdf", 0, 0L, acknowledgment);

            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("handlePdfStorageReply()")
    class HandlePdfStorageReplyTests {

        @Test
        @DisplayName("delegates to application service with success")
        void success_delegatesToApplicationService() {
            when(mockReply.getSagaId()).thenReturn("saga-001");
            when(mockReply.getSagaStep()).thenReturn(SagaStep.PDF_STORAGE);
            when(mockReply.isSuccess()).thenReturn(true);
            when(mockReply.isFailure()).thenReturn(false);

            consumer.handlePdfStorageReply(mockReply, "saga.reply.pdf-storage", 0, 0L, acknowledgment);

            verify(sagaApplicationService).handleReply("saga-001", "pdf-storage", true, null, null);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("with null reply acknowledges and skips")
        void nullReply_acknowledgesAndSkips() {
            consumer.handlePdfStorageReply(null, "saga.reply.pdf-storage", 0, 0L, acknowledgment);

            verifyNoInteractions(sagaApplicationService);
            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("handlePdfSigningReply()")
    class HandlePdfSigningReplyTests {

        @Test
        @DisplayName("delegates to application service with success")
        void success_delegatesToApplicationService() {
            when(mockReply.getSagaId()).thenReturn("saga-001");
            when(mockReply.getSagaStep()).thenReturn(SagaStep.SIGN_PDF);
            when(mockReply.isSuccess()).thenReturn(true);
            when(mockReply.isFailure()).thenReturn(false);

            consumer.handlePdfSigningReply(mockReply, "saga.reply.pdf-signing", 0, 0L, acknowledgment);

            verify(sagaApplicationService).handleReply("saga-001", "sign-pdf", true, null, null);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("with ConcreteSagaReply passes additional data to handleReply")
        void concreteReplyWithAdditionalData_passesDataToService() {
            ConcreteSagaReply concreteReply = new ConcreteSagaReply(
                UUID.randomUUID(), Instant.now(), "SagaReplyEvent", 1,
                "saga-001", SagaStep.SIGN_PDF, "corr-001", ReplyStatus.SUCCESS, null
            );
            concreteReply.setAdditionalData("signedPdfUrl", "http://storage/signed.pdf");
            concreteReply.setAdditionalData("signedDocumentId", "signed-doc-001");

            consumer.handlePdfSigningReply(concreteReply, "saga.reply.pdf-signing", 0, 0L, acknowledgment);

            verify(sagaApplicationService).handleReply(
                eq("saga-001"), eq("sign-pdf"), eq(true), isNull(),
                argThat(data -> data != null && data.containsKey("signedPdfUrl"))
            );
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("with null reply acknowledges and skips")
        void nullReply_acknowledgesAndSkips() {
            consumer.handlePdfSigningReply(null, "saga.reply.pdf-signing", 0, 0L, acknowledgment);

            verifyNoInteractions(sagaApplicationService);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("with exception still acknowledges")
        void withException_stillAcknowledges() {
            when(mockReply.getSagaId()).thenReturn("saga-001");
            when(mockReply.getSagaStep()).thenReturn(SagaStep.SIGN_PDF);
            when(mockReply.isSuccess()).thenReturn(true);
            when(mockReply.isFailure()).thenReturn(false);
            doThrow(new RuntimeException("Error"))
                .when(sagaApplicationService).handleReply(any(), any(), anyBoolean(), any(), any());

            consumer.handlePdfSigningReply(mockReply, "saga.reply.pdf-signing", 0, 0L, acknowledgment);

            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("handleEbmsSendingReply()")
    class HandleEbmsSendingReplyTests {

        @Test
        @DisplayName("delegates to application service with success")
        void success_delegatesToApplicationService() {
            when(mockReply.getSagaId()).thenReturn("saga-001");
            when(mockReply.getSagaStep()).thenReturn(SagaStep.SEND_EBMS);
            when(mockReply.isSuccess()).thenReturn(true);
            when(mockReply.isFailure()).thenReturn(false);

            consumer.handleEbmsSendingReply(mockReply, "saga.reply.ebms-sending", 0, 0L, acknowledgment);

            verify(sagaApplicationService).handleReply("saga-001", "send-ebms", true, null, null);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("with failure status passes error message")
        void failure_passesErrorMessage() {
            when(mockReply.getSagaId()).thenReturn("saga-001");
            when(mockReply.getSagaStep()).thenReturn(SagaStep.SEND_EBMS);
            when(mockReply.isSuccess()).thenReturn(false);
            when(mockReply.isFailure()).thenReturn(true);
            when(mockReply.getErrorMessage()).thenReturn("EBMS sending failed");

            consumer.handleEbmsSendingReply(mockReply, "saga.reply.ebms-sending", 0, 0L, acknowledgment);

            verify(sagaApplicationService).handleReply("saga-001", "send-ebms", false, "EBMS sending failed", null);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("with null reply acknowledges and skips")
        void nullReply_acknowledgesAndSkips() {
            consumer.handleEbmsSendingReply(null, "saga.reply.ebms-sending", 0, 0L, acknowledgment);

            verifyNoInteractions(sagaApplicationService);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("with exception still acknowledges")
        void withException_stillAcknowledges() {
            when(mockReply.getSagaId()).thenReturn("saga-001");
            when(mockReply.getSagaStep()).thenReturn(SagaStep.SEND_EBMS);
            when(mockReply.isSuccess()).thenReturn(true);
            when(mockReply.isFailure()).thenReturn(false);
            doThrow(new RuntimeException("Error"))
                .when(sagaApplicationService).handleReply(any(), any(), anyBoolean(), any(), any());

            consumer.handleEbmsSendingReply(mockReply, "saga.reply.ebms-sending", 0, 0L, acknowledgment);

            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("processReply() - ConcreteSagaReply path")
    class ProcessReplyWithConcreteTests {

        @Test
        @DisplayName("passes empty additionalData as null resultData when map is empty")
        void emptyAdditionalData_passesNullResultData() {
            ConcreteSagaReply concreteReply = new ConcreteSagaReply(
                UUID.randomUUID(), Instant.now(), "SagaReplyEvent", 1,
                "saga-001", SagaStep.SIGNEDXML_STORAGE, "corr-001", ReplyStatus.SUCCESS, null
            );
            // No additional data set → getAdditionalData() returns empty map

            consumer.handleSignedXmlStorageReply(concreteReply, "saga.reply.signedxml-storage", 0, 0L, acknowledgment);

            verify(sagaApplicationService).handleReply(
                eq("saga-001"), eq("signedxml-storage"), eq(true), isNull(), isNull()
            );
        }
    }

}
