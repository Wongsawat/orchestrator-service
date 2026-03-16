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
     *
     * @param key the metadata key to retrieve
     * @return the metadata value, or null if the key doesn't exist or metadata is null
     */
    public Object getMetadataValue(String key) {
        if (metadata == null) {
            return null;
        }
        return metadata.get(key);
    }

    /**
     * Adds a metadata value.
     * <p><b>Warning:</b> This method mutates the DocumentMetadata instance.
     * Use {@link #withMetadataValue(String, Object)} for immutable updates instead.
     * This method is retained for internal use within saga orchestration where
     * performance is critical and the instance is not shared across threads.</p>
     */
    public void addMetadataValue(String key, Object value) {
        if (this.metadata == null) {
            this.metadata = new java.util.HashMap<>();
        }
        this.metadata.put(key, value);
    }

    /**
     * Returns a new DocumentMetadata instance with an additional metadata value.
     * <p>This method creates a new instance without modifying the current one,
     * making it safe for use in concurrent scenarios or when immutability is desired.</p>
     *
     * @param key   the metadata key
     * @param value the metadata value
     * @return a new DocumentMetadata instance with the added metadata
     */
    public DocumentMetadata withMetadataValue(String key, Object value) {
        java.util.Map<String, Object> newMap = this.metadata != null
            ? new java.util.HashMap<>(this.metadata)
            : new java.util.HashMap<>();
        newMap.put(key, value);
        return DocumentMetadata.builder()
                .filePath(this.filePath)
                .xmlContent(this.xmlContent)
                .metadata(newMap)
                .fileSize(this.fileSize)
                .mimeType(this.mimeType)
                .checksum(this.checksum)
                .build();
    }
}
