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
        return switch (currentStep) {
            case PROCESS_INVOICE -> SagaStep.SIGN_XML;
            case PROCESS_TAX_INVOICE -> SagaStep.SIGN_XML;
            case SIGN_XML -> DocumentType.INVOICE.equals(documentType)
                    ? SagaStep.GENERATE_INVOICE_PDF
                    : SagaStep.GENERATE_TAX_INVOICE_PDF;
            case GENERATE_INVOICE_PDF -> SagaStep.SIGN_PDF;
            case GENERATE_TAX_INVOICE_PDF -> SagaStep.SIGN_PDF;
            case SIGN_PDF -> SagaStep.SEND_EBMS;
            case SEND_EBMS -> null; // Saga complete
            // SIGNEDXML_STORAGE, PDF_STORAGE, STORE_DOCUMENT removed from flow
            default -> throw new IllegalStateException("Unknown current step: " + currentStep);
        };
    }

    @Override
    public SagaStep getCompensationStep(SagaStep currentStep, DocumentType documentType) {
        return switch (currentStep) {
            case SEND_EBMS -> SagaStep.SIGN_PDF;
            case SIGN_PDF -> DocumentType.INVOICE.equals(documentType)
                    ? SagaStep.GENERATE_INVOICE_PDF
                    : SagaStep.GENERATE_TAX_INVOICE_PDF;
            case GENERATE_INVOICE_PDF -> SagaStep.SIGN_XML;
            case GENERATE_TAX_INVOICE_PDF -> SagaStep.SIGN_XML;
            case SIGN_XML -> DocumentType.INVOICE.equals(documentType)
                    ? SagaStep.PROCESS_INVOICE
                    : SagaStep.PROCESS_TAX_INVOICE;
            // SIGNEDXML_STORAGE, PDF_STORAGE, STORE_DOCUMENT removed from flow
            default -> null; // No compensation available for early steps
        };
    }
}
