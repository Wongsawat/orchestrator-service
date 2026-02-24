package com.wpanther.orchestrator.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DocumentMetadata Tests")
class DocumentMetadataTest {

    @Nested
    @DisplayName("getMetadataValue()")
    class GetMetadataValueTests {

        @Test
        @DisplayName("returns value when key exists in metadata")
        void returnsValueForExistingKey() {
            Map<String, Object> meta = new HashMap<>();
            meta.put("invoiceNumber", "INV-001");
            DocumentMetadata dm = DocumentMetadata.builder().metadata(meta).build();

            assertThat(dm.getMetadataValue("invoiceNumber")).isEqualTo("INV-001");
        }

        @Test
        @DisplayName("returns null when key does not exist")
        void returnsNullForMissingKey() {
            Map<String, Object> meta = new HashMap<>();
            DocumentMetadata dm = DocumentMetadata.builder().metadata(meta).build();

            assertThat(dm.getMetadataValue("missing")).isNull();
        }

        @Test
        @DisplayName("returns null when metadata map is null")
        void returnsNullWhenMetadataIsNull() {
            DocumentMetadata dm = DocumentMetadata.builder().build();

            assertThat(dm.getMetadataValue("anyKey")).isNull();
        }
    }

    @Nested
    @DisplayName("addMetadataValue()")
    class AddMetadataValueTests {

        @Test
        @DisplayName("adds value to existing metadata map")
        void addsToExistingMetadata() {
            Map<String, Object> meta = new HashMap<>();
            meta.put("existing", "value");
            DocumentMetadata dm = DocumentMetadata.builder().metadata(meta).build();

            dm.addMetadataValue("newKey", "newValue");

            assertThat(dm.getMetadataValue("newKey")).isEqualTo("newValue");
            assertThat(dm.getMetadataValue("existing")).isEqualTo("value");
        }

        @Test
        @DisplayName("initializes metadata map when null and adds value")
        void initializesNullMetadata() {
            DocumentMetadata dm = DocumentMetadata.builder().build();

            dm.addMetadataValue("key", "value");

            assertThat(dm.getMetadata()).isNotNull();
            assertThat(dm.getMetadataValue("key")).isEqualTo("value");
        }

        @Test
        @DisplayName("overwrites existing key with new value")
        void overwritesExistingKey() {
            Map<String, Object> meta = new HashMap<>();
            meta.put("key", "original");
            DocumentMetadata dm = DocumentMetadata.builder().metadata(meta).build();

            dm.addMetadataValue("key", "updated");

            assertThat(dm.getMetadataValue("key")).isEqualTo("updated");
        }
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("builds with all fields set")
        void buildsWithAllFields() {
            DocumentMetadata dm = DocumentMetadata.builder()
                    .filePath("/path/to/file.xml")
                    .xmlContent("<xml/>")
                    .fileSize(1024L)
                    .mimeType("application/xml")
                    .checksum("abc123")
                    .build();

            assertThat(dm.getFilePath()).isEqualTo("/path/to/file.xml");
            assertThat(dm.getXmlContent()).isEqualTo("<xml/>");
            assertThat(dm.getFileSize()).isEqualTo(1024L);
            assertThat(dm.getMimeType()).isEqualTo("application/xml");
            assertThat(dm.getChecksum()).isEqualTo("abc123");
        }
    }
}
