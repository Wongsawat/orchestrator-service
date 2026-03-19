package com.wpanther.orchestrator.infrastructure.metrics;

import com.wpanther.orchestrator.domain.model.enums.DocumentType;
import com.wpanther.saga.domain.enums.SagaStep;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Metrics component for saga monitoring using Micrometer.
 * <p>
 * Tracks key performance indicators for saga orchestration:
 * <ul>
 *   <li>Saga duration (percentiles, throughput)</li>
 *   <li>Saga counts by status and document type</li>
 *   <li>Saga step completion rates</li>
 *   <li>Compensation operations</li>
 * </ul>
 * <p>
 * Metrics are exposed via Actuator at {@code /actuator/metrics} and can be
 * scraped by Prometheus, Grafana, or other monitoring systems.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SagaMetrics {

    private final MeterRegistry meterRegistry;

    // Metric name constants
    private static final String SAGA_DURATION = "saga.duration";
    private static final String SAGA_STARTED = "saga.started";
    private static final String SAGA_COMPLETED = "saga.completed";
    private static final String SAGA_FAILED = "saga.failed";
    private static final String SAGA_COMPENSATING = "saga.compensating";
    private static final String SAGA_STEP_COMPLETED = "saga.step.completed";
    private static final String SAGA_STEP_FAILED = "saga.step.failed";

    // Tag names
    private static final String TAG_DOCUMENT_TYPE = "document_type";
    private static final String TAG_STATUS = "status";
    private static final String TAG_STEP = "step";
    private static final String TAG_COMPENSATING = "compensating";

    /**
     * Records the start of a new saga.
     *
     * @param documentType the type of document being processed
     */
    public void recordSagaStarted(DocumentType documentType) {
        Counter.builder(SAGA_STARTED)
                .tag(TAG_DOCUMENT_TYPE, documentType.name())
                .description("Total number of sagas started")
                .register(meterRegistry)
                .increment();
    }

    /**
     * Records the successful completion of a saga with its duration.
     *
     * @param documentType the type of document that was processed
     * @param durationMs   the duration in milliseconds
     */
    public void recordSagaCompleted(DocumentType documentType, long durationMs) {
        // Record completion counter
        Counter.builder(SAGA_COMPLETED)
                .tag(TAG_DOCUMENT_TYPE, documentType.name())
                .description("Total number of sagas completed successfully")
                .register(meterRegistry)
                .increment();

        // Record duration timer (provides percentiles, throughput)
        Timer.builder(SAGA_DURATION)
                .tag(TAG_DOCUMENT_TYPE, documentType.name())
                .tag(TAG_STATUS, "completed")
                .description("Saga duration in milliseconds")
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Records a failed saga with its duration and compensation status.
     *
     * @param documentType the type of document being processed
     * @param durationMs   the duration in milliseconds
     * @param compensating whether compensation was initiated
     */
    public void recordSagaFailed(DocumentType documentType, long durationMs, boolean compensating) {
        // Record failure counter
        Counter.builder(SAGA_FAILED)
                .tag(TAG_DOCUMENT_TYPE, documentType.name())
                .tag(TAG_COMPENSATING, String.valueOf(compensating))
                .description("Total number of sagas failed")
                .register(meterRegistry)
                .increment();

        // Record duration for failed sagas
        Timer.builder(SAGA_DURATION)
                .tag(TAG_DOCUMENT_TYPE, documentType.name())
                .tag(TAG_STATUS, compensating ? "compensating" : "failed")
                .description("Saga duration in milliseconds")
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);

        // Track compensation operations separately
        if (compensating) {
            Counter.builder(SAGA_COMPENSATING)
                    .tag(TAG_DOCUMENT_TYPE, documentType.name())
                    .description("Total number of sagas entering compensation")
                    .register(meterRegistry)
                    .increment();
        }
    }

    /**
     * Records the successful completion of a saga step.
     *
     * @param documentType the type of document being processed
     * @param step         the completed step
     */
    public void recordStepCompleted(DocumentType documentType, SagaStep step) {
        Counter.builder(SAGA_STEP_COMPLETED)
                .tag(TAG_DOCUMENT_TYPE, documentType.name())
                .tag(TAG_STEP, step.getCode())
                .description("Total number of saga steps completed successfully")
                .register(meterRegistry)
                .increment();
    }

    /**
     * Records the failure of a saga step.
     *
     * @param documentType the type of document being processed
     * @param step         the failed step
     */
    public void recordStepFailed(DocumentType documentType, SagaStep step) {
        Counter.builder(SAGA_STEP_FAILED)
                .tag(TAG_DOCUMENT_TYPE, documentType.name())
                .tag(TAG_STEP, step.getCode())
                .description("Total number of saga steps failed")
                .register(meterRegistry)
                .increment();
    }

    /**
     * Gets the current count of sagas started for a document type.
     * Useful for health checks and monitoring dashboards.
     *
     * @param documentType the document type to query
     * @return the count of started sagas
     */
    public double getSagaStartedCount(DocumentType documentType) {
        Counter counter = meterRegistry.find(SAGA_STARTED)
                .tag(TAG_DOCUMENT_TYPE, documentType.name())
                .counter();
        return counter != null ? counter.count() : 0.0;
    }

    /**
     * Gets the current count of sagas completed for a document type.
     *
     * @param documentType the document type to query
     * @return the count of completed sagas
     */
    public double getSagaCompletedCount(DocumentType documentType) {
        Counter counter = meterRegistry.find(SAGA_COMPLETED)
                .tag(TAG_DOCUMENT_TYPE, documentType.name())
                .counter();
        return counter != null ? counter.count() : 0.0;
    }

    /**
     * Gets the current count of sagas failed for a document type.
     *
     * @param documentType the document type to query
     * @return the count of failed sagas
     */
    public double getSagaFailedCount(DocumentType documentType) {
        Counter counter = meterRegistry.find(SAGA_FAILED)
                .tag(TAG_DOCUMENT_TYPE, documentType.name())
                .counter();
        return counter != null ? counter.count() : 0.0;
    }
}
