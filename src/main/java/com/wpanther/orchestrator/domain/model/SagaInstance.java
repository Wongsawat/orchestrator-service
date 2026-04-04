package com.wpanther.orchestrator.domain.model;

import com.wpanther.orchestrator.domain.model.enums.DocumentType;
import com.wpanther.saga.domain.enums.SagaStatus;
import com.wpanther.saga.domain.enums.SagaStep;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
     * Document number from the original XML (e.g., tax invoice number).
     * Stored separately from documentMetadata for easy access without loading CLOB columns.
     */
    private String documentNumber;

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
     * Excludes Lombok-generated setter to ensure all modifications go through addCommand().
     * Getter returns an unmodifiable view to prevent external mutation.
     */
    @Builder.Default
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private List<SagaCommandRecord> commandHistory = new ArrayList<>();

    /**
     * Correlation ID for distributed tracing across services.
     * This ID remains constant throughout the entire saga lifecycle,
     * enabling end-to-end request tracking across microservices.
     */
    private String correlationId;

    /**
     * Number of retry attempts for the current step.
     */
    @Builder.Default
    private int retryCount = 0;

    /**
     * Maximum retries allowed before marking as failed.
     * Default value of 3 matches the configured default in SagaProperties.
     */
    @Builder.Default
    private int maxRetries = DEFAULT_MAX_RETRIES;

    /**
     * Optimistic locking version. Incremented by JPA on each update.
     * Initial value 0 matches the database default.
     */
    @Builder.Default
    private Integer version = 0;

    /**
     * Default maximum number of retry attempts per saga step.
     * This value should match the default in SagaProperties.
     */
    private static final int DEFAULT_MAX_RETRIES = 3;

    /**
     * Default step flow strategy used for determining next and compensation steps.
     * A new instance is created per SagaInstance for better testability.
     * Using @Builder.Default ensures this is initialized when using the builder pattern.
     */
    @Builder.Default
    private com.wpanther.orchestrator.domain.service.SagaStepFlowStrategy flowStrategy =
            new com.wpanther.orchestrator.domain.service.DefaultSagaStepFlowStrategy();

    /**
     * Creates a new saga instance with configurable max retries.
     *
     * @param documentType the type of document being processed
     * @param documentId the external document identifier
     * @param metadata the document metadata
     * @param maxRetries the maximum number of retry attempts per step
     * @return a new saga instance
     */
    public static SagaInstance create(DocumentType documentType, String documentId,
                                      DocumentMetadata metadata, int maxRetries) {
        SagaInstance instance = SagaInstance.builder()
                .id(UUID.randomUUID().toString())
                .documentType(documentType)
                .documentId(documentId)
                .status(SagaStatus.STARTED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .documentMetadata(metadata)
                .retryCount(0)
                .maxRetries(maxRetries)
                .build();

        instance.initializeFirstStep();
        return instance;
    }

    /**
     * Creates a new saga instance with default max retries (3).
     *
     * @param documentType the type of document being processed
     * @param documentId the external document identifier
     * @param metadata the document metadata
     * @return a new saga instance
     */
    public static SagaInstance create(DocumentType documentType, String documentId,
                                      DocumentMetadata metadata) {
        return create(documentType, documentId, metadata, DEFAULT_MAX_RETRIES);
    }

    /**
     * Initializes the first step based on document type.
     *
     * @throws UnsupportedOperationException if the document type is not supported
     */
    private void initializeFirstStep() {
        this.currentStep = documentType.getInitialStep();
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
        if (SagaStatus.COMPLETED.equals(this.status)) {
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
     * Uses the configured step flow strategy to determine the next step based on
     * the current step and document type.
     *
     * @return the next saga step, or null if the saga is complete
     * @throws IllegalStateException if the current step is unknown
     */
    public SagaStep getNextStep() {
        return flowStrategy.getNextStep(currentStep, documentType);
    }

    /**
     * Gets the next step in the saga flow using a specific strategy.
     * This overload is provided for testing purposes to allow injecting mock strategies.
     *
     * @param strategy the strategy to use for determining the next step
     * @return the next saga step, or null if the saga is complete
     * @throws IllegalStateException if the current step is unknown
     */
    public SagaStep getNextStep(com.wpanther.orchestrator.domain.service.SagaStepFlowStrategy strategy) {
        return strategy.getNextStep(currentStep, documentType);
    }

    /**
     * Gets the compensation step for the current step.
     * Uses the configured step flow strategy to determine the compensation step based on
     * the current step and document type.
     *
     * @return the compensation step, or null if no compensation is available
     */
    public SagaStep getCompensationStep() {
        return flowStrategy.getCompensationStep(currentStep, documentType);
    }

    /**
     * Gets the compensation step for the current step using a specific strategy.
     * This overload is provided for testing purposes to allow injecting mock strategies.
     *
     * @param strategy the strategy to use for determining the compensation step
     * @return the compensation step, or null if no compensation is available
     */
    public SagaStep getCompensationStep(com.wpanther.orchestrator.domain.service.SagaStepFlowStrategy strategy) {
        return strategy.getCompensationStep(currentStep, documentType);
    }

    /**
     * Adds a command to the history.
     * This is the only way to modify command history, ensuring controlled mutation.
     */
    public void addCommand(SagaCommandRecord command) {
        this.commandHistory.add(command);
        this.updatedAt = Instant.now();
    }

    /**
     * Returns an unmodifiable view of the command history.
     * <p>
     * The returned list cannot be modified externally. All additions must go
     * through {@link #addCommand(SagaCommandRecord)} to ensure proper state
     * management and audit trail integrity.
     *
     * @return an unmodifiable list of command records
     */
    public List<SagaCommandRecord> getCommandHistory() {
        return Collections.unmodifiableList(commandHistory);
    }

    /**
     * Checks if this saga has expired based on the given timeout duration.
     * A saga is considered expired if it has been in IN_PROGRESS status
     * longer than the specified timeout since its last update.
     *
     * @param timeoutMinutes the timeout duration in minutes
     * @return true if the saga has expired, false otherwise
     */
    public boolean isExpired(int timeoutMinutes) {
        if (timeoutMinutes <= 0) {
            return false; // Timeout disabled
        }
        if (status != SagaStatus.IN_PROGRESS) {
            return false; // Only IN_PROGRESS sagas can expire
        }
        long timeoutMs = timeoutMinutes * 60L * 1000L;
        long elapsedMs = Instant.now().toEpochMilli() - updatedAt.toEpochMilli();
        return elapsedMs > timeoutMs;
    }
}
