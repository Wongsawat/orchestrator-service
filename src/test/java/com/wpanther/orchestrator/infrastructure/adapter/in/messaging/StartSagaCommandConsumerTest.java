package com.wpanther.orchestrator.infrastructure.adapter.in.messaging;

import com.wpanther.orchestrator.application.usecase.StartSagaUseCase;
import com.wpanther.orchestrator.infrastructure.adapter.in.messaging.StartSagaCommand;
import com.wpanther.orchestrator.infrastructure.adapter.in.messaging.StartSagaCommandConsumer;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StartSagaCommandConsumer Tests")
class StartSagaCommandConsumerTest {

    @Mock private StartSagaUseCase startSagaUseCase;
    @Mock private Acknowledgment acknowledgment;

    private StartSagaCommandConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new StartSagaCommandConsumer(startSagaUseCase);
    }

    private StartSagaCommand createCommand(String documentType) {
        // Constructor order: (eventId, occurredAt, eventType, version, documentId, source, correlationId, documentType, documentNumber, xmlContent)
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
            when(startSagaUseCase.startSaga(any(DocumentType.class), anyString(), any(DocumentMetadata.class), anyString())).thenReturn(createSagaInstance());

            consumer.handleStartSagaCommand(command, "doc-001", acknowledgment);

            verify(startSagaUseCase).startSaga(any(DocumentType.class), anyString(), any(DocumentMetadata.class), anyString());
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("starts saga for valid TAX_INVOICE document type and acknowledges")
        void validTaxInvoiceCommand_startsAndAcknowledges() {
            StartSagaCommand command = createCommand("TAX_INVOICE");
            SagaInstance saga = SagaInstance.create(DocumentType.TAX_INVOICE, "doc-001",
                    DocumentMetadata.builder().xmlContent("<xml/>").build());
            saga.start();
            when(startSagaUseCase.startSaga(any(DocumentType.class), anyString(), any(DocumentMetadata.class), anyString())).thenReturn(saga);

            consumer.handleStartSagaCommand(command, "doc-001", acknowledgment);

            verify(startSagaUseCase).startSaga(any(DocumentType.class), anyString(), any(DocumentMetadata.class), anyString());
            verify(acknowledgment).acknowledge();
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

            verify(startSagaUseCase, never()).startSaga(any(), any(), any(), any());
            verify(acknowledgment).acknowledge(); // Acknowledge to skip invalid message
        }

        @Test
        @DisplayName("null document type acknowledges and skips message")
        void nullDocumentType_acknowledgesAndSkips() {
            StartSagaCommand command = createCommand(null);

            consumer.handleStartSagaCommand(command, "doc-001", acknowledgment);

            verify(startSagaUseCase, never()).startSaga(any(), any(), any(), any());
            verify(acknowledgment).acknowledge(); // Acknowledge to skip invalid message
        }

        @Test
        @DisplayName("blank document type acknowledges and skips message")
        void blankDocumentType_acknowledgesAndSkips() {
            StartSagaCommand command = createCommand("   ");

            consumer.handleStartSagaCommand(command, "doc-001", acknowledgment);

            verify(startSagaUseCase, never()).startSaga(any(), any(), any(), any());
            verify(acknowledgment).acknowledge(); // Acknowledge to skip invalid message
        }
    }

    @Nested
    @DisplayName("handleStartSagaCommand() - exception handling")
    class ExceptionHandlingTests {

        @Test
        @DisplayName("does not acknowledge on transient exception (throws to trigger Kafka retry)")
        void serviceThrowsException_doesNotAcknowledge() {
            StartSagaCommand command = createCommand("INVOICE");
            when(startSagaUseCase.startSaga(any(DocumentType.class), anyString(), any(DocumentMetadata.class), anyString()))
                    .thenThrow(new RuntimeException("Database connection failed"));

            // Expect exception to be thrown (Kafka will retry)
            org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> {
                consumer.handleStartSagaCommand(command, "doc-001", acknowledgment);
            });

            verify(startSagaUseCase).startSaga(any(DocumentType.class), anyString(), any(DocumentMetadata.class), anyString());
            verify(acknowledgment, never()).acknowledge(); // Don't ack - let Kafka retry
        }

    }
}
