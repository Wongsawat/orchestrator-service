package com.wpanther.orchestrator.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.IntegrationEvent;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.UUID;

/**
 * Command sent to orchestrator-service to start a new saga.
 * Published to topic: saga.commands.orchestrator
 * <p>
 * This command contains all the information the orchestrator needs to begin
 * orchestrating the multi-step invoice processing pipeline.
 */
@Getter
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
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
     * Constructor for Builder pattern - used when creating new instances.
     * Calls super() to auto-generate eventId, occurredAt, eventType, version.
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

    /**
     * Constructor for Jackson deserialization - includes all fields from parent class.
     * Used when consuming from Kafka.
     */
    @JsonCreator
    private StartSagaCommand(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("occurredAt") Instant occurredAt,
            @JsonProperty("eventType") String eventType,
            @JsonProperty("version") int version,
            @JsonProperty("documentId") String documentId,
            @JsonProperty("documentType") String documentType,
            @JsonProperty("invoiceNumber") String invoiceNumber,
            @JsonProperty("xmlContent") String xmlContent,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("source") String source) {
        super(eventId, occurredAt, eventType, version);
        this.documentId = documentId;
        this.documentType = documentType;
        this.invoiceNumber = invoiceNumber;
        this.xmlContent = xmlContent;
        this.correlationId = correlationId;
        this.source = source;
    }
}
