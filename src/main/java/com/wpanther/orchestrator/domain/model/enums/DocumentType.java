package com.wpanther.orchestrator.domain.model.enums;

import com.wpanther.saga.domain.enums.SagaStep;

import java.util.List;
import java.util.Map;

/**
 * Enum representing the types of documents that can be processed by the saga orchestrator.
 */
public enum DocumentType {

    /**
     * Standard invoice document.
     */
    INVOICE("invoice", "Standard Invoice"),

    /**
     * Tax invoice document (Thai e-Tax specific).
     */
    TAX_INVOICE("tax-invoice", "Tax Invoice"),

    /**
     * Receipt document.
     */
    RECEIPT("receipt", "Receipt"),

    /**
     * Debit note document.
     */
    DEBIT_NOTE("debit-note", "Debit Note"),

    /**
     * Credit note document.
     */
    CREDIT_NOTE("credit-note", "Credit Note"),

    /**
     * Cancellation note document.
     */
    CANCELLATION_NOTE("cancellation-note", "Cancellation Note"),

    /**
     * Abbreviated tax invoice document.
     */
    ABBREVIATED_TAX_INVOICE("abbreviated-tax-invoice", "Abbreviated Tax Invoice");

    private final String code;
    private final String description;

    private static final Map<DocumentType, List<SagaStep>> FLOW = Map.of(
        INVOICE, List.of(
            SagaStep.PROCESS_INVOICE,
            SagaStep.SIGN_XML,
            SagaStep.GENERATE_INVOICE_PDF,
            SagaStep.SIGN_PDF,
            SagaStep.SEND_EBMS
        ),
        TAX_INVOICE, List.of(
            SagaStep.PROCESS_TAX_INVOICE,
            SagaStep.SIGN_XML,
            SagaStep.GENERATE_TAX_INVOICE_PDF,
            SagaStep.SIGN_PDF,
            SagaStep.SEND_EBMS
        ),
        ABBREVIATED_TAX_INVOICE, List.of(
            SagaStep.PROCESS_ABBREVIATED_TAX_INVOICE,
            SagaStep.SIGN_XML,
            SagaStep.GENERATE_ABBREVIATED_TAX_INVOICE_PDF,
            SagaStep.SIGN_PDF,
            SagaStep.SEND_EBMS
        ),
        RECEIPT, List.of(
            SagaStep.PROCESS_RECEIPT,
            SagaStep.SIGN_XML,
            SagaStep.GENERATE_RECEIPT_PDF,
            SagaStep.SIGN_PDF,
            SagaStep.SEND_EBMS
        ),
        CANCELLATION_NOTE, List.of(
            SagaStep.PROCESS_CANCELLATION_NOTE,
            SagaStep.SIGN_XML,
            SagaStep.GENERATE_CANCELLATION_NOTE_PDF,
            SagaStep.SIGN_PDF,
            SagaStep.SEND_EBMS
        ),
        DEBIT_NOTE, List.of(
            SagaStep.PROCESS_DEBIT_CREDIT_NOTE,
            SagaStep.SIGN_XML,
            SagaStep.GENERATE_DEBIT_CREDIT_NOTE_PDF,
            SagaStep.SIGN_PDF,
            SagaStep.SEND_EBMS
        ),
        CREDIT_NOTE, List.of(
            SagaStep.PROCESS_DEBIT_CREDIT_NOTE,
            SagaStep.SIGN_XML,
            SagaStep.GENERATE_DEBIT_CREDIT_NOTE_PDF,
            SagaStep.SIGN_PDF,
            SagaStep.SEND_EBMS
        )
    );

    DocumentType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static Map<DocumentType, List<SagaStep>> getFlow() {
        return FLOW;
    }

    /**
     * Finds a DocumentType by its code.
     *
     * @param code The document type code
     * @return The matching DocumentType
     * @throws IllegalArgumentException if code is not found
     */
    public static DocumentType fromCode(String code) {
        for (DocumentType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown document type code: " + code);
    }

    /**
     * Checks if this document type is currently supported for saga processing.
     * <p>
     * Unsupported types are defined for API stability but not yet implemented.
     * This allows client code to validate document types before attempting
     * saga creation.
     * </p>
     *
     * @return true if the document type can be processed by sagas
     */
    public boolean isSupported() {
        return FLOW.containsKey(this);
    }

    /**
     * Gets the initial saga step for this document type.
     * <p>
     * The initial step is the first processing step executed when a saga
     * is created for this document type.
     * </p>
     *
     * @return the first saga step for processing this document type
     * @throws UnsupportedOperationException if the document type is not yet supported
     */
    public SagaStep getInitialStep() {
        List<SagaStep> steps = FLOW.get(this);
        if (steps == null || steps.isEmpty()) {
            throw new UnsupportedOperationException(
                "Document type '" + this.name() + "' is not yet supported for saga processing"
            );
        }
        return steps.get(0);
    }
}
