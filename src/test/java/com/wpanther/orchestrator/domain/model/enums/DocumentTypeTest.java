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
        void receipt_returnsFalse() {
            assertThat(DocumentType.RECEIPT.isSupported()).isFalse();
        }

        @Test
        void debitNote_returnsFalse() {
            assertThat(DocumentType.DEBIT_NOTE.isSupported()).isFalse();
        }

        @Test
        void creditNote_returnsFalse() {
            assertThat(DocumentType.CREDIT_NOTE.isSupported()).isFalse();
        }

        @Test
        void cancellationNote_returnsFalse() {
            assertThat(DocumentType.CANCELLATION_NOTE.isSupported()).isFalse();
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
        void abbreviatedTaxInvoice_returnsProcessTaxInvoiceStep() {
            assertThat(DocumentType.ABBREVIATED_TAX_INVOICE.getInitialStep())
                    .isEqualTo(SagaStep.PROCESS_TAX_INVOICE);
        }

        @Test
        void receipt_throwsUnsupportedOperationException() {
            assertThatThrownBy(() -> DocumentType.RECEIPT.getInitialStep())
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("RECEIPT")
                    .hasMessageContaining("not yet supported");
        }

        @Test
        void debitNote_throwsUnsupportedOperationException() {
            assertThatThrownBy(() -> DocumentType.DEBIT_NOTE.getInitialStep())
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("DEBIT_NOTE")
                    .hasMessageContaining("not yet supported");
        }

        @Test
        void creditNote_throwsUnsupportedOperationException() {
            assertThatThrownBy(() -> DocumentType.CREDIT_NOTE.getInitialStep())
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("CREDIT_NOTE")
                    .hasMessageContaining("not yet supported");
        }

        @Test
        void cancellationNote_throwsUnsupportedOperationException() {
            assertThatThrownBy(() -> DocumentType.CANCELLATION_NOTE.getInitialStep())
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("CANCELLATION_NOTE")
                    .hasMessageContaining("not yet supported");
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
