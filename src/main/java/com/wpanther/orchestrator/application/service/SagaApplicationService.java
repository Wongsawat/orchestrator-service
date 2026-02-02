package com.wpanther.orchestrator.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.orchestrator.application.dto.StartSagaRequest;
import com.wpanther.orchestrator.domain.model.DocumentMetadata;
import com.wpanther.orchestrator.domain.model.SagaCommandRecord;
import com.wpanther.orchestrator.domain.model.SagaInstance;
import com.wpanther.orchestrator.domain.model.enums.DocumentType;
import com.wpanther.orchestrator.domain.repository.SagaCommandRecordRepository;
import com.wpanther.orchestrator.domain.repository.SagaInstanceRepository;
import com.wpanther.orchestrator.domain.service.SagaOrchestrationService;
import com.wpanther.orchestrator.infrastructure.messaging.producer.SagaCommandProducer;
import com.wpanther.saga.domain.enums.SagaStatus;
import com.wpanther.saga.domain.enums.SagaStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Application service for saga orchestration.
 * Coordinates between the domain layer and infrastructure components.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SagaApplicationService implements SagaOrchestrationService {

    private final SagaInstanceRepository sagaRepository;
    private final SagaCommandRecordRepository commandRepository;
    private final SagaCommandProducer commandProducer;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public SagaInstance startSaga(DocumentType documentType, String documentId, DocumentMetadata metadata) {
        log.info("Starting saga for document type {} with ID {}", documentType, documentId);

        // Check if saga already exists for this document
        var existing = sagaRepository.findByDocumentTypeAndDocumentId(documentType, documentId);
        if (existing.isPresent()) {
            SagaInstance existingSaga = existing.get();
            if (existingSaga.getStatus() != SagaStatus.FAILED
                    && existingSaga.getStatus() != SagaStatus.COMPLETED) {
                log.warn("Saga already exists for document {} with status {}", documentId, existingSaga.getStatus());
                return existingSaga;
            }
        }

        // Create new saga instance
        SagaInstance instance = SagaInstance.create(documentType, documentId, metadata);
        instance.start();

        // Save saga instance
        SagaInstance saved = sagaRepository.save(instance);

        // Create and send first command
        sendCommandForStep(saved);

        log.info("Started saga {} for document {}", saved.getId(), documentId);
        return saved;
    }

    /**
     * Starts a saga from a DTO request.
     */
    @Transactional
    public SagaInstance startSaga(StartSagaRequest request) {
        DocumentMetadata metadata = DocumentMetadata.builder()
                .filePath(request.filePath())
                .xmlContent(request.xmlContent())
                .metadata(request.metadata())
                .fileSize(request.fileSize())
                .mimeType(request.mimeType())
                .checksum(request.checksum())
                .build();

        return startSaga(request.documentType(), request.documentId(), metadata);
    }

    @Override
    @Transactional
    public SagaInstance handleReply(String sagaId, String step, boolean success, String errorMessage) {
        log.debug("Handling reply for saga {}, step {}, success: {}", sagaId, step, success);

        SagaInstance instance = sagaRepository.findById(sagaId)
                .orElseThrow(() -> new IllegalArgumentException("Saga not found: " + sagaId));

        SagaStep completedStep = SagaStep.fromCode(step);

        // Update the command record
        List<SagaCommandRecord> commands = commandRepository.findBySagaId(sagaId);
        SagaCommandRecord lastCommand = commands.stream()
                .filter(c -> c.getTargetStep() == completedStep)
                .filter(c -> c.getStatus() == SagaCommandRecord.CommandStatus.SENT)
                .reduce((a, b) -> b) // Get the last one
                .orElse(null);

        if (lastCommand != null) {
            if (success) {
                lastCommand.markAsCompleted();
                commandRepository.save(lastCommand);
            } else {
                lastCommand.markAsFailed(errorMessage);
                commandRepository.save(lastCommand);

                // Check if we should retry
                instance.incrementRetry();
                if (!instance.hasExceededMaxRetries()) {
                    log.info("Retrying step {} for saga {} (attempt {})",
                            step, sagaId, instance.getRetryCount());
                    sagaRepository.save(instance);
                    sendCommandForStep(instance);
                    return instance;
                }

                // Max retries exceeded, initiate compensation
                instance.fail(errorMessage);
                instance.startCompensation();
                sagaRepository.save(instance);
                sendCompensationCommand(instance, completedStep);
                return instance;
            }
        }

        if (success) {
            // Check if there's a next step
            SagaStep nextStep = instance.getNextStep();
            if (nextStep != null) {
                instance.advanceTo(nextStep);
                sagaRepository.save(instance);
                sendCommandForStep(instance);
            } else {
                // Saga completed
                instance.complete();
                sagaRepository.save(instance);
                log.info("Saga {} completed successfully", sagaId);
            }
        }

        return instance;
    }

    @Override
    public SagaInstance getSagaInstance(String sagaId) {
        return sagaRepository.findById(sagaId)
                .orElseThrow(() -> new IllegalArgumentException("Saga not found: " + sagaId));
    }

    @Override
    @Transactional
    public SagaInstance advanceSaga(String sagaId) {
        SagaInstance instance = sagaRepository.findById(sagaId)
                .orElseThrow(() -> new IllegalArgumentException("Saga not found: " + sagaId));

        SagaStep nextStep = instance.getNextStep();
        if (nextStep == null) {
            instance.complete();
        } else {
            instance.advanceTo(nextStep);
            sendCommandForStep(instance);
        }

        return sagaRepository.save(instance);
    }

    @Override
    @Transactional
    public SagaInstance initiateCompensation(String sagaId, String errorMessage) {
        SagaInstance instance = sagaRepository.findById(sagaId)
                .orElseThrow(() -> new IllegalArgumentException("Saga not found: " + sagaId));

        instance.fail(errorMessage);
        instance.startCompensation();

        SagaInstance saved = sagaRepository.save(instance);

        // Send compensation for current step
        if (instance.getCurrentStep() != null) {
            sendCompensationCommand(instance, instance.getCurrentStep());
        }

        return saved;
    }

    @Override
    @Transactional
    public SagaInstance retryStep(String sagaId) {
        SagaInstance instance = sagaRepository.findById(sagaId)
                .orElseThrow(() -> new IllegalArgumentException("Saga not found: " + sagaId));

        if (instance.hasExceededMaxRetries()) {
            throw new IllegalStateException("Max retries exceeded for saga " + sagaId);
        }

        instance.incrementRetry();
        SagaInstance saved = sagaRepository.save(instance);
        sendCommandForStep(instance);

        return saved;
    }

    @Override
    public List<SagaInstance> getActiveSagas() {
        return sagaRepository.findByStatus(SagaStatus.IN_PROGRESS);
    }

    @Override
    public List<SagaInstance> getSagasForDocument(DocumentType documentType, String documentId) {
        return List.of(
                sagaRepository.findByDocumentTypeAndDocumentId(documentType, documentId)
                        .orElse(null)
        ).stream()
                .filter(s -> s != null)
                .toList();
    }

    /**
     * Sends a command for the current step of the saga.
     */
    private void sendCommandForStep(SagaInstance instance) {
        SagaStep step = instance.getCurrentStep();
        boolean isInvoice = instance.getDocumentType() == DocumentType.INVOICE;

        // Create command record
        String commandPayload = createCommandPayload(instance, step);
        SagaCommandRecord commandRecord = SagaCommandRecord.create(
                instance.getId(),
                step.getClass().getSimpleName(),
                step,
                commandPayload
        );

        // Save command record
        SagaCommandRecord saved = commandRepository.save(commandRecord);
        instance.addCommand(saved);

        // Mark as sent and produce to Kafka
        saved.markAsSent();
        commandRepository.save(saved);

        // Send to Kafka
        commandProducer.sendCommand(instance.getId(), createCommand(instance, step, saved.getCorrelationId()), isInvoice);
    }

    /**
     * Creates a command payload as JSON.
     */
    private String createCommandPayload(SagaInstance instance, SagaStep step) {
        try {
            return objectMapper.writeValueAsString(new CommandPayload(
                    instance.getId(),
                    step.getCode(),
                    instance.getDocumentId(),
                    instance.getDocumentMetadata()
            ));
        } catch (JsonProcessingException e) {
            log.error("Failed to create command payload", e);
            return "{}";
        }
    }

    /**
     * Creates a saga command object.
     */
    private com.wpanther.saga.domain.model.SagaCommand createCommand(
            SagaInstance instance,
            SagaStep step,
            String correlationId) {
        // For now, return a generic command
        // In a full implementation, you'd have specific command classes
        return new com.wpanther.saga.domain.model.SagaCommand(
                instance.getId(),
                step.getCode(),
                correlationId
        ) {};
    }

    /**
     * Sends a compensation command for a failed step.
     */
    private void sendCompensationCommand(SagaInstance instance, SagaStep failedStep) {
        SagaStep compensationStep = instance.getCompensationStep();
        if (compensationStep != null) {
            log.info("Sending compensation command for saga {} from {} to {}",
                    instance.getId(), failedStep, compensationStep);
            // Compensation logic would go here
        }
    }

    /**
     * Internal record for command payloads.
     */
    private record CommandPayload(
            String sagaId,
            String step,
            String documentId,
            DocumentMetadata metadata
    ) {}
}
