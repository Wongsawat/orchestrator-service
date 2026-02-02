package com.wpanther.orchestrator.domain.model;

import com.wpanther.saga.domain.enums.SagaStep;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a command sent as part of a saga execution.
 * Maintains a history of all commands for audit and compensation purposes.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class SagaCommandRecord {

    /**
     * Unique identifier for this command.
     */
    private String id;

    /**
     * The saga instance this command belongs to.
     */
    private String sagaId;

    /**
     * The type of command (class name).
     */
    private String commandType;

    /**
     * The step this command targets.
     */
    private SagaStep targetStep;

    /**
     * The command payload as JSON.
     */
    private String payload;

    /**
     * Current status of this command.
     */
    private CommandStatus status;

    /**
     * When the command was created.
     */
    private Instant createdAt;

    /**
     * When the command was sent to Kafka.
     */
    private Instant sentAt;

    /**
     * When the command was completed.
     */
    private Instant completedAt;

    /**
     * Error message if the command failed.
     */
    private String errorMessage;

    /**
     * Correlation ID for tracking the request-response.
     */
    private String correlationId;

    /**
     * Creates a new pending command.
     */
    public static SagaCommandRecord create(String sagaId, String commandType, SagaStep targetStep, String payload) {
        return SagaCommandRecord.builder()
                .id(UUID.randomUUID().toString())
                .sagaId(sagaId)
                .commandType(commandType)
                .targetStep(targetStep)
                .payload(payload)
                .status(CommandStatus.PENDING)
                .createdAt(Instant.now())
                .correlationId(UUID.randomUUID().toString())
                .build();
    }

    /**
     * Marks the command as sent.
     */
    public void markAsSent() {
        if (this.status != CommandStatus.PENDING) {
            throw new IllegalStateException("Can only mark a pending command as sent");
        }
        this.status = CommandStatus.SENT;
        this.sentAt = Instant.now();
    }

    /**
     * Marks the command as completed successfully.
     */
    public void markAsCompleted() {
        if (this.status != CommandStatus.SENT) {
            throw new IllegalStateException("Can only complete a sent command");
        }
        this.status = CommandStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    /**
     * Marks the command as failed.
     */
    public void markAsFailed(String errorMessage) {
        this.status = CommandStatus.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = Instant.now();
    }

    /**
     * Marks the command as compensated.
     */
    public void markAsCompensated() {
        this.status = CommandStatus.COMPENSATED;
        this.completedAt = Instant.now();
    }

    /**
     * Status of a saga command.
     */
    public enum CommandStatus {
        /**
         * Command created but not yet sent.
         */
        PENDING,

        /**
         * Command sent to Kafka, awaiting response.
         */
        SENT,

        /**
         * Command completed successfully.
         */
        COMPLETED,

        /**
         * Command failed.
         */
        FAILED,

        /**
         * Command was compensated during rollback.
         */
        COMPENSATED
    }
}
