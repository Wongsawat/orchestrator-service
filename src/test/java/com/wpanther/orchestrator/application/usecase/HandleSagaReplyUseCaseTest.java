package com.wpanther.orchestrator.application.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.orchestrator.infrastructure.adapter.out.messaging.SagaCommandPublisher;
import com.wpanther.orchestrator.infrastructure.adapter.out.messaging.SagaEventPublisher;
import com.wpanther.orchestrator.infrastructure.config.SagaProperties;
import com.wpanther.orchestrator.domain.model.DocumentMetadata;
import com.wpanther.orchestrator.domain.model.SagaCommandRecord;
import com.wpanther.orchestrator.domain.model.SagaInstance;
import com.wpanther.orchestrator.domain.model.enums.DocumentType;
import com.wpanther.orchestrator.domain.repository.SagaCommandRecordRepository;
import com.wpanther.orchestrator.domain.repository.SagaInstanceRepository;
import com.wpanther.saga.domain.enums.SagaStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("HandleSagaReplyUseCase contract tests")
class HandleSagaReplyUseCaseTest {

    @Mock private SagaInstanceRepository sagaRepository;
    @Mock private SagaCommandRecordRepository commandRepository;
    @Mock private SagaCommandPublisher commandPublisher;
    @Mock private SagaEventPublisher eventPublisher;
    @Mock private SagaProperties sagaProperties;
    @Mock private JdbcTemplate jdbcTemplate;

    private SagaApplicationService service;

    @BeforeEach
    void setUp() {
        lenient().when(sagaProperties.getMaxRetries()).thenReturn(3);
        service = new SagaApplicationService(
            sagaRepository, commandRepository,
            commandPublisher, eventPublisher, new ObjectMapper(), sagaProperties,
            jdbcTemplate
        );
    }

    @Test
    @DisplayName("SagaApplicationService implements HandleSagaReplyUseCase")
    void sagaApplicationService_implementsHandleSagaReplyUseCase() {
        assertThat(service).isInstanceOf(HandleSagaReplyUseCase.class);
    }

    @Test
    @DisplayName("handleReply (4-arg) returns saga instance on success")
    void handleReply_4arg_successPath_returnsSagaInstance() {
        SagaInstance existing = buildInProgressSaga();
        when(jdbcTemplate.queryForObject(any(String.class), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
                .thenReturn(existing);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        when(commandRepository.findBySagaId(anyString())).thenReturn(List.of());
        when(commandRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HandleSagaReplyUseCase useCase = service;
        SagaInstance result = useCase.handleReply("saga-123", "process-invoice", true, null);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("handleReply (5-arg) with resultData returns saga instance")
    void handleReply_5arg_withResultData_returnsSagaInstance() {
        SagaInstance existing = buildInProgressSaga();
        when(jdbcTemplate.queryForObject(any(String.class), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
                .thenReturn(existing);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        when(commandRepository.findBySagaId(anyString())).thenReturn(List.of());
        when(commandRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HandleSagaReplyUseCase useCase = service;
        SagaInstance result = useCase.handleReply("saga-123", "sign-xml", true, null,
                Map.of("signedXmlUrl", "http://example.com/signed.xml"));

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("handleReply throws when saga not found")
    void handleReply_throwsException_whenSagaNotFound() {
        when(jdbcTemplate.queryForObject(any(String.class), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
                .thenReturn(null);

        HandleSagaReplyUseCase useCase = service;
        assertThatThrownBy(() -> useCase.handleReply("missing", "process-invoice", true, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("handleReply with success=false and a matching SENT command increments retry count")
    void handleReply_failurePath_incrementsRetry() {
        SagaInstance existing = buildInProgressSaga();

        // Build a command record in SENT status matching the expected step
        SagaCommandRecord cmd = SagaCommandRecord.create("saga-123", "ProcessInvoiceCommand",
                SagaStep.PROCESS_INVOICE, "{}");
        cmd.markAsSent();

        when(jdbcTemplate.queryForObject(any(String.class), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
                .thenReturn(existing);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        when(commandRepository.findBySagaId(anyString())).thenReturn(List.of(cmd));
        when(commandRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HandleSagaReplyUseCase useCase = service;
        SagaInstance result = useCase.handleReply("saga-123", "process-invoice", false, "Service unavailable");

        assertThat(result).isNotNull();
        assertThat(result.getRetryCount()).isGreaterThan(0);
    }

    private SagaInstance buildInProgressSaga() {
        DocumentMetadata metadata = DocumentMetadata.builder().xmlContent("<Invoice/>").build();
        SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-001", metadata);
        saga.setId("saga-123");
        saga.start();
        return saga;
    }
}
