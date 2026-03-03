package com.wpanther.orchestrator.infrastructure.messaging.consumer;

import com.wpanther.orchestrator.application.dto.StartSagaRequest;
import com.wpanther.orchestrator.application.usecase.SagaApplicationService;
import com.wpanther.orchestrator.adapter.in.messaging.StartSagaCommand;
import com.wpanther.orchestrator.adapter.in.messaging.StartSagaCommandConsumer;
import com.wpanther.orchestrator.domain.model.DocumentMetadata;
import com.wpanther.orchestrator.domain.model.SagaInstance;
import com.wpanther.orchestrator.domain.model.enums.DocumentType;
import com.wpanther.saga.domain.enums.SagaStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StartSagaCommandConsumer Tests")
class StartSagaCommandConsumerTest {

    @Mock private SagaApplicationService sagaApplicationService;
    @Mock private Acknowledgment acknowledgment;

    private StartSagaCommandConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new StartSagaCommandConsumer(sagaApplicationService);
    }

    private StartSagaCommand createCommand(String documentType) {
        // Constructor order: (eventId, occurredAt, eventType, version, documentId, source, correlationId, documentType, invoiceNumber, xmlContent)
        return new StartSagaCommand(
                UUID.randomUUID(),
                java.time.Instant.now(),
                "StartSagaCommand",
                1,
                "doc-001",
                "document-intake",
                "corr-001",
                documentType,
                "INV-001",
                "<xml/>"
        );
    }

    private SagaInstance createSagaInstance() {
        SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-001",
                DocumentMetadata.builder().xmlContent("<xml/>").build());
        saga.start();
        return saga;
    }

    @Nested
    @DisplayName("handleStartSagaCommand() - success paths")
    class SuccessPathTests {

        @Test
        @DisplayName("starts saga for valid INVOICE document type and acknowledges")
        void validInvoiceCommand_startsAndAcknowledges() {
            StartSagaCommand command = createCommand("INVOICE");
            when(sagaApplicationService.startSaga(any(StartSagaRequest.class))).thenReturn(createSagaInstance());

            consumer.handleStartSagaCommand(command, "doc-001", acknowledgment);

            verify(sagaApplicationService).startSaga(any(StartSagaRequest.class));
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("starts saga for valid TAX_INVOICE document type and acknowledges")
        void validTaxInvoiceCommand_startsAndAcknowledges() {
            StartSagaCommand command = createCommand("TAX_INVOICE");
            SagaInstance saga = SagaInstance.create(DocumentType.TAX_INVOICE, "doc-001",
                    DocumentMetadata.builder().xmlContent("<xml/>").build());
            saga.start();
            when(sagaApplicationService.startSaga(any(StartSagaRequest.class))).thenReturn(saga);

            consumer.handleStartSagaCommand(command, "doc-001", acknowledgment);

            verify(sagaApplicationService).startSaga(any(StartSagaRequest.class));
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("handles null acknowledgment without NPE")
        void nullAcknowledgment_doesNotThrow() {
            StartSagaCommand command = createCommand("INVOICE");
            when(sagaApplicationService.startSaga(any(StartSagaRequest.class))).thenReturn(createSagaInstance());

            consumer.handleStartSagaCommand(command, "doc-001", null);

            verify(sagaApplicationService).startSaga(any(StartSagaRequest.class));
        }
    }

    @Nested
    @DisplayName("handleStartSagaCommand() - invalid document type")
    class InvalidDocumentTypeTests {

        @Test
        @DisplayName("acknowledges and skips message with invalid document type")
        void invalidDocumentType_acknowledgesAndSkips() {
            StartSagaCommand command = createCommand("UNKNOWN_TYPE");

            consumer.handleStartSagaCommand(command, "doc-001", acknowledgment);

            verify(sagaApplicationService, never()).startSaga(any());
            verify(acknowledgment).acknowledge(); // Acknowledge to skip invalid message
        }

        @Test
        @DisplayName("null document type causes NPE which falls into generic exception handler (no ack)")
        void nullDocumentType_doesNotAcknowledge() {
            // DocumentType.valueOf(null) throws NullPointerException, not IllegalArgumentException
            // This falls into the generic `catch (Exception e)` block which does NOT acknowledge
            StartSagaCommand command = createCommand(null);

            consumer.handleStartSagaCommand(command, "doc-001", acknowledgment);

            verify(sagaApplicationService, never()).startSaga(any());
            verify(acknowledgment, never()).acknowledge(); // NPE → retry
        }
    }

    @Nested
    @DisplayName("handleStartSagaCommand() - exception handling")
    class ExceptionHandlingTests {

        @Test
        @DisplayName("does not acknowledge on generic exception (triggers retry)")
        void serviceThrowsException_doesNotAcknowledge() {
            StartSagaCommand command = createCommand("INVOICE");
            when(sagaApplicationService.startSaga(any(StartSagaRequest.class)))
                    .thenThrow(new RuntimeException("Database connection failed"));

            consumer.handleStartSagaCommand(command, "doc-001", acknowledgment);

            verify(sagaApplicationService).startSaga(any(StartSagaRequest.class));
            verify(acknowledgment, never()).acknowledge(); // Don't ack - let Kafka retry
        }

        @Test
        @DisplayName("does not acknowledge on null ack when exception occurs")
        void nullAckWithException_doesNotThrow() {
            StartSagaCommand command = createCommand("INVOICE");
            when(sagaApplicationService.startSaga(any(StartSagaRequest.class)))
                    .thenThrow(new RuntimeException("Database error"));

            consumer.handleStartSagaCommand(command, "doc-001", null);

            // Should not throw NPE even with null ack
            verify(sagaApplicationService).startSaga(any(StartSagaRequest.class));
        }
    }
}
