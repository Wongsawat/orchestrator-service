package com.wpanther.orchestrator.domain.service;

import com.wpanther.orchestrator.domain.model.enums.DocumentType;
import com.wpanther.saga.domain.enums.SagaStep;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Default implementation of saga step flow strategy.
 * Encapsulates the step transition logic for different document types.
 * Uses the declarative DocumentType.FLOW table for linear-time lookups.
 */
@Component
public class DefaultSagaStepFlowStrategy implements SagaStepFlowStrategy {

    @Override
    public SagaStep getNextStep(SagaStep currentStep, DocumentType documentType) {
        List<SagaStep> flow = DocumentType.getFlow().get(documentType);
        if (flow == null) {
            throw new IllegalStateException("No flow defined for document type: " + documentType);
        }
        int idx = flow.indexOf(currentStep);
        if (idx == -1) {
            throw new IllegalStateException(
                "Step " + currentStep + " is not in the flow for " + documentType);
        }
        if (idx + 1 >= flow.size()) {
            return null; // Saga complete
        }
        return flow.get(idx + 1);
    }

    @Override
    public SagaStep getCompensationStep(SagaStep currentStep, DocumentType documentType) {
        List<SagaStep> flow = DocumentType.getFlow().get(documentType);
        if (flow == null) {
            throw new IllegalStateException("No flow defined for document type: " + documentType);
        }
        int idx = flow.indexOf(currentStep);
        if (idx == -1) {
            return null; // Step not in flow, no compensation
        }
        if (idx == 0) {
            return null; // First step, nothing to compensate
        }
        return flow.get(idx - 1);
    }
}
