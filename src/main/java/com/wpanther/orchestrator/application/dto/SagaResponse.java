package com.wpanther.orchestrator.application.dto;

import com.wpanther.orchestrator.domain.model.SagaInstance;
import com.wpanther.saga.domain.enums.SagaStatus;
import com.wpanther.saga.domain.enums.SagaStep;

import java.time.Instant;
import java.util.List;

/**
 * DTO for saga instance responses.
 */
public record SagaResponse(

        /**
         * Unique identifier for the saga instance.
         */
        String id,

        /**
         * The type of document being processed.
         */
        String documentType,

        /**
         * External document identifier.
         */
        String documentId,

        /**
         * Current step in the saga execution.
         */
        String currentStep,

        /**
         * Current status of the saga.
         */
        String status,

        /**
         * When the saga was created.
         */
        Instant createdAt,

        /**
         * When the saga was last updated.
         */
        Instant updatedAt,

        /**
         * When the saga completed (null if not completed).
         */
        Instant completedAt,

        /**
         * Error message if the saga failed.
         */
        String errorMessage,

        /**
         * Number of retries for the current step.
         */
        int retryCount,

        /**
         * Command history for the saga.
         */
        List<CommandSummary> commandHistory
) {

    /**
     * Creates a SagaResponse from a SagaInstance domain model.
     */
    public static SagaResponse fromDomain(SagaInstance instance) {
        return new SagaResponse(
                instance.getId(),
                instance.getDocumentType().getCode(),
                instance.getDocumentId(),
                instance.getCurrentStep() != null ? instance.getCurrentStep().getCode() : null,
                instance.getStatus().name(),
                instance.getCreatedAt(),
                instance.getUpdatedAt(),
                instance.getCompletedAt(),
                instance.getErrorMessage(),
                instance.getRetryCount(),
                instance.getCommandHistory().stream()
                        .map(CommandSummary::fromDomain)
                        .toList()
        );
    }

    /**
     * Summary of a command in the saga history.
     */
    public record CommandSummary(
            String id,
            String commandType,
            String targetStep,
            String status,
            Instant createdAt,
            String errorMessage
    ) {
        public static CommandSummary fromDomain(com.wpanther.orchestrator.domain.model.SagaCommandRecord command) {
            return new CommandSummary(
                    command.getId(),
                    command.getCommandType(),
                    command.getTargetStep().getCode(),
                    command.getStatus().name(),
                    command.getCreatedAt(),
                    command.getErrorMessage()
            );
        }
    }
}
