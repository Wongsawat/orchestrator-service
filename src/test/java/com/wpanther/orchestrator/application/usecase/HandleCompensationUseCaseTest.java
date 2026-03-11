package com.wpanther.orchestrator.application.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.orchestrator.infrastructure.adapter.out.messaging.SagaCommandPublisher;
import com.wpanther.orchestrator.infrastructure.adapter.out.messaging.SagaEventPublisher;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("HandleCompensationUseCase contract tests")
class HandleCompensationUseCaseTest {

    @Mock private SagaInstanceRepository sagaRepository;
    @Mock private SagaCommandRecordRepository commandRepository;
    @Mock private SagaCommandPublisher commandPublisher;
    @Mock private SagaEventPublisher eventPublisher;

    private SagaApplicationService service;

    @BeforeEach
    void setUp() {
        service = new SagaApplicationService(
            sagaRepository, commandRepository,
            commandPublisher, eventPublisher, new ObjectMapper()
        );
    }

    @Test
    @DisplayName("SagaApplicationService implements HandleCompensationUseCase")
    void sagaApplicationService_implementsHandleCompensationUseCase() {
        assertThat(service).isInstanceOf(HandleCompensationUseCase.class);
    }

    @Test
    @DisplayName("initiateCompensation returns updated saga instance")
    void initiateCompensation_returnsSagaInstance() {
        SagaInstance existing = buildInProgressSaga();
        when(sagaRepository.findById("saga-123")).thenReturn(Optional.of(existing));
        when(sagaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HandleCompensationUseCase useCase = service;
        SagaInstance result = useCase.initiateCompensation("saga-123", "Processing failed");

        assertThat(result).isNotNull();
    }

    private SagaInstance buildInProgressSaga() {
        DocumentMetadata metadata = DocumentMetadata.builder().xmlContent("<Invoice/>").build();
        SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-001", metadata);
        saga.setId("saga-123");
        saga.start();
        return saga;
    }
}
