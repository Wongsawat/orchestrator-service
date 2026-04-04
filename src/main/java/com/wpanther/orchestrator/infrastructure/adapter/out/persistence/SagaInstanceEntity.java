package com.wpanther.orchestrator.infrastructure.adapter.out.persistence;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.wpanther.orchestrator.domain.model.enums.DocumentType;
import org.hibernate.annotations.JdbcTypeCode;

import java.sql.Types;
import com.wpanther.saga.domain.enums.SagaStatus;
import com.wpanther.saga.domain.enums.SagaStep;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * JPA entity for persisting saga instances.
 */
@Entity
@Table(name = "saga_instances", indexes = {
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_document", columnList = "document_type, document_id"),
    @Index(name = "idx_updated_at", columnList = "updated_at")
})
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SagaInstanceEntity {

    @Id
    @Column(name = "id")
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false)
    private DocumentType documentType;

    @Column(name = "document_id", nullable = false, length = 100)
    private String documentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_step", length = 50)
    private SagaStep currentStep;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SagaStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "file_path", length = 500)
    private String filePath;

    @JdbcTypeCode(Types.VARCHAR)
    @Column(name = "xml_content", columnDefinition = "TEXT")
    private String xmlContent;

    @JdbcTypeCode(Types.VARCHAR)
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "checksum", length = 255)
    private String checksum;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Column(name = "document_number", length = 100)
    private String documentNumber;

    @Column(name = "retry_count")
    private Integer retryCount;

    @Column(name = "max_retries")
    private Integer maxRetries;

    @Version
    @Column(name = "version")
    @JsonIgnore
    private Integer version;

    /**
     * Default max retries when persisting new saga instances.
     * Matches the default in SagaProperties and SagaInstance.DEFAULT_MAX_RETRIES.
     */
    private static final int DEFAULT_MAX_RETRIES = 3;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (version == null) {
            version = 0;
        }
        if (retryCount == null) {
            retryCount = 0;
        }
        if (maxRetries == null) {
            maxRetries = DEFAULT_MAX_RETRIES;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
        // Note: version is now automatically managed by JPA's @Version annotation
        // No manual incrementing needed - JPA will handle optimistic locking
    }
}
