package com.wpanther.orchestrator.domain.service;

import com.wpanther.orchestrator.domain.model.enums.DocumentType;
import com.wpanther.saga.domain.enums.SagaStep;

/**
 * Strategy interface for defining saga step flows.
 * Different document types can have different step sequences.
 */
public interface SagaStepFlowStrategy {

    /**
     * Gets the next step in the saga flow based on the current step and document type.
     *
     * @param currentStep the current saga step
     * @param documentType the type of document being processed
     * @return the next saga step, or null if the saga is complete
     * @throws IllegalStateException if the current step is unknown
     */
    SagaStep getNextStep(SagaStep currentStep, DocumentType documentType);

    /**
     * Gets the compensation step for the given current step and document type.
     *
     * @param currentStep the current saga step
     * @param documentType the type of document being processed
     * @return the compensation step, or null if no compensation is available
     */
    SagaStep getCompensationStep(SagaStep currentStep, DocumentType documentType);
}
