package com.wpanther.orchestrator.infrastructure.persistence;

import com.wpanther.orchestrator.domain.model.SagaCommandRecord;
import com.wpanther.saga.domain.enums.SagaStep;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * JPA entity for persisting saga command records.
 */
@Entity
@Table(name = "saga_commands", indexes = {
    @Index(name = "idx_saga_id", columnList = "saga_id"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SagaCommandEntity {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "saga_id", nullable = false)
    private String sagaId;

    @Column(name = "command_type", nullable = false, length = 100)
    private String commandType;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_step", nullable = false, length = 50)
    private SagaStep targetStep;

    @Lob
    @Column(name = "payload", nullable = false, columnDefinition = "JSONB")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SagaCommandRecord.CommandStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
