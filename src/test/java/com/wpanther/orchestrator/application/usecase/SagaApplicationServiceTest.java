package com.wpanther.orchestrator.application.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.orchestrator.application.dto.StartSagaRequest;
import com.wpanther.orchestrator.domain.model.DocumentMetadata;
import com.wpanther.orchestrator.domain.model.SagaCommandRecord;
import com.wpanther.orchestrator.domain.model.SagaInstance;
import com.wpanther.orchestrator.domain.model.enums.DocumentType;
import com.wpanther.orchestrator.domain.repository.SagaCommandRecordRepository;
import com.wpanther.orchestrator.domain.repository.SagaInstanceRepository;
import com.wpanther.orchestrator.infrastructure.adapter.out.messaging.SagaCommandPublisher;
import com.wpanther.orchestrator.infrastructure.adapter.out.messaging.SagaEventPublisher;
import com.wpanther.orchestrator.infrastructure.config.SagaProperties;
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
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class SagaApplicationServiceTest {

    @Mock private SagaInstanceRepository sagaRepository;
    @Mock private SagaCommandRecordRepository commandRepository;
    @Mock private SagaCommandPublisher commandPublisher;
    @Mock private SagaEventPublisher eventPublisher;
    @Mock private SagaProperties sagaProperties;
    @Mock private JdbcTemplate jdbcTemplate;

    private ObjectMapper objectMapper;
    private SagaApplicationService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        // Configure default SagaProperties behavior
        lenient().when(sagaProperties.getMaxRetries()).thenReturn(3);

        service = new SagaApplicationService(
            sagaRepository, commandRepository,
            commandPublisher, eventPublisher, objectMapper, sagaProperties,
            jdbcTemplate
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
            // commandProducer.sendCommand deprecated - using outbox pattern instead
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
                Map.of("documentNumber", "INV-001"),
                1024L,
                "application/xml",
                "abc123"
            );

            SagaInstance result = service.startSaga(request);

            assertThat(result).isNotNull();
            assertThat(result.getDocumentMetadata().getFilePath()).isEqualTo("/path/to/file.xml");
            assertThat(result.getDocumentMetadata().getXmlContent()).isEqualTo("<Invoice/>");
            assertThat(result.getDocumentMetadata().getMetadata()).containsEntry("documentNumber", "INV-001");
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

            // handleReply loads saga via JdbcTemplate (bypasses JPA for CLOB/optimistic lock safety)
            when(jdbcTemplate.queryForObject(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
                    .thenReturn(saga);
            when(commandRepository.findBySagaId("saga-001")).thenReturn(createCommandRecords(saga));
            when(commandRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            service.handleReply("saga-001", SagaStep.PROCESS_INVOICE.getCode(), true, null);

            // Saga state updated in-place; verify via jdbcTemplate.update call
            assertThat(saga.getCurrentStep()).isEqualTo(SagaStep.SIGN_XML);
        }

        @Test
        void successOnLastStep_completesSaga() {
            SagaInstance saga = createSaga(SagaStatus.IN_PROGRESS, "saga-001");
            saga.advanceTo(SagaStep.STORE_DOCUMENT);
            saga.advanceTo(SagaStep.SEND_EBMS);

            when(jdbcTemplate.queryForObject(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
                    .thenReturn(saga);
            when(commandRepository.findBySagaId("saga-001")).thenReturn(createCommandRecords(saga));
            when(commandRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            service.handleReply("saga-001", SagaStep.SEND_EBMS.getCode(), true, null);

            assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPLETED);
            verify(eventPublisher).publishSagaCompleted(any(), any(), any());
        }

        @Test
        void failure_retriesIfBelowMaxRetries() {
            SagaInstance saga = createSaga(SagaStatus.IN_PROGRESS, "saga-001");
            saga.advanceTo(SagaStep.PROCESS_INVOICE);

            when(jdbcTemplate.queryForObject(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
                    .thenReturn(saga);
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

            when(jdbcTemplate.queryForObject(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
                    .thenReturn(saga);
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

            when(jdbcTemplate.queryForObject(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
                    .thenReturn(saga);
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

            when(jdbcTemplate.queryForObject(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
                    .thenReturn(saga);
            when(commandRepository.findBySagaId("saga-001")).thenReturn(commands);
            when(commandRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            service.handleReply("saga-001", SagaStep.PROCESS_INVOICE.getCode(), false, "Error");

            assertThat(command.getStatus()).isEqualTo(SagaCommandRecord.CommandStatus.FAILED);
            assertThat(command.getErrorMessage()).isEqualTo("Error");
        }

        @Test
        void throwsException_whenSagaNotFound() {
            // JdbcTemplate returns null when no row matches
            when(jdbcTemplate.queryForObject(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
                    .thenReturn(null);

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

            when(jdbcTemplate.queryForObject(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
                    .thenReturn(saga);
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

        @Test
        void sendsCompensationCommand_whenStepHasCompensation() {
            SagaInstance saga = createSaga(SagaStatus.IN_PROGRESS, "saga-001");
            saga.advanceTo(SagaStep.SIGN_XML);
            when(sagaRepository.findById("saga-001")).thenReturn(Optional.of(saga));
            when(sagaRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(commandRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            service.initiateCompensation("saga-001", "Test error");

            assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPENSATING);
            assertThat(saga.getErrorMessage()).isEqualTo("Test error");
            verify(commandPublisher).publishCompensationCommand(eq(saga), eq(SagaStep.PROCESS_INVOICE), any());
        }

        @Test
        void doesNotSendCompensation_whenNoCompensationStep() {
            SagaInstance saga = createSaga(SagaStatus.IN_PROGRESS, "saga-001");
            // At PROCESS_INVOICE, there's no compensation step (it's the first step)
            when(sagaRepository.findById("saga-001")).thenReturn(Optional.of(saga));
            when(sagaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            service.initiateCompensation("saga-001", "Test error");

            assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPENSATING);
            // Should not call publishCompensationCommand since there's no compensation step
            verify(commandPublisher, never()).publishCompensationCommand(any(), any(), any());
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

    @Nested
    @DisplayName("Compensation flow")
    class CompensationFlowTests {
        @Test
        void handleReply_sendsCompensationAfterMaxRetries() {
            SagaInstance saga = createSaga(SagaStatus.IN_PROGRESS, "saga-001");
            saga.advanceTo(SagaStep.SIGN_XML);
            // Exhaust retries
            saga.incrementRetry();
            saga.incrementRetry();
            saga.incrementRetry();

            // Create a command record for SIGN_XML that will be marked as failed
            SagaCommandRecord signXmlCommand = SagaCommandRecord.create(
                "saga-001", "Command", SagaStep.SIGN_XML, "payload");
            signXmlCommand.markAsSent();

            // handleReply loads saga via JdbcTemplate (bypasses JPA for CLOB/optimistic lock safety)
            when(jdbcTemplate.queryForObject(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
                    .thenReturn(saga);
            when(commandRepository.findBySagaId("saga-001")).thenReturn(List.of(signXmlCommand));
            when(commandRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            service.handleReply("saga-001", SagaStep.SIGN_XML.getCode(), false, "Max retries");

            // Verify compensation command was sent
            verify(commandPublisher).publishCompensationCommand(any(), eq(SagaStep.PROCESS_INVOICE), any());
        }

        @Test
        void compensation_traversesStepsInReverseOrder() {
            // Test that compensation steps are correctly identified
            SagaInstance saga1 = SagaInstance.create(DocumentType.INVOICE, "doc-001", createMetadata());
            saga1.start();
            saga1.advanceTo(SagaStep.SIGN_XML);
            saga1.advanceTo(SagaStep.SIGNEDXML_STORAGE);
            saga1.advanceTo(SagaStep.GENERATE_INVOICE_PDF);

            // Compensation from GENERATE_INVOICE_PDF should go to SIGNEDXML_STORAGE
            assertThat(saga1.getCompensationStep()).isEqualTo(SagaStep.SIGNEDXML_STORAGE);

            SagaInstance saga2 = SagaInstance.create(DocumentType.INVOICE, "doc-002", createMetadata());
            saga2.start();
            saga2.advanceTo(SagaStep.SIGN_XML);
            saga2.advanceTo(SagaStep.SIGNEDXML_STORAGE);
            saga2.advanceTo(SagaStep.GENERATE_INVOICE_PDF);
            saga2.advanceTo(SagaStep.SIGN_PDF);

            // Compensation from SIGN_PDF should go to GENERATE_INVOICE_PDF
            assertThat(saga2.getCompensationStep()).isEqualTo(SagaStep.GENERATE_INVOICE_PDF);

            SagaInstance saga3 = SagaInstance.create(DocumentType.INVOICE, "doc-003", createMetadata());
            saga3.start();
            saga3.advanceTo(SagaStep.SIGN_XML);
            saga3.advanceTo(SagaStep.SIGNEDXML_STORAGE);
            saga3.advanceTo(SagaStep.GENERATE_INVOICE_PDF);
            saga3.advanceTo(SagaStep.SIGN_PDF);
            saga3.advanceTo(SagaStep.STORE_DOCUMENT);

            // Compensation from STORE_DOCUMENT should go to SIGN_PDF
            assertThat(saga3.getCompensationStep()).isEqualTo(SagaStep.SIGN_PDF);

            SagaInstance saga4 = SagaInstance.create(DocumentType.INVOICE, "doc-004", createMetadata());
            saga4.start();
            saga4.advanceTo(SagaStep.SIGN_XML);
            saga4.advanceTo(SagaStep.SIGNEDXML_STORAGE);
            saga4.advanceTo(SagaStep.GENERATE_INVOICE_PDF);
            saga4.advanceTo(SagaStep.SIGN_PDF);
            saga4.advanceTo(SagaStep.STORE_DOCUMENT);
            saga4.advanceTo(SagaStep.SEND_EBMS);

            // Compensation from SEND_EBMS should go to STORE_DOCUMENT
            assertThat(saga4.getCompensationStep()).isEqualTo(SagaStep.STORE_DOCUMENT);
        }

        @Test
        void compensationForTaxInvoice_usesCorrectSteps() {
            SagaInstance saga = SagaInstance.create(DocumentType.TAX_INVOICE, "doc-001", createMetadata());
            saga.start();
            saga.advanceTo(SagaStep.SIGN_XML);
            saga.advanceTo(SagaStep.GENERATE_TAX_INVOICE_PDF);

            // Compensation from GENERATE_TAX_INVOICE_PDF should go to SIGN_XML (tax invoice skips SIGNEDXML_STORAGE)
            assertThat(saga.getCompensationStep()).isEqualTo(SagaStep.SIGN_XML);

            // Create a new saga at SIGN_XML to verify compensation step
            SagaInstance sagaAtSignXml = SagaInstance.create(DocumentType.TAX_INVOICE, "doc-002", createMetadata());
            sagaAtSignXml.start();
            sagaAtSignXml.advanceTo(SagaStep.SIGN_XML);
            assertThat(sagaAtSignXml.getCompensationStep()).isEqualTo(SagaStep.PROCESS_TAX_INVOICE);
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

    @Nested
    @DisplayName("Unsupported Document Type Validation")
    class UnsupportedDocumentTypeTests {

        @Test
        void startSaga_withReceipt_throwsIllegalArgumentException() {
            DocumentMetadata metadata = createMetadata();

            assertThatThrownBy(() ->
                service.startSaga(DocumentType.RECEIPT, "doc-001", metadata)
            )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RECEIPT")
                .hasMessageContaining("not yet supported")
                .hasMessageContaining("INVOICE, TAX_INVOICE, ABBREVIATED_TAX_INVOICE");

            // Verify saga was never created
            verify(sagaRepository, never()).save(any());
        }

        @Test
        void startSaga_withDebitNote_throwsIllegalArgumentException() {
            DocumentMetadata metadata = createMetadata();

            assertThatThrownBy(() ->
                service.startSaga(DocumentType.DEBIT_NOTE, "doc-002", metadata)
            )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DEBIT_NOTE")
                .hasMessageContaining("not yet supported");

            verify(sagaRepository, never()).save(any());
        }

        @Test
        void startSaga_withCreditNote_throwsIllegalArgumentException() {
            DocumentMetadata metadata = createMetadata();

            assertThatThrownBy(() ->
                service.startSaga(DocumentType.CREDIT_NOTE, "doc-003", metadata)
            )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CREDIT_NOTE")
                .hasMessageContaining("not yet supported");

            verify(sagaRepository, never()).save(any());
        }

        @Test
        void startSaga_withCancellationNote_throwsIllegalArgumentException() {
            DocumentMetadata metadata = createMetadata();

            assertThatThrownBy(() ->
                service.startSaga(DocumentType.CANCELLATION_NOTE, "doc-004", metadata)
            )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CANCELLATION_NOTE")
                .hasMessageContaining("not yet supported");

            verify(sagaRepository, never()).save(any());
        }

        @Test
        void startSaga_withSupportedTypes_doesNotThrow() {
            when(sagaRepository.findByDocumentTypeAndDocumentId(any(), any()))
                .thenReturn(Optional.empty());
            when(sagaRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(commandRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            DocumentMetadata metadata = createMetadata();

            // These should not throw
            assertThatCode(() ->
                service.startSaga(DocumentType.INVOICE, "doc-001", metadata)
            ).doesNotThrowAnyException();

            assertThatCode(() ->
                service.startSaga(DocumentType.TAX_INVOICE, "doc-002", metadata)
            ).doesNotThrowAnyException();

            assertThatCode(() ->
                service.startSaga(DocumentType.ABBREVIATED_TAX_INVOICE, "doc-003", metadata)
            ).doesNotThrowAnyException();
        }
    }
}
