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
import com.wpanther.saga.domain.enums.SagaStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("QuerySagaUseCase contract tests")
class QuerySagaUseCaseTest {

    @Mock private SagaInstanceRepository sagaRepository;
    @Mock private SagaCommandRecordRepository commandRepository;
    @Mock private SagaCommandPublisher commandPublisher;
    @Mock private SagaEventPublisher eventPublisher;
    @Mock private SagaProperties sagaProperties;

    private SagaApplicationService service;

    @BeforeEach
    void setUp() {
        lenient().when(sagaProperties.getMaxRetries()).thenReturn(3);
        service = new SagaApplicationService(
            sagaRepository, commandRepository,
            commandPublisher, eventPublisher, new ObjectMapper(), sagaProperties
        );
    }

    @Test
    @DisplayName("SagaApplicationService implements QuerySagaUseCase")
    void sagaApplicationService_implementsQuerySagaUseCase() {
        assertThat(service).isInstanceOf(QuerySagaUseCase.class);
    }

    @Test
    @DisplayName("getSagaInstance returns saga when found")
    void getSagaInstance_returnsInstance_whenFound() {
        DocumentMetadata metadata = DocumentMetadata.builder().xmlContent("<Invoice/>").build();
        SagaInstance expected = SagaInstance.create(DocumentType.INVOICE, "doc-001", metadata);
        expected.setId("saga-123");
        when(sagaRepository.findById("saga-123")).thenReturn(Optional.of(expected));

        QuerySagaUseCase useCase = service;
        SagaInstance result = useCase.getSagaInstance("saga-123");

        assertThat(result.getId()).isEqualTo("saga-123");
    }

    @Test
    @DisplayName("getSagaInstance throws exception when not found")
    void getSagaInstance_throwsException_whenNotFound() {
        when(sagaRepository.findById(anyString())).thenReturn(Optional.empty());

        QuerySagaUseCase useCase = service;
        assertThatThrownBy(() -> useCase.getSagaInstance("missing-id"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("getActiveSagas returns list")
    void getActiveSagas_returnsList() {
        when(sagaRepository.findByStatus(SagaStatus.IN_PROGRESS)).thenReturn(List.of());

        QuerySagaUseCase useCase = service;
        List<SagaInstance> result = useCase.getActiveSagas();

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("getSagasForDocument returns list")
    void getSagasForDocument_returnsList() {
        when(sagaRepository.findByDocumentTypeAndDocumentId(DocumentType.TAX_INVOICE, "doc-123"))
                .thenReturn(Optional.empty());

        QuerySagaUseCase useCase = service;
        List<SagaInstance> result = useCase.getSagasForDocument(DocumentType.TAX_INVOICE, "doc-123");

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("getSagasByStatus delegates to repository with given status")
    void getSagasByStatus_returnsList() {
        when(sagaRepository.findByStatus(SagaStatus.FAILED)).thenReturn(List.of());

        QuerySagaUseCase useCase = service;
        List<SagaInstance> result = useCase.getSagasByStatus(SagaStatus.FAILED);

        assertThat(result).isNotNull();
    }
}
