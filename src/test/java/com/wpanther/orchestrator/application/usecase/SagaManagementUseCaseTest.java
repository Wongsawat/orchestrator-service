package com.wpanther.orchestrator.application.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.orchestrator.infrastructure.adapter.out.messaging.SagaCommandPublisher;
import com.wpanther.orchestrator.infrastructure.adapter.out.messaging.SagaEventPublisher;
import com.wpanther.orchestrator.infrastructure.config.SagaProperties;
import com.wpanther.orchestrator.domain.model.DocumentMetadata;
import com.wpanther.orchestrator.domain.model.SagaInstance;
import com.wpanther.orchestrator.domain.model.enums.DocumentType;
import com.wpanther.orchestrator.domain.repository.SagaCommandRecordRepository;
import com.wpanther.orchestrator.domain.repository.SagaInstanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SagaManagementUseCase contract tests")
class SagaManagementUseCaseTest {

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
    @DisplayName("SagaApplicationService implements SagaManagementUseCase")
    void sagaApplicationService_implementsSagaManagementUseCase() {
        assertThat(service).isInstanceOf(SagaManagementUseCase.class);
    }

    @Test
    @DisplayName("advanceSaga throws when saga not found")
    void advanceSaga_throwsException_whenSagaNotFound() {
        when(sagaRepository.findById("missing")).thenReturn(Optional.empty());

        SagaManagementUseCase useCase = service;
        assertThatThrownBy(() -> useCase.advanceSaga("missing"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("advanceSaga returns updated saga when found")
    void advanceSaga_returnsUpdatedSaga_whenFound() {
        SagaInstance existing = buildInProgressSaga();
        when(sagaRepository.findById("saga-123")).thenReturn(Optional.of(existing));
        when(sagaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(commandRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SagaManagementUseCase useCase = service;
        SagaInstance result = useCase.advanceSaga("saga-123");

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("retryStep throws when saga not found")
    void retryStep_throwsException_whenSagaNotFound() {
        when(sagaRepository.findById("missing")).thenReturn(Optional.empty());

        SagaManagementUseCase useCase = service;
        assertThatThrownBy(() -> useCase.retryStep("missing"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private SagaInstance buildInProgressSaga() {
        DocumentMetadata metadata = DocumentMetadata.builder().xmlContent("<Invoice/>").build();
        SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-001", metadata);
        saga.setId("saga-123");
        saga.start();
        return saga;
    }
}
