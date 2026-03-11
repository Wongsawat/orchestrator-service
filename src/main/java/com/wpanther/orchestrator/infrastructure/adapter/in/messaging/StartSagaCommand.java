package com.wpanther.orchestrator.infrastructure.adapter.in.messaging;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.InboundCommand;

import java.time.Instant;
import java.util.UUID;

/**
 * Command sent to orchestrator-service to start a new saga.
 * Published to topic: saga.commands.orchestrator
 * <p>
 * This command contains all the information the orchestrator needs to begin
 * orchestrating the multi-step invoice processing pipeline.
 * <p>
 * Extends {@link InboundCommand} which provides the common fields for commands
 * sent TO the orchestrator (documentId, source, correlationId, documentType).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StartSagaCommand extends InboundCommand {

    private static final long serialVersionUID = 1L;

    /**
     * The invoice/tax invoice number from the document.
     */
    @JsonProperty("invoiceNumber")
    private final String invoiceNumber;

    /**
     * The full XML content of the document.
     * This will be passed through the pipeline for processing and signing.
     */
    @JsonProperty("xmlContent")
    private final String xmlContent;

    /**
     * Convenience constructor for creating a new StartSagaCommand.
     *
     * @param documentId    ID of the IncomingDocument that triggered this saga
     * @param documentType  Type of document (TAX_INVOICE, INVOICE, RECEIPT, etc.)
     * @param invoiceNumber The invoice/tax invoice number from the document
     * @param xmlContent    The full XML content of the document
     * @param correlationId Correlation ID for tracing the request across all services
     * @param source        Source of the document (API, KAFKA, etc.)
     */
    public StartSagaCommand(String documentId, String documentType, String invoiceNumber,
                            String xmlContent, String correlationId, String source) {
        super(documentId, source, correlationId, documentType);
        this.invoiceNumber = invoiceNumber;
        this.xmlContent = xmlContent;
    }

    /**
     * Full constructor for Jackson deserialization.
     */
    @JsonCreator
    public StartSagaCommand(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("occurredAt") Instant occurredAt,
            @JsonProperty("eventType") String eventType,
            @JsonProperty("version") int version,
            @JsonProperty("documentId") String documentId,
            @JsonProperty("source") String source,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("documentType") String documentType,
            @JsonProperty("invoiceNumber") String invoiceNumber,
            @JsonProperty("xmlContent") String xmlContent) {
        super(eventId, occurredAt, eventType, version, documentId, source, correlationId, documentType);
        this.invoiceNumber = invoiceNumber;
        this.xmlContent = xmlContent;
    }

    // Getters for additional fields
    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public String getXmlContent() {
        return xmlContent;
    }
}
