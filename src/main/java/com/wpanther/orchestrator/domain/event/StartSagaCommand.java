package com.wpanther.orchestrator.domain.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

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
public class StartSagaCommand {

    /**
     * Unique identifier for this command.
     */
    @JsonProperty("commandId")
    @Builder.Default
    private final UUID commandId = UUID.randomUUID();

    /**
     * Timestamp when this command was created.
     */
    @JsonProperty("occurredAt")
    @Builder.Default
    private final Instant occurredAt = Instant.now();

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
}
