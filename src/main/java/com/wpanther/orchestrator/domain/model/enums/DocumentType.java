package com.wpanther.orchestrator.domain.model.enums;

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
        return this == INVOICE || this == TAX_INVOICE || this == ABBREVIATED_TAX_INVOICE;
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
    public com.wpanther.saga.domain.enums.SagaStep getInitialStep() {
        if (!isSupported()) {
            throw new UnsupportedOperationException(
                "Document type '" + this.name() + "' is not yet supported for saga processing. " +
                "Supported types: INVOICE, TAX_INVOICE, ABBREVIATED_TAX_INVOICE"
            );
        }
        return switch (this) {
            case INVOICE -> com.wpanther.saga.domain.enums.SagaStep.PROCESS_INVOICE;
            case TAX_INVOICE, ABBREVIATED_TAX_INVOICE -> com.wpanther.saga.domain.enums.SagaStep.PROCESS_TAX_INVOICE;
            default -> throw new AssertionError("Unsupported type should have been caught by isSupported()");
        };
    }
}
