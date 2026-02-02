package com.wpanther.orchestrator.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Value object containing metadata about a document being processed.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class DocumentMetadata {

    /**
     * Original file path if available.
     */
    private String filePath;

    /**
     * XML content of the document.
     */
    private String xmlContent;

    /**
     * Additional metadata as key-value pairs.
     */
    private Map<String, Object> metadata;

    /**
     * Size of the document in bytes.
     */
    private Long fileSize;

    /**
     * MIME type of the document.
     */
    private String mimeType;

    /**
     * Checksum/hash for verification.
     */
    private String checksum;

    /**
     * Retrieves a metadata value by key.
     */
    public Object getMetadataValue(String key) {
        return metadata != null ? metadata.get(key) : null;
    }

    /**
     * Adds a metadata value.
     */
    public void addMetadataValue(String key, Object value) {
        if (this.metadata == null) {
            this.metadata = new java.util.HashMap<>();
        }
        this.metadata.put(key, value);
    }
}
