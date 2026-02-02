package com.wpanther.orchestrator.application.dto;

import com.wpanther.orchestrator.domain.model.enums.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * DTO for starting a new saga instance.
 */
public record StartSagaRequest(

        /**
         * The type of document to process.
         */
        @NotNull(message = "Document type is required")
        DocumentType documentType,

        /**
         * The external document identifier.
         */
        @NotBlank(message = "Document ID is required")
        String documentId,

        /**
         * Optional file path for the document.
         */
        String filePath,

        /**
         * Optional XML content of the document.
         */
        String xmlContent,

        /**
         * Optional metadata as key-value pairs.
         */
        Map<String, Object> metadata,

        /**
         * Optional file size in bytes.
         */
        Long fileSize,

        /**
         * Optional MIME type.
         */
        String mimeType,

        /**
         * Optional checksum for verification.
         */
        String checksum
) {
    /**
     * Creates a StartSagaRequest with minimal required fields.
     */
    public static StartSagaRequest of(DocumentType documentType, String documentId) {
        return new StartSagaRequest(documentType, documentId, null, null, null, null, null, null);
    }
}
