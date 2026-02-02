package com.wpanther.orchestrator.infrastructure.messaging.producer;

import com.wpanther.orchestrator.domain.model.SagaInstance;
import com.wpanther.orchestrator.infrastructure.outbox.OutboxService;
import com.wpanther.saga.domain.enums.SagaStep;
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

    @Value("${app.kafka.topics.saga-command-invoice:saga.command.invoice}")
    private String invoiceCommandTopic;

    @Value("${app.kafka.topics.saga-command-tax-invoice:saga.command.tax-invoice}")
    private String taxInvoiceCommandTopic;

    /**
     * Publishes a ProcessInvoiceCommand to the invoice processing service.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishProcessInvoiceCommand(SagaInstance saga, String correlationId) {
        ProcessInvoiceCommand command = ProcessInvoiceCommand.builder()
            .sagaId(saga.getId())
            .documentId(saga.getDocumentId())
            .xmlContent(saga.getDocumentMetadata().getXmlContent())
            .correlationId(correlationId)
            .invoiceNumber(getInvoiceNumber(saga))
            .build();

        Map<String, String> headers = new HashMap<>();
        headers.put("sagaId", saga.getId());
        headers.put("correlationId", correlationId);
        headers.put("documentType", saga.getDocumentType().name());
        headers.put("commandType", "ProcessInvoiceCommand");

        outboxService.writeEvent(
            "SagaInstance",
            saga.getId(),
            "ProcessInvoiceCommand",
            invoiceCommandTopic,
            command,
            headers
        );

        log.debug("Published ProcessInvoiceCommand for saga {} to topic {}",
            saga.getId(), invoiceCommandTopic);
    }

    /**
     * Publishes a ProcessTaxInvoiceCommand to the tax invoice processing service.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishProcessTaxInvoiceCommand(SagaInstance saga, String correlationId) {
        ProcessTaxInvoiceCommand command = ProcessTaxInvoiceCommand.builder()
            .sagaId(saga.getId())
            .documentId(saga.getDocumentId())
            .xmlContent(saga.getDocumentMetadata().getXmlContent())
            .correlationId(correlationId)
            .invoiceNumber(getInvoiceNumber(saga))
            .build();

        Map<String, String> headers = new HashMap<>();
        headers.put("sagaId", saga.getId());
        headers.put("correlationId", correlationId);
        headers.put("documentType", saga.getDocumentType().name());
        headers.put("commandType", "ProcessTaxInvoiceCommand");

        outboxService.writeEvent(
            "SagaInstance",
            saga.getId(),
            "ProcessTaxInvoiceCommand",
            taxInvoiceCommandTopic,
            command,
            headers
        );

        log.debug("Published ProcessTaxInvoiceCommand for saga {} to topic {}",
            saga.getId(), taxInvoiceCommandTopic);
    }

    /**
     * Publishes a command for the current saga step.
     * Routes to the appropriate topic based on document type and step.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishCommandForStep(SagaInstance saga, SagaStep step, String correlationId) {
        boolean isInvoice = saga.getDocumentType().name().equals("INVOICE");

        switch (step) {
            case PROCESS_INVOICE:
                publishProcessInvoiceCommand(saga, correlationId);
                break;
            case PROCESS_TAX_INVOICE:
                publishProcessTaxInvoiceCommand(saga, correlationId);
                break;
            // Future steps will be added here:
            // case SIGN_XML -> publishSignXmlCommand
            // case GENERATE_INVOICE_PDF -> publishGenerateInvoicePdfCommand
            // etc.
            default:
                log.warn("No command publisher configured for step {}", step);
        }
    }

    /**
     * Extracts invoice number from saga metadata.
     */
    private String getInvoiceNumber(SagaInstance saga) {
        if (saga.getDocumentMetadata() != null && saga.getDocumentMetadata().getMetadata() != null) {
            Object invoiceNumber = saga.getDocumentMetadata().getMetadata().get("invoiceNumber");
            if (invoiceNumber != null) {
                return invoiceNumber.toString();
            }
        }
        return null;
    }

    /**
     * Command for invoice processing service.
     */
    @lombok.Builder
    @lombok.Getter
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class ProcessInvoiceCommand {
        private String sagaId;
        private String documentId;
        private String xmlContent;
        private String correlationId;
        private String invoiceNumber;
    }

    /**
     * Command for tax invoice processing service.
     */
    @lombok.Builder
    @lombok.Getter
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class ProcessTaxInvoiceCommand {
        private String sagaId;
        private String documentId;
        private String xmlContent;
        private String correlationId;
        private String invoiceNumber;
    }
}
