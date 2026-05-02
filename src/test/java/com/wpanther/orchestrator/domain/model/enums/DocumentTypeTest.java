package com.wpanther.orchestrator.domain.model.enums;

import com.wpanther.saga.domain.enums.SagaStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DocumentType Tests")
class DocumentTypeTest {

    @Nested
    @DisplayName("isSupported()")
    class IsSupportedTests {

        @Test
        void invoice_returnsTrue() {
            assertThat(DocumentType.INVOICE.isSupported()).isTrue();
        }

        @Test
        void taxInvoice_returnsTrue() {
            assertThat(DocumentType.TAX_INVOICE.isSupported()).isTrue();
        }

        @Test
        void abbreviatedTaxInvoice_returnsTrue() {
            assertThat(DocumentType.ABBREVIATED_TAX_INVOICE.isSupported()).isTrue();
        }

        @Test
        void receipt_returnsTrue() {
            assertThat(DocumentType.RECEIPT.isSupported()).isTrue();
        }

        @Test
        void debitNote_returnsTrue() {
            assertThat(DocumentType.DEBIT_NOTE.isSupported()).isTrue();
        }

        @Test
        void creditNote_returnsTrue() {
            assertThat(DocumentType.CREDIT_NOTE.isSupported()).isTrue();
        }

        @Test
        void cancellationNote_returnsTrue() {
            assertThat(DocumentType.CANCELLATION_NOTE.isSupported()).isTrue();
        }
    }

    @Nested
    @DisplayName("getInitialStep()")
    class GetInitialStepTests {

        @Test
        void invoice_returnsProcessInvoiceStep() {
            assertThat(DocumentType.INVOICE.getInitialStep())
                    .isEqualTo(SagaStep.PROCESS_INVOICE);
        }

        @Test
        void taxInvoice_returnsProcessTaxInvoiceStep() {
            assertThat(DocumentType.TAX_INVOICE.getInitialStep())
                    .isEqualTo(SagaStep.PROCESS_TAX_INVOICE);
        }

        @Test
        void abbreviatedTaxInvoice_returnsProcessAbbreviatedTaxInvoiceStep() {
            assertThat(DocumentType.ABBREVIATED_TAX_INVOICE.getInitialStep())
                    .isEqualTo(SagaStep.PROCESS_ABBREVIATED_TAX_INVOICE);
        }

        @Test
        void receipt_returnsProcessReceiptStep() {
            assertThat(DocumentType.RECEIPT.getInitialStep())
                    .isEqualTo(SagaStep.PROCESS_RECEIPT);
        }

        @Test
        void debitNote_returnsProcessDebitCreditNoteStep() {
            assertThat(DocumentType.DEBIT_NOTE.getInitialStep())
                    .isEqualTo(SagaStep.PROCESS_DEBIT_CREDIT_NOTE);
        }

        @Test
        void creditNote_returnsProcessDebitCreditNoteStep() {
            assertThat(DocumentType.CREDIT_NOTE.getInitialStep())
                    .isEqualTo(SagaStep.PROCESS_DEBIT_CREDIT_NOTE);
        }

        @Test
        void cancellationNote_returnsProcessCancellationNoteStep() {
            assertThat(DocumentType.CANCELLATION_NOTE.getInitialStep())
                    .isEqualTo(SagaStep.PROCESS_CANCELLATION_NOTE);
        }
    }

    @Nested
    @DisplayName("fromCode()")
    class FromCodeTests {

        @Test
        void withValidCode_returnsCorrectDocumentType() {
            assertThat(DocumentType.fromCode("invoice")).isEqualTo(DocumentType.INVOICE);
            assertThat(DocumentType.fromCode("tax-invoice")).isEqualTo(DocumentType.TAX_INVOICE);
            assertThat(DocumentType.fromCode("abbreviated-tax-invoice"))
                    .isEqualTo(DocumentType.ABBREVIATED_TAX_INVOICE);
            assertThat(DocumentType.fromCode("receipt")).isEqualTo(DocumentType.RECEIPT);
            assertThat(DocumentType.fromCode("debit-note")).isEqualTo(DocumentType.DEBIT_NOTE);
            assertThat(DocumentType.fromCode("credit-note")).isEqualTo(DocumentType.CREDIT_NOTE);
            assertThat(DocumentType.fromCode("cancellation-note"))
                    .isEqualTo(DocumentType.CANCELLATION_NOTE);
        }

        @Test
        void withInvalidCode_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> DocumentType.fromCode("invalid"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown document type code");
        }

        @Test
        void withNullCode_throwsIllegalArgumentException() {
            // fromCode handles null and throws IllegalArgumentException with descriptive message
            assertThatThrownBy(() -> DocumentType.fromCode(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown document type code: null");
        }
    }
}
