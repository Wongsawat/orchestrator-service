package com.wpanther.orchestrator.infrastructure.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.orchestrator.domain.model.DocumentMetadata;
import com.wpanther.orchestrator.domain.model.SagaCommandRecord;
import com.wpanther.orchestrator.domain.model.SagaInstance;
import com.wpanther.orchestrator.domain.repository.SagaCommandRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Mapper for converting between SagaInstance domain model and SagaInstanceEntity JPA entity.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SagaInstanceMapper {

    /**
     * Default max retries when loading from database with null value.
     * Matches the default in SagaProperties and SagaInstance.DEFAULT_MAX_RETRIES.
     */
    private static final int DEFAULT_MAX_RETRIES = 3;

    private final ObjectMapper objectMapper;
    private final SagaCommandRecordRepository commandRepository;

    /**
     * Converts domain model to JPA entity.
     */
    public SagaInstanceEntity toEntity(SagaInstance domain) {
        if (domain == null) {
            return null;
        }

        var builder = SagaInstanceEntity.builder()
                .id(domain.getId())
                .documentType(domain.getDocumentType())
                .documentId(domain.getDocumentId())
                .currentStep(domain.getCurrentStep())
                .status(domain.getStatus())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .completedAt(domain.getCompletedAt())
                .errorMessage(domain.getErrorMessage())
                .correlationId(domain.getCorrelationId())
                .retryCount(domain.getRetryCount())
                .maxRetries(domain.getMaxRetries());

        // Map DocumentMetadata if present
        if (domain.getDocumentMetadata() != null) {
            DocumentMetadata metadata = domain.getDocumentMetadata();
            builder.filePath(metadata.getFilePath())
                    .xmlContent(metadata.getXmlContent())
                    .fileSize(metadata.getFileSize())
                    .mimeType(metadata.getMimeType())
                    .checksum(metadata.getChecksum());

            // Convert metadata map to JSON string
            if (metadata.getMetadata() != null && !metadata.getMetadata().isEmpty()) {
                try {
                    builder.metadata(objectMapper.writeValueAsString(metadata.getMetadata()));
                } catch (JsonProcessingException e) {
                    log.warn("Failed to serialize metadata for saga {}", domain.getId(), e);
                    builder.metadata(null);
                }
            }
        }

        return builder.build();
    }

    /**
     * Converts JPA entity to domain model.
     */
    public SagaInstance toDomain(SagaInstanceEntity entity) {
        if (entity == null) {
            return null;
        }

        var builder = SagaInstance.builder()
                .id(entity.getId())
                .documentType(entity.getDocumentType())
                .documentId(entity.getDocumentId())
                .currentStep(entity.getCurrentStep())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .completedAt(entity.getCompletedAt())
                .errorMessage(entity.getErrorMessage())
                .correlationId(entity.getCorrelationId())
                .retryCount(entity.getRetryCount() != null ? entity.getRetryCount() : 0)
                .maxRetries(entity.getMaxRetries() != null ? entity.getMaxRetries() : DEFAULT_MAX_RETRIES);

        // Map DocumentMetadata if present
        if (entity.getFilePath() != null || entity.getXmlContent() != null
                || entity.getMetadata() != null) {
            DocumentMetadata.DocumentMetadataBuilder metadataBuilder = DocumentMetadata.builder()
                    .filePath(entity.getFilePath())
                    .xmlContent(entity.getXmlContent())
                    .fileSize(entity.getFileSize())
                    .mimeType(entity.getMimeType())
                    .checksum(entity.getChecksum());

            // Parse metadata JSON string
            if (entity.getMetadata() != null && !entity.getMetadata().isEmpty()) {
                try {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> metadataMap = objectMapper.readValue(
                            entity.getMetadata(),
                            java.util.Map.class
                    );
                    metadataBuilder.metadata(metadataMap);
                } catch (JsonProcessingException e) {
                    log.warn("Failed to deserialize metadata for saga {}", entity.getId(), e);
                }
            }

            builder.documentMetadata(metadataBuilder.build());
        }

        // Load command history
        List<SagaCommandRecord> commands = commandRepository.findBySagaId(entity.getId());
        builder.commandHistory(new ArrayList<>(commands));

        return builder.build();
    }
}
