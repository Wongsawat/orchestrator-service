package com.wpanther.orchestrator.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.orchestrator.domain.model.SagaInstance;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.domain.model.SagaCommand;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publisher for saga commands to processing services via the outbox pattern.
 * Replaces the direct Kafka producer approach with transactional outbox writes.
 * Commands are written to the outbox table within the same transaction as the
 * domain state changes, ensuring atomicity. Debezium CDC reads the outbox table
 * and publishes commands to Kafka topics.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SagaCommandPublisher {

    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topics.saga-command-invoice:saga.command.invoice}")
    private String invoiceCommandTopic;

    @Value("${app.kafka.topics.saga-command-tax-invoice:saga.command.tax-invoice}")
    private String taxInvoiceCommandTopic;

    @Value("${app.kafka.topics.saga-command-xml-signing:saga.command.xml-signing}")
    private String xmlSigningCommandTopic;

    @Value("${app.kafka.topics.saga.command-signedxml-storage:saga.command.signedxml-storage}")
    private String signedXmlStorageCommandTopic;

    @Value("${app.kafka.topics.saga-command-invoice-pdf:saga.command.invoice-pdf}")
    private String invoicePdfCommandTopic;

    @Value("${app.kafka.topics.saga-command-tax-invoice-pdf:saga.command.tax-invoice-pdf}")
    private String taxInvoicePdfCommandTopic;

    @Value("${app.kafka.topics.saga-command-pdf-signing:saga.command.pdf-signing}")
    private String pdfSigningCommandTopic;

    @Value("${app.kafka.topics.saga-command-document-storage:saga.command.document-storage}")
    private String documentStorageCommandTopic;

    @Value("${app.saga.compensation.invoice:saga.compensation.invoice}")
    private String invoiceCompensationTopic;

    @Value("${app.saga.compensation.tax-invoice:saga.compensation.tax-invoice}")
    private String taxInvoiceCompensationTopic;

    @Value("${app.saga.compensation.xml-signing:saga.compensation.xml-signing}")
    private String xmlSigningCompensationTopic;

    @Value("${app.saga.compensation.signedxml-storage:saga.compensation.signedxml-storage}")
    private String signedXmlStorageCompensationTopic;

    @Value("${app.saga.compensation.invoice-pdf:saga.compensation.invoice-pdf}")
    private String invoicePdfCompensationTopic;

    @Value("${app.saga.compensation.tax-invoice-pdf:saga.compensation.tax-invoice-pdf}")
    private String taxInvoicePdfCompensationTopic;

    @Value("${app.saga.compensation.pdf-signing:saga.compensation.pdf-signing}")
    private String pdfSigningCompensationTopic;

    @Value("${app.saga.compensation.document-storage:saga.compensation.document-storage}")
    private String documentStorageCompensationTopic;

    @Value("${app.kafka.topics.saga-command-ebms-sending:saga.command.ebms-sending}")
    private String ebmsSendingCommandTopic;

    @Value("${app.saga.compensation.ebms-sending:saga.compensation.ebms-sending}")
    private String ebmsSendingCompensationTopic;

    @Value("${app.kafka.topics.saga-command-pdf-storage:saga.command.pdf-storage}")
    private String pdfStorageCommandTopic;

    @Value("${app.saga.compensation.pdf-storage:saga.compensation.pdf-storage}")
    private String pdfStorageCompensationTopic;

    /**
     * Publishes a ProcessInvoiceCommand to the invoice processing service.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishProcessInvoiceCommand(SagaInstance saga, String correlationId) {
        ProcessInvoiceCommand command = new ProcessInvoiceCommand(
            saga.getId(),
            SagaStep.PROCESS_INVOICE,
            correlationId,
            saga.getDocumentId(),
            getXmlContent(saga),
            getDocumentNumber(saga)
        );

        publishCommand(command, invoiceCommandTopic, saga, correlationId, "ProcessInvoiceCommand");

        log.debug("Published ProcessInvoiceCommand for saga {} to topic {}",
            saga.getId(), invoiceCommandTopic);
    }

    /**
     * Publishes a ProcessTaxInvoiceCommand to the tax invoice processing service.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishProcessTaxInvoiceCommand(SagaInstance saga, String correlationId) {
        ProcessTaxInvoiceCommand command = new ProcessTaxInvoiceCommand(
            saga.getId(),
            SagaStep.PROCESS_TAX_INVOICE,
            correlationId,
            saga.getDocumentId(),
            getXmlContent(saga),
            getDocumentNumber(saga)
        );

        publishCommand(command, taxInvoiceCommandTopic, saga, correlationId, "ProcessTaxInvoiceCommand");

        log.debug("Published ProcessTaxInvoiceCommand for saga {} to topic {}",
            saga.getId(), taxInvoiceCommandTopic);
    }

    /**
     * Publishes a StoreDocumentCommand to the document storage service.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishStoreDocumentCommand(SagaInstance saga, String correlationId) {
        String signedPdfUrl = getMetadataValue(saga, "signedPdfUrl");
        String signedDocumentId = getMetadataValue(saga, "signedDocumentId");
        String signatureLevel = getMetadataValue(saga, "signatureLevel");

        StoreDocumentCommand command = new StoreDocumentCommand(
            saga.getId(),
            SagaStep.STORE_DOCUMENT,
            correlationId,
            saga.getDocumentId(),
            getDocumentNumber(saga),
            saga.getDocumentType().getCode(),
            signedPdfUrl,
            signedDocumentId,
            signatureLevel
        );

        publishCommand(command, documentStorageCommandTopic, saga, correlationId, "StoreDocumentCommand");

        log.debug("Published StoreDocumentCommand for saga {} to topic {}",
            saga.getId(), documentStorageCommandTopic);
    }

    /**
     * Publishes a SendEbmsCommand to the ebMS sending service.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishSendEbmsCommand(SagaInstance saga, String correlationId) {
        String signedXmlUrl = getMetadataValue(saga, "signedXmlUrl");

        SendEbmsCommand command = new SendEbmsCommand(
            saga.getId(),
            SagaStep.SEND_EBMS,
            correlationId,
            saga.getDocumentId(),
            getDocumentNumber(saga),
            saga.getDocumentType().getCode(),
            signedXmlUrl
        );

        publishCommand(command, ebmsSendingCommandTopic, saga, correlationId, "SendEbmsCommand");

        log.debug("Published SendEbmsCommand for saga {} to topic {}",
            saga.getId(), ebmsSendingCommandTopic);
    }

    /**
     * Publishes a ProcessXmlSigningCommand to the xml-signing-service.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishSignXmlCommand(SagaInstance saga, String correlationId) {
        ProcessXmlSigningCommand command = new ProcessXmlSigningCommand(
            saga.getId(),
            SagaStep.SIGN_XML,
            correlationId,
            saga.getDocumentId(),
            getXmlContent(saga),
            getDocumentNumber(saga),
            saga.getDocumentType().getCode()
        );

        publishCommand(command, xmlSigningCommandTopic, saga, correlationId, "ProcessXmlSigningCommand");

        log.debug("Published ProcessXmlSigningCommand for saga {} to topic {}",
            saga.getId(), xmlSigningCommandTopic);
    }

    /**
     * Publishes a ProcessSignedXmlStorageCommand to the document-storage-service.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishSignedXmlStorageCommand(SagaInstance saga, String correlationId) {
        String signedXmlUrl = getMetadataValue(saga, "signedXmlUrl");

        ProcessSignedXmlStorageCommand command = new ProcessSignedXmlStorageCommand(
            saga.getId(),
            SagaStep.SIGNEDXML_STORAGE,
            correlationId,
            saga.getDocumentId(),
            signedXmlUrl,
            getDocumentNumber(saga),
            saga.getDocumentType().getCode()
        );

        publishCommand(command, signedXmlStorageCommandTopic, saga, correlationId, "ProcessSignedXmlStorageCommand");

        log.debug("Published ProcessSignedXmlStorageCommand for saga {} to topic {}",
            saga.getId(), signedXmlStorageCommandTopic);
    }

    /**
     * Publishes a ProcessInvoicePdfCommand to the invoice-pdf-generation-service.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishGenerateInvoicePdfCommand(SagaInstance saga, String correlationId) {
        String signedXmlUrl = getMetadataValue(saga, "signedXmlUrl");
        String invoiceDataJson = getMetadataValue(saga, "invoiceDataJson");

        ProcessInvoicePdfCommand command = new ProcessInvoicePdfCommand(
            saga.getId(),
            SagaStep.GENERATE_INVOICE_PDF,
            correlationId,
            saga.getDocumentId(),
            getInvoiceId(saga),
            getDocumentNumber(saga),
            signedXmlUrl,
            invoiceDataJson
        );

        publishCommand(command, invoicePdfCommandTopic, saga, correlationId, "ProcessInvoicePdfCommand");

        log.debug("Published ProcessInvoicePdfCommand for saga {} to topic {}",
            saga.getId(), invoicePdfCommandTopic);
    }

    /**
     * Publishes a ProcessTaxInvoicePdfCommand to the taxinvoice-pdf-generation-service.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishGenerateTaxInvoicePdfCommand(SagaInstance saga, String correlationId) {
        String signedXmlUrl = getMetadataValue(saga, "signedXmlUrl");
        String taxInvoiceDataJson = getMetadataValue(saga, "taxInvoiceDataJson");

        ProcessTaxInvoicePdfCommand command = new ProcessTaxInvoicePdfCommand(
            saga.getId(),
            SagaStep.GENERATE_TAX_INVOICE_PDF,
            correlationId,
            saga.getDocumentId(),
            getTaxInvoiceId(saga),
            getDocumentNumber(saga),
            signedXmlUrl,
            taxInvoiceDataJson
        );

        publishCommand(command, taxInvoicePdfCommandTopic, saga, correlationId, "ProcessTaxInvoicePdfCommand");

        log.debug("Published ProcessTaxInvoicePdfCommand for saga {} to topic {}",
            saga.getId(), taxInvoicePdfCommandTopic);
    }

    /**
     * Publishes a ProcessPdfSigningCommand to the pdf-signing-service.
     * The PDF URL comes from the PDF_STORAGE step reply (storedDocumentUrl).
     * Falls back to pdfUrl for backwards compatibility.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishSignPdfCommand(SagaInstance saga, String correlationId) {
        // PDF URL from PDF_STORAGE step (storedDocumentUrl)
        String pdfUrl = getMetadataValue(saga, "storedDocumentUrl");
        if (pdfUrl == null) {
            pdfUrl = getMetadataValue(saga, "pdfUrl");
        }

        ProcessPdfSigningCommand command = new ProcessPdfSigningCommand(
            saga.getId(),
            SagaStep.SIGN_PDF,
            correlationId,
            saga.getDocumentId(),
            getDocumentNumber(saga),
            saga.getDocumentType().getCode(),
            pdfUrl
        );

        publishCommand(command, pdfSigningCommandTopic, saga, correlationId, "ProcessPdfSigningCommand");

        log.debug("Published ProcessPdfSigningCommand for saga {} to topic {}",
            saga.getId(), pdfSigningCommandTopic);
    }

    /**
     * Publishes a ProcessPdfStorageCommand to the document-storage-service (PDF_STORAGE step).
     * This step stores the unsigned tax invoice PDF from MinIO into document-storage-service.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishPdfStorageCommand(SagaInstance saga, String correlationId) {
        String pdfUrl = getMetadataValue(saga, "pdfUrl");
        Long pdfSize = getMetadataLongValue(saga, "pdfSize");

        ProcessPdfStorageCommand command = new ProcessPdfStorageCommand(
            saga.getId(),
            SagaStep.PDF_STORAGE,
            correlationId,
            saga.getDocumentId(),
            getDocumentNumber(saga),
            saga.getDocumentType().getCode(),
            pdfUrl,
            pdfSize
        );

        publishCommand(command, pdfStorageCommandTopic, saga, correlationId, "ProcessPdfStorageCommand");

        log.debug("Published ProcessPdfStorageCommand for saga {} to topic {}",
            saga.getId(), pdfStorageCommandTopic);
    }

    /**
     * Publishes a command for the current saga step.
     * Routes to the appropriate topic based on document type and step.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishCommandForStep(SagaInstance saga, SagaStep step, String correlationId) {
        switch (step) {
            case PROCESS_INVOICE:
                publishProcessInvoiceCommand(saga, correlationId);
                break;
            case PROCESS_TAX_INVOICE:
                publishProcessTaxInvoiceCommand(saga, correlationId);
                break;
            case SIGN_XML:
                publishSignXmlCommand(saga, correlationId);
                break;
            case SIGNEDXML_STORAGE:
                publishSignedXmlStorageCommand(saga, correlationId);
                break;
            case GENERATE_INVOICE_PDF:
                publishGenerateInvoicePdfCommand(saga, correlationId);
                break;
            case GENERATE_TAX_INVOICE_PDF:
                publishGenerateTaxInvoicePdfCommand(saga, correlationId);
                break;
            case PDF_STORAGE:
                publishPdfStorageCommand(saga, correlationId);
                break;
            case SIGN_PDF:
                publishSignPdfCommand(saga, correlationId);
                break;
            case STORE_DOCUMENT:
                publishStoreDocumentCommand(saga, correlationId);
                break;
            case SEND_EBMS:
                publishSendEbmsCommand(saga, correlationId);
                break;
            default:
                log.warn("No command publisher configured for step {}", step);
        }
    }

    /**
     * Publishes a compensation command to rollback a completed step.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishCompensationCommand(SagaInstance saga, SagaStep stepToCompensate, String correlationId) {
        String compensationTopic = switch (stepToCompensate) {
            case STORE_DOCUMENT -> documentStorageCompensationTopic;
            case PROCESS_INVOICE -> invoiceCompensationTopic;
            case PROCESS_TAX_INVOICE -> taxInvoiceCompensationTopic;
            case SIGN_XML -> xmlSigningCompensationTopic;
            case SIGNEDXML_STORAGE -> signedXmlStorageCompensationTopic;
            case GENERATE_INVOICE_PDF -> invoicePdfCompensationTopic;
            case GENERATE_TAX_INVOICE_PDF -> taxInvoicePdfCompensationTopic;
            case PDF_STORAGE -> pdfStorageCompensationTopic;
            case SIGN_PDF -> pdfSigningCompensationTopic;
            case SEND_EBMS -> ebmsSendingCompensationTopic;
            default -> {
                boolean isInvoice = saga.getDocumentType().name().equals("INVOICE");
                yield isInvoice ? invoiceCompensationTopic : taxInvoiceCompensationTopic;
            }
        };

        CompensationCommand command = new CompensationCommand(
            saga.getId(),
            stepToCompensate,
            correlationId,
            stepToCompensate.getCode(),
            saga.getDocumentId(),
            saga.getDocumentType().getCode()
        );

        Map<String, String> headers = new HashMap<>();
        headers.put("sagaId", saga.getId());
        headers.put("correlationId", correlationId);
        headers.put("documentType", saga.getDocumentType().name());
        headers.put("commandType", "CompensationCommand");
        headers.put("compensation", "true");

        String headersJson = toJson(headers);

        outboxService.saveWithRouting(
            command,
            "SagaInstance",
            saga.getId(),
            compensationTopic,
            correlationId,
            headersJson
        );

        log.info("Published CompensationCommand for saga {} to compensate step {} on topic {}",
            saga.getId(), stepToCompensate, compensationTopic);
    }

    private void publishCommand(SagaCommand command, String topic, SagaInstance saga,
                                String correlationId, String commandType) {
        Map<String, String> headers = new HashMap<>();
        headers.put("sagaId", saga.getId());
        headers.put("correlationId", correlationId);
        headers.put("documentType", saga.getDocumentType().name());
        headers.put("commandType", commandType);

        String headersJson = toJson(headers);

        outboxService.saveWithRouting(
            command,
            "SagaInstance",
            saga.getId(),
            topic,
            correlationId,
            headersJson
        );
    }

    private String getDocumentNumber(SagaInstance saga) {
        return getMetadataValue(saga, "documentNumber");
    }

    private String getInvoiceId(SagaInstance saga) {
        return getMetadataValue(saga, "invoiceId");
    }

    private String getTaxInvoiceId(SagaInstance saga) {
        return getMetadataValue(saga, "taxInvoiceId");
    }

    private String toJson(Map<String, String> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize headers to JSON", e);
            return null;
        }
    }

    /**
     * Helper method to safely extract a metadata value from a saga instance.
     * Handles null DocumentMetadata and null metadata map gracefully.
     *
     * @param saga the saga instance
     * @param key the metadata key to extract
     * @return the metadata value as a String, or null if not found
     */
    /**
     * Helper method to safely extract the XML content from a saga instance.
     * Handles null DocumentMetadata gracefully.
     *
     * @param saga the saga instance
     * @return the XML content, or null if DocumentMetadata is null
     */
    private String getXmlContent(SagaInstance saga) {
        return saga.getDocumentMetadata() != null
                ? saga.getDocumentMetadata().getXmlContent()
                : null;
    }

    /**
     * Helper method to safely extract a metadata value from a saga instance.
     * Handles null DocumentMetadata and null metadata map gracefully.
     *
     * @param saga the saga instance
     * @param key the metadata key to extract
     * @return the metadata value as a String, or null if not found
     */
    private String getMetadataValue(SagaInstance saga, String key) {
        if (saga.getDocumentMetadata() == null ||
            saga.getDocumentMetadata().getMetadata() == null) {
            return null;
        }
        Object value = saga.getDocumentMetadata().getMetadata().get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * Helper method to safely extract a Long metadata value from a saga instance.
     *
     * @param saga the saga instance
     * @param key the metadata key to extract
     * @return the metadata value as a Long, or null if not found or not a number
     */
    private Long getMetadataLongValue(SagaInstance saga, String key) {
        String stringValue = getMetadataValue(saga, key);
        if (stringValue == null) {
            return null;
        }
        try {
            return Long.parseLong(stringValue);
        } catch (NumberFormatException e) {
            log.warn("Metadata value '{}' for key '{}' is not a valid Long", stringValue, key);
            return null;
        }
    }

    /**
     * Command for invoice processing service.
     */
    @Getter
    public static class ProcessInvoiceCommand extends SagaCommand {
        private static final long serialVersionUID = 1L;

        @JsonProperty("documentId")
        private final String documentId;

        @JsonProperty("xmlContent")
        private final String xmlContent;

        @JsonProperty("documentNumber")
        private final String documentNumber;

        public ProcessInvoiceCommand(String sagaId, SagaStep sagaStep, String correlationId,
                                     String documentId, String xmlContent, String documentNumber) {
            super(sagaId, sagaStep, correlationId);
            this.documentId = documentId;
            this.xmlContent = xmlContent;
            this.documentNumber = documentNumber;
        }

        @JsonCreator
        public ProcessInvoiceCommand(
                @JsonProperty("eventId") UUID eventId,
                @JsonProperty("occurredAt") Instant occurredAt,
                @JsonProperty("eventType") String eventType,
                @JsonProperty("version") int version,
                @JsonProperty("sagaId") String sagaId,
                @JsonProperty("sagaStep") SagaStep sagaStep,
                @JsonProperty("correlationId") String correlationId,
                @JsonProperty("documentId") String documentId,
                @JsonProperty("xmlContent") String xmlContent,
                @JsonProperty("documentNumber") String documentNumber) {
            super(eventId, occurredAt, eventType, version, sagaId, sagaStep, correlationId);
            this.documentId = documentId;
            this.xmlContent = xmlContent;
            this.documentNumber = documentNumber;
        }
    }

    /**
     * Command for tax invoice processing service.
     */
    @Getter
    public static class ProcessTaxInvoiceCommand extends SagaCommand {
        private static final long serialVersionUID = 1L;

        @JsonProperty("documentId")
        private final String documentId;

        @JsonProperty("xmlContent")
        private final String xmlContent;

        @JsonProperty("documentNumber")
        private final String documentNumber;

        public ProcessTaxInvoiceCommand(String sagaId, SagaStep sagaStep, String correlationId,
                                         String documentId, String xmlContent, String documentNumber) {
            super(sagaId, sagaStep, correlationId);
            this.documentId = documentId;
            this.xmlContent = xmlContent;
            this.documentNumber = documentNumber;
        }

        @JsonCreator
        public ProcessTaxInvoiceCommand(
                @JsonProperty("eventId") UUID eventId,
                @JsonProperty("occurredAt") Instant occurredAt,
                @JsonProperty("eventType") String eventType,
                @JsonProperty("version") int version,
                @JsonProperty("sagaId") String sagaId,
                @JsonProperty("sagaStep") SagaStep sagaStep,
                @JsonProperty("correlationId") String correlationId,
                @JsonProperty("documentId") String documentId,
                @JsonProperty("xmlContent") String xmlContent,
                @JsonProperty("documentNumber") String documentNumber) {
            super(eventId, occurredAt, eventType, version, sagaId, sagaStep, correlationId);
            this.documentId = documentId;
            this.xmlContent = xmlContent;
            this.documentNumber = documentNumber;
        }
    }

    /**
     * Command for compensating (rolling back) a completed step.
     */
    @Getter
    public static class CompensationCommand extends SagaCommand {
        private static final long serialVersionUID = 1L;

        @JsonProperty("stepToCompensate")
        private final String stepToCompensate;

        @JsonProperty("documentId")
        private final String documentId;

        @JsonProperty("documentType")
        private final String documentType;

        public CompensationCommand(String sagaId, SagaStep sagaStep, String correlationId,
                                   String stepToCompensate, String documentId, String documentType) {
            super(sagaId, sagaStep, correlationId);
            this.stepToCompensate = stepToCompensate;
            this.documentId = documentId;
            this.documentType = documentType;
        }

        @JsonCreator
        public CompensationCommand(
                @JsonProperty("eventId") UUID eventId,
                @JsonProperty("occurredAt") Instant occurredAt,
                @JsonProperty("eventType") String eventType,
                @JsonProperty("version") int version,
                @JsonProperty("sagaId") String sagaId,
                @JsonProperty("sagaStep") SagaStep sagaStep,
                @JsonProperty("correlationId") String correlationId,
                @JsonProperty("stepToCompensate") String stepToCompensate,
                @JsonProperty("documentId") String documentId,
                @JsonProperty("documentType") String documentType) {
            super(eventId, occurredAt, eventType, version, sagaId, sagaStep, correlationId);
            this.stepToCompensate = stepToCompensate;
            this.documentId = documentId;
            this.documentType = documentType;
        }
    }

    /**
     * Command for document storage service.
     */
    @Getter
    public static class StoreDocumentCommand extends SagaCommand {
        private static final long serialVersionUID = 1L;

        @JsonProperty("documentId")
        private final String documentId;

        @JsonProperty("documentNumber")
        private final String documentNumber;

        @JsonProperty("documentType")
        private final String documentType;

        @JsonProperty("signedPdfUrl")
        private final String signedPdfUrl;

        @JsonProperty("signedDocumentId")
        private final String signedDocumentId;

        @JsonProperty("signatureLevel")
        private final String signatureLevel;

        public StoreDocumentCommand(String sagaId, SagaStep sagaStep, String correlationId,
                                     String documentId, String documentNumber, String documentType,
                                     String signedPdfUrl, String signedDocumentId, String signatureLevel) {
            super(sagaId, sagaStep, correlationId);
            this.documentId = documentId;
            this.documentNumber = documentNumber;
            this.documentType = documentType;
            this.signedPdfUrl = signedPdfUrl;
            this.signedDocumentId = signedDocumentId;
            this.signatureLevel = signatureLevel;
        }

        @JsonCreator
        public StoreDocumentCommand(
                @JsonProperty("eventId") UUID eventId,
                @JsonProperty("occurredAt") Instant occurredAt,
                @JsonProperty("eventType") String eventType,
                @JsonProperty("version") int version,
                @JsonProperty("sagaId") String sagaId,
                @JsonProperty("sagaStep") SagaStep sagaStep,
                @JsonProperty("correlationId") String correlationId,
                @JsonProperty("documentId") String documentId,
                @JsonProperty("documentNumber") String documentNumber,
                @JsonProperty("documentType") String documentType,
                @JsonProperty("signedPdfUrl") String signedPdfUrl,
                @JsonProperty("signedDocumentId") String signedDocumentId,
                @JsonProperty("signatureLevel") String signatureLevel) {
            super(eventId, occurredAt, eventType, version, sagaId, sagaStep, correlationId);
            this.documentId = documentId;
            this.documentNumber = documentNumber;
            this.documentType = documentType;
            this.signedPdfUrl = signedPdfUrl;
            this.signedDocumentId = signedDocumentId;
            this.signatureLevel = signatureLevel;
        }
    }

    /**
     * Command for ebMS sending service.
     */
    @Getter
    public static class SendEbmsCommand extends SagaCommand {
        private static final long serialVersionUID = 1L;

        @JsonProperty("documentId")
        private final String documentId;

        @JsonProperty("documentNumber")
        private final String documentNumber;

        @JsonProperty("documentType")
        private final String documentType;

        @JsonProperty("signedXmlUrl")
        private final String signedXmlUrl;

        public SendEbmsCommand(String sagaId, SagaStep sagaStep, String correlationId,
                               String documentId, String documentNumber, String documentType,
                               String signedXmlUrl) {
            super(sagaId, sagaStep, correlationId);
            this.documentId = documentId;
            this.documentNumber = documentNumber;
            this.documentType = documentType;
            this.signedXmlUrl = signedXmlUrl;
        }

        @JsonCreator
        public SendEbmsCommand(
                @JsonProperty("eventId") UUID eventId,
                @JsonProperty("occurredAt") Instant occurredAt,
                @JsonProperty("eventType") String eventType,
                @JsonProperty("version") int version,
                @JsonProperty("sagaId") String sagaId,
                @JsonProperty("sagaStep") SagaStep sagaStep,
                @JsonProperty("correlationId") String correlationId,
                @JsonProperty("documentId") String documentId,
                @JsonProperty("documentNumber") String documentNumber,
                @JsonProperty("documentType") String documentType,
                @JsonProperty("signedXmlUrl") String signedXmlUrl) {
            super(eventId, occurredAt, eventType, version, sagaId, sagaStep, correlationId);
            this.documentId = documentId;
            this.documentNumber = documentNumber;
            this.documentType = documentType;
            this.signedXmlUrl = signedXmlUrl;
        }
    }

    /**
     * Command for xml-signing-service.
     */
    @Getter
    public static class ProcessXmlSigningCommand extends SagaCommand {
        private static final long serialVersionUID = 1L;

        @JsonProperty("documentId")
        private final String documentId;

        @JsonProperty("xmlContent")
        private final String xmlContent;

        @JsonProperty("documentNumber")
        private final String documentNumber;

        @JsonProperty("documentType")
        private final String documentType;

        public ProcessXmlSigningCommand(String sagaId, SagaStep sagaStep, String correlationId,
                                           String documentId, String xmlContent, String documentNumber,
                                           String documentType) {
            super(sagaId, sagaStep, correlationId);
            this.documentId = documentId;
            this.xmlContent = xmlContent;
            this.documentNumber = documentNumber;
            this.documentType = documentType;
        }

        @JsonCreator
        public ProcessXmlSigningCommand(
                @JsonProperty("eventId") UUID eventId,
                @JsonProperty("occurredAt") Instant occurredAt,
                @JsonProperty("eventType") String eventType,
                @JsonProperty("version") int version,
                @JsonProperty("sagaId") String sagaId,
                @JsonProperty("sagaStep") SagaStep sagaStep,
                @JsonProperty("correlationId") String correlationId,
                @JsonProperty("documentId") String documentId,
                @JsonProperty("xmlContent") String xmlContent,
                @JsonProperty("documentNumber") String documentNumber,
                @JsonProperty("documentType") String documentType) {
            super(eventId, occurredAt, eventType, version, sagaId, sagaStep, correlationId);
            this.documentId = documentId;
            this.xmlContent = xmlContent;
            this.documentNumber = documentNumber;
            this.documentType = documentType;
        }
    }

    /**
     * Command for document-storage-service (signed XML).
     */
    @Getter
    public static class ProcessSignedXmlStorageCommand extends SagaCommand {
        private static final long serialVersionUID = 1L;

        @JsonProperty("documentId")
        private final String documentId;

        @JsonProperty("signedXmlUrl")
        private final String signedXmlUrl;

        @JsonProperty("documentNumber")
        private final String documentNumber;

        @JsonProperty("documentType")
        private final String documentType;

        public ProcessSignedXmlStorageCommand(String sagaId, SagaStep sagaStep, String correlationId,
                                              String documentId, String signedXmlUrl, String documentNumber,
                                              String documentType) {
            super(sagaId, sagaStep, correlationId);
            this.documentId = documentId;
            this.signedXmlUrl = signedXmlUrl;
            this.documentNumber = documentNumber;
            this.documentType = documentType;
        }

        @JsonCreator
        public ProcessSignedXmlStorageCommand(
                @JsonProperty("eventId") UUID eventId,
                @JsonProperty("occurredAt") Instant occurredAt,
                @JsonProperty("eventType") String eventType,
                @JsonProperty("version") int version,
                @JsonProperty("sagaId") String sagaId,
                @JsonProperty("sagaStep") SagaStep sagaStep,
                @JsonProperty("correlationId") String correlationId,
                @JsonProperty("documentId") String documentId,
                @JsonProperty("signedXmlUrl") String signedXmlUrl,
                @JsonProperty("documentNumber") String documentNumber,
                @JsonProperty("documentType") String documentType) {
            super(eventId, occurredAt, eventType, version, sagaId, sagaStep, correlationId);
            this.documentId = documentId;
            this.signedXmlUrl = signedXmlUrl;
            this.documentNumber = documentNumber;
            this.documentType = documentType;
        }
    }

    /**
     * Command for invoice-pdf-generation-service.
     */
    @Getter
    public static class ProcessInvoicePdfCommand extends SagaCommand {
        private static final long serialVersionUID = 1L;

        @JsonProperty("documentId")
        private final String documentId;

        @JsonProperty("invoiceId")
        private final String invoiceId;

        @JsonProperty("documentNumber")
        private final String documentNumber;

        @JsonProperty("signedXmlUrl")
        private final String signedXmlUrl;

        @JsonProperty("invoiceDataJson")
        private final String invoiceDataJson;

        public ProcessInvoicePdfCommand(String sagaId, SagaStep sagaStep, String correlationId,
                                         String documentId, String invoiceId, String documentNumber,
                                         String signedXmlUrl, String invoiceDataJson) {
            super(sagaId, sagaStep, correlationId);
            this.documentId = documentId;
            this.invoiceId = invoiceId;
            this.documentNumber = documentNumber;
            this.signedXmlUrl = signedXmlUrl;
            this.invoiceDataJson = invoiceDataJson;
        }

        @JsonCreator
        public ProcessInvoicePdfCommand(
                @JsonProperty("eventId") UUID eventId,
                @JsonProperty("occurredAt") Instant occurredAt,
                @JsonProperty("eventType") String eventType,
                @JsonProperty("version") int version,
                @JsonProperty("sagaId") String sagaId,
                @JsonProperty("sagaStep") SagaStep sagaStep,
                @JsonProperty("correlationId") String correlationId,
                @JsonProperty("documentId") String documentId,
                @JsonProperty("invoiceId") String invoiceId,
                @JsonProperty("documentNumber") String documentNumber,
                @JsonProperty("signedXmlUrl") String signedXmlUrl,
                @JsonProperty("invoiceDataJson") String invoiceDataJson) {
            super(eventId, occurredAt, eventType, version, sagaId, sagaStep, correlationId);
            this.documentId = documentId;
            this.invoiceId = invoiceId;
            this.documentNumber = documentNumber;
            this.signedXmlUrl = signedXmlUrl;
            this.invoiceDataJson = invoiceDataJson;
        }
    }

    /**
     * Command for taxinvoice-pdf-generation-service.
     */
    @Getter
    public static class ProcessTaxInvoicePdfCommand extends SagaCommand {
        private static final long serialVersionUID = 1L;

        @JsonProperty("documentId")
        private final String documentId;

        @JsonProperty("taxInvoiceId")
        private final String taxInvoiceId;

        @JsonProperty("taxInvoiceNumber")
        private final String taxInvoiceNumber;

        @JsonProperty("signedXmlUrl")
        private final String signedXmlUrl;

        @JsonProperty("taxInvoiceDataJson")
        private final String taxInvoiceDataJson;

        public ProcessTaxInvoicePdfCommand(String sagaId, SagaStep sagaStep, String correlationId,
                                            String documentId, String taxInvoiceId, String taxInvoiceNumber,
                                            String signedXmlUrl, String taxInvoiceDataJson) {
            super(sagaId, sagaStep, correlationId);
            this.documentId = documentId;
            this.taxInvoiceId = taxInvoiceId;
            this.taxInvoiceNumber = taxInvoiceNumber;
            this.signedXmlUrl = signedXmlUrl;
            this.taxInvoiceDataJson = taxInvoiceDataJson;
        }

        @JsonCreator
        public ProcessTaxInvoicePdfCommand(
                @JsonProperty("eventId") UUID eventId,
                @JsonProperty("occurredAt") Instant occurredAt,
                @JsonProperty("eventType") String eventType,
                @JsonProperty("version") int version,
                @JsonProperty("sagaId") String sagaId,
                @JsonProperty("sagaStep") SagaStep sagaStep,
                @JsonProperty("correlationId") String correlationId,
                @JsonProperty("documentId") String documentId,
                @JsonProperty("taxInvoiceId") String taxInvoiceId,
                @JsonProperty("taxInvoiceNumber") String taxInvoiceNumber,
                @JsonProperty("signedXmlUrl") String signedXmlUrl,
                @JsonProperty("taxInvoiceDataJson") String taxInvoiceDataJson) {
            super(eventId, occurredAt, eventType, version, sagaId, sagaStep, correlationId);
            this.documentId = documentId;
            this.taxInvoiceId = taxInvoiceId;
            this.taxInvoiceNumber = taxInvoiceNumber;
            this.signedXmlUrl = signedXmlUrl;
            this.taxInvoiceDataJson = taxInvoiceDataJson;
        }
    }

    /**
     * Command for pdf-signing-service.
     * Uses @JsonProperty("pdfUrl") to match pdf-signing-service's ProcessPdfSigningCommand.
     */
    @Getter
    public static class ProcessPdfSigningCommand extends SagaCommand {
        private static final long serialVersionUID = 1L;

        @JsonProperty("documentId")
        private final String documentId;

        @JsonProperty("documentNumber")
        private final String documentNumber;

        @JsonProperty("documentType")
        private final String documentType;

        @JsonProperty("pdfUrl")
        private final String pdfUrl;

        public ProcessPdfSigningCommand(String sagaId, SagaStep sagaStep, String correlationId,
                                        String documentId, String documentNumber, String documentType,
                                        String pdfUrl) {
            super(sagaId, sagaStep, correlationId);
            this.documentId = documentId;
            this.documentNumber = documentNumber;
            this.documentType = documentType;
            this.pdfUrl = pdfUrl;
        }

        @JsonCreator
        public ProcessPdfSigningCommand(
                @JsonProperty("eventId") UUID eventId,
                @JsonProperty("occurredAt") Instant occurredAt,
                @JsonProperty("eventType") String eventType,
                @JsonProperty("version") int version,
                @JsonProperty("sagaId") String sagaId,
                @JsonProperty("sagaStep") SagaStep sagaStep,
                @JsonProperty("correlationId") String correlationId,
                @JsonProperty("documentId") String documentId,
                @JsonProperty("documentNumber") String documentNumber,
                @JsonProperty("documentType") String documentType,
                @JsonProperty("pdfUrl") String pdfUrl) {
            super(eventId, occurredAt, eventType, version, sagaId, sagaStep, correlationId);
            this.documentId = documentId;
            this.documentNumber = documentNumber;
            this.documentType = documentType;
            this.pdfUrl = pdfUrl;
        }
    }

    /**
     * Command for document-storage-service (PDF_STORAGE step).
     * Requests storage of an unsigned tax invoice PDF from MinIO.
     */
    @Getter
    public static class ProcessPdfStorageCommand extends SagaCommand {
        private static final long serialVersionUID = 1L;

        @JsonProperty("documentId")
        private final String documentId;

        @JsonProperty("documentNumber")
        private final String documentNumber;

        @JsonProperty("documentType")
        private final String documentType;

        @JsonProperty("pdfUrl")
        private final String pdfUrl;

        @JsonProperty("pdfSize")
        private final Long pdfSize;

        public ProcessPdfStorageCommand(String sagaId, SagaStep sagaStep, String correlationId,
                                        String documentId, String documentNumber, String documentType,
                                        String pdfUrl, Long pdfSize) {
            super(sagaId, sagaStep, correlationId);
            this.documentId = documentId;
            this.documentNumber = documentNumber;
            this.documentType = documentType;
            this.pdfUrl = pdfUrl;
            this.pdfSize = pdfSize;
        }

        @JsonCreator
        public ProcessPdfStorageCommand(
                @JsonProperty("eventId") UUID eventId,
                @JsonProperty("occurredAt") Instant occurredAt,
                @JsonProperty("eventType") String eventType,
                @JsonProperty("version") int version,
                @JsonProperty("sagaId") String sagaId,
                @JsonProperty("sagaStep") SagaStep sagaStep,
                @JsonProperty("correlationId") String correlationId,
                @JsonProperty("documentId") String documentId,
                @JsonProperty("documentNumber") String documentNumber,
                @JsonProperty("documentType") String documentType,
                @JsonProperty("pdfUrl") String pdfUrl,
                @JsonProperty("pdfSize") Long pdfSize) {
            super(eventId, occurredAt, eventType, version, sagaId, sagaStep, correlationId);
            this.documentId = documentId;
            this.documentNumber = documentNumber;
            this.documentType = documentType;
            this.pdfUrl = pdfUrl;
            this.pdfSize = pdfSize;
        }
    }
}
