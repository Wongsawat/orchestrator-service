package com.wpanther.orchestrator.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.orchestrator.application.dto.StartSagaRequest;
import com.wpanther.orchestrator.domain.model.DocumentMetadata;
import com.wpanther.orchestrator.domain.model.SagaCommandRecord;
import com.wpanther.orchestrator.domain.model.SagaInstance;
import com.wpanther.orchestrator.domain.model.enums.DocumentType;
import com.wpanther.orchestrator.domain.repository.SagaCommandRecordRepository;
import com.wpanther.orchestrator.domain.repository.SagaInstanceRepository;
import com.wpanther.orchestrator.infrastructure.messaging.producer.SagaCommandProducer;
import com.wpanther.orchestrator.infrastructure.messaging.producer.SagaCommandPublisher;
import com.wpanther.orchestrator.infrastructure.messaging.producer.SagaEventPublisher;
import com.wpanther.saga.domain.enums.SagaStatus;
import com.wpanther.saga.domain.enums.SagaStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SagaApplicationServiceTest {

    @Mock private SagaInstanceRepository sagaRepository;
    @Mock private SagaCommandRecordRepository commandRepository;
    @Mock private SagaCommandProducer commandProducer;
    @Mock private SagaCommandPublisher commandPublisher;
    @Mock private SagaEventPublisher eventPublisher;

    private ObjectMapper objectMapper;
    private SagaApplicationService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new SagaApplicationService(
            sagaRepository, commandRepository, commandProducer,
            commandPublisher, eventPublisher, objectMapper
        );
    }

    @Nested
    @DisplayName("startSaga(DocumentType, String, DocumentMetadata)")
    class StartSagaBasicTests {
        @Test
        void createsNewSaga_whenNoExistingSaga() {
            when(sagaRepository.findByDocumentTypeAndDocumentId(any(), any()))
                .thenReturn(Optional.empty());
            when(sagaRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(commandRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            DocumentMetadata metadata = createMetadata();
            SagaInstance result = service.startSaga(DocumentType.INVOICE, "doc-001", metadata);

            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(SagaStatus.IN_PROGRESS);
            assertThat(result.getDocumentType()).isEqualTo(DocumentType.INVOICE);
            assertThat(result.getDocumentId()).isEqualTo("doc-001");

            verify(sagaRepository).save(any(SagaInstance.class));
            verify(eventPublisher).publishSagaStarted(any(), any(), any());
            verify(commandPublisher).publishCommandForStep(any(), eq(SagaStep.PROCESS_INVOICE), any());
            verify(commandProducer).sendCommand(any(), any(), eq(true));
        }

        @Test
        void returnsExistingSaga_whenActiveSagaExists() {
            SagaInstance existing = createSaga(SagaStatus.IN_PROGRESS, "existing-saga-id");
            when(sagaRepository.findByDocumentTypeAndDocumentId(any(), any()))
                .thenReturn(Optional.of(existing));

            DocumentMetadata metadata = createMetadata();
            SagaInstance result = service.startSaga(DocumentType.INVOICE, "doc-001", metadata);

            assertThat(result).isSameAs(existing);
            verify(sagaRepository, never()).save(any());
            verify(eventPublisher, never()).publishSagaStarted(any(), any(), any());
            verify(commandPublisher, never()).publishCommandForStep(any(), any(), any());
        }

        @Test
        void createsNewSaga_whenExistingSagaIsFailed() {
            SagaInstance failed = createSaga(SagaStatus.FAILED, "failed-saga-id");
            when(sagaRepository.findByDocumentTypeAndDocumentId(any(), any()))
                .thenReturn(Optional.of(failed));
            when(sagaRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(commandRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            DocumentMetadata metadata = createMetadata();
            SagaInstance result = service.startSaga(DocumentType.INVOICE, "doc-001", metadata);

            assertThat(result.getId()).isNotEqualTo(failed.getId());
            assertThat(result.getStatus()).isEqualTo(SagaStatus.IN_PROGRESS);
        }

        @Test
        void createsNewSaga_whenExistingSagaIsCompleted() {
            SagaInstance completed = createSaga(SagaStatus.COMPLETED, "completed-saga-id");
            when(sagaRepository.findByDocumentTypeAndDocumentId(any(), any()))
                .thenReturn(Optional.of(completed));
            when(sagaRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(commandRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            DocumentMetadata metadata = createMetadata();
            SagaInstance result = service.startSaga(DocumentType.INVOICE, "doc-001", metadata);

            assertThat(result.getId()).isNotEqualTo(completed.getId());
            assertThat(result.getStatus()).isEqualTo(SagaStatus.IN_PROGRESS);
        }

        @Test
        void withTaxInvoice_startsAtProcessTaxInvoice() {
            when(sagaRepository.findByDocumentTypeAndDocumentId(any(), any()))
                .thenReturn(Optional.empty());
            when(sagaRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(commandRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            DocumentMetadata metadata = createMetadata();
            SagaInstance result = service.startSaga(DocumentType.TAX_INVOICE, "doc-001", metadata);

            assertThat(result.getCurrentStep()).isEqualTo(SagaStep.PROCESS_TAX_INVOICE);
        }

        @Test
        void savesCommandRecord_forInitialCommand() {
            when(sagaRepository.findByDocumentTypeAndDocumentId(any(), any()))
                .thenReturn(Optional.empty());
            when(sagaRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(commandRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            DocumentMetadata metadata = createMetadata();
            service.startSaga(DocumentType.INVOICE, "doc-001", metadata);

            verify(commandRepository, atLeastOnce()).save(any(SagaCommandRecord.class));
        }
    }

    @Nested
    @DisplayName("startSaga(StartSagaRequest)")
    class StartSagaRequestTests {
        @Test
        void createsSagaFromRequest() {
            when(sagaRepository.findByDocumentTypeAndDocumentId(any(), any()))
                .thenReturn(Optional.empty());
            when(sagaRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(commandRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            StartSagaRequest request = new StartSagaRequest(
                DocumentType.INVOICE,
                "doc-001",
                "/path/to/file.xml",
                "<Invoice/>",
                Map.of("invoiceNumber", "INV-001"),
                1024L,
                "application/xml",
                "abc123"
            );

            SagaInstance result = service.startSaga(request);

            assertThat(result).isNotNull();
            assertThat(result.getDocumentMetadata().getFilePath()).isEqualTo("/path/to/file.xml");
            assertThat(result.getDocumentMetadata().getXmlContent()).isEqualTo("<Invoice/>");
            assertThat(result.getDocumentMetadata().getMetadata()).containsEntry("invoiceNumber", "INV-001");
            assertThat(result.getDocumentMetadata().getFileSize()).isEqualTo(1024L);
            assertThat(result.getDocumentMetadata().getMimeType()).isEqualTo("application/xml");
            assertThat(result.getDocumentMetadata().getChecksum()).isEqualTo("abc123");
        }

        @Test
        void withNullMetadata_handlesGracefully() {
            when(sagaRepository.findByDocumentTypeAndDocumentId(any(), any()))
                .thenReturn(Optional.empty());
            when(sagaRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(commandRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            StartSagaRequest request = new StartSagaRequest(
                DocumentType.INVOICE,
                "doc-001",
                null,
                null,
                null,
                null,
                null,
                null
            );

            SagaInstance result = service.startSaga(request);

            assertThat(result).isNotNull();
            assertThat(result.getDocumentMetadata()).isNotNull();
        }
    }

    @Nested
    @DisplayName("handleReply()")
    class HandleReplyTests {
        @Test
        void success_advancesToNextStep() {
            SagaInstance saga = createSaga(SagaStatus.IN_PROGRESS, "saga-001");
            saga.advanceTo(SagaStep.PROCESS_INVOICE);

            when(sagaRepository.findById("saga-001")).thenReturn(Optional.of(saga));
            when(sagaRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(commandRepository.findBySagaId("saga-001")).thenReturn(createCommandRecords(saga));
            when(commandRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            service.handleReply("saga-001", SagaStep.PROCESS_INVOICE.getCode(), true, null);

            ArgumentCaptor<SagaInstance> captor = ArgumentCaptor.forClass(SagaInstance.class);
            verify(sagaRepository, atLeast(1)).save(captor.capture());

            SagaInstance saved = captor.getValue();
            assertThat(saved.getCurrentStep()).isEqualTo(SagaStep.SIGN_XML);
        }

        @Test
        void successOnLastStep_completesSaga() {
            SagaInstance saga = createSaga(SagaStatus.IN_PROGRESS, "saga-001");
            saga.advanceTo(SagaStep.STORE_DOCUMENT);
            saga.advanceTo(SagaStep.SEND_EBMS);

            when(sagaRepository.findById("saga-001")).thenReturn(Optional.of(saga));
            when(sagaRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(commandRepository.findBySagaId("saga-001")).thenReturn(createCommandRecords(saga));
            when(commandRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            service.handleReply("saga-001", SagaStep.SEND_EBMS.getCode(), true, null);

            ArgumentCaptor<SagaInstance> captor = ArgumentCaptor.forClass(SagaInstance.class);
            verify(sagaRepository, atLeast(1)).save(captor.capture());

            assertThat(captor.getValue().getStatus()).isEqualTo(SagaStatus.COMPLETED);
            verify(eventPublisher).publishSagaCompleted(any(), any(), any());
        }

        @Test
        void failure_retriesIfBelowMaxRetries() {
            SagaInstance saga = createSaga(SagaStatus.IN_PROGRESS, "saga-001");
            saga.advanceTo(SagaStep.PROCESS_INVOICE);

            when(sagaRepository.findById("saga-001")).thenReturn(Optional.of(saga));
            when(sagaRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(commandRepository.findBySagaId("saga-001")).thenReturn(createCommandRecords(saga));
            when(commandRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            service.handleReply("saga-001", SagaStep.PROCESS_INVOICE.getCode(), false, "Error");

            verify(commandPublisher).publishCommandForStep(any(), eq(SagaStep.PROCESS_INVOICE), any());
            assertThat(saga.getStatus()).isEqualTo(SagaStatus.IN_PROGRESS);
        }

        @Test
        void failure_failsAfterMaxRetries() {
            SagaInstance saga = createSaga(SagaStatus.IN_PROGRESS, "saga-001");
            saga.advanceTo(SagaStep.PROCESS_INVOICE);
            // Exhaust retries
            saga.incrementRetry();
            saga.incrementRetry();
            saga.incrementRetry();

            when(sagaRepository.findById("saga-001")).thenReturn(Optional.of(saga));
            when(sagaRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(commandRepository.findBySagaId("saga-001")).thenReturn(createCommandRecords(saga));
            when(commandRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            service.handleReply("saga-001", SagaStep.PROCESS_INVOICE.getCode(), false, "Max retries");

            assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPENSATING);
            verify(eventPublisher).publishSagaFailed(any(), any(), any(), any(), any());
        }

        @Test
        void marksCommandRecordAsCompleted_onSuccess() {
            SagaInstance saga = createSaga(SagaStatus.IN_PROGRESS, "saga-001");
            saga.advanceTo(SagaStep.PROCESS_INVOICE);
            List<SagaCommandRecord> commands = createCommandRecords(saga);
            SagaCommandRecord command = commands.get(0);

            when(sagaRepository.findById("saga-001")).thenReturn(Optional.of(saga));
            when(sagaRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(commandRepository.findBySagaId("saga-001")).thenReturn(commands);
            when(commandRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            service.handleReply("saga-001", SagaStep.PROCESS_INVOICE.getCode(), true, null);

            assertThat(command.getStatus()).isEqualTo(SagaCommandRecord.CommandStatus.COMPLETED);
        }

        @Test
        void marksCommandRecordAsFailed_onFailure() {
            SagaInstance saga = createSaga(SagaStatus.IN_PROGRESS, "saga-001");
            saga.advanceTo(SagaStep.PROCESS_INVOICE);
            List<SagaCommandRecord> commands = createCommandRecords(saga);
            SagaCommandRecord command = commands.get(0);

            when(sagaRepository.findById("saga-001")).thenReturn(Optional.of(saga));
            when(sagaRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(commandRepository.findBySagaId("saga-001")).thenReturn(commands);
            when(commandRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            service.handleReply("saga-001", SagaStep.PROCESS_INVOICE.getCode(), false, "Error");

            assertThat(command.getStatus()).isEqualTo(SagaCommandRecord.CommandStatus.FAILED);
            assertThat(command.getErrorMessage()).isEqualTo("Error");
        }

        @Test
        void throwsException_whenSagaNotFound() {
            when(sagaRepository.findById("non-existent")).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                service.handleReply("non-existent", SagaStep.PROCESS_INVOICE.getCode(), true, null)
            )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
        }

        @Test
        void publishesStepCompletedEvent_onSuccess() {
            SagaInstance saga = createSaga(SagaStatus.IN_PROGRESS, "saga-001");
            saga.advanceTo(SagaStep.PROCESS_INVOICE);

            when(sagaRepository.findById("saga-001")).thenReturn(Optional.of(saga));
            when(sagaRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(commandRepository.findBySagaId("saga-001")).thenReturn(createCommandRecords(saga));
            when(commandRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            service.handleReply("saga-001", SagaStep.PROCESS_INVOICE.getCode(), true, null);

            verify(eventPublisher).publishSagaStepCompleted(eq(saga), eq(SagaStep.PROCESS_INVOICE), any());
        }
    }

    @Nested
    @DisplayName("getSagaInstance()")
    class GetSagaInstanceTests {
        @Test
        void returnsSaga_whenFound() {
            SagaInstance saga = createSaga(SagaStatus.IN_PROGRESS, "saga-001");
            when(sagaRepository.findById("saga-001")).thenReturn(Optional.of(saga));

            SagaInstance result = service.getSagaInstance("saga-001");

            assertThat(result).isSameAs(saga);
        }

        @Test
        void throwsException_whenNotFound() {
            when(sagaRepository.findById("non-existent")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getSagaInstance("non-existent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
        }
    }

    @Nested
    @DisplayName("advanceSaga()")
    class AdvanceSagaTests {
        @Test
        void advancesToNextStep() {
            SagaInstance saga = createSaga(SagaStatus.IN_PROGRESS, "saga-001");
            when(sagaRepository.findById("saga-001")).thenReturn(Optional.of(saga));
            when(sagaRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(commandRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            service.advanceSaga("saga-001");

            assertThat(saga.getCurrentStep()).isEqualTo(SagaStep.SIGN_XML);
            verify(commandPublisher).publishCommandForStep(any(), eq(SagaStep.SIGN_XML), any());
        }

        @Test
        void completesSaga_whenOnLastStep() {
            SagaInstance saga = createSaga(SagaStatus.IN_PROGRESS, "saga-001");
            saga.advanceTo(SagaStep.SEND_EBMS);
            when(sagaRepository.findById("saga-001")).thenReturn(Optional.of(saga));
            when(sagaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            service.advanceSaga("saga-001");

            assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPLETED);
        }
    }

    @Nested
    @DisplayName("initiateCompensation()")
    class InitiateCompensationTests {
        @Test
        void marksSagaAsCompensating() {
            SagaInstance saga = createSaga(SagaStatus.IN_PROGRESS, "saga-001");
            when(sagaRepository.findById("saga-001")).thenReturn(Optional.of(saga));
            when(sagaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            service.initiateCompensation("saga-001", "Test error");

            assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPENSATING);
            assertThat(saga.getErrorMessage()).isEqualTo("Test error");
        }
    }

    @Nested
    @DisplayName("retryStep()")
    class RetryStepTests {
        @Test
        void incrementsRetryCount_andSendsCommand() {
            SagaInstance saga = createSaga(SagaStatus.IN_PROGRESS, "saga-001");
            when(sagaRepository.findById("saga-001")).thenReturn(Optional.of(saga));
            when(sagaRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(commandRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            service.retryStep("saga-001");

            assertThat(saga.getRetryCount()).isEqualTo(1);
            verify(commandPublisher).publishCommandForStep(any(), eq(SagaStep.PROCESS_INVOICE), any());
        }

        @Test
        void throwsException_whenMaxRetriesExceeded() {
            SagaInstance saga = createSaga(SagaStatus.IN_PROGRESS, "saga-001");
            saga.incrementRetry();
            saga.incrementRetry();
            saga.incrementRetry();
            when(sagaRepository.findById("saga-001")).thenReturn(Optional.of(saga));

            assertThatThrownBy(() -> service.retryStep("saga-001"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Max retries exceeded");
        }
    }

    @Nested
    @DisplayName("getActiveSagas()")
    class GetActiveSagasTests {
        @Test
        void returnsSagasFromRepository() {
            List<SagaInstance> activeSagas = List.of(
                createSaga(SagaStatus.IN_PROGRESS, "saga-001"),
                createSaga(SagaStatus.IN_PROGRESS, "saga-002")
            );
            when(sagaRepository.findByStatus(SagaStatus.IN_PROGRESS)).thenReturn(activeSagas);

            List<SagaInstance> result = service.getActiveSagas();

            assertThat(result).hasSize(2);
            assertThat(result).isEqualTo(activeSagas);
        }
    }

    @Nested
    @DisplayName("getSagasForDocument()")
    class GetSagasForDocumentTests {
        @Test
        void returnsSagasForDocument() {
            SagaInstance saga = createSaga(SagaStatus.IN_PROGRESS, "saga-001");
            when(sagaRepository.findByDocumentTypeAndDocumentId(DocumentType.INVOICE, "doc-001"))
                .thenReturn(Optional.of(saga));

            List<SagaInstance> result = service.getSagasForDocument(DocumentType.INVOICE, "doc-001");

            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isSameAs(saga);
        }

        @Test
        void returnsEmptyList_whenNoSagaFound() {
            when(sagaRepository.findByDocumentTypeAndDocumentId(DocumentType.INVOICE, "doc-001"))
                .thenReturn(Optional.empty());

            List<SagaInstance> result = service.getSagasForDocument(DocumentType.INVOICE, "doc-001");

            assertThat(result).isEmpty();
        }
    }

    // Helper methods
    private SagaInstance createSaga(SagaStatus status, String id) {
        SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-001", createMetadata());
        saga.setId(id);
        if (status == SagaStatus.IN_PROGRESS) {
            saga.start();
        } else if (status == SagaStatus.FAILED) {
            saga.start();
            saga.fail("test error");
        } else if (status == SagaStatus.COMPLETED) {
            saga.start();
            saga.complete();
        }
        return saga;
    }

    private DocumentMetadata createMetadata() {
        return DocumentMetadata.builder()
            .xmlContent("<Invoice/>")
            .build();
    }

    private List<SagaCommandRecord> createCommandRecords(SagaInstance saga) {
        SagaCommandRecord command = SagaCommandRecord.create(
            saga.getId(),
            "TestCommand",
            saga.getCurrentStep(),
            "test-payload"
        );
        command.markAsSent();
        return List.of(command);
    }
}
