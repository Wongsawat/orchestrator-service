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
@DisplayName("StartSagaUseCase contract tests")
class StartSagaUseCaseTest {

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
    @DisplayName("SagaApplicationService implements StartSagaUseCase")
    void sagaApplicationService_implementsStartSagaUseCase() {
        assertThat(service).isInstanceOf(StartSagaUseCase.class);
    }

    @Test
    @DisplayName("startSaga creates and returns new saga instance")
    void startSaga_savesAndReturnsNewSagaInstance() {
        when(sagaRepository.findByDocumentTypeAndDocumentId(any(), any())).thenReturn(Optional.empty());
        when(sagaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(commandRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DocumentMetadata metadata = DocumentMetadata.builder()
                .xmlContent("<Invoice/>")
                .build();

        StartSagaUseCase useCase = service;
        SagaInstance result = useCase.startSaga(DocumentType.INVOICE, "doc-123", metadata);

        assertThat(result).isNotNull();
        assertThat(result.getDocumentId()).isEqualTo("doc-123");
        assertThat(result.getDocumentType()).isEqualTo(DocumentType.INVOICE);
    }

    @Test
    @DisplayName("startSaga returns existing non-terminal saga when one exists")
    void startSaga_returnsExistingSaga_whenActiveSagaExists() {
        DocumentMetadata metadata = DocumentMetadata.builder().xmlContent("<Invoice/>").build();
        SagaInstance existing = SagaInstance.create(DocumentType.INVOICE, "doc-123", metadata);
        existing.start();
        when(sagaRepository.findByDocumentTypeAndDocumentId(any(), any())).thenReturn(Optional.of(existing));

        StartSagaUseCase useCase = service;
        SagaInstance result = useCase.startSaga(DocumentType.INVOICE, "doc-123", metadata);

        assertThat(result).isSameAs(existing);
    }
}
