package com.wpanther.orchestrator.infrastructure.adapter.in.rest;

import com.wpanther.orchestrator.application.dto.SagaResponse;
import com.wpanther.orchestrator.application.dto.StartSagaRequest;
import com.wpanther.orchestrator.application.usecase.QuerySagaUseCase;
import com.wpanther.orchestrator.application.usecase.SagaManagementUseCase;
import com.wpanther.orchestrator.application.usecase.StartSagaUseCase;
import com.wpanther.orchestrator.domain.model.DocumentMetadata;
import com.wpanther.orchestrator.domain.model.SagaInstance;
import com.wpanther.orchestrator.domain.model.enums.DocumentType;
import com.wpanther.saga.domain.enums.SagaStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for the orchestrator service.
 * Provides endpoints for managing saga instances.
 * <p>
 * All endpoints require JWT authentication with ROLE_API_USER authority.
 * </p>
 */
@RestController
@RequestMapping("/api/saga")
@RequiredArgsConstructor
@Slf4j
public class OrchestratorController {

    private final StartSagaUseCase startSagaUseCase;
    private final QuerySagaUseCase querySagaUseCase;
    private final SagaManagementUseCase sagaManagementUseCase;

    /**
     * Starts a new saga instance.
     * Requires ROLE_API_USER authority.
     *
     * @param request The start saga request
     * @return The created saga response
     */
    @PostMapping("/start")
    @PreAuthorize("hasAuthority('ROLE_API_USER')")
    public ResponseEntity<SagaResponse> startSaga(@Valid @RequestBody StartSagaRequest request) {
        log.info("Received request to start saga for document type {} with ID {}",
                request.documentType(), request.documentId());

        DocumentMetadata metadata = DocumentMetadata.builder()
                .filePath(request.filePath())
                .xmlContent(request.xmlContent())
                .metadata(request.metadata())
                .fileSize(request.fileSize())
                .mimeType(request.mimeType())
                .checksum(request.checksum())
                .build();

        SagaInstance instance = startSagaUseCase.startSaga(request.documentType(), request.documentId(), metadata);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(SagaResponse.fromDomain(instance));
    }

    /**
     * Gets a saga instance by ID.
     *
     * @param sagaId The saga instance ID
     * @return The saga response
     */
    @GetMapping("/{sagaId}")
    @PreAuthorize("hasAuthority('ROLE_API_USER')")
    public ResponseEntity<SagaResponse> getSaga(@PathVariable String sagaId) {
        log.debug("Fetching saga {}", sagaId);

        SagaInstance instance = querySagaUseCase.getSagaInstance(sagaId);
        return ResponseEntity.ok(SagaResponse.fromDomain(instance));
    }

    /**
     * Gets all active sagas.
     *
     * @return List of active saga responses
     */
    @GetMapping("/active")
    @PreAuthorize("hasAuthority('ROLE_API_USER')")
    public ResponseEntity<List<SagaResponse>> getActiveSagas() {
        log.debug("Fetching active sagas");

        List<SagaInstance> instances = querySagaUseCase.getActiveSagas();
        return ResponseEntity.ok(
                instances.stream()
                        .map(SagaResponse::fromDomain)
                        .toList()
        );
    }

    /**
     * Gets sagas for a specific document.
     *
     * @param documentType The document type
     * @param documentId   The document ID
     * @return List of saga responses for the document
     */
    @GetMapping("/document")
    @PreAuthorize("hasAuthority('ROLE_API_USER')")
    public ResponseEntity<List<SagaResponse>> getSagasForDocument(
            @RequestParam DocumentType documentType,
            @RequestParam String documentId) {

        log.debug("Fetching sagas for document type {} with ID {}", documentType, documentId);

        List<SagaInstance> instances = querySagaUseCase.getSagasForDocument(documentType, documentId);
        return ResponseEntity.ok(
                instances.stream()
                        .map(SagaResponse::fromDomain)
                        .toList()
        );
    }

    /**
     * Manually advances a saga to the next step.
     *
     * @param sagaId The saga instance ID
     * @return The updated saga response
     */
    @PostMapping("/{sagaId}/advance")
    @PreAuthorize("hasAuthority('ROLE_API_USER')")
    public ResponseEntity<SagaResponse> advanceSaga(@PathVariable String sagaId) {
        log.info("Manually advancing saga {}", sagaId);

        SagaInstance instance = sagaManagementUseCase.advanceSaga(sagaId);
        return ResponseEntity.ok(SagaResponse.fromDomain(instance));
    }

    /**
     * Retries a failed saga step.
     *
     * @param sagaId The saga instance ID
     * @return The updated saga response
     */
    @PostMapping("/{sagaId}/retry")
    @PreAuthorize("hasAuthority('ROLE_API_USER')")
    public ResponseEntity<SagaResponse> retrySaga(@PathVariable String sagaId) {
        log.info("Retrying saga {}", sagaId);

        SagaInstance instance = sagaManagementUseCase.retryStep(sagaId);
        return ResponseEntity.ok(SagaResponse.fromDomain(instance));
    }

    /**
     * Gets sagas by status.
     *
     * @param status The saga status
     * @return List of saga responses
     */
    @GetMapping("/status/{status}")
    @PreAuthorize("hasAuthority('ROLE_API_USER')")
    public ResponseEntity<List<SagaResponse>> getSagasByStatus(@PathVariable SagaStatus status) {
        log.debug("Fetching sagas with status {}", status);

        List<SagaInstance> instances = querySagaUseCase.getSagasByStatus(status);
        return ResponseEntity.ok(
                instances.stream()
                        .map(SagaResponse::fromDomain)
                        .toList()
        );
    }

    /**
     * Health check endpoint.
     *
     * @return Health status
     */
    @GetMapping("/health")
    public ResponseEntity<HealthStatus> health() {
        return ResponseEntity.ok(new HealthStatus("UP", "Orchestrator service is running"));
    }

    /**
     * Health status record.
     */
    public record HealthStatus(String status, String message) {}
}
