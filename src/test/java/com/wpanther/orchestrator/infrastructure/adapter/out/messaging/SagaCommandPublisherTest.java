package com.wpanther.orchestrator.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.wpanther.orchestrator.infrastructure.adapter.out.messaging.SagaCommandPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.orchestrator.domain.model.DocumentMetadata;
import com.wpanther.orchestrator.domain.model.SagaInstance;
import com.wpanther.orchestrator.domain.model.enums.DocumentType;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SagaCommandPublisher Tests")
class SagaCommandPublisherTest {

    @Mock private OutboxService outboxService;

    private SagaCommandPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new SagaCommandPublisher(outboxService, new ObjectMapper());
        // Inject topic values via reflection (normally done by @Value)
        ReflectionTestUtils.setField(publisher, "invoiceCommandTopic", "saga.command.invoice");
        ReflectionTestUtils.setField(publisher, "taxInvoiceCommandTopic", "saga.command.tax-invoice");
        ReflectionTestUtils.setField(publisher, "xmlSigningCommandTopic", "saga.command.xml-signing");
        ReflectionTestUtils.setField(publisher, "signedXmlStorageCommandTopic", "saga.command.signedxml-storage");
        ReflectionTestUtils.setField(publisher, "invoicePdfCommandTopic", "saga.command.invoice-pdf");
        ReflectionTestUtils.setField(publisher, "taxInvoicePdfCommandTopic", "saga.command.tax-invoice-pdf");
        ReflectionTestUtils.setField(publisher, "pdfSigningCommandTopic", "saga.command.pdf-signing");
        ReflectionTestUtils.setField(publisher, "documentStorageCommandTopic", "saga.command.document-storage");
        ReflectionTestUtils.setField(publisher, "ebmsSendingCommandTopic", "saga.command.ebms-sending");
        ReflectionTestUtils.setField(publisher, "pdfStorageCommandTopic", "saga.command.pdf-storage");
        // Compensation topics
        ReflectionTestUtils.setField(publisher, "invoiceCompensationTopic", "saga.compensation.invoice");
        ReflectionTestUtils.setField(publisher, "taxInvoiceCompensationTopic", "saga.compensation.tax-invoice");
        ReflectionTestUtils.setField(publisher, "xmlSigningCompensationTopic", "saga.compensation.xml-signing");
        ReflectionTestUtils.setField(publisher, "signedXmlStorageCompensationTopic", "saga.compensation.signedxml-storage");
        ReflectionTestUtils.setField(publisher, "invoicePdfCompensationTopic", "saga.compensation.invoice-pdf");
        ReflectionTestUtils.setField(publisher, "taxInvoicePdfCompensationTopic", "saga.compensation.tax-invoice-pdf");
        ReflectionTestUtils.setField(publisher, "pdfSigningCompensationTopic", "saga.compensation.pdf-signing");
        ReflectionTestUtils.setField(publisher, "documentStorageCompensationTopic", "saga.compensation.document-storage");
        ReflectionTestUtils.setField(publisher, "ebmsSendingCompensationTopic", "saga.compensation.ebms-sending");
        ReflectionTestUtils.setField(publisher, "pdfStorageCompensationTopic", "saga.compensation.pdf-storage");
    }

    private SagaInstance createInvoiceSaga() {
        Map<String, Object> meta = new HashMap<>();
        meta.put("documentNumber", "INV-001");
        meta.put("invoiceId", "uuid-001");
        DocumentMetadata dm = DocumentMetadata.builder()
                .xmlContent("<xml/>").metadata(meta).build();
        SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-001", dm);
        saga.start();
        return saga;
    }

    private SagaInstance createTaxInvoiceSaga() {
        Map<String, Object> meta = new HashMap<>();
        meta.put("documentNumber", "TAX-001");
        meta.put("taxInvoiceId", "tax-uuid-001");
        DocumentMetadata dm = DocumentMetadata.builder()
                .xmlContent("<xml/>").metadata(meta).build();
        SagaInstance saga = SagaInstance.create(DocumentType.TAX_INVOICE, "doc-002", dm);
        saga.start();
        return saga;
    }

    private SagaInstance createSagaWithMetadata(DocumentType type, Map<String, Object> meta) {
        DocumentMetadata dm = DocumentMetadata.builder()
                .xmlContent("<xml/>").metadata(meta).build();
        SagaInstance saga = SagaInstance.create(type, "doc-003", dm);
        saga.start();
        return saga;
    }

    @Nested
    @DisplayName("publishProcessInvoiceCommand()")
    class PublishProcessInvoiceCommandTests {

        @Test
        @DisplayName("publishes command to invoice topic via outbox")
        void publishesToInvoiceTopic() {
            SagaInstance saga = createInvoiceSaga();

            publisher.publishProcessInvoiceCommand(saga, "corr-001");

            verify(outboxService).saveWithRouting(
                    any(), eq("SagaInstance"), eq(saga.getId()),
                    eq("saga.command.invoice"), eq("corr-001"), any());
        }

        @Test
        @DisplayName("publishes command with null metadata")
        void publishesWithNullMetadata() {
            DocumentMetadata dm = DocumentMetadata.builder().xmlContent("<xml/>").build();
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-null", dm);
            saga.start();

            publisher.publishProcessInvoiceCommand(saga, "corr-001");

            verify(outboxService).saveWithRouting(any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("publishProcessTaxInvoiceCommand()")
    class PublishProcessTaxInvoiceCommandTests {

        @Test
        @DisplayName("publishes command to tax invoice topic")
        void publishesToTaxInvoiceTopic() {
            SagaInstance saga = createTaxInvoiceSaga();

            publisher.publishProcessTaxInvoiceCommand(saga, "corr-001");

            verify(outboxService).saveWithRouting(
                    any(), eq("SagaInstance"), eq(saga.getId()),
                    eq("saga.command.tax-invoice"), eq("corr-001"), any());
        }
    }

    @Nested
    @DisplayName("publishSignXmlCommand()")
    class PublishSignXmlCommandTests {

        @Test
        @DisplayName("publishes command to xml-signing topic")
        void publishesToXmlSigningTopic() {
            SagaInstance saga = createInvoiceSaga();

            publisher.publishSignXmlCommand(saga, "corr-001");

            verify(outboxService).saveWithRouting(
                    any(), eq("SagaInstance"), eq(saga.getId()),
                    eq("saga.command.xml-signing"), eq("corr-001"), any());
        }
    }

    @Nested
    @DisplayName("publishSignedXmlStorageCommand()")
    class PublishSignedXmlStorageCommandTests {

        @Test
        @DisplayName("publishes with signedXmlUrl from metadata")
        void publishesWithSignedXmlUrl() {
            Map<String, Object> meta = new HashMap<>();
            meta.put("signedXmlUrl", "http://storage/signed.xml");
            SagaInstance saga = createSagaWithMetadata(DocumentType.INVOICE, meta);

            publisher.publishSignedXmlStorageCommand(saga, "corr-001");

            verify(outboxService).saveWithRouting(
                    any(), eq("SagaInstance"), eq(saga.getId()),
                    eq("saga.command.signedxml-storage"), eq("corr-001"), any());
        }

        @Test
        @DisplayName("publishes without signedXmlUrl when not in metadata")
        void publishesWithoutSignedXmlUrl() {
            SagaInstance saga = createInvoiceSaga();

            publisher.publishSignedXmlStorageCommand(saga, "corr-001");

            verify(outboxService).saveWithRouting(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("publishes with null metadata")
        void publishesWithNullMetadata() {
            DocumentMetadata dm = DocumentMetadata.builder().xmlContent("<xml/>").build();
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-null", dm);
            saga.start();

            publisher.publishSignedXmlStorageCommand(saga, "corr-001");

            verify(outboxService).saveWithRouting(any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("publishGenerateInvoicePdfCommand()")
    class PublishGenerateInvoicePdfCommandTests {

        @Test
        @DisplayName("publishes with signedXmlUrl and invoiceDataJson")
        void publishesWithFullMetadata() {
            Map<String, Object> meta = new HashMap<>();
            meta.put("signedXmlUrl", "http://storage/signed.xml");
            meta.put("invoiceDataJson", "{\"invoice\":\"data\"}");
            meta.put("invoiceId", "uuid-001");
            SagaInstance saga = createSagaWithMetadata(DocumentType.INVOICE, meta);

            publisher.publishGenerateInvoicePdfCommand(saga, "corr-001");

            verify(outboxService).saveWithRouting(
                    any(), eq("SagaInstance"), eq(saga.getId()),
                    eq("saga.command.invoice-pdf"), eq("corr-001"), any());
        }

        @Test
        @DisplayName("publishes with null metadata")
        void publishesWithNullMetadata() {
            DocumentMetadata dm = DocumentMetadata.builder().xmlContent("<xml/>").build();
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-null", dm);
            saga.start();

            publisher.publishGenerateInvoicePdfCommand(saga, "corr-001");

            verify(outboxService).saveWithRouting(any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("publishGenerateTaxInvoicePdfCommand()")
    class PublishGenerateTaxInvoicePdfCommandTests {

        @Test
        @DisplayName("publishes with taxInvoiceDataJson from metadata")
        void publishesWithFullMetadata() {
            Map<String, Object> meta = new HashMap<>();
            meta.put("signedXmlUrl", "http://storage/signed.xml");
            meta.put("taxInvoiceDataJson", "{\"taxInvoice\":\"data\"}");
            meta.put("taxInvoiceId", "tax-uuid-001");
            SagaInstance saga = createSagaWithMetadata(DocumentType.TAX_INVOICE, meta);

            publisher.publishGenerateTaxInvoicePdfCommand(saga, "corr-001");

            verify(outboxService).saveWithRouting(
                    any(), eq("SagaInstance"), eq(saga.getId()),
                    eq("saga.command.tax-invoice-pdf"), eq("corr-001"), any());
        }

        @Test
        @DisplayName("publishes when metadata does not have taxInvoiceDataJson")
        void publishesWithMissingMetadataKeys() {
            SagaInstance saga = createTaxInvoiceSaga();

            publisher.publishGenerateTaxInvoicePdfCommand(saga, "corr-001");

            verify(outboxService).saveWithRouting(any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("publishSignPdfCommand()")
    class PublishSignPdfCommandTests {

        @Test
        @DisplayName("publishes with storedDocumentUrl (tax invoice path)")
        void publishesWithStoredDocumentUrl() {
            Map<String, Object> meta = new HashMap<>();
            meta.put("storedDocumentUrl", "http://storage/unsigned.pdf");
            SagaInstance saga = createSagaWithMetadata(DocumentType.TAX_INVOICE, meta);

            publisher.publishSignPdfCommand(saga, "corr-001");

            verify(outboxService).saveWithRouting(
                    any(), eq("SagaInstance"), eq(saga.getId()),
                    eq("saga.command.pdf-signing"), eq("corr-001"), any());
        }

        @Test
        @DisplayName("publishes with pdfUrl (invoice path, fallback)")
        void publishesWithPdfUrl() {
            Map<String, Object> meta = new HashMap<>();
            meta.put("pdfUrl", "http://storage/unsigned.pdf");
            SagaInstance saga = createSagaWithMetadata(DocumentType.INVOICE, meta);

            publisher.publishSignPdfCommand(saga, "corr-001");

            verify(outboxService).saveWithRouting(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("publishes without pdf url when metadata missing")
        void publishesWithNullMetadata() {
            DocumentMetadata dm = DocumentMetadata.builder().xmlContent("<xml/>").build();
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-null", dm);
            saga.start();

            publisher.publishSignPdfCommand(saga, "corr-001");

            verify(outboxService).saveWithRouting(any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("publishPdfStorageCommand()")
    class PublishPdfStorageCommandTests {

        @Test
        @DisplayName("publishes with pdfUrl and pdfSize from metadata")
        void publishesWithFullMetadata() {
            Map<String, Object> meta = new HashMap<>();
            meta.put("pdfUrl", "http://minio/tax.pdf");
            meta.put("pdfSize", "10240");
            SagaInstance saga = createSagaWithMetadata(DocumentType.TAX_INVOICE, meta);

            publisher.publishPdfStorageCommand(saga, "corr-001");

            verify(outboxService).saveWithRouting(
                    any(), eq("SagaInstance"), eq(saga.getId()),
                    eq("saga.command.pdf-storage"), eq("corr-001"), any());
        }

        @Test
        @DisplayName("publishes without pdfUrl when not in metadata")
        void publishesWithoutPdfUrl() {
            SagaInstance saga = createTaxInvoiceSaga();

            publisher.publishPdfStorageCommand(saga, "corr-001");

            verify(outboxService).saveWithRouting(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("publishes with empty metadata map (no pdfUrl or pdfSize)")
        void publishesWithEmptyMetadata() {
            SagaInstance saga = SagaInstance.create(DocumentType.TAX_INVOICE, "doc-empty",
                    DocumentMetadata.builder().xmlContent("<xml/>").metadata(new java.util.HashMap<>()).build());
            saga.start();

            publisher.publishPdfStorageCommand(saga, "corr-001");

            verify(outboxService).saveWithRouting(any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("publishStoreDocumentCommand()")
    class PublishStoreDocumentCommandTests {

        @Test
        @DisplayName("publishes with signedPdfUrl, signedDocumentId and signatureLevel from metadata")
        void publishesWithFullMetadata() {
            Map<String, Object> meta = new HashMap<>();
            meta.put("signedPdfUrl", "http://storage/signed.pdf");
            meta.put("signedDocumentId", "signed-doc-001");
            meta.put("signatureLevel", "PAdES-BASELINE-T");
            SagaInstance saga = createSagaWithMetadata(DocumentType.INVOICE, meta);

            publisher.publishStoreDocumentCommand(saga, "corr-001");

            verify(outboxService).saveWithRouting(
                    any(), eq("SagaInstance"), eq(saga.getId()),
                    eq("saga.command.document-storage"), eq("corr-001"), any());
        }

        @Test
        @DisplayName("publishes with empty metadata map (no signedPdfUrl)")
        void publishesWithEmptyMetadata() {
            DocumentMetadata dm = DocumentMetadata.builder().xmlContent("<xml/>").metadata(new java.util.HashMap<>()).build();
            SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-empty", dm);
            saga.start();

            publisher.publishStoreDocumentCommand(saga, "corr-001");

            verify(outboxService).saveWithRouting(any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("publishSendEbmsCommand()")
    class PublishSendEbmsCommandTests {

        @Test
        @DisplayName("publishes with signedXmlUrl from metadata")
        void publishesWithSignedXmlUrl() {
            Map<String, Object> meta = new HashMap<>();
            meta.put("signedXmlUrl", "http://storage/signed.xml");
            SagaInstance saga = createSagaWithMetadata(DocumentType.INVOICE, meta);

            publisher.publishSendEbmsCommand(saga, "corr-001");

            verify(outboxService).saveWithRouting(
                    any(), eq("SagaInstance"), eq(saga.getId()),
                    eq("saga.command.ebms-sending"), eq("corr-001"), any());
        }

        @Test
        @DisplayName("publishes without signedXmlUrl when not in metadata")
        void publishesWithoutSignedXmlUrl() {
            SagaInstance saga = createInvoiceSaga();

            publisher.publishSendEbmsCommand(saga, "corr-001");

            verify(outboxService).saveWithRouting(any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("publishCommandForStep()")
    class PublishCommandForStepTests {

        @Test
        @DisplayName("routes PROCESS_INVOICE to invoice command")
        void routesProcessInvoice() {
            SagaInstance saga = createInvoiceSaga();

            publisher.publishCommandForStep(saga, SagaStep.PROCESS_INVOICE, "corr-001");

            verify(outboxService).saveWithRouting(any(), any(), any(), eq("saga.command.invoice"), any(), any());
        }

        @Test
        @DisplayName("routes PROCESS_TAX_INVOICE to tax invoice command")
        void routesProcessTaxInvoice() {
            SagaInstance saga = createTaxInvoiceSaga();

            publisher.publishCommandForStep(saga, SagaStep.PROCESS_TAX_INVOICE, "corr-001");

            verify(outboxService).saveWithRouting(any(), any(), any(), eq("saga.command.tax-invoice"), any(), any());
        }

        @Test
        @DisplayName("routes SIGN_XML to xml-signing command")
        void routesSignXml() {
            SagaInstance saga = createInvoiceSaga();

            publisher.publishCommandForStep(saga, SagaStep.SIGN_XML, "corr-001");

            verify(outboxService).saveWithRouting(any(), any(), any(), eq("saga.command.xml-signing"), any(), any());
        }

        @Test
        @DisplayName("routes SIGNEDXML_STORAGE to signedxml-storage command")
        void routesSignedXmlStorage() {
            SagaInstance saga = createInvoiceSaga();

            publisher.publishCommandForStep(saga, SagaStep.SIGNEDXML_STORAGE, "corr-001");

            verify(outboxService).saveWithRouting(any(), any(), any(), eq("saga.command.signedxml-storage"), any(), any());
        }

        @Test
        @DisplayName("routes GENERATE_INVOICE_PDF to invoice-pdf command")
        void routesGenerateInvoicePdf() {
            SagaInstance saga = createInvoiceSaga();

            publisher.publishCommandForStep(saga, SagaStep.GENERATE_INVOICE_PDF, "corr-001");

            verify(outboxService).saveWithRouting(any(), any(), any(), eq("saga.command.invoice-pdf"), any(), any());
        }

        @Test
        @DisplayName("routes GENERATE_TAX_INVOICE_PDF to tax-invoice-pdf command")
        void routesGenerateTaxInvoicePdf() {
            SagaInstance saga = createTaxInvoiceSaga();

            publisher.publishCommandForStep(saga, SagaStep.GENERATE_TAX_INVOICE_PDF, "corr-001");

            verify(outboxService).saveWithRouting(any(), any(), any(), eq("saga.command.tax-invoice-pdf"), any(), any());
        }

        @Test
        @DisplayName("routes PDF_STORAGE to pdf-storage command")
        void routesPdfStorage() {
            SagaInstance saga = createTaxInvoiceSaga();

            publisher.publishCommandForStep(saga, SagaStep.PDF_STORAGE, "corr-001");

            verify(outboxService).saveWithRouting(any(), any(), any(), eq("saga.command.pdf-storage"), any(), any());
        }

        @Test
        @DisplayName("routes SIGN_PDF to pdf-signing command")
        void routesSignPdf() {
            SagaInstance saga = createInvoiceSaga();

            publisher.publishCommandForStep(saga, SagaStep.SIGN_PDF, "corr-001");

            verify(outboxService).saveWithRouting(any(), any(), any(), eq("saga.command.pdf-signing"), any(), any());
        }

        @Test
        @DisplayName("routes STORE_DOCUMENT to document-storage command")
        void routesStoreDocument() {
            SagaInstance saga = createInvoiceSaga();

            publisher.publishCommandForStep(saga, SagaStep.STORE_DOCUMENT, "corr-001");

            verify(outboxService).saveWithRouting(any(), any(), any(), eq("saga.command.document-storage"), any(), any());
        }

        @Test
        @DisplayName("routes SEND_EBMS to ebms-sending command")
        void routesSendEbms() {
            SagaInstance saga = createInvoiceSaga();

            publisher.publishCommandForStep(saga, SagaStep.SEND_EBMS, "corr-001");

            verify(outboxService).saveWithRouting(any(), any(), any(), eq("saga.command.ebms-sending"), any(), any());
        }
    }

    @Nested
    @DisplayName("ProcessTaxInvoicePdfCommand DTO serialisation")
    class ProcessTaxInvoicePdfCommandSerialisationTests {

        @Test
        @DisplayName("serialises documentNumber key (not taxInvoiceNumber) to match taxinvoice-pdf-generation-service")
        void processTaxInvoicePdfCommand_serialisesDocumentNumberField() throws Exception {
            // taxinvoice-pdf-generation-service deserialises @JsonProperty("documentNumber"),
            // NOT "taxInvoiceNumber". Verify the orchestrator publishes the correct key.
            SagaCommandPublisher.ProcessTaxInvoicePdfCommand cmd =
                    new SagaCommandPublisher.ProcessTaxInvoicePdfCommand(
                            "saga-001",
                            SagaStep.GENERATE_TAX_INVOICE_PDF,
                            "corr-001",
                            "doc-001",
                            "taxinv-uuid",
                            "TIV-TEST-001",       // documentNumber
                            "http://minio/signed.xml",
                            "{}"
                    );

            ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
            String json = mapper.writeValueAsString(cmd);
            JsonNode node = mapper.readTree(json);

            assertThat(node.has("documentNumber"))
                    .as("JSON must contain 'documentNumber' key (not 'taxInvoiceNumber')")
                    .isTrue();
            assertThat(node.get("documentNumber").asText()).isEqualTo("TIV-TEST-001");
            assertThat(node.has("taxInvoiceNumber"))
                    .as("JSON must NOT contain legacy 'taxInvoiceNumber' key")
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("publishCompensationCommand()")
    class PublishCompensationCommandTests {

        @Test
        @DisplayName("compensates STORE_DOCUMENT step")
        void compensatesStoreDocument() {
            SagaInstance saga = createInvoiceSaga();

            publisher.publishCompensationCommand(saga, SagaStep.STORE_DOCUMENT, "corr-001");

            verify(outboxService).saveWithRouting(any(), any(), any(), eq("saga.compensation.document-storage"), any(), any());
        }

        @Test
        @DisplayName("compensates PROCESS_INVOICE step")
        void compensatesProcessInvoice() {
            SagaInstance saga = createInvoiceSaga();

            publisher.publishCompensationCommand(saga, SagaStep.PROCESS_INVOICE, "corr-001");

            verify(outboxService).saveWithRouting(any(), any(), any(), eq("saga.compensation.invoice"), any(), any());
        }

        @Test
        @DisplayName("compensates PROCESS_TAX_INVOICE step")
        void compensatesProcessTaxInvoice() {
            SagaInstance saga = createTaxInvoiceSaga();

            publisher.publishCompensationCommand(saga, SagaStep.PROCESS_TAX_INVOICE, "corr-001");

            verify(outboxService).saveWithRouting(any(), any(), any(), eq("saga.compensation.tax-invoice"), any(), any());
        }

        @Test
        @DisplayName("compensates SIGN_XML step")
        void compensatesSignXml() {
            SagaInstance saga = createInvoiceSaga();

            publisher.publishCompensationCommand(saga, SagaStep.SIGN_XML, "corr-001");

            verify(outboxService).saveWithRouting(any(), any(), any(), eq("saga.compensation.xml-signing"), any(), any());
        }

        @Test
        @DisplayName("compensates SIGNEDXML_STORAGE step")
        void compensatesSignedXmlStorage() {
            SagaInstance saga = createInvoiceSaga();

            publisher.publishCompensationCommand(saga, SagaStep.SIGNEDXML_STORAGE, "corr-001");

            verify(outboxService).saveWithRouting(any(), any(), any(), eq("saga.compensation.signedxml-storage"), any(), any());
        }

        @Test
        @DisplayName("compensates GENERATE_INVOICE_PDF step")
        void compensatesGenerateInvoicePdf() {
            SagaInstance saga = createInvoiceSaga();

            publisher.publishCompensationCommand(saga, SagaStep.GENERATE_INVOICE_PDF, "corr-001");

            verify(outboxService).saveWithRouting(any(), any(), any(), eq("saga.compensation.invoice-pdf"), any(), any());
        }

        @Test
        @DisplayName("compensates GENERATE_TAX_INVOICE_PDF step")
        void compensatesGenerateTaxInvoicePdf() {
            SagaInstance saga = createTaxInvoiceSaga();

            publisher.publishCompensationCommand(saga, SagaStep.GENERATE_TAX_INVOICE_PDF, "corr-001");

            verify(outboxService).saveWithRouting(any(), any(), any(), eq("saga.compensation.tax-invoice-pdf"), any(), any());
        }

        @Test
        @DisplayName("compensates PDF_STORAGE step")
        void compensatesPdfStorage() {
            SagaInstance saga = createTaxInvoiceSaga();

            publisher.publishCompensationCommand(saga, SagaStep.PDF_STORAGE, "corr-001");

            verify(outboxService).saveWithRouting(any(), any(), any(), eq("saga.compensation.pdf-storage"), any(), any());
        }

        @Test
        @DisplayName("compensates SIGN_PDF step")
        void compensatesSignPdf() {
            SagaInstance saga = createInvoiceSaga();

            publisher.publishCompensationCommand(saga, SagaStep.SIGN_PDF, "corr-001");

            verify(outboxService).saveWithRouting(any(), any(), any(), eq("saga.compensation.pdf-signing"), any(), any());
        }

        @Test
        @DisplayName("compensates SEND_EBMS step")
        void compensatesSendEbms() {
            SagaInstance saga = createInvoiceSaga();

            publisher.publishCompensationCommand(saga, SagaStep.SEND_EBMS, "corr-001");

            verify(outboxService).saveWithRouting(any(), any(), any(), eq("saga.compensation.ebms-sending"), any(), any());
        }
    }
}
