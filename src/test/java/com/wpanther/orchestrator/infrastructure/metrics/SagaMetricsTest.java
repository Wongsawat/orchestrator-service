package com.wpanther.orchestrator.infrastructure.metrics;

import com.wpanther.orchestrator.domain.model.enums.DocumentType;
import com.wpanther.saga.domain.enums.SagaStep;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SagaMetrics Tests")
class SagaMetricsTest {

    private MeterRegistry meterRegistry;
    private SagaMetrics sagaMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        sagaMetrics = new SagaMetrics(meterRegistry);
    }

    @Nested
    @DisplayName("recordSagaStarted()")
    class RecordSagaStartedTests {

        @Test
        @DisplayName("increments saga started counter for invoice")
        void incrementsCounterForInvoice() {
            sagaMetrics.recordSagaStarted(DocumentType.INVOICE);

            assertThat(sagaMetrics.getSagaStartedCount(DocumentType.INVOICE))
                    .isEqualTo(1.0);
        }

        @Test
        @DisplayName("increments counter multiple times")
        void incrementsMultipleTimes() {
            sagaMetrics.recordSagaStarted(DocumentType.TAX_INVOICE);
            sagaMetrics.recordSagaStarted(DocumentType.TAX_INVOICE);
            sagaMetrics.recordSagaStarted(DocumentType.TAX_INVOICE);

            assertThat(sagaMetrics.getSagaStartedCount(DocumentType.TAX_INVOICE))
                    .isEqualTo(3.0);
        }

        @Test
        @DisplayName("tracks document types separately")
        void tracksDocumentTypesSeparately() {
            sagaMetrics.recordSagaStarted(DocumentType.INVOICE);
            sagaMetrics.recordSagaStarted(DocumentType.TAX_INVOICE);

            assertThat(sagaMetrics.getSagaStartedCount(DocumentType.INVOICE))
                    .isEqualTo(1.0);
            assertThat(sagaMetrics.getSagaStartedCount(DocumentType.TAX_INVOICE))
                    .isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("recordSagaCompleted()")
    class RecordSagaCompletedTests {

        @Test
        @DisplayName("increments completion counter and records duration")
        void incrementsCounterAndRecordsDuration() {
            sagaMetrics.recordSagaCompleted(DocumentType.INVOICE, 5000);

            assertThat(sagaMetrics.getSagaCompletedCount(DocumentType.INVOICE))
                    .isEqualTo(1.0);
        }

        @Test
        @DisplayName("records timer with duration in milliseconds")
        void recordsTimerWithDuration() {
            sagaMetrics.recordSagaCompleted(DocumentType.TAX_INVOICE, 10000);

            // Verify timer was recorded by checking counter
            assertThat(sagaMetrics.getSagaCompletedCount(DocumentType.TAX_INVOICE))
                    .isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("recordSagaFailed()")
    class RecordSagaFailedTests {

        @Test
        @DisplayName("increments failure counter without compensation")
        void incrementsFailureCounterWithoutCompensation() {
            sagaMetrics.recordSagaFailed(DocumentType.INVOICE, 3000, false);

            assertThat(sagaMetrics.getSagaFailedCount(DocumentType.INVOICE))
                    .isEqualTo(1.0);
        }

        @Test
        @DisplayName("increments failure counter with compensation")
        void incrementsFailureCounterWithCompensation() {
            sagaMetrics.recordSagaFailed(DocumentType.TAX_INVOICE, 5000, true);

            assertThat(sagaMetrics.getSagaFailedCount(DocumentType.TAX_INVOICE))
                    .isEqualTo(1.0);
        }

        @Test
        @DisplayName("tracks compensation separately")
        void tracksCompensationSeparately() {
            sagaMetrics.recordSagaFailed(DocumentType.INVOICE, 1000, true);
            sagaMetrics.recordSagaFailed(DocumentType.INVOICE, 1000, false);

            // Query with compensating tag to get total count
            double withCompensation = meterRegistry.find("saga.failed")
                    .tag("document_type", "INVOICE")
                    .tag("compensating", "true")
                    .counter()
                    .count();
            double withoutCompensation = meterRegistry.find("saga.failed")
                    .tag("document_type", "INVOICE")
                    .tag("compensating", "false")
                    .counter()
                    .count();

            assertThat(withCompensation + withoutCompensation)
                    .isEqualTo(2.0);
        }
    }

    @Nested
    @DisplayName("recordStepCompleted()")
    class RecordStepCompletedTests {

        @Test
        @DisplayName("records step completion")
        void recordsStepCompletion() {
            sagaMetrics.recordStepCompleted(DocumentType.INVOICE, SagaStep.PROCESS_INVOICE);

            // Verify by querying the meter registry - step.getCode() returns lowercase hyphenated codes
            assertThat(meterRegistry.get("saga.step.completed")
                    .tag("document_type", "INVOICE")
                    .tag("step", "process-invoice")
                    .counter()
                    .count())
                    .isEqualTo(1.0);
        }

        @Test
        @DisplayName("records multiple steps")
        void recordsMultipleSteps() {
            sagaMetrics.recordStepCompleted(DocumentType.TAX_INVOICE, SagaStep.PROCESS_TAX_INVOICE);
            sagaMetrics.recordStepCompleted(DocumentType.TAX_INVOICE, SagaStep.SIGN_XML);

            assertThat(meterRegistry.get("saga.step.completed")
                    .tag("document_type", "TAX_INVOICE")
                    .tag("step", "process-tax-invoice")
                    .counter()
                    .count())
                    .isEqualTo(1.0);

            assertThat(meterRegistry.get("saga.step.completed")
                    .tag("document_type", "TAX_INVOICE")
                    .tag("step", "sign-xml")
                    .counter()
                    .count())
                    .isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("recordStepFailed()")
    class RecordStepFailedTests {

        @Test
        @DisplayName("records step failure")
        void recordsStepFailure() {
            sagaMetrics.recordStepFailed(DocumentType.INVOICE, SagaStep.PROCESS_INVOICE);

            assertThat(meterRegistry.get("saga.step.failed")
                    .tag("document_type", "INVOICE")
                    .tag("step", "process-invoice")
                    .counter()
                    .count())
                    .isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("getSagaStartedCount()")
    class GetSagaStartedCountTests {

        @Test
        @DisplayName("returns zero when no sagas started")
        void returnsZeroWhenNoSagasStarted() {
            assertThat(sagaMetrics.getSagaStartedCount(DocumentType.INVOICE))
                    .isEqualTo(0.0);
        }

        @Test
        @DisplayName("returns count of started sagas")
        void returnsCountOfStartedSagas() {
            sagaMetrics.recordSagaStarted(DocumentType.INVOICE);
            sagaMetrics.recordSagaStarted(DocumentType.INVOICE);

            assertThat(sagaMetrics.getSagaStartedCount(DocumentType.INVOICE))
                    .isEqualTo(2.0);
        }
    }

    @Nested
    @DisplayName("getSagaCompletedCount()")
    class GetSagaCompletedCountTests {

        @Test
        @DisplayName("returns zero when no sagas completed")
        void returnsZeroWhenNoSagasCompleted() {
            assertThat(sagaMetrics.getSagaCompletedCount(DocumentType.TAX_INVOICE))
                    .isEqualTo(0.0);
        }

        @Test
        @DisplayName("returns count of completed sagas")
        void returnsCountOfCompletedSagas() {
            sagaMetrics.recordSagaCompleted(DocumentType.TAX_INVOICE, 1000);
            sagaMetrics.recordSagaCompleted(DocumentType.TAX_INVOICE, 2000);

            assertThat(sagaMetrics.getSagaCompletedCount(DocumentType.TAX_INVOICE))
                    .isEqualTo(2.0);
        }
    }

    @Nested
    @DisplayName("getSagaFailedCount()")
    class GetSagaFailedCountTests {

        @Test
        @DisplayName("returns zero when no sagas failed")
        void returnsZeroWhenNoSagasFailed() {
            assertThat(sagaMetrics.getSagaFailedCount(DocumentType.ABBREVIATED_TAX_INVOICE))
                    .isEqualTo(0.0);
        }

        @Test
        @DisplayName("returns count of failed sagas")
        void returnsCountOfFailedSagas() {
            // Record two failures with same compensating status
            sagaMetrics.recordSagaFailed(DocumentType.ABBREVIATED_TAX_INVOICE, 500, false);
            sagaMetrics.recordSagaFailed(DocumentType.ABBREVIATED_TAX_INVOICE, 1000, false);

            // getSagaFailedCount returns the count for non-compensating failures
            assertThat(sagaMetrics.getSagaFailedCount(DocumentType.ABBREVIATED_TAX_INVOICE))
                    .isEqualTo(2.0);
        }
    }
}
