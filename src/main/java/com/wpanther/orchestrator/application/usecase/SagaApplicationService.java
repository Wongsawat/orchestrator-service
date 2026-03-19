package com.wpanther.orchestrator.application.usecase;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Application service for saga orchestration.
 * Coordinates between the domain layer and infrastructure components.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SagaApplicationService implements StartSagaUseCase, HandleSagaReplyUseCase,
        HandleCompensationUseCase, QuerySagaUseCase, SagaManagementUseCase {

    private final SagaInstanceRepository sagaRepository;
    private final SagaCommandRecordRepository commandRepository;
    private final SagaCommandPublisher commandPublisher;
    private final SagaEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final SagaProperties sagaProperties;

    /**
     * Starts a saga from a DTO request.
     */
    @Transactional
    public SagaInstance startSaga(StartSagaRequest request) {
        // Extract correlation ID from metadata if present
        String correlationId = extractCorrelationIdFromMetadata(request.metadata());

        DocumentMetadata metadata = DocumentMetadata.builder()
                .filePath(request.filePath())
                .xmlContent(request.xmlContent())
                .metadata(request.metadata())
                .fileSize(request.fileSize())
                .mimeType(request.mimeType())
                .checksum(request.checksum())
                .build();

        return startSaga(request.documentType(), request.documentId(), metadata, correlationId);
    }

    /**
     * Starts a saga with an explicit correlation ID for end-to-end tracing.
     * If {@code correlationId} is null, a new one is generated.
     */
    @Override
    @Transactional
    public SagaInstance startSaga(DocumentType documentType, String documentId,
                                  DocumentMetadata metadata, String correlationId) {
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

        // Generate correlation ID if not provided
        if (correlationId == null) {
            correlationId = generateCorrelationId();
        }

        // Create new saga instance with configured max retries
        int maxRetries = sagaProperties.getMaxRetries();
        SagaInstance instance = SagaInstance.create(documentType, documentId, metadata, maxRetries);
        instance.setCorrelationId(correlationId);
        instance.start();

        // Save saga instance
        SagaInstance saved = sagaRepository.save(instance);

        // Publish saga started event
        String invoiceNumber = extractInvoiceNumber(metadata);
        eventPublisher.publishSagaStarted(saved, correlationId, invoiceNumber);

        // Create and send first command via outbox
        sendCommandForStep(saved, correlationId);

        log.info("Started saga {} for document {}", saved.getId(), documentId);
        return saved;
    }

    @Override
    @Transactional
    public SagaInstance handleReply(String sagaId, String step, boolean success,
                                     String errorMessage, Map<String, Object> resultData) {
        log.debug("Handling reply for saga {}, step {}, success: {}", sagaId, step, success);

        SagaInstance instance = sagaRepository.findById(sagaId)
                .orElseThrow(() -> new IllegalArgumentException("Saga not found: " + sagaId));

        // Merge result data into saga metadata for subsequent steps to use
        // withMetadataValue() handles null metadata maps internally
        if (resultData != null && !resultData.isEmpty() && success) {
            DocumentMetadata metadata = instance.getDocumentMetadata();
            if (metadata != null) {
                // Apply all resultData entries immutably using withMetadataValue pattern
                DocumentMetadata updatedMetadata = metadata;
                for (Map.Entry<String, Object> entry : resultData.entrySet()) {
                    updatedMetadata = updatedMetadata.withMetadataValue(entry.getKey(), entry.getValue());
                }
                instance.setDocumentMetadata(updatedMetadata);
                log.debug("Merged {} result data fields into saga {} metadata: {}",
                        resultData.size(), sagaId, resultData.keySet());
            }
        }

        SagaStep completedStep = SagaStep.fromCode(step);
        // Retrieve correlation ID from saga instance for distributed tracing
        String correlationId = instance.getCorrelationId();
        if (correlationId == null) {
            correlationId = generateCorrelationId();
            instance.setCorrelationId(correlationId);
            log.warn("Saga {} had no correlation ID, generated new one: {}", sagaId, correlationId);
        }

        // Update the command record
        List<SagaCommandRecord> commands = commandRepository.findBySagaId(sagaId);
        SagaCommandRecord lastCommand = commands.stream()
                .filter(c -> c.getTargetStep() == completedStep)
                .filter(c -> SagaCommandRecord.CommandStatus.SENT.equals(c.getStatus()))
                .reduce((a, b) -> b) // Get the last one
                .orElse(null);

        if (lastCommand != null) {
            if (success) {
                lastCommand.markAsCompleted();
                commandRepository.save(lastCommand);

                // Publish step completed event
                eventPublisher.publishSagaStepCompleted(instance, completedStep, correlationId);
            } else {
                lastCommand.markAsFailed(errorMessage);
                commandRepository.save(lastCommand);

                // Check if we should retry
                instance.incrementRetry();
                if (!instance.hasExceededMaxRetries()) {
                    log.info("Retrying step {} for saga {} (attempt {})",
                            step, sagaId, instance.getRetryCount());
                    sagaRepository.save(instance);
                    sendCommandForStep(instance, correlationId);
                    return instance;
                }

                // Max retries exceeded, initiate compensation
                instance.fail(errorMessage);
                instance.startCompensation();
                sagaRepository.save(instance);

                // Publish saga failed event
                String invoiceNumber = extractInvoiceNumber(instance.getDocumentMetadata());
                eventPublisher.publishSagaFailed(instance, completedStep, errorMessage,
                    correlationId, invoiceNumber);

                sendCompensationCommand(instance, completedStep, correlationId);
                return instance;
            }
        }

        if (success) {
            // Check if there's a next step
            SagaStep nextStep = instance.getNextStep();
            if (nextStep != null) {
                instance.advanceTo(nextStep);
                sagaRepository.save(instance);
                sendCommandForStep(instance, correlationId);
            } else {
                // Saga completed
                instance.complete();
                sagaRepository.save(instance);

                // Publish saga completed event
                String invoiceNumber = extractInvoiceNumber(instance.getDocumentMetadata());
                eventPublisher.publishSagaCompleted(instance, correlationId, invoiceNumber);

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

        String correlationId = instance.getCorrelationId();
        if (correlationId == null) {
            correlationId = generateCorrelationId();
            instance.setCorrelationId(correlationId);
        }
        SagaStep nextStep = instance.getNextStep();
        if (nextStep == null) {
            instance.complete();
        } else {
            instance.advanceTo(nextStep);
            sendCommandForStep(instance, correlationId);
        }

        return sagaRepository.save(instance);
    }

    @Override
    @Transactional
    public SagaInstance initiateCompensation(String sagaId, String errorMessage) {
        SagaInstance instance = sagaRepository.findById(sagaId)
                .orElseThrow(() -> new IllegalArgumentException("Saga not found: " + sagaId));

        String correlationId = instance.getCorrelationId();
        if (correlationId == null) {
            correlationId = generateCorrelationId();
            instance.setCorrelationId(correlationId);
        }
        instance.fail(errorMessage);
        instance.startCompensation();

        SagaInstance saved = sagaRepository.save(instance);

        // Send compensation for current step
        if (instance.getCurrentStep() != null) {
            sendCompensationCommand(instance, instance.getCurrentStep(), correlationId);
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
        String correlationId = instance.getCorrelationId();
        if (correlationId == null) {
            correlationId = generateCorrelationId();
            instance.setCorrelationId(correlationId);
        }
        SagaInstance saved = sagaRepository.save(instance);
        sendCommandForStep(instance, correlationId);

        return saved;
    }

    @Override
    public List<SagaInstance> getSagasByStatus(SagaStatus status) {
        return sagaRepository.findByStatus(status);
    }

    @Override
    public List<SagaInstance> getSagasForDocument(DocumentType documentType, String documentId) {
        return sagaRepository.findByDocumentTypeAndDocumentId(documentType, documentId)
                .map(List::of)
                .orElse(List.of());
    }

    /**
     * Sends a command for the current step of the saga.
     */
    private void sendCommandForStep(SagaInstance instance, String correlationId) {
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

        // Mark as sent
        saved.markAsSent();
        commandRepository.save(saved);

        // Publish command via outbox
        commandPublisher.publishCommandForStep(instance, step, correlationId);

        // Direct Kafka producer deprecated - using outbox pattern instead
        // commandProducer.sendCommand(instance.getId(), createCommand(instance, step, saved.getCorrelationId()), isInvoice);
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
     * Sends a compensation command for a failed step.
     */
    private void sendCompensationCommand(SagaInstance instance, SagaStep failedStep, String correlationId) {
        SagaStep compensationStep = instance.getCompensationStep();
        if (compensationStep != null) {
            log.info("Sending compensation command for saga {} from {} to {}",
                    instance.getId(), failedStep, compensationStep);

            // Create compensation command record
            String commandPayload = createCompensationPayload(instance, compensationStep);
            SagaCommandRecord commandRecord = SagaCommandRecord.create(
                    instance.getId(),
                    "CompensationCommand",
                    compensationStep,
                    commandPayload
            );
            commandRecord.markAsSent();
            commandRepository.save(commandRecord);
            instance.addCommand(commandRecord);

            // Publish compensation command via outbox
            commandPublisher.publishCompensationCommand(instance, compensationStep, correlationId);
        } else {
            log.warn("No compensation step available for saga {} at step {}",
                    instance.getId(), failedStep);
        }
    }

    /**
     * Creates a compensation command payload as JSON.
     */
    private String createCompensationPayload(SagaInstance instance, SagaStep compensationStep) {
        try {
            return objectMapper.writeValueAsString(new CompensationPayload(
                    instance.getId(),
                    compensationStep.getCode(),
                    instance.getDocumentId(),
                    instance.getDocumentType().getCode()
            ));
        } catch (JsonProcessingException e) {
            log.error("Failed to create compensation payload", e);
            return "{}";
        }
    }

    /**
     * Generates a correlation ID for tracking.
     * Uses a random UUID for distributed tracing across services.
     */
    private String generateCorrelationId() {
        return java.util.UUID.randomUUID().toString();
    }

    /**
     * Extracts correlation ID from metadata if present.
     */
    private String extractCorrelationIdFromMetadata(Map<String, Object> metadata) {
        if (metadata != null && metadata.containsKey("correlationId")) {
            Object correlationId = metadata.get("correlationId");
            return correlationId != null ? correlationId.toString() : null;
        }
        return null;
    }

    /**
     * Extracts invoice number from metadata.
     */
    private String extractInvoiceNumber(DocumentMetadata metadata) {
        if (metadata != null && metadata.getMetadata() != null) {
            Object invoiceNumber = metadata.getMetadata().get("invoiceNumber");
            return invoiceNumber != null ? invoiceNumber.toString() : null;
        }
        return null;
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

    /**
     * Internal record for compensation payloads.
     */
    private record CompensationPayload(
            String sagaId,
            String stepToCompensate,
            String documentId,
            String documentType
    ) {}
}
