package com.wpanther.orchestrator.domain.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.IntegrationEvent;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/**
 * Command sent to orchestrator-service to start a new saga.
 * Published to topic: saga.commands.orchestrator
 * <p>
 * This command contains all the information the orchestrator needs to begin
 * orchestrating the multi-step invoice processing pipeline.
 */
@Getter
@Builder
@Jacksonized  // Enable Jackson builder deserialization
public class StartSagaCommand extends IntegrationEvent {

    private static final long serialVersionUID = 1L;

    /**
     * ID of the IncomingDocument that triggered this saga.
     */
    @JsonProperty("documentId")
    private final String documentId;

    /**
     * Type of document (TAX_INVOICE, INVOICE, RECEIPT, etc.)
     */
    @JsonProperty("documentType")
    private final String documentType;

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
     * Correlation ID for tracing the request across all services.
     */
    @JsonProperty("correlationId")
    private final String correlationId;

    /**
     * Source of the document (API, KAFKA, etc.)
     */
    @JsonProperty("source")
    private final String source;

    /**
     * Constructor for creating a new StartSagaCommand.
     * Initializes the base IntegrationEvent fields.
     */
    public StartSagaCommand() {
        super();
        this.documentId = null;
        this.documentType = null;
        this.invoiceNumber = null;
        this.xmlContent = null;
        this.correlationId = null;
        this.source = null;
    }

    /**
     * Full constructor for Builder.
     * Note: eventId, occurredAt, eventType, and version are set by IntegrationEvent base class.
     */
    @Builder
    private StartSagaCommand(String documentId, String documentType, String invoiceNumber,
                             String xmlContent, String correlationId, String source) {
        super();
        this.documentId = documentId;
        this.documentType = documentType;
        this.invoiceNumber = invoiceNumber;
        this.xmlContent = xmlContent;
        this.correlationId = correlationId;
        this.source = source;
    }
}
