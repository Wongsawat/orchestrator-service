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
import java.util.Map;

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
     * Loads command history from the repository.
     */
    public SagaInstance toDomain(SagaInstanceEntity entity) {
        if (entity == null) {
            return null;
        }
        // Load command history and delegate to the common mapping method
        List<SagaCommandRecord> commands = commandRepository.findBySagaId(entity.getId());
        return toDomainWithCommands(entity, commands);
    }

    /**
     * Converts multiple JPA entities to domain models using batch-loaded command history.
     * This method avoids N+1 queries by loading all command records in a single database query.
     *
     * @param entities list of JPA entities to convert
     * @return list of domain models with command history populated
     */
    public List<SagaInstance> toDomainBatch(List<SagaInstanceEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }

        // Collect all saga IDs for batch loading
        List<String> sagaIds = entities.stream()
                .map(SagaInstanceEntity::getId)
                .toList();

        // Batch load all command records in one query
        Map<String, List<SagaCommandRecord>> commandsBySagaId = commandRepository.findBySagaIdIn(sagaIds);

        // Convert each entity, using pre-loaded commands
        return entities.stream()
                .map(entity -> toDomainWithCommands(entity, commandsBySagaId.getOrDefault(entity.getId(), List.of())))
                .toList();
    }

    /**
     * Converts JPA entity to domain model with pre-loaded command history.
     * Used internally by batch conversion to avoid redundant queries.
     */
    private SagaInstance toDomainWithCommands(SagaInstanceEntity entity, List<SagaCommandRecord> commands) {
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

        // Use pre-loaded command history
        builder.commandHistory(new ArrayList<>(commands));

        return builder.build();
    }
}
