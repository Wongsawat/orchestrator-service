package com.wpanther.orchestrator.infrastructure.messaging.producer;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.orchestrator.domain.model.SagaInstance;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.domain.model.IntegrationEvent;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

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

    @Value("${app.kafka.topics.saga-command-document-storage:saga.command.document-storage}")
    private String documentStorageCommandTopic;

    @Value("${app.saga.compensation.invoice:saga.compensation.invoice}")
    private String invoiceCompensationTopic;

    @Value("${app.saga.compensation.tax-invoice:saga.compensation.tax-invoice}")
    private String taxInvoiceCompensationTopic;

    @Value("${app.saga.compensation.document-storage:saga.compensation.document-storage}")
    private String documentStorageCompensationTopic;

    @Value("${app.kafka.topics.saga-command-ebms-sending:saga.command.ebms-sending}")
    private String ebmsSendingCommandTopic;

    @Value("${app.saga.compensation.ebms-sending:saga.compensation.ebms-sending}")
    private String ebmsSendingCompensationTopic;

    /**
     * Publishes a ProcessInvoiceCommand to the invoice processing service.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishProcessInvoiceCommand(SagaInstance saga, String correlationId) {
        ProcessInvoiceCommand command = new ProcessInvoiceCommand(
            saga.getId(),
            SagaStep.PROCESS_INVOICE.getCode(),
            correlationId,
            saga.getDocumentId(),
            saga.getDocumentMetadata().getXmlContent(),
            getInvoiceNumber(saga)
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
            SagaStep.PROCESS_TAX_INVOICE.getCode(),
            correlationId,
            saga.getDocumentId(),
            saga.getDocumentMetadata().getXmlContent(),
            getInvoiceNumber(saga)
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
        Map<String, Object> metadata = saga.getDocumentMetadata() != null
                ? saga.getDocumentMetadata().getMetadata()
                : Map.of();

        String signedPdfUrl = metadata.get("signedPdfUrl") != null
                ? metadata.get("signedPdfUrl").toString() : null;
        String signedDocumentId = metadata.get("signedDocumentId") != null
                ? metadata.get("signedDocumentId").toString() : null;
        String signatureLevel = metadata.get("signatureLevel") != null
                ? metadata.get("signatureLevel").toString() : null;

        StoreDocumentCommand command = new StoreDocumentCommand(
            saga.getId(),
            SagaStep.STORE_DOCUMENT.getCode(),
            correlationId,
            saga.getDocumentId(),
            getInvoiceNumber(saga),
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
        Map<String, Object> metadata = saga.getDocumentMetadata() != null
                ? saga.getDocumentMetadata().getMetadata()
                : Map.of();

        String signedXmlContent = metadata.get("signedXmlContent") != null
                ? metadata.get("signedXmlContent").toString() : null;

        SendEbmsCommand command = new SendEbmsCommand(
            saga.getId(),
            SagaStep.SEND_EBMS.getCode(),
            correlationId,
            saga.getDocumentId(),
            getInvoiceNumber(saga),
            saga.getDocumentType().getCode(),
            signedXmlContent
        );

        publishCommand(command, ebmsSendingCommandTopic, saga, correlationId, "SendEbmsCommand");

        log.debug("Published SendEbmsCommand for saga {} to topic {}",
            saga.getId(), ebmsSendingCommandTopic);
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
            case SEND_EBMS -> ebmsSendingCompensationTopic;
            default -> {
                boolean isInvoice = saga.getDocumentType().name().equals("INVOICE");
                yield isInvoice ? invoiceCompensationTopic : taxInvoiceCompensationTopic;
            }
        };

        CompensationCommand command = new CompensationCommand(
            saga.getId(),
            "COMPENSATE_" + stepToCompensate.getCode(),
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

    private void publishCommand(IntegrationEvent command, String topic, SagaInstance saga,
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

    private String getInvoiceNumber(SagaInstance saga) {
        if (saga.getDocumentMetadata() != null && saga.getDocumentMetadata().getMetadata() != null) {
            Object invoiceNumber = saga.getDocumentMetadata().getMetadata().get("invoiceNumber");
            if (invoiceNumber != null) {
                return invoiceNumber.toString();
            }
        }
        return null;
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
     * Command for invoice processing service.
     */
    @Getter
    public static class ProcessInvoiceCommand extends IntegrationEvent {
        private static final long serialVersionUID = 1L;

        @JsonProperty("sagaId")
        private final String sagaId;

        @JsonProperty("sagaStep")
        private final String sagaStep;

        @JsonProperty("correlationId")
        private final String correlationId;

        @JsonProperty("documentId")
        private final String documentId;

        @JsonProperty("xmlContent")
        private final String xmlContent;

        @JsonProperty("invoiceNumber")
        private final String invoiceNumber;

        public ProcessInvoiceCommand(String sagaId, String sagaStep, String correlationId,
                                     String documentId, String xmlContent, String invoiceNumber) {
            super();
            this.sagaId = sagaId;
            this.sagaStep = sagaStep;
            this.correlationId = correlationId;
            this.documentId = documentId;
            this.xmlContent = xmlContent;
            this.invoiceNumber = invoiceNumber;
        }
    }

    /**
     * Command for tax invoice processing service.
     */
    @Getter
    public static class ProcessTaxInvoiceCommand extends IntegrationEvent {
        private static final long serialVersionUID = 1L;

        @JsonProperty("sagaId")
        private final String sagaId;

        @JsonProperty("sagaStep")
        private final String sagaStep;

        @JsonProperty("correlationId")
        private final String correlationId;

        @JsonProperty("documentId")
        private final String documentId;

        @JsonProperty("xmlContent")
        private final String xmlContent;

        @JsonProperty("invoiceNumber")
        private final String invoiceNumber;

        public ProcessTaxInvoiceCommand(String sagaId, String sagaStep, String correlationId,
                                         String documentId, String xmlContent, String invoiceNumber) {
            super();
            this.sagaId = sagaId;
            this.sagaStep = sagaStep;
            this.correlationId = correlationId;
            this.documentId = documentId;
            this.xmlContent = xmlContent;
            this.invoiceNumber = invoiceNumber;
        }
    }

    /**
     * Command for compensating (rolling back) a completed step.
     */
    @Getter
    public static class CompensationCommand extends IntegrationEvent {
        private static final long serialVersionUID = 1L;

        @JsonProperty("sagaId")
        private final String sagaId;

        @JsonProperty("sagaStep")
        private final String sagaStep;

        @JsonProperty("correlationId")
        private final String correlationId;

        @JsonProperty("stepToCompensate")
        private final String stepToCompensate;

        @JsonProperty("documentId")
        private final String documentId;

        @JsonProperty("documentType")
        private final String documentType;

        public CompensationCommand(String sagaId, String sagaStep, String correlationId,
                                   String stepToCompensate, String documentId, String documentType) {
            super();
            this.sagaId = sagaId;
            this.sagaStep = sagaStep;
            this.correlationId = correlationId;
            this.stepToCompensate = stepToCompensate;
            this.documentId = documentId;
            this.documentType = documentType;
        }
    }

    /**
     * Command for document storage service.
     */
    @Getter
    public static class StoreDocumentCommand extends IntegrationEvent {
        private static final long serialVersionUID = 1L;

        @JsonProperty("sagaId")
        private final String sagaId;

        @JsonProperty("sagaStep")
        private final String sagaStep;

        @JsonProperty("correlationId")
        private final String correlationId;

        @JsonProperty("documentId")
        private final String documentId;

        @JsonProperty("invoiceNumber")
        private final String invoiceNumber;

        @JsonProperty("documentType")
        private final String documentType;

        @JsonProperty("signedPdfUrl")
        private final String signedPdfUrl;

        @JsonProperty("signedDocumentId")
        private final String signedDocumentId;

        @JsonProperty("signatureLevel")
        private final String signatureLevel;

        public StoreDocumentCommand(String sagaId, String sagaStep, String correlationId,
                                     String documentId, String invoiceNumber, String documentType,
                                     String signedPdfUrl, String signedDocumentId, String signatureLevel) {
            super();
            this.sagaId = sagaId;
            this.sagaStep = sagaStep;
            this.correlationId = correlationId;
            this.documentId = documentId;
            this.invoiceNumber = invoiceNumber;
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
    public static class SendEbmsCommand extends IntegrationEvent {
        private static final long serialVersionUID = 1L;

        @JsonProperty("sagaId")
        private final String sagaId;

        @JsonProperty("sagaStep")
        private final String sagaStep;

        @JsonProperty("correlationId")
        private final String correlationId;

        @JsonProperty("documentId")
        private final String documentId;

        @JsonProperty("invoiceNumber")
        private final String invoiceNumber;

        @JsonProperty("documentType")
        private final String documentType;

        @JsonProperty("signedXmlContent")
        private final String signedXmlContent;

        public SendEbmsCommand(String sagaId, String sagaStep, String correlationId,
                               String documentId, String invoiceNumber, String documentType,
                               String signedXmlContent) {
            super();
            this.sagaId = sagaId;
            this.sagaStep = sagaStep;
            this.correlationId = correlationId;
            this.documentId = documentId;
            this.invoiceNumber = invoiceNumber;
            this.documentType = documentType;
            this.signedXmlContent = signedXmlContent;
        }
    }
}
