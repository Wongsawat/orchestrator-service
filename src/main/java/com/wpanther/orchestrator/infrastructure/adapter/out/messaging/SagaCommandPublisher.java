package com.wpanther.orchestrator.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.orchestrator.domain.model.SagaInstance;
import com.wpanther.orchestrator.domain.repository.SagaInstanceRepository;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.domain.model.SagaCommand;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

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
    private final SagaInstanceRepository sagaRepository;

    @Value("${app.kafka.topics.saga-command-invoice:saga.command.invoice}")
    private String invoiceCommandTopic;

    @Value("${app.kafka.topics.saga-command-tax-invoice:saga.command.tax-invoice}")
    private String taxInvoiceCommandTopic;

    @Value("${app.kafka.topics.saga-command-abbreviated-tax-invoice:saga.command.abbreviated-tax-invoice}")
    private String abbreviatedTaxInvoiceCommandTopic;

    @Value("${app.kafka.topics.saga-command-receipt:saga.command.receipt}")
    private String receiptCommandTopic;

    @Value("${app.kafka.topics.saga-command-cancellation-note:saga.command.cancellation-note}")
    private String cancellationNoteCommandTopic;

    @Value("${app.kafka.topics.saga-command-debit-credit-note:saga.command.debit-credit-note}")
    private String debitCreditNoteCommandTopic;

    @Value("${app.kafka.topics.saga-command-xml-signing:saga.command.xml-signing}")
    private String xmlSigningCommandTopic;

    @Value("${app.kafka.topics.saga-command-invoice-pdf:saga.command.invoice-pdf}")
    private String invoicePdfCommandTopic;

    @Value("${app.kafka.topics.saga-command-tax-invoice-pdf:saga.command.tax-invoice-pdf}")
    private String taxInvoicePdfCommandTopic;

    @Value("${app.kafka.topics.saga-command-abbreviated-tax-invoice-pdf:saga.command.abbreviated-tax-invoice-pdf}")
    private String abbreviatedTaxInvoicePdfCommandTopic;

    @Value("${app.kafka.topics.saga-command-receipt-pdf:saga.command.receipt-pdf}")
    private String receiptPdfCommandTopic;

    @Value("${app.kafka.topics.saga-command-cancellation-note-pdf:saga.command.cancellation-note-pdf}")
    private String cancellationNotePdfCommandTopic;

    @Value("${app.kafka.topics.saga-command-debit-credit-note-pdf:saga.command.debit-credit-note-pdf}")
    private String debitCreditNotePdfCommandTopic;

    @Value("${app.kafka.topics.saga-command-pdf-signing:saga.command.pdf-signing}")
    private String pdfSigningCommandTopic;

    @Value("${app.saga.compensation.invoice:saga.compensation.invoice}")
    private String invoiceCompensationTopic;

    @Value("${app.saga.compensation.tax-invoice:saga.compensation.tax-invoice}")
    private String taxInvoiceCompensationTopic;

    @Value("${app.saga.compensation.xml-signing:saga.compensation.xml-signing}")
    private String xmlSigningCompensationTopic;

    @Value("${app.saga.compensation.invoice-pdf:saga.compensation.invoice-pdf}")
    private String invoicePdfCompensationTopic;

    @Value("${app.saga.compensation.tax-invoice-pdf:saga.compensation.tax-invoice-pdf}")
    private String taxInvoicePdfCompensationTopic;

    @Value("${app.saga.compensation.abbreviated-tax-invoice:saga.compensation.abbreviated-tax-invoice}")
    private String abbreviatedTaxInvoiceCompensationTopic;

    @Value("${app.saga.compensation.abbreviated-tax-invoice-pdf:saga.compensation.abbreviated-tax-invoice-pdf}")
    private String abbreviatedTaxInvoicePdfCompensationTopic;

    @Value("${app.saga.compensation.receipt:saga.compensation.receipt}")
    private String receiptCompensationTopic;

    @Value("${app.saga.compensation.receipt-pdf:saga.compensation.receipt-pdf}")
    private String receiptPdfCompensationTopic;

    @Value("${app.saga.compensation.cancellation-note:saga.compensation.cancellation-note}")
    private String cancellationNoteCompensationTopic;

    @Value("${app.saga.compensation.cancellation-note-pdf:saga.compensation.cancellation-note-pdf}")
    private String cancellationNotePdfCompensationTopic;

    @Value("${app.saga.compensation.debit-credit-note:saga.compensation.debit-credit-note}")
    private String debitCreditNoteCompensationTopic;

    @Value("${app.saga.compensation.debit-credit-note-pdf:saga.compensation.debit-credit-note-pdf}")
    private String debitCreditNotePdfCompensationTopic;

    @Value("${app.saga.compensation.pdf-signing:saga.compensation.pdf-signing}")
    private String pdfSigningCompensationTopic;

    @Value("${app.kafka.topics.saga-command-ebms-sending:saga.command.ebms-sending}")
    private String ebmsSendingCommandTopic;

    @Value("${app.saga.compensation.ebms-sending:saga.compensation.ebms-sending}")
    private String ebmsSendingCompensationTopic;

    private Map<SagaStep, BiConsumer<SagaInstance, String>> commandRouter =
            new EnumMap<>(SagaStep.class);
    private Map<SagaStep, String> compensationRouter =
            new EnumMap<>(SagaStep.class);

    @PostConstruct
    void initRouters() {
        commandRouter.put(SagaStep.PROCESS_INVOICE, this::publishProcessInvoiceCommand);
        commandRouter.put(SagaStep.PROCESS_TAX_INVOICE, this::publishProcessTaxInvoiceCommand);
        commandRouter.put(SagaStep.PROCESS_ABBREVIATED_TAX_INVOICE, this::publishProcessAbbreviatedTaxInvoiceCommand);
        commandRouter.put(SagaStep.PROCESS_RECEIPT, this::publishProcessReceiptCommand);
        commandRouter.put(SagaStep.PROCESS_CANCELLATION_NOTE, this::publishProcessCancellationNoteCommand);
        commandRouter.put(SagaStep.PROCESS_DEBIT_CREDIT_NOTE, this::publishProcessDebitCreditNoteCommand);
        commandRouter.put(SagaStep.SIGN_XML, this::publishSignXmlCommand);
        commandRouter.put(SagaStep.GENERATE_INVOICE_PDF, this::publishGenerateInvoicePdfCommand);
        commandRouter.put(SagaStep.GENERATE_TAX_INVOICE_PDF, this::publishGenerateTaxInvoicePdfCommand);
        commandRouter.put(SagaStep.GENERATE_ABBREVIATED_TAX_INVOICE_PDF, this::publishGenerateAbbreviatedTaxInvoicePdfCommand);
        commandRouter.put(SagaStep.GENERATE_RECEIPT_PDF, this::publishGenerateReceiptPdfCommand);
        commandRouter.put(SagaStep.GENERATE_CANCELLATION_NOTE_PDF, this::publishGenerateCancellationNotePdfCommand);
        commandRouter.put(SagaStep.GENERATE_DEBIT_CREDIT_NOTE_PDF, this::publishGenerateDebitCreditNotePdfCommand);
        commandRouter.put(SagaStep.SIGN_PDF, this::publishSignPdfCommand);
        commandRouter.put(SagaStep.SEND_EBMS, this::publishSendEbmsCommand);

        compensationRouter.put(SagaStep.PROCESS_INVOICE, invoiceCompensationTopic);
        compensationRouter.put(SagaStep.PROCESS_TAX_INVOICE, taxInvoiceCompensationTopic);
        compensationRouter.put(SagaStep.PROCESS_ABBREVIATED_TAX_INVOICE, abbreviatedTaxInvoiceCompensationTopic);
        compensationRouter.put(SagaStep.PROCESS_RECEIPT, receiptCompensationTopic);
        compensationRouter.put(SagaStep.PROCESS_CANCELLATION_NOTE, cancellationNoteCompensationTopic);
        compensationRouter.put(SagaStep.PROCESS_DEBIT_CREDIT_NOTE, debitCreditNoteCompensationTopic);
        compensationRouter.put(SagaStep.SIGN_XML, xmlSigningCompensationTopic);
        compensationRouter.put(SagaStep.GENERATE_INVOICE_PDF, invoicePdfCompensationTopic);
        compensationRouter.put(SagaStep.GENERATE_TAX_INVOICE_PDF, taxInvoicePdfCompensationTopic);
        compensationRouter.put(SagaStep.GENERATE_ABBREVIATED_TAX_INVOICE_PDF, abbreviatedTaxInvoicePdfCompensationTopic);
        compensationRouter.put(SagaStep.GENERATE_RECEIPT_PDF, receiptPdfCompensationTopic);
        compensationRouter.put(SagaStep.GENERATE_CANCELLATION_NOTE_PDF, cancellationNotePdfCompensationTopic);
        compensationRouter.put(SagaStep.GENERATE_DEBIT_CREDIT_NOTE_PDF, debitCreditNotePdfCompensationTopic);
        compensationRouter.put(SagaStep.SIGN_PDF, pdfSigningCompensationTopic);
        compensationRouter.put(SagaStep.SEND_EBMS, ebmsSendingCompensationTopic);
    }

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
     * Publishes a ProcessAbbreviatedTaxInvoiceCommand to the abbreviatedtaxinvoice-processing-service.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishProcessAbbreviatedTaxInvoiceCommand(SagaInstance saga, String correlationId) {
        ProcessAbbreviatedTaxInvoiceCommand command = new ProcessAbbreviatedTaxInvoiceCommand(
            saga.getId(),
            SagaStep.PROCESS_ABBREVIATED_TAX_INVOICE,
            correlationId,
            saga.getDocumentId(),
            getXmlContent(saga),
            getDocumentNumber(saga)
        );

        publishCommand(command, abbreviatedTaxInvoiceCommandTopic, saga, correlationId, "ProcessAbbreviatedTaxInvoiceCommand");

        log.debug("Published ProcessAbbreviatedTaxInvoiceCommand for saga {} to topic {}",
            saga.getId(), abbreviatedTaxInvoiceCommandTopic);
    }

    /**
     * Publishes a ProcessReceiptCommand to the receipt-processing-service.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishProcessReceiptCommand(SagaInstance saga, String correlationId) {
        ProcessReceiptCommand command = new ProcessReceiptCommand(
            saga.getId(),
            SagaStep.PROCESS_RECEIPT,
            correlationId,
            saga.getDocumentId(),
            getXmlContent(saga),
            getDocumentNumber(saga)
        );

        publishCommand(command, receiptCommandTopic, saga, correlationId, "ProcessReceiptCommand");

        log.debug("Published ProcessReceiptCommand for saga {} to topic {}",
            saga.getId(), receiptCommandTopic);
    }

    /**
     * Publishes a ProcessCancellationNoteCommand to the cancellationnote-processing-service.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishProcessCancellationNoteCommand(SagaInstance saga, String correlationId) {
        ProcessCancellationNoteCommand command = new ProcessCancellationNoteCommand(
            saga.getId(),
            SagaStep.PROCESS_CANCELLATION_NOTE,
            correlationId,
            saga.getDocumentId(),
            getXmlContent(saga),
            getDocumentNumber(saga)
        );

        publishCommand(command, cancellationNoteCommandTopic, saga, correlationId, "ProcessCancellationNoteCommand");

        log.debug("Published ProcessCancellationNoteCommand for saga {} to topic {}",
            saga.getId(), cancellationNoteCommandTopic);
    }

    /**
     * Publishes a ProcessDebitCreditNoteCommand to the debitcreditnote-processing-service.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishProcessDebitCreditNoteCommand(SagaInstance saga, String correlationId) {
        ProcessDebitCreditNoteCommand command = new ProcessDebitCreditNoteCommand(
            saga.getId(),
            SagaStep.PROCESS_DEBIT_CREDIT_NOTE,
            correlationId,
            saga.getDocumentId(),
            getXmlContent(saga),
            getDocumentNumber(saga)
        );

        publishCommand(command, debitCreditNoteCommandTopic, saga, correlationId, "ProcessDebitCreditNoteCommand");

        log.debug("Published ProcessDebitCreditNoteCommand for saga {} to topic {}",
            saga.getId(), debitCreditNoteCommandTopic);
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
            saga.getDocumentType().name(),
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
            saga.getDocumentType().name()
        );

        publishCommand(command, xmlSigningCommandTopic, saga, correlationId, "ProcessXmlSigningCommand");

        log.debug("Published ProcessXmlSigningCommand for saga {} to topic {}",
            saga.getId(), xmlSigningCommandTopic);
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
     * Publishes a ProcessAbbreviatedTaxInvoicePdfCommand to the abbreviatedtaxinvoice-pdf-generation-service.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishGenerateAbbreviatedTaxInvoicePdfCommand(SagaInstance saga, String correlationId) {
        String signedXmlUrl = getMetadataValue(saga, "signedXmlUrl");
        String abbreviatedTaxInvoiceDataJson = getMetadataValue(saga, "abbreviatedTaxInvoiceDataJson");

        ProcessAbbreviatedTaxInvoicePdfCommand command = new ProcessAbbreviatedTaxInvoicePdfCommand(
            saga.getId(),
            SagaStep.GENERATE_ABBREVIATED_TAX_INVOICE_PDF,
            correlationId,
            saga.getDocumentId(),
            getMetadataValue(saga, "abbreviatedTaxInvoiceId"),
            getDocumentNumber(saga),
            signedXmlUrl,
            abbreviatedTaxInvoiceDataJson
        );

        publishCommand(command, abbreviatedTaxInvoicePdfCommandTopic, saga, correlationId, "ProcessAbbreviatedTaxInvoicePdfCommand");

        log.debug("Published ProcessAbbreviatedTaxInvoicePdfCommand for saga {} to topic {}",
            saga.getId(), abbreviatedTaxInvoicePdfCommandTopic);
    }

    /**
     * Publishes a ProcessReceiptPdfCommand to the receipt-pdf-generation-service.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishGenerateReceiptPdfCommand(SagaInstance saga, String correlationId) {
        String signedXmlUrl = getMetadataValue(saga, "signedXmlUrl");
        String receiptDataJson = getMetadataValue(saga, "receiptDataJson");

        ProcessReceiptPdfCommand command = new ProcessReceiptPdfCommand(
            saga.getId(),
            SagaStep.GENERATE_RECEIPT_PDF,
            correlationId,
            saga.getDocumentId(),
            getMetadataValue(saga, "receiptId"),
            getDocumentNumber(saga),
            signedXmlUrl,
            receiptDataJson
        );

        publishCommand(command, receiptPdfCommandTopic, saga, correlationId, "ProcessReceiptPdfCommand");

        log.debug("Published ProcessReceiptPdfCommand for saga {} to topic {}",
            saga.getId(), receiptPdfCommandTopic);
    }

    /**
     * Publishes a ProcessCancellationNotePdfCommand to the cancellationnote-pdf-generation-service.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishGenerateCancellationNotePdfCommand(SagaInstance saga, String correlationId) {
        String signedXmlUrl = getMetadataValue(saga, "signedXmlUrl");
        String cancellationNoteDataJson = getMetadataValue(saga, "cancellationNoteDataJson");

        ProcessCancellationNotePdfCommand command = new ProcessCancellationNotePdfCommand(
            saga.getId(),
            SagaStep.GENERATE_CANCELLATION_NOTE_PDF,
            correlationId,
            saga.getDocumentId(),
            getMetadataValue(saga, "cancellationNoteId"),
            getDocumentNumber(saga),
            signedXmlUrl,
            cancellationNoteDataJson
        );

        publishCommand(command, cancellationNotePdfCommandTopic, saga, correlationId, "ProcessCancellationNotePdfCommand");

        log.debug("Published ProcessCancellationNotePdfCommand for saga {} to topic {}",
            saga.getId(), cancellationNotePdfCommandTopic);
    }

    /**
     * Publishes a ProcessDebitCreditNotePdfCommand to the debitcreditnote-pdf-generation-service.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishGenerateDebitCreditNotePdfCommand(SagaInstance saga, String correlationId) {
        String signedXmlUrl = getMetadataValue(saga, "signedXmlUrl");
        String debitCreditNoteDataJson = getMetadataValue(saga, "debitCreditNoteDataJson");

        ProcessDebitCreditNotePdfCommand command = new ProcessDebitCreditNotePdfCommand(
            saga.getId(),
            SagaStep.GENERATE_DEBIT_CREDIT_NOTE_PDF,
            correlationId,
            saga.getDocumentId(),
            getMetadataValue(saga, "debitCreditNoteId"),
            getDocumentNumber(saga),
            signedXmlUrl,
            debitCreditNoteDataJson
        );

        publishCommand(command, debitCreditNotePdfCommandTopic, saga, correlationId, "ProcessDebitCreditNotePdfCommand");

        log.debug("Published ProcessDebitCreditNotePdfCommand for saga {} to topic {}",
            saga.getId(), debitCreditNotePdfCommandTopic);
    }

    /**
     * Publishes a ProcessPdfSigningCommand to the pdf-signing-service.
     * The PDF URL comes from the GENERATE_TAX_INVOICE_PDF reply (pdfUrl, MinIO URL).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishSignPdfCommand(SagaInstance saga, String correlationId) {
        // PDF URL + size set by GENERATE_TAX_INVOICE_PDF or GENERATE_INVOICE_PDF reply (MinIO URL)
        String pdfUrl = getMetadataValue(saga, "pdfUrl");
        Long pdfSize = getMetadataLongValue(saga, "pdfSize");

        ProcessPdfSigningCommand command = new ProcessPdfSigningCommand(
            saga.getId(),
            SagaStep.SIGN_PDF,
            correlationId,
            saga.getDocumentId(),
            getDocumentNumber(saga),
            saga.getDocumentType().name(),
            pdfUrl,
            pdfSize
        );

        publishCommand(command, pdfSigningCommandTopic, saga, correlationId, "ProcessPdfSigningCommand");

        log.debug("Published ProcessPdfSigningCommand for saga {} to topic {}",
            saga.getId(), pdfSigningCommandTopic);
    }

    /**
     * Publishes a command for the current saga step.
     * Routes to the appropriate topic based on document type and step.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishCommandForStep(SagaInstance saga, SagaStep step, String correlationId) {
        BiConsumer<SagaInstance, String> handler = commandRouter.get(step);
        if (handler == null) {
            log.warn("No command publisher configured for step {}", step);
            return;
        }
        handler.accept(saga, correlationId);
    }

    /**
     * Publishes a compensation command to rollback a completed step.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishCompensationCommand(SagaInstance saga, SagaStep stepToCompensate, String correlationId) {
        String compensationTopic = compensationRouter.get(stepToCompensate);
        if (compensationTopic == null) {
            boolean isInvoice = saga.getDocumentType().name().equals("INVOICE");
            compensationTopic = isInvoice ? invoiceCompensationTopic : taxInvoiceCompensationTopic;
        }

        CompensationCommand command = new CompensationCommand(
            saga.getId(),
            stepToCompensate,
            correlationId,
            stepToCompensate.getCode(),
            saga.getDocumentId(),
            saga.getDocumentType().name()
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
        // Prefer saga's dedicated document_number column (set at saga creation and
        // correctly loaded via JdbcTemplate in handleReply without CLOB issues).
        // Fallback to metadata map for sagas created via REST API where documentMetadata
        // is available from JPA repository loading.
        if (saga.getDocumentNumber() != null && !saga.getDocumentNumber().isBlank()) {
            return saga.getDocumentNumber();
        }
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
        if (saga.getDocumentMetadata() != null
                && saga.getDocumentMetadata().getXmlContent() != null) {
            return saga.getDocumentMetadata().getXmlContent();
        }
        // The reply-handling path loads sagas via findByIdWithoutClob (to avoid
        // pulling CLOB columns for every step), so documentMetadata/xmlContent is
        // null here. The SIGN_XML command requires the XML, so reload the full
        // record (with CLOB) once to recover it.
        return sagaRepository.findById(saga.getId())
                .map(SagaInstance::getDocumentMetadata)
                .map(com.wpanther.orchestrator.domain.model.DocumentMetadata::getXmlContent)
                .orElse(null);
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

        @JsonProperty("documentNumber")
        private final String documentNumber;

        @JsonProperty("signedXmlUrl")
        private final String signedXmlUrl;

        @JsonProperty("taxInvoiceDataJson")
        private final String taxInvoiceDataJson;

        public ProcessTaxInvoicePdfCommand(String sagaId, SagaStep sagaStep, String correlationId,
                                            String documentId, String taxInvoiceId, String documentNumber,
                                            String signedXmlUrl, String taxInvoiceDataJson) {
            super(sagaId, sagaStep, correlationId);
            this.documentId = documentId;
            this.taxInvoiceId = taxInvoiceId;
            this.documentNumber = documentNumber;
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
                @JsonProperty("documentNumber") String documentNumber,
                @JsonProperty("signedXmlUrl") String signedXmlUrl,
                @JsonProperty("taxInvoiceDataJson") String taxInvoiceDataJson) {
            super(eventId, occurredAt, eventType, version, sagaId, sagaStep, correlationId);
            this.documentId = documentId;
            this.taxInvoiceId = taxInvoiceId;
            this.documentNumber = documentNumber;
            this.signedXmlUrl = signedXmlUrl;
            this.taxInvoiceDataJson = taxInvoiceDataJson;
        }
    }

    /**
     * Command for abbreviatedtaxinvoice-processing-service.
     */
    @Getter
    public static class ProcessAbbreviatedTaxInvoiceCommand extends SagaCommand {
        private static final long serialVersionUID = 1L;

        @JsonProperty("documentId")
        private final String documentId;

        @JsonProperty("xmlContent")
        private final String xmlContent;

        @JsonProperty("documentNumber")
        private final String documentNumber;

        public ProcessAbbreviatedTaxInvoiceCommand(String sagaId, SagaStep sagaStep, String correlationId,
                                                     String documentId, String xmlContent, String documentNumber) {
            super(sagaId, sagaStep, correlationId);
            this.documentId = documentId;
            this.xmlContent = xmlContent;
            this.documentNumber = documentNumber;
        }

        @JsonCreator
        public ProcessAbbreviatedTaxInvoiceCommand(
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
     * Command for receipt-processing-service.
     */
    @Getter
    public static class ProcessReceiptCommand extends SagaCommand {
        private static final long serialVersionUID = 1L;

        @JsonProperty("documentId")
        private final String documentId;

        @JsonProperty("xmlContent")
        private final String xmlContent;

        @JsonProperty("documentNumber")
        private final String documentNumber;

        public ProcessReceiptCommand(String sagaId, SagaStep sagaStep, String correlationId,
                                      String documentId, String xmlContent, String documentNumber) {
            super(sagaId, sagaStep, correlationId);
            this.documentId = documentId;
            this.xmlContent = xmlContent;
            this.documentNumber = documentNumber;
        }

        @JsonCreator
        public ProcessReceiptCommand(
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
     * Command for cancellationnote-processing-service.
     */
    @Getter
    public static class ProcessCancellationNoteCommand extends SagaCommand {
        private static final long serialVersionUID = 1L;

        @JsonProperty("documentId")
        private final String documentId;

        @JsonProperty("xmlContent")
        private final String xmlContent;

        @JsonProperty("documentNumber")
        private final String documentNumber;

        public ProcessCancellationNoteCommand(String sagaId, SagaStep sagaStep, String correlationId,
                                               String documentId, String xmlContent, String documentNumber) {
            super(sagaId, sagaStep, correlationId);
            this.documentId = documentId;
            this.xmlContent = xmlContent;
            this.documentNumber = documentNumber;
        }

        @JsonCreator
        public ProcessCancellationNoteCommand(
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
     * Command for debitcreditnote-processing-service.
     */
    @Getter
    public static class ProcessDebitCreditNoteCommand extends SagaCommand {
        private static final long serialVersionUID = 1L;

        @JsonProperty("documentId")
        private final String documentId;

        @JsonProperty("xmlContent")
        private final String xmlContent;

        @JsonProperty("documentNumber")
        private final String documentNumber;

        public ProcessDebitCreditNoteCommand(String sagaId, SagaStep sagaStep, String correlationId,
                                               String documentId, String xmlContent, String documentNumber) {
            super(sagaId, sagaStep, correlationId);
            this.documentId = documentId;
            this.xmlContent = xmlContent;
            this.documentNumber = documentNumber;
        }

        @JsonCreator
        public ProcessDebitCreditNoteCommand(
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
     * Command for abbreviatedtaxinvoice-pdf-generation-service.
     */
    @Getter
    public static class ProcessAbbreviatedTaxInvoicePdfCommand extends SagaCommand {
        private static final long serialVersionUID = 1L;

        @JsonProperty("documentId")
        private final String documentId;

        @JsonProperty("abbreviatedTaxInvoiceId")
        private final String abbreviatedTaxInvoiceId;

        @JsonProperty("documentNumber")
        private final String documentNumber;

        @JsonProperty("signedXmlUrl")
        private final String signedXmlUrl;

        @JsonProperty("abbreviatedTaxInvoiceDataJson")
        private final String abbreviatedTaxInvoiceDataJson;

        public ProcessAbbreviatedTaxInvoicePdfCommand(String sagaId, SagaStep sagaStep, String correlationId,
                                                      String documentId, String abbreviatedTaxInvoiceId,
                                                      String documentNumber, String signedXmlUrl,
                                                      String abbreviatedTaxInvoiceDataJson) {
            super(sagaId, sagaStep, correlationId);
            this.documentId = documentId;
            this.abbreviatedTaxInvoiceId = abbreviatedTaxInvoiceId;
            this.documentNumber = documentNumber;
            this.signedXmlUrl = signedXmlUrl;
            this.abbreviatedTaxInvoiceDataJson = abbreviatedTaxInvoiceDataJson;
        }

        @JsonCreator
        public ProcessAbbreviatedTaxInvoicePdfCommand(
                @JsonProperty("eventId") UUID eventId,
                @JsonProperty("occurredAt") Instant occurredAt,
                @JsonProperty("eventType") String eventType,
                @JsonProperty("version") int version,
                @JsonProperty("sagaId") String sagaId,
                @JsonProperty("sagaStep") SagaStep sagaStep,
                @JsonProperty("correlationId") String correlationId,
                @JsonProperty("documentId") String documentId,
                @JsonProperty("abbreviatedTaxInvoiceId") String abbreviatedTaxInvoiceId,
                @JsonProperty("documentNumber") String documentNumber,
                @JsonProperty("signedXmlUrl") String signedXmlUrl,
                @JsonProperty("abbreviatedTaxInvoiceDataJson") String abbreviatedTaxInvoiceDataJson) {
            super(eventId, occurredAt, eventType, version, sagaId, sagaStep, correlationId);
            this.documentId = documentId;
            this.abbreviatedTaxInvoiceId = abbreviatedTaxInvoiceId;
            this.documentNumber = documentNumber;
            this.signedXmlUrl = signedXmlUrl;
            this.abbreviatedTaxInvoiceDataJson = abbreviatedTaxInvoiceDataJson;
        }
    }

    /**
     * Command for receipt-pdf-generation-service.
     */
    @Getter
    public static class ProcessReceiptPdfCommand extends SagaCommand {
        private static final long serialVersionUID = 1L;

        @JsonProperty("documentId")
        private final String documentId;

        @JsonProperty("receiptId")
        private final String receiptId;

        @JsonProperty("documentNumber")
        private final String documentNumber;

        @JsonProperty("signedXmlUrl")
        private final String signedXmlUrl;

        @JsonProperty("receiptDataJson")
        private final String receiptDataJson;

        public ProcessReceiptPdfCommand(String sagaId, SagaStep sagaStep, String correlationId,
                                         String documentId, String receiptId, String documentNumber,
                                         String signedXmlUrl, String receiptDataJson) {
            super(sagaId, sagaStep, correlationId);
            this.documentId = documentId;
            this.receiptId = receiptId;
            this.documentNumber = documentNumber;
            this.signedXmlUrl = signedXmlUrl;
            this.receiptDataJson = receiptDataJson;
        }

        @JsonCreator
        public ProcessReceiptPdfCommand(
                @JsonProperty("eventId") UUID eventId,
                @JsonProperty("occurredAt") Instant occurredAt,
                @JsonProperty("eventType") String eventType,
                @JsonProperty("version") int version,
                @JsonProperty("sagaId") String sagaId,
                @JsonProperty("sagaStep") SagaStep sagaStep,
                @JsonProperty("correlationId") String correlationId,
                @JsonProperty("documentId") String documentId,
                @JsonProperty("receiptId") String receiptId,
                @JsonProperty("documentNumber") String documentNumber,
                @JsonProperty("signedXmlUrl") String signedXmlUrl,
                @JsonProperty("receiptDataJson") String receiptDataJson) {
            super(eventId, occurredAt, eventType, version, sagaId, sagaStep, correlationId);
            this.documentId = documentId;
            this.receiptId = receiptId;
            this.documentNumber = documentNumber;
            this.signedXmlUrl = signedXmlUrl;
            this.receiptDataJson = receiptDataJson;
        }
    }

    /**
     * Command for cancellationnote-pdf-generation-service.
     */
    @Getter
    public static class ProcessCancellationNotePdfCommand extends SagaCommand {
        private static final long serialVersionUID = 1L;

        @JsonProperty("documentId")
        private final String documentId;

        @JsonProperty("cancellationNoteId")
        private final String cancellationNoteId;

        @JsonProperty("documentNumber")
        private final String documentNumber;

        @JsonProperty("signedXmlUrl")
        private final String signedXmlUrl;

        @JsonProperty("cancellationNoteDataJson")
        private final String cancellationNoteDataJson;

        public ProcessCancellationNotePdfCommand(String sagaId, SagaStep sagaStep, String correlationId,
                                                  String documentId, String cancellationNoteId,
                                                  String documentNumber, String signedXmlUrl,
                                                  String cancellationNoteDataJson) {
            super(sagaId, sagaStep, correlationId);
            this.documentId = documentId;
            this.cancellationNoteId = cancellationNoteId;
            this.documentNumber = documentNumber;
            this.signedXmlUrl = signedXmlUrl;
            this.cancellationNoteDataJson = cancellationNoteDataJson;
        }

        @JsonCreator
        public ProcessCancellationNotePdfCommand(
                @JsonProperty("eventId") UUID eventId,
                @JsonProperty("occurredAt") Instant occurredAt,
                @JsonProperty("eventType") String eventType,
                @JsonProperty("version") int version,
                @JsonProperty("sagaId") String sagaId,
                @JsonProperty("sagaStep") SagaStep sagaStep,
                @JsonProperty("correlationId") String correlationId,
                @JsonProperty("documentId") String documentId,
                @JsonProperty("cancellationNoteId") String cancellationNoteId,
                @JsonProperty("documentNumber") String documentNumber,
                @JsonProperty("signedXmlUrl") String signedXmlUrl,
                @JsonProperty("cancellationNoteDataJson") String cancellationNoteDataJson) {
            super(eventId, occurredAt, eventType, version, sagaId, sagaStep, correlationId);
            this.documentId = documentId;
            this.cancellationNoteId = cancellationNoteId;
            this.documentNumber = documentNumber;
            this.signedXmlUrl = signedXmlUrl;
            this.cancellationNoteDataJson = cancellationNoteDataJson;
        }
    }

    /**
     * Command for debitcreditnote-pdf-generation-service.
     */
    @Getter
    public static class ProcessDebitCreditNotePdfCommand extends SagaCommand {
        private static final long serialVersionUID = 1L;

        @JsonProperty("documentId")
        private final String documentId;

        @JsonProperty("debitCreditNoteId")
        private final String debitCreditNoteId;

        @JsonProperty("documentNumber")
        private final String documentNumber;

        @JsonProperty("signedXmlUrl")
        private final String signedXmlUrl;

        @JsonProperty("debitCreditNoteDataJson")
        private final String debitCreditNoteDataJson;

        public ProcessDebitCreditNotePdfCommand(String sagaId, SagaStep sagaStep, String correlationId,
                                                String documentId, String debitCreditNoteId,
                                                String documentNumber, String signedXmlUrl,
                                                String debitCreditNoteDataJson) {
            super(sagaId, sagaStep, correlationId);
            this.documentId = documentId;
            this.debitCreditNoteId = debitCreditNoteId;
            this.documentNumber = documentNumber;
            this.signedXmlUrl = signedXmlUrl;
            this.debitCreditNoteDataJson = debitCreditNoteDataJson;
        }

        @JsonCreator
        public ProcessDebitCreditNotePdfCommand(
                @JsonProperty("eventId") UUID eventId,
                @JsonProperty("occurredAt") Instant occurredAt,
                @JsonProperty("eventType") String eventType,
                @JsonProperty("version") int version,
                @JsonProperty("sagaId") String sagaId,
                @JsonProperty("sagaStep") SagaStep sagaStep,
                @JsonProperty("correlationId") String correlationId,
                @JsonProperty("documentId") String documentId,
                @JsonProperty("debitCreditNoteId") String debitCreditNoteId,
                @JsonProperty("documentNumber") String documentNumber,
                @JsonProperty("signedXmlUrl") String signedXmlUrl,
                @JsonProperty("debitCreditNoteDataJson") String debitCreditNoteDataJson) {
            super(eventId, occurredAt, eventType, version, sagaId, sagaStep, correlationId);
            this.documentId = documentId;
            this.debitCreditNoteId = debitCreditNoteId;
            this.documentNumber = documentNumber;
            this.signedXmlUrl = signedXmlUrl;
            this.debitCreditNoteDataJson = debitCreditNoteDataJson;
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

        // pdfSize is required by etax-signing-service (SignedPdfDocument.create rejects a null
        // originalPdfSize). It is propagated from the GENERATE_*_PDF reply via saga metadata.
        @JsonProperty("pdfSize")
        private final Long pdfSize;

        public ProcessPdfSigningCommand(String sagaId, SagaStep sagaStep, String correlationId,
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
