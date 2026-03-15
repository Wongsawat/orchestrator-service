package com.wpanther.orchestrator.domain.model;

import com.wpanther.orchestrator.domain.model.enums.DocumentType;
import com.wpanther.saga.domain.enums.SagaStatus;
import com.wpanther.saga.domain.enums.SagaStep;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate root representing a saga instance.
 * Manages the lifecycle and state transitions of a saga.
 */
@Data
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SagaInstance {

    /**
     * Unique identifier for this saga instance.
     */
    private String id;

    /**
     * The type of document being processed.
     */
    private DocumentType documentType;

    /**
     * External document identifier.
     */
    private String documentId;

    /**
     * Current step in the saga execution.
     */
    private SagaStep currentStep;

    /**
     * Current status of the saga.
     */
    private SagaStatus status;

    /**
     * When the saga was created.
     */
    private Instant createdAt;

    /**
     * When the saga was last updated.
     */
    private Instant updatedAt;

    /**
     * When the saga completed (null if not completed).
     */
    private Instant completedAt;

    /**
     * Error message if the saga failed.
     */
    private String errorMessage;

    /**
     * Metadata associated with the document.
     */
    private DocumentMetadata documentMetadata;

    /**
     * History of commands sent for this saga.
     */
    @Builder.Default
    private List<SagaCommandRecord> commandHistory = new ArrayList<>();

    /**
     * Number of retry attempts for the current step.
     */
    @Builder.Default
    private int retryCount = 0;

    /**
     * Maximum retries allowed before marking as failed.
     */
    @Builder.Default
    private int maxRetries = 3;

    /**
     * Default step flow strategy used for determining next and compensation steps.
     * This can be overridden by injecting a custom strategy in the application layer.
     */
    private static final com.wpanther.orchestrator.domain.service.SagaStepFlowStrategy DEFAULT_FLOW_STRATEGY =
            new com.wpanther.orchestrator.domain.service.DefaultSagaStepFlowStrategy();

    /**
     * Creates a new saga instance.
     */
    public static SagaInstance create(DocumentType documentType, String documentId, DocumentMetadata metadata) {
        SagaInstance instance = SagaInstance.builder()
                .id(UUID.randomUUID().toString())
                .documentType(documentType)
                .documentId(documentId)
                .status(SagaStatus.STARTED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .documentMetadata(metadata)
                .retryCount(0)
                .build();

        instance.initializeFirstStep();
        return instance;
    }

    /**
     * Initializes the first step based on document type.
     */
    private void initializeFirstStep() {
        this.currentStep = documentType == DocumentType.INVOICE
                ? SagaStep.PROCESS_INVOICE
                : SagaStep.PROCESS_TAX_INVOICE;
    }

    /**
     * Starts the saga execution.
     */
    public void start() {
        if (this.status != SagaStatus.STARTED) {
            throw new IllegalStateException("Can only start a saga in STARTED status");
        }
        this.status = SagaStatus.IN_PROGRESS;
        this.updatedAt = Instant.now();
    }

    /**
     * Advances the saga to the next step.
     */
    public void advanceTo(SagaStep nextStep) {
        if (this.status != SagaStatus.IN_PROGRESS) {
            throw new IllegalStateException("Can only advance when saga is IN_PROGRESS");
        }
        this.currentStep = nextStep;
        this.retryCount = 0;
        this.updatedAt = Instant.now();
    }

    /**
     * Marks the saga as completed.
     * This method is idempotent - calling it multiple times has no additional effect.
     */
    public void complete() {
        if (this.status == SagaStatus.COMPLETED) {
            return; // Already completed, idempotent
        }
        if (this.status != SagaStatus.IN_PROGRESS) {
            throw new IllegalStateException("Can only complete a saga that is IN_PROGRESS");
        }
        this.status = SagaStatus.COMPLETED;
        this.completedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Marks the saga as failed with an error message.
     */
    public void fail(String errorMessage) {
        this.status = SagaStatus.FAILED;
        this.errorMessage = errorMessage;
        this.updatedAt = Instant.now();
    }

    /**
     * Starts the compensation process.
     */
    public void startCompensation() {
        this.status = SagaStatus.COMPENSATING;
        this.updatedAt = Instant.now();
    }

    /**
     * Increments the retry count for the current step.
     */
    public void incrementRetry() {
        this.retryCount++;
        this.updatedAt = Instant.now();
    }

    /**
     * Checks if max retries have been exceeded.
     */
    public boolean hasExceededMaxRetries() {
        return this.retryCount >= this.maxRetries;
    }

    /**
     * Gets the next step in the saga flow.
     * Uses the default step flow strategy to determine the next step based on
     * the current step and document type.
     *
     * @return the next saga step, or null if the saga is complete
     * @throws IllegalStateException if the current step is unknown
     */
    public SagaStep getNextStep() {
        return DEFAULT_FLOW_STRATEGY.getNextStep(currentStep, documentType);
    }

    /**
     * Gets the compensation step for the current step.
     * Uses the default step flow strategy to determine the compensation step based on
     * the current step and document type.
     *
     * @return the compensation step, or null if no compensation is available
     */
    public SagaStep getCompensationStep() {
        return DEFAULT_FLOW_STRATEGY.getCompensationStep(currentStep, documentType);
    }

    /**
     * Adds a command to the history.
     */
    public void addCommand(SagaCommandRecord command) {
        this.commandHistory.add(command);
        this.updatedAt = Instant.now();
    }

    /**
     * Returns an unmodifiable view of the command history.
     */
    public List<SagaCommandRecord> getCommandHistory() {
        return Collections.unmodifiableList(commandHistory);
    }
}
