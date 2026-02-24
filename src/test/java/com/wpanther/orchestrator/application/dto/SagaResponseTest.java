package com.wpanther.orchestrator.application.dto;

import com.wpanther.orchestrator.domain.model.DocumentMetadata;
import com.wpanther.orchestrator.domain.model.SagaCommandRecord;
import com.wpanther.orchestrator.domain.model.SagaInstance;
import com.wpanther.orchestrator.domain.model.enums.DocumentType;
import com.wpanther.saga.domain.enums.SagaStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SagaResponse Tests")
class SagaResponseTest {

    private SagaInstance createSaga() {
        SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-001",
                DocumentMetadata.builder().xmlContent("<xml/>").build());
        saga.start();
        return saga;
    }

    @Nested
    @DisplayName("fromDomain()")
    class FromDomainTests {

        @Test
        @DisplayName("creates SagaResponse from SagaInstance with IN_PROGRESS status")
        void fromDomainInProgress() {
            SagaInstance saga = createSaga();

            SagaResponse response = SagaResponse.fromDomain(saga);

            assertThat(response.id()).isEqualTo(saga.getId());
            assertThat(response.documentType()).isEqualTo("INVOICE");
            assertThat(response.documentId()).isEqualTo("doc-001");
            assertThat(response.status()).isEqualTo("IN_PROGRESS");
            assertThat(response.currentStep()).isNotNull();
            assertThat(response.createdAt()).isNotNull();
            assertThat(response.updatedAt()).isNotNull();
            assertThat(response.completedAt()).isNull();
            assertThat(response.errorMessage()).isNull();
            assertThat(response.retryCount()).isZero();
            assertThat(response.commandHistory()).isNotNull();
        }

        @Test
        @DisplayName("includes empty command history for new saga")
        void fromDomainWithEmptyCommandHistory() {
            SagaInstance saga = createSaga();

            SagaResponse response = SagaResponse.fromDomain(saga);

            assertThat(response.commandHistory()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("currentStep reflects initial step set by create()")
        void fromDomainInitialStep() {
            SagaInstance saga = SagaInstance.create(DocumentType.TAX_INVOICE, "doc-002",
                    DocumentMetadata.builder().xmlContent("<xml/>").build());

            SagaResponse response = SagaResponse.fromDomain(saga);

            // create() sets the initial step based on document type
            assertThat(response.currentStep()).isNotNull();
            assertThat(response.status()).isEqualTo("STARTED");
        }

        @Test
        @DisplayName("maps TAX_INVOICE document type correctly")
        void fromDomainTaxInvoice() {
            SagaInstance saga = SagaInstance.create(DocumentType.TAX_INVOICE, "doc-tax",
                    DocumentMetadata.builder().xmlContent("<xml/>").build());
            saga.start();

            SagaResponse response = SagaResponse.fromDomain(saga);

            assertThat(response.documentType()).isEqualTo("TAX_INVOICE");
            assertThat(response.documentId()).isEqualTo("doc-tax");
        }
    }

    @Nested
    @DisplayName("CommandSummary.fromDomain()")
    class CommandSummaryFromDomainTests {

        @Test
        @DisplayName("creates CommandSummary from SagaCommandRecord")
        void fromDomain() {
            SagaCommandRecord cmd = SagaCommandRecord.create("saga-001", "ProcessInvoiceCommand",
                    SagaStep.PROCESS_INVOICE, null);

            SagaResponse.CommandSummary summary = SagaResponse.CommandSummary.fromDomain(cmd);

            assertThat(summary.id()).isEqualTo(cmd.getId());
            assertThat(summary.commandType()).isEqualTo("ProcessInvoiceCommand");
            assertThat(summary.targetStep()).isEqualTo("PROCESS_INVOICE");
            assertThat(summary.status()).isEqualTo("PENDING");
            assertThat(summary.createdAt()).isNotNull();
            assertThat(summary.errorMessage()).isNull();
        }

        @Test
        @DisplayName("creates CommandSummary from a sent command")
        void fromDomainSentCommand() {
            SagaCommandRecord cmd = SagaCommandRecord.create("saga-001", "ProcessTaxInvoiceCommand",
                    SagaStep.PROCESS_TAX_INVOICE, null);
            cmd.markAsSent();

            SagaResponse.CommandSummary summary = SagaResponse.CommandSummary.fromDomain(cmd);

            assertThat(summary.status()).isEqualTo("SENT");
            assertThat(summary.targetStep()).isEqualTo("PROCESS_TAX_INVOICE");
        }
    }
}
