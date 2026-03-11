package com.wpanther.orchestrator.application.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.orchestrator.adapter.out.messaging.SagaCommandProducer;
import com.wpanther.orchestrator.adapter.out.messaging.SagaCommandPublisher;
import com.wpanther.orchestrator.adapter.out.messaging.SagaEventPublisher;
import com.wpanther.orchestrator.domain.model.DocumentMetadata;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("HandleSagaReplyUseCase contract tests")
class HandleSagaReplyUseCaseTest {

    @Mock private SagaInstanceRepository sagaRepository;
    @Mock private SagaCommandRecordRepository commandRepository;
    @Mock private SagaCommandProducer commandProducer;
    @Mock private SagaCommandPublisher commandPublisher;
    @Mock private SagaEventPublisher eventPublisher;

    private SagaApplicationService service;

    @BeforeEach
    void setUp() {
        service = new SagaApplicationService(
            sagaRepository, commandRepository, commandProducer,
            commandPublisher, eventPublisher, new ObjectMapper()
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
        when(sagaRepository.findById("saga-123")).thenReturn(Optional.of(existing));
        when(sagaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
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
        when(sagaRepository.findById("saga-123")).thenReturn(Optional.of(existing));
        when(sagaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(commandRepository.findBySagaId(anyString())).thenReturn(List.of());
        when(commandRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HandleSagaReplyUseCase useCase = service;
        SagaInstance result = useCase.handleReply("saga-123", "sign-xml", true, null,
                Map.of("signedXmlUrl", "http://example.com/signed.xml"));

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
