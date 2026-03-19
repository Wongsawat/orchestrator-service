package com.wpanther.orchestrator.domain.model;

import com.wpanther.orchestrator.domain.model.enums.DocumentType;
import com.wpanther.saga.domain.enums.SagaStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SagaInstance Timeout Tests")
class SagaInstanceTimeoutTest {

    @Nested
    @DisplayName("isExpired()")
    class IsExpiredTests {

        @Test
        void withInProgressSaga_updatedRecently_returnsFalse() {
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .xmlContent("<xml/>")
                    .build();
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-001", metadata);
            saga.start();

            // Saga was just updated, should not be expired
            assertThat(saga.isExpired(30)).isFalse();
        }

        @Test
        void withInProgressSaga_updatedLongAgo_returnsTrue() {
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .xmlContent("<xml/>")
                    .build();
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-001", metadata);
            saga.start();

            // Manually set updatedAt to 31 minutes ago
            Instant past = Instant.now().minusSeconds(31 * 60);
            saga.setUpdatedAt(past);

            // Saga should be expired with 30 minute timeout
            assertThat(saga.isExpired(30)).isTrue();
        }

        @Test
        void withCompletedSaga_returnsFalse() {
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .xmlContent("<xml/>")
                    .build();
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-001", metadata);
            saga.start();
            saga.complete();

            // Manually set updatedAt to long ago
            Instant past = Instant.now().minusSeconds(31 * 60);
            saga.setUpdatedAt(past);

            // Completed sagas don't expire
            assertThat(saga.isExpired(30)).isFalse();
        }

        @Test
        void withFailedSaga_returnsFalse() {
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .xmlContent("<xml/>")
                    .build();
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-001", metadata);
            saga.start();
            saga.fail("Test error");

            // Manually set updatedAt to long ago
            Instant past = Instant.now().minusSeconds(31 * 60);
            saga.setUpdatedAt(past);

            // Failed sagas don't expire
            assertThat(saga.isExpired(30)).isFalse();
        }

        @Test
        void withCompensatingSaga_returnsFalse() {
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .xmlContent("<xml/>")
                    .build();
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-001", metadata);
            saga.start();
            saga.startCompensation();

            // Manually set updatedAt to long ago
            Instant past = Instant.now().minusSeconds(31 * 60);
            saga.setUpdatedAt(past);

            // Compensating sagas don't expire
            assertThat(saga.isExpired(30)).isFalse();
        }

        @Test
        void withZeroTimeout_returnsFalse() {
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .xmlContent("<xml/>")
                    .build();
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-001", metadata);
            saga.start();

            // Zero timeout means timeout is disabled
            assertThat(saga.isExpired(0)).isFalse();
        }

        @Test
        void withNegativeTimeout_returnsFalse() {
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .xmlContent("<xml/>")
                    .build();
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-001", metadata);
            saga.start();

            // Negative timeout means timeout is disabled
            assertThat(saga.isExpired(-1)).isFalse();
        }

        @Test
        void exactlyAtTimeoutBoundary_returnsFalse() {
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .xmlContent("<xml/>")
                    .build();
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-001", metadata);
            saga.start();

            // Set updatedAt to exactly 30 minutes ago
            Instant past = Instant.now().minusSeconds(30 * 60);
            saga.setUpdatedAt(past);

            // At the boundary, not expired yet (must be greater than timeout)
            assertThat(saga.isExpired(30)).isFalse();
        }

        @Test
        void oneMillisecondPastTimeout_returnsTrue() {
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .xmlContent("<xml/>")
                    .build();
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-001", metadata);
            saga.start();

            // Set updatedAt to 30 minutes + 1 millisecond ago
            Instant past = Instant.now().minusSeconds(30 * 60).minusMillis(1);
            saga.setUpdatedAt(past);

            // Just past the boundary, should be expired
            assertThat(saga.isExpired(30)).isTrue();
        }
    }
}
