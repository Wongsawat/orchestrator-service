package com.wpanther.orchestrator.application.dto;

import com.wpanther.orchestrator.domain.model.enums.DocumentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StartSagaRequest Tests")
class StartSagaRequestTest {

    @Test
    @DisplayName("of() creates request with required fields and null optionals")
    void ofCreatesMinimalRequest() {
        StartSagaRequest request = StartSagaRequest.of(DocumentType.INVOICE, "doc-001");

        assertThat(request.documentType()).isEqualTo(DocumentType.INVOICE);
        assertThat(request.documentId()).isEqualTo("doc-001");
        assertThat(request.filePath()).isNull();
        assertThat(request.xmlContent()).isNull();
        assertThat(request.metadata()).isNull();
        assertThat(request.fileSize()).isNull();
        assertThat(request.mimeType()).isNull();
        assertThat(request.checksum()).isNull();
    }

    @Test
    @DisplayName("of() creates TAX_INVOICE request")
    void ofCreatesTaxInvoiceRequest() {
        StartSagaRequest request = StartSagaRequest.of(DocumentType.TAX_INVOICE, "tax-doc-001");

        assertThat(request.documentType()).isEqualTo(DocumentType.TAX_INVOICE);
        assertThat(request.documentId()).isEqualTo("tax-doc-001");
    }
}
