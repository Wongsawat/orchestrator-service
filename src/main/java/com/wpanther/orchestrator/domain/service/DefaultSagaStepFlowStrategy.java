package com.wpanther.orchestrator.domain.service;

import com.wpanther.orchestrator.domain.model.enums.DocumentType;
import com.wpanther.saga.domain.enums.SagaStep;
import org.springframework.stereotype.Component;

/**
 * Default implementation of saga step flow strategy.
 * Encapsulates the step transition logic for different document types.
 */
@Component
public class DefaultSagaStepFlowStrategy implements SagaStepFlowStrategy {

    @Override
    public SagaStep getNextStep(SagaStep currentStep, DocumentType documentType) {
        boolean isInvoice = DocumentType.INVOICE.equals(documentType);
        boolean isTaxInvoice = DocumentType.TAX_INVOICE.equals(documentType)
                || DocumentType.ABBREVIATED_TAX_INVOICE.equals(documentType);

        return switch (currentStep) {
            case PROCESS_INVOICE -> SagaStep.SIGN_XML;
            case PROCESS_TAX_INVOICE -> SagaStep.SIGN_XML;
            case SIGN_XML -> SagaStep.SIGNEDXML_STORAGE;
            case SIGNEDXML_STORAGE -> isInvoice
                    ? SagaStep.GENERATE_INVOICE_PDF
                    : SagaStep.GENERATE_TAX_INVOICE_PDF;
            case GENERATE_INVOICE_PDF -> SagaStep.SIGN_PDF;
            case GENERATE_TAX_INVOICE_PDF -> SagaStep.PDF_STORAGE;
            case PDF_STORAGE -> SagaStep.SIGN_PDF;
            case SIGN_PDF -> SagaStep.STORE_DOCUMENT;
            case STORE_DOCUMENT -> SagaStep.SEND_EBMS;
            case SEND_EBMS -> null; // Saga complete
            default -> throw new IllegalStateException("Unknown current step: " + currentStep);
        };
    }

    @Override
    public SagaStep getCompensationStep(SagaStep currentStep, DocumentType documentType) {
        boolean isInvoice = DocumentType.INVOICE.equals(documentType);
        boolean isTaxInvoice = DocumentType.TAX_INVOICE.equals(documentType)
                || DocumentType.ABBREVIATED_TAX_INVOICE.equals(documentType);

        return switch (currentStep) {
            case SEND_EBMS -> SagaStep.STORE_DOCUMENT;
            case STORE_DOCUMENT -> SagaStep.SIGN_PDF;
            case SIGN_PDF -> isInvoice
                    ? SagaStep.GENERATE_INVOICE_PDF
                    : SagaStep.PDF_STORAGE;
            case PDF_STORAGE -> SagaStep.GENERATE_TAX_INVOICE_PDF;
            case GENERATE_INVOICE_PDF, GENERATE_TAX_INVOICE_PDF -> SagaStep.SIGNEDXML_STORAGE;
            case SIGNEDXML_STORAGE -> SagaStep.SIGN_XML;
            case SIGN_XML -> isInvoice
                    ? SagaStep.PROCESS_INVOICE
                    : SagaStep.PROCESS_TAX_INVOICE;
            default -> null; // No compensation available for PROCESS_* steps (first step in flow)
        };
    }
}
