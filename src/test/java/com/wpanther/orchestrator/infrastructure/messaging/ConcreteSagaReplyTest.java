package com.wpanther.orchestrator.infrastructure.messaging;

import com.wpanther.saga.domain.enums.ReplyStatus;
import com.wpanther.saga.domain.enums.SagaStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConcreteSagaReply Tests")
class ConcreteSagaReplyTest {

    private ConcreteSagaReply createReply(String sagaId, SagaStep step, ReplyStatus status) {
        return new ConcreteSagaReply(
                UUID.randomUUID(),
                Instant.now(),
                "SagaReplyEvent",
                1,
                sagaId,
                step,
                "corr-001",
                status,
                status == ReplyStatus.FAILURE ? "Error occurred" : null
        );
    }

    @Nested
    @DisplayName("Constructor and basic fields")
    class ConstructorTests {

        @Test
        @DisplayName("creates reply with SUCCESS status")
        void createsSuccessReply() {
            ConcreteSagaReply reply = createReply("saga-001", SagaStep.PROCESS_INVOICE, ReplyStatus.SUCCESS);

            assertThat(reply.getSagaId()).isEqualTo("saga-001");
            assertThat(reply.getSagaStep()).isEqualTo(SagaStep.PROCESS_INVOICE);
            assertThat(reply.isSuccess()).isTrue();
            assertThat(reply.isFailure()).isFalse();
        }

        @Test
        @DisplayName("creates reply with FAILURE status")
        void createsFailureReply() {
            ConcreteSagaReply reply = createReply("saga-002", SagaStep.SIGN_XML, ReplyStatus.FAILURE);

            assertThat(reply.getSagaId()).isEqualTo("saga-002");
            assertThat(reply.getSagaStep()).isEqualTo(SagaStep.SIGN_XML);
            assertThat(reply.isSuccess()).isFalse();
            assertThat(reply.isFailure()).isTrue();
            assertThat(reply.getErrorMessage()).isEqualTo("Error occurred");
        }
    }

    @Nested
    @DisplayName("Additional data handling")
    class AdditionalDataTests {

        @Test
        @DisplayName("stores and retrieves additional data via @JsonAnySetter/@JsonAnyGetter")
        void storesAdditionalData() {
            ConcreteSagaReply reply = createReply("saga-001", SagaStep.SIGN_PDF, ReplyStatus.SUCCESS);

            reply.setAdditionalData("signedPdfUrl", "http://storage/signed.pdf");
            reply.setAdditionalData("signedDocumentId", "doc-signed-001");

            assertThat(reply.getAdditionalData()).containsEntry("signedPdfUrl", "http://storage/signed.pdf");
            assertThat(reply.getAdditionalData()).containsEntry("signedDocumentId", "doc-signed-001");
        }

        @Test
        @DisplayName("returns empty map when no additional data set")
        void returnsEmptyMapByDefault() {
            ConcreteSagaReply reply = createReply("saga-001", SagaStep.PROCESS_INVOICE, ReplyStatus.SUCCESS);

            assertThat(reply.getAdditionalData()).isEmpty();
        }

        @Test
        @DisplayName("stores pdfUrl and pdfSize from tax invoice pdf step")
        void storesPdfUrlAndSize() {
            ConcreteSagaReply reply = createReply("saga-001", SagaStep.GENERATE_TAX_INVOICE_PDF, ReplyStatus.SUCCESS);

            reply.setAdditionalData("pdfUrl", "http://minio/tax.pdf");
            reply.setAdditionalData("pdfSize", 10240L);

            assertThat(reply.getAdditionalData()).containsKey("pdfUrl");
            assertThat(reply.getAdditionalData()).containsKey("pdfSize");
        }

        @Test
        @DisplayName("stores storedDocumentUrl from pdf-storage step")
        void storesStoredDocumentUrl() {
            ConcreteSagaReply reply = createReply("saga-001", SagaStep.PDF_STORAGE, ReplyStatus.SUCCESS);

            reply.setAdditionalData("storedDocumentUrl", "http://storage/unsigned.pdf");

            assertThat(reply.getAdditionalData()).containsEntry("storedDocumentUrl", "http://storage/unsigned.pdf");
        }
    }
}
