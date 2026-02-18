package com.wpanther.orchestrator.domain.model;

import com.wpanther.orchestrator.domain.model.enums.DocumentType;
import com.wpanther.saga.domain.enums.SagaStatus;
import com.wpanther.saga.domain.enums.SagaStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class SagaInstanceTest {

    @Nested
    @DisplayName("create()")
    class CreateTests {
        @Test
        void withInvoiceType_setsProcessInvoiceAsFirstStep() {
            DocumentMetadata metadata = createTestMetadata();
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-123", metadata);

            assertThat(saga.getCurrentStep()).isEqualTo(SagaStep.PROCESS_INVOICE);
            assertThat(saga.getStatus()).isEqualTo(SagaStatus.STARTED);
            assertThat(saga.getId()).isNotNull();
            assertThat(saga.getDocumentId()).isEqualTo("doc-123");
            assertThat(saga.getDocumentType()).isEqualTo(DocumentType.INVOICE);
            assertThat(saga.getDocumentMetadata()).isSameAs(metadata);
            assertThat(saga.getCreatedAt()).isNotNull();
            assertThat(saga.getUpdatedAt()).isNotNull();
        }

        @Test
        void withTaxInvoiceType_setsProcessTaxInvoiceAsFirstStep() {
            DocumentMetadata metadata = createTestMetadata();
            SagaInstance saga = SagaInstance.create(DocumentType.TAX_INVOICE, "doc-456", metadata);

            assertThat(saga.getCurrentStep()).isEqualTo(SagaStep.PROCESS_TAX_INVOICE);
            assertThat(saga.getDocumentType()).isEqualTo(DocumentType.TAX_INVOICE);
        }

        @Test
        void createsNewUniqueIdEachTime() {
            SagaInstance saga1 = SagaInstance.create(DocumentType.INVOICE, "doc-1", createTestMetadata());
            SagaInstance saga2 = SagaInstance.create(DocumentType.INVOICE, "doc-2", createTestMetadata());

            assertThat(saga1.getId()).isNotEqualTo(saga2.getId());
        }

        @Test
        void initializesRetryCountToZero() {
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-123", createTestMetadata());

            assertThat(saga.getRetryCount()).isZero();
        }

        @Test
        void initializesMaxRetriesToThree() {
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-123", createTestMetadata());

            assertThat(saga.getMaxRetries()).isEqualTo(3);
        }

        @Test
        void initializesEmptyCommandHistory() {
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-123", createTestMetadata());

            assertThat(saga.getCommandHistory()).isEmpty();
        }
    }

    @Nested
    @DisplayName("start()")
    class StartTests {
        @Test
        void fromStartedStatus_transitionsToInProgress() {
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-123", createTestMetadata());

            saga.start();

            assertThat(saga.getStatus()).isEqualTo(SagaStatus.IN_PROGRESS);
            assertThat(saga.getUpdatedAt()).isNotNull();
        }

        @Test
        void fromInProgressStatus_throwsException() {
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-123", createTestMetadata());
            saga.start();

            assertThatThrownBy(() -> saga.start())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STARTED");
        }

        @Test
        void fromCompletedStatus_throwsException() {
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-123", createTestMetadata());
            saga.start();
            saga.complete();

            assertThatThrownBy(() -> saga.start())
                .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void fromFailedStatus_throwsException() {
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-123", createTestMetadata());
            saga.start();
            saga.fail("test error");

            assertThatThrownBy(() -> saga.start())
                .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("advanceTo()")
    class AdvanceToTests {
        @Test
        void validTransition_updatesCurrentStep() {
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-123", createTestMetadata());
            saga.start();

            saga.advanceTo(SagaStep.SIGN_XML);

            assertThat(saga.getCurrentStep()).isEqualTo(SagaStep.SIGN_XML);
            assertThat(saga.getRetryCount()).isZero();
        }

        @Test
        void resetsRetryCount() {
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-123", createTestMetadata());
            saga.start();
            saga.incrementRetry();
            saga.incrementRetry();

            assertThat(saga.getRetryCount()).isEqualTo(2);

            saga.advanceTo(SagaStep.SIGN_XML);

            assertThat(saga.getRetryCount()).isZero();
        }

        @Test
        void fromStartedStatus_throwsException() {
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-123", createTestMetadata());

            assertThatThrownBy(() -> saga.advanceTo(SagaStep.SIGN_XML))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IN_PROGRESS");
        }

        @Test
        void fromCompletedStatus_throwsException() {
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-123", createTestMetadata());
            saga.start();
            saga.complete();

            assertThatThrownBy(() -> saga.advanceTo(SagaStep.SIGN_XML))
                .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("complete()")
    class CompleteTests {
        @Test
        void fromInProgress_setsCompletedStatus() {
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-123", createTestMetadata());
            saga.start();

            saga.complete();

            assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPLETED);
            assertThat(saga.getCompletedAt()).isNotNull();
            assertThat(saga.getUpdatedAt()).isNotNull();
        }

        @Test
        void fromStartedStatus_throwsException() {
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-123", createTestMetadata());

            assertThatThrownBy(() -> saga.complete())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IN_PROGRESS");
        }

        @Test
        void fromCompletedStatus_isIdempotent() {
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-123", createTestMetadata());
            saga.start();
            saga.complete();

            saga.complete(); // Should not throw - idempotent

            assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPLETED);
        }
    }

    @Nested
    @DisplayName("fail()")
    class FailTests {
        @Test
        void setsStatusAndErrorMessage() {
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-123", createTestMetadata());
            saga.start();

            saga.fail("Processing error");

            assertThat(saga.getStatus()).isEqualTo(SagaStatus.FAILED);
            assertThat(saga.getErrorMessage()).isEqualTo("Processing error");
            assertThat(saga.getUpdatedAt()).isNotNull();
        }

        @Test
        void nullErrorMessage_isAllowed() {
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-123", createTestMetadata());
            saga.start();

            saga.fail(null);

            assertThat(saga.getErrorMessage()).isNull();
        }

        @Test
        void canBeCalledMultipleTimes() {
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-123", createTestMetadata());
            saga.start();

            saga.fail("Error 1");
            saga.fail("Error 2");

            assertThat(saga.getErrorMessage()).isEqualTo("Error 2");
        }
    }

    @Nested
    @DisplayName("startCompensation()")
    class StartCompensationTests {
        @Test
        void setsStatusToCompensating() {
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-123", createTestMetadata());
            saga.start();

            saga.startCompensation();

            assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPENSATING);
            assertThat(saga.getUpdatedAt()).isNotNull();
        }

        @Test
        void canBeCalledFromAnyStatus() {
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-123", createTestMetadata());

            saga.startCompensation();

            assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPENSATING);
        }
    }

    @Nested
    @DisplayName("getNextStep()")
    class GetNextStepTests {
        @Test
        void forInvoicePath_returnsCorrectSequence() {
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-123", createTestMetadata());
            saga.start();

            // PROCESS_INVOICE -> SIGN_XML
            assertThat(saga.getNextStep()).isEqualTo(SagaStep.SIGN_XML);

            saga.advanceTo(SagaStep.SIGN_XML);
            // SIGN_XML -> SIGNEDXML_STORAGE
            assertThat(saga.getNextStep()).isEqualTo(SagaStep.SIGNEDXML_STORAGE);

            saga.advanceTo(SagaStep.SIGNEDXML_STORAGE);
            // SIGNEDXML_STORAGE -> GENERATE_INVOICE_PDF
            assertThat(saga.getNextStep()).isEqualTo(SagaStep.GENERATE_INVOICE_PDF);

            saga.advanceTo(SagaStep.GENERATE_INVOICE_PDF);
            // GENERATE_INVOICE_PDF -> SIGN_PDF
            assertThat(saga.getNextStep()).isEqualTo(SagaStep.SIGN_PDF);

            saga.advanceTo(SagaStep.SIGN_PDF);
            // SIGN_PDF -> STORE_DOCUMENT
            assertThat(saga.getNextStep()).isEqualTo(SagaStep.STORE_DOCUMENT);

            saga.advanceTo(SagaStep.STORE_DOCUMENT);
            // STORE_DOCUMENT -> SEND_EBMS
            assertThat(saga.getNextStep()).isEqualTo(SagaStep.SEND_EBMS);

            saga.advanceTo(SagaStep.SEND_EBMS);
            // SEND_EBMS -> null (complete)
            assertThat(saga.getNextStep()).isNull();
        }

        @Test
        void forTaxInvoicePath_returnsCorrectSequence() {
            SagaInstance saga = SagaInstance.create(DocumentType.TAX_INVOICE, "doc-456", createTestMetadata());
            saga.start();

            // PROCESS_TAX_INVOICE -> SIGN_XML
            assertThat(saga.getNextStep()).isEqualTo(SagaStep.SIGN_XML);

            saga.advanceTo(SagaStep.SIGN_XML);
            // SIGN_XML -> SIGNEDXML_STORAGE
            assertThat(saga.getNextStep()).isEqualTo(SagaStep.SIGNEDXML_STORAGE);

            saga.advanceTo(SagaStep.SIGNEDXML_STORAGE);
            // SIGNEDXML_STORAGE -> GENERATE_TAX_INVOICE_PDF (different from invoice)
            assertThat(saga.getNextStep()).isEqualTo(SagaStep.GENERATE_TAX_INVOICE_PDF);

            saga.advanceTo(SagaStep.GENERATE_TAX_INVOICE_PDF);
            // GENERATE_TAX_INVOICE_PDF -> PDF_STORAGE (tax invoice has extra storage step)
            assertThat(saga.getNextStep()).isEqualTo(SagaStep.PDF_STORAGE);

            saga.advanceTo(SagaStep.PDF_STORAGE);
            // PDF_STORAGE -> SIGN_PDF
            assertThat(saga.getNextStep()).isEqualTo(SagaStep.SIGN_PDF);

            saga.advanceTo(SagaStep.SIGN_PDF);
            // SIGN_PDF -> STORE_DOCUMENT
            assertThat(saga.getNextStep()).isEqualTo(SagaStep.STORE_DOCUMENT);

            saga.advanceTo(SagaStep.STORE_DOCUMENT);
            // STORE_DOCUMENT -> SEND_EBMS
            assertThat(saga.getNextStep()).isEqualTo(SagaStep.SEND_EBMS);

            saga.advanceTo(SagaStep.SEND_EBMS);
            // SEND_EBMS -> null (complete)
            assertThat(saga.getNextStep()).isNull();
        }
    }

    @Nested
    @DisplayName("getCompensationStep()")
    class GetCompensationStepTests {
        @Test
        void fromSignXml_returnsProcessInvoiceForInvoice() {
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-123", createTestMetadata());
            saga.start();
            saga.advanceTo(SagaStep.SIGN_XML);

            // Compensation from SIGN_XML goes back to PROCESS_INVOICE
            assertThat(saga.getCompensationStep()).isEqualTo(SagaStep.PROCESS_INVOICE);
        }

        @Test
        void fromSignXml_returnsProcessTaxInvoiceForTaxInvoice() {
            SagaInstance saga = SagaInstance.create(DocumentType.TAX_INVOICE, "doc-456", createTestMetadata());
            saga.start();
            saga.advanceTo(SagaStep.SIGN_XML);

            // Compensation from SIGN_XML goes back to PROCESS_TAX_INVOICE
            assertThat(saga.getCompensationStep()).isEqualTo(SagaStep.PROCESS_TAX_INVOICE);
        }

        @Test
        void fromSignedXmlStorage_returnsSignXml() {
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-123", createTestMetadata());
            saga.start();
            saga.advanceTo(SagaStep.SIGN_XML);
            saga.advanceTo(SagaStep.SIGNEDXML_STORAGE);

            assertThat(saga.getCompensationStep()).isEqualTo(SagaStep.SIGN_XML);
        }

        @Test
        void fromGenerateInvoicePdf_returnsSignedXmlStorage() {
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-123", createTestMetadata());
            saga.start();
            saga.advanceTo(SagaStep.SIGN_XML);
            saga.advanceTo(SagaStep.SIGNEDXML_STORAGE);
            saga.advanceTo(SagaStep.GENERATE_INVOICE_PDF);

            assertThat(saga.getCompensationStep()).isEqualTo(SagaStep.SIGNEDXML_STORAGE);
        }

        @Test
        void fromPdfStorage_returnsGenerateTaxInvoicePdf() {
            SagaInstance saga = SagaInstance.create(DocumentType.TAX_INVOICE, "doc-456", createTestMetadata());
            saga.start();
            saga.advanceTo(SagaStep.SIGN_XML);
            saga.advanceTo(SagaStep.SIGNEDXML_STORAGE);
            saga.advanceTo(SagaStep.GENERATE_TAX_INVOICE_PDF);
            saga.advanceTo(SagaStep.PDF_STORAGE);

            assertThat(saga.getCompensationStep()).isEqualTo(SagaStep.GENERATE_TAX_INVOICE_PDF);
        }

        @Test
        void fromSignPdf_returnsPdfStorageForTaxInvoice() {
            SagaInstance saga = SagaInstance.create(DocumentType.TAX_INVOICE, "doc-456", createTestMetadata());
            saga.start();
            saga.advanceTo(SagaStep.SIGN_XML);
            saga.advanceTo(SagaStep.SIGNEDXML_STORAGE);
            saga.advanceTo(SagaStep.GENERATE_TAX_INVOICE_PDF);
            saga.advanceTo(SagaStep.PDF_STORAGE);
            saga.advanceTo(SagaStep.SIGN_PDF);

            assertThat(saga.getCompensationStep()).isEqualTo(SagaStep.PDF_STORAGE);
        }

        @Test
        void fromStoreDocument_returnsSignPdf() {
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-123", createTestMetadata());
            saga.start();
            saga.advanceTo(SagaStep.SIGN_XML);
            saga.advanceTo(SagaStep.SIGNEDXML_STORAGE);
            saga.advanceTo(SagaStep.GENERATE_INVOICE_PDF);
            saga.advanceTo(SagaStep.SIGN_PDF);
            saga.advanceTo(SagaStep.STORE_DOCUMENT);

            assertThat(saga.getCompensationStep()).isEqualTo(SagaStep.SIGN_PDF);
        }

        @Test
        void fromSendEbms_returnsStoreDocument() {
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-123", createTestMetadata());
            saga.start();
            saga.advanceTo(SagaStep.SIGN_XML);
            saga.advanceTo(SagaStep.SIGNEDXML_STORAGE);
            saga.advanceTo(SagaStep.GENERATE_INVOICE_PDF);
            saga.advanceTo(SagaStep.SIGN_PDF);
            saga.advanceTo(SagaStep.STORE_DOCUMENT);
            saga.advanceTo(SagaStep.SEND_EBMS);

            assertThat(saga.getCompensationStep()).isEqualTo(SagaStep.STORE_DOCUMENT);
        }

        @Test
        void fromProcessInvoice_returnsNull() {
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-123", createTestMetadata());
            saga.start();

            assertThat(saga.getCompensationStep()).isNull();
        }
    }

    @Nested
    @DisplayName("incrementRetry()")
    class IncrementRetryTests {
        @Test
        void incrementsRetryCount() {
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-123", createTestMetadata());

            saga.incrementRetry();
            assertThat(saga.getRetryCount()).isEqualTo(1);

            saga.incrementRetry();
            assertThat(saga.getRetryCount()).isEqualTo(2);

            saga.incrementRetry();
            assertThat(saga.getRetryCount()).isEqualTo(3);
        }

        @Test
        void updatesUpdatedAt() {
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-123", createTestMetadata());
            Instant originalUpdatedAt = saga.getUpdatedAt();

            // Small delay to ensure timestamp difference
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                // Ignore
            }

            saga.incrementRetry();

            assertThat(saga.getUpdatedAt()).isAfter(originalUpdatedAt);
        }
    }

    @Nested
    @DisplayName("hasExceededMaxRetries()")
    class HasExceededMaxRetriesTests {
        @Test
        void whenBelowMaxRetries_returnsFalse() {
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-123", createTestMetadata());
            saga.incrementRetry();
            saga.incrementRetry();

            assertThat(saga.hasExceededMaxRetries()).isFalse();
        }

        @Test
        void whenAtMaxRetries_returnsTrue() {
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-123", createTestMetadata());
            saga.incrementRetry();
            saga.incrementRetry();
            saga.incrementRetry();

            assertThat(saga.hasExceededMaxRetries()).isTrue();
        }

        @Test
        void whenAboveMaxRetries_returnsTrue() {
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-123", createTestMetadata());
            for (int i = 0; i < 5; i++) {
                saga.incrementRetry();
            }

            assertThat(saga.hasExceededMaxRetries()).isTrue();
        }

        @Test
        void whenZeroRetries_returnsFalse() {
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-123", createTestMetadata());

            assertThat(saga.hasExceededMaxRetries()).isFalse();
        }
    }

    @Nested
    @DisplayName("addCommand() and getCommandHistory()")
    class CommandHistoryTests {
        @Test
        void addCommand_addsToHistory() {
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-123", createTestMetadata());
            SagaCommandRecord command = SagaCommandRecord.create(
                "saga-1",
                "TestCommand",
                SagaStep.PROCESS_INVOICE,
                "test-payload"
            );

            saga.addCommand(command);

            assertThat(saga.getCommandHistory()).hasSize(1);
            assertThat(saga.getCommandHistory().get(0)).isSameAs(command);
        }

        @Test
        void getCommandHistory_returnsUnmodifiableList() {
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-123", createTestMetadata());

            List<SagaCommandRecord> history = saga.getCommandHistory();

            assertThatThrownBy(() -> history.add(SagaCommandRecord.create(
                "saga-1", "TestCommand", SagaStep.PROCESS_INVOICE, "payload"
            )))
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void addCommand_updatesUpdatedAt() {
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-123", createTestMetadata());
            Instant originalUpdatedAt = saga.getUpdatedAt();

            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                // Ignore
            }

            saga.addCommand(SagaCommandRecord.create(
                "saga-1", "TestCommand", SagaStep.PROCESS_INVOICE, "payload"
            ));

            assertThat(saga.getUpdatedAt()).isAfter(originalUpdatedAt);
        }

        @Test
        void multipleCommands_addedInOrder() {
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-123", createTestMetadata());
            SagaCommandRecord cmd1 = SagaCommandRecord.create(
                "saga-1", "Command1", SagaStep.PROCESS_INVOICE, "payload1");
            SagaCommandRecord cmd2 = SagaCommandRecord.create(
                "saga-1", "Command2", SagaStep.SIGN_XML, "payload2");

            saga.addCommand(cmd1);
            saga.addCommand(cmd2);

            assertThat(saga.getCommandHistory()).containsExactly(cmd1, cmd2);
        }
    }

    @Nested
    @DisplayName("Setters - Lombok generated")
    class SetterTests {
        @Test
        void setId_updatesId() {
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-123", createTestMetadata());

            saga.setId("new-id");

            assertThat(saga.getId()).isEqualTo("new-id");
        }

        @Test
        void setDocumentId_updatesDocumentId() {
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-123", createTestMetadata());

            saga.setDocumentId("new-doc-id");

            assertThat(saga.getDocumentId()).isEqualTo("new-doc-id");
        }

        @Test
        void setMaxRetries_updatesMaxRetries() {
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-123", createTestMetadata());

            saga.setMaxRetries(5);

            assertThat(saga.getMaxRetries()).isEqualTo(5);
        }

        @Test
        void customMaxRetries_affectsHasExceededMaxRetries() {
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-123", createTestMetadata());
            saga.setMaxRetries(5);

            for (int i = 0; i < 5; i++) {
                saga.incrementRetry();
            }

            assertThat(saga.hasExceededMaxRetries()).isTrue();
        }
    }

    // Helper methods
    private DocumentMetadata createTestMetadata() {
        return DocumentMetadata.builder()
            .xmlContent("<test/>")
            .build();
    }
}
