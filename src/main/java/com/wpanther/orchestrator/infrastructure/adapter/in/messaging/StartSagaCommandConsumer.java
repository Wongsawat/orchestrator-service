package com.wpanther.orchestrator.infrastructure.adapter.in.messaging;

import com.wpanther.orchestrator.application.usecase.StartSagaUseCase;
import com.wpanther.orchestrator.domain.model.DocumentMetadata;
import com.wpanther.orchestrator.domain.model.SagaInstance;
import com.wpanther.orchestrator.domain.model.enums.DocumentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

// Common database and network transient exceptions
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;

/**
 * Kafka consumer for StartSagaCommand events from document-intake-service.
 * Consumes from saga.commands.orchestrator topic and starts new saga instances.
 * <p>
 * Error handling strategy:
 * - Permanent errors (validation, invalid data): Acknowledge and skip
 * - Transient errors (database connection, temporary failures): Don't acknowledge (trigger retry)
 * - Concurrent modification errors: Don't acknowledge (trigger retry with backoff)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StartSagaCommandConsumer {

    private final StartSagaUseCase startSagaUseCase;

    @KafkaListener(
        topics = "${app.kafka.topics.saga-commands-orchestrator:saga.commands.orchestrator}",
        groupId = "orchestrator-service",
        containerFactory = "startSagaCommandKafkaListenerContainerFactory"
    )
    public void handleStartSagaCommand(StartSagaCommand command,
                                       @Header(KafkaHeaders.RECEIVED_KEY) String key,
                                       Acknowledgment ack) {
        log.info("Received StartSagaCommand: documentId={}, documentType={}, correlationId={}",
            command.getDocumentId(), command.getDocumentType(), command.getCorrelationId());

        try {
            // Validate document type is not null or blank before processing
            String docTypeStr = command.getDocumentType();
            if (docTypeStr == null || docTypeStr.isBlank()) {
                throw new IllegalArgumentException("Document type cannot be null or blank");
            }

            // Convert document type string to enum
            DocumentType documentType = DocumentType.valueOf(docTypeStr);

            // Create metadata map
            Map<String, Object> metadataMap = new HashMap<>();
            metadataMap.put("invoiceNumber", command.getInvoiceNumber());
            metadataMap.put("source", command.getSource());
            metadataMap.put("eventId", command.getEventId().toString());

            // Build DocumentMetadata from command
            DocumentMetadata metadata = DocumentMetadata.builder()
                .xmlContent(command.getXmlContent())
                .metadata(metadataMap)
                .build();

            // Start the saga, propagating the caller's correlationId for end-to-end tracing
            SagaInstance saga = startSagaUseCase.startSaga(
                    documentType, command.getDocumentId(), metadata, command.getCorrelationId());

            // Acknowledge message only after successful processing
            ack.acknowledge();

            log.info("Successfully started saga {} for document: {}", saga.getId(), command.getDocumentId());

        } catch (IllegalArgumentException e) {
            // Permanent error: Invalid document type or validation failure
            // Acknowledge to skip this message permanently
            log.error("Permanent error processing StartSagaCommand for document {}: Invalid data - {}",
                command.getDocumentId(), e.getMessage());
            ack.acknowledge();

        } catch (DataIntegrityViolationException e) {
            // Check if this is a constraint violation (duplicate saga)
            if (isUniqueConstraintViolation(e)) {
                // Permanent error: Duplicate document - acknowledge and skip
                log.warn("Duplicate saga for document {}, acknowledging and skipping: {}",
                    command.getDocumentId(), e.getMessage());
                ack.acknowledge();
            } else {
                // Transient error: Other data integrity issues - retry
                log.warn("Transient data integrity error for document {}, will retry: {}",
                    command.getDocumentId(), e.getMessage());
                throw e;
            }

        } catch (OptimisticLockingFailureException e) {
            // Transient error: Concurrent modification - retry with backoff
            log.warn("Concurrent modification detected for document {}, will retry: {}",
                command.getDocumentId(), e.getMessage());
            // Don't acknowledge - message will be retried by Kafka
            throw e;

        } catch (Exception e) {
            // For unexpected exceptions, check if it's a transient error
            if (isTransientError(e)) {
                // Transient error: Don't acknowledge to trigger retry with backoff
                log.warn("Transient error processing StartSagaCommand for document {}, will retry: {}",
                    command.getDocumentId(), e.getMessage());
                // Don't acknowledge - message will be retried by Kafka
                throw e;
            } else {
                // Permanent error: Acknowledge to skip
                log.error("Permanent error processing StartSagaCommand for document {}: {}",
                    command.getDocumentId(), e.getMessage(), e);
                ack.acknowledge();
            }
        }
    }

    /**
     * Checks if a DataIntegrityViolationException is due to a unique constraint violation.
     * This indicates a duplicate saga for the same document.
     */
    private boolean isUniqueConstraintViolation(DataIntegrityViolationException e) {
        if (e.getCause() instanceof SQLException sqlEx) {
            String sqlState = sqlEx.getSQLState();
            String message = sqlEx.getMessage();
            // PostgreSQL unique constraint violation: 23505
            // MySQL duplicate entry: 1062 (ER_DUP_ENTRY)
            return "23505".equals(sqlState) ||
                   (message != null && message.toLowerCase().contains("duplicate"));
        }
        return false;
    }

    /**
     * Determines if an exception represents a transient error that should be retried.
     * Transient errors include: database connection issues, timeouts, temporary network issues.
     * Uses instanceof checks for known exception types first, with message matching as fallback.
     */
    private boolean isTransientError(Exception e) {
        // Check for known transient exception types using instanceof
        if (e instanceof TimeoutException ||
            e instanceof QueryTimeoutException ||
            e instanceof TransientDataAccessException ||
            e instanceof CannotCreateTransactionException) {
            return true;
        }

        // Check SQLException causes for SQL transient error states
        if (e.getCause() instanceof SQLException sqlEx) {
            String sqlState = sqlEx.getSQLState();
            // PostgreSQL transient errors: 08001 (connection does not exist), 08004 (server rejected),
            // 08006 (connection failure), 08007 (transaction resolution unknown), 08P01 (protocol violation)
            // MySQL transient errors: communication errors, timeouts
            if (sqlState != null && (sqlState.startsWith("08") || "40P01".equals(sqlState))) {
                return true;  // Connection errors and deadlock retries
            }
        }

        // Fallback: message-based matching for unknown exception types
        // Only used for exceptions that don't match the known types above
        String message = e.getMessage();
        if (message != null) {
            String lowerMessage = message.toLowerCase();
            return lowerMessage.contains("connection") ||
                   lowerMessage.contains("timeout") ||
                   lowerMessage.contains("temporary") ||
                   lowerMessage.contains("unavailable");
        }
        return false;
    }
}
