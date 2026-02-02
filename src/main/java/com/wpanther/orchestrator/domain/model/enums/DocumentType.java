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
}
