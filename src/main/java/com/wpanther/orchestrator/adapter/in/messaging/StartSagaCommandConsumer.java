package com.wpanther.orchestrator.adapter.in.messaging;

import com.wpanther.orchestrator.application.usecase.StartSagaUseCase;
import com.wpanther.orchestrator.domain.model.DocumentMetadata;
import com.wpanther.orchestrator.domain.model.SagaInstance;
import com.wpanther.orchestrator.domain.model.enums.DocumentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka consumer for StartSagaCommand events from document-intake-service.
 * Consumes from saga.commands.orchestrator topic and starts new saga instances.
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
            // Convert document type string to enum
            DocumentType documentType = DocumentType.valueOf(command.getDocumentType());

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

            // Start the saga
            SagaInstance saga = startSagaUseCase.startSaga(documentType, command.getDocumentId(), metadata);

            // Acknowledge message only after successful processing
            if (ack != null) {
                ack.acknowledge();
            }

            log.info("Successfully started saga {} for document: {}", saga.getId(), command.getDocumentId());

        } catch (IllegalArgumentException e) {
            // Invalid document type - don't retry, just log and skip
            log.error("Failed to process StartSagaCommand for document {}: Invalid document type '{}'",
                command.getDocumentId(), command.getDocumentType(), e);
            if (ack != null) {
                ack.acknowledge(); // Acknowledge to skip invalid message
            }
        } catch (Exception e) {
            // Other errors - don't acknowledge to trigger retry
            log.error("Failed to process StartSagaCommand for document: {}",
                command.getDocumentId(), e);
            // Don't acknowledge - message will be retried
        }
    }
}
