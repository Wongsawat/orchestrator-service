package com.wpanther.orchestrator.infrastructure.messaging.consumer;

import com.wpanther.orchestrator.application.service.SagaApplicationService;
import com.wpanther.orchestrator.infrastructure.messaging.ConcreteSagaReply;
import com.wpanther.saga.domain.model.SagaReply;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Kafka consumer for handling saga replies from services.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SagaReplyConsumer {

    private final SagaApplicationService sagaApplicationService;

    /**
     * Handles replies from invoice processing services.
     */
    @KafkaListener(
            topics = "${app.saga.reply.invoice:saga.reply.invoice}",
            groupId = "${spring.kafka.consumer.group-id:orchestrator-service}",
            containerFactory = "sagaReplyKafkaListenerContainerFactory"
    )
    public void handleInvoiceReply(
            @Payload SagaReply reply,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.debug("Received invoice reply from topic {} (partition: {}, offset: {}): {}",
                topic, partition, offset, reply);

        try {
            processReply(reply);
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
                if (reply != null) {
                    log.trace("Acknowledged reply for saga {}", reply.getSagaId());
                }
            }
        } catch (Exception e) {
            log.error("Error processing invoice reply for saga {}: {}",
                    reply != null ? reply.getSagaId() : "null", e.getMessage(), e);
            // Still acknowledge to avoid retry loop - the saga will be marked as failed
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }
        }
    }

    /**
     * Handles replies from tax invoice processing services.
     */
    @KafkaListener(
            topics = "${app.saga.reply.tax-invoice:saga.reply.tax-invoice}",
            groupId = "${spring.kafka.consumer.group-id:orchestrator-service}",
            containerFactory = "sagaReplyKafkaListenerContainerFactory"
    )
    public void handleTaxInvoiceReply(
            @Payload SagaReply reply,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.debug("Received tax-invoice reply from topic {} (partition: {}, offset: {}): {}",
                topic, partition, offset, reply);

        try {
            processReply(reply);
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
                if (reply != null) {
                    log.trace("Acknowledged reply for saga {}", reply.getSagaId());
                }
            }
        } catch (Exception e) {
            log.error("Error processing tax-invoice reply for saga {}: {}",
                    reply != null ? reply.getSagaId() : "null", e.getMessage(), e);
            // Still acknowledge to avoid retry loop
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }
        }
    }

    /**
     * Handles replies from document storage service.
     */
    @KafkaListener(
            topics = "${app.saga.reply.document-storage:saga.reply.document-storage}",
            groupId = "${spring.kafka.consumer.group-id:orchestrator-service}",
            containerFactory = "sagaReplyKafkaListenerContainerFactory"
    )
    public void handleDocumentStorageReply(
            @Payload SagaReply reply,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.debug("Received document-storage reply from topic {} (partition: {}, offset: {}): {}",
                topic, partition, offset, reply);

        try {
            processReply(reply);
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
                if (reply != null) {
                    log.trace("Acknowledged reply for saga {}", reply.getSagaId());
                }
            }
        } catch (Exception e) {
            log.error("Error processing document-storage reply for saga {}: {}",
                    reply != null ? reply.getSagaId() : "null", e.getMessage(), e);
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }
        }
    }

    /**
     * Handles replies from XML signing service.
     */
    @KafkaListener(
            topics = "${app.saga.reply.xml-signing:saga.reply.xml-signing}",
            groupId = "${spring.kafka.consumer.group-id:orchestrator-service}",
            containerFactory = "sagaReplyKafkaListenerContainerFactory"
    )
    public void handleXmlSigningReply(
            @Payload SagaReply reply,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.debug("Received xml-signing reply from topic {} (partition: {}, offset: {}): {}",
                topic, partition, offset, reply);

        try {
            processReply(reply);
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
                if (reply != null) {
                    log.trace("Acknowledged reply for saga {}", reply.getSagaId());
                }
            }
        } catch (Exception e) {
            log.error("Error processing xml-signing reply for saga {}: {}",
                    reply != null ? reply.getSagaId() : "null", e.getMessage(), e);
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }
        }
    }

    /**
     * Handles replies from invoice PDF generation service.
     */
    @KafkaListener(
            topics = "${app.saga.reply.invoice-pdf:saga.reply.invoice-pdf}",
            groupId = "${spring.kafka.consumer.group-id:orchestrator-service}",
            containerFactory = "sagaReplyKafkaListenerContainerFactory"
    )
    public void handleInvoicePdfReply(
            @Payload SagaReply reply,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.debug("Received invoice-pdf reply from topic {} (partition: {}, offset: {}): {}",
                topic, partition, offset, reply);

        try {
            processReply(reply);
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
                if (reply != null) {
                    log.trace("Acknowledged reply for saga {}", reply.getSagaId());
                }
            }
        } catch (Exception e) {
            log.error("Error processing invoice-pdf reply for saga {}: {}",
                    reply != null ? reply.getSagaId() : "null", e.getMessage(), e);
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }
        }
    }

    /**
     * Handles replies from tax invoice PDF generation service.
     */
    @KafkaListener(
            topics = "${app.saga.reply.tax-invoice-pdf:saga.reply.tax-invoice-pdf}",
            groupId = "${spring.kafka.consumer.group-id:orchestrator-service}",
            containerFactory = "sagaReplyKafkaListenerContainerFactory"
    )
    public void handleTaxInvoicePdfReply(
            @Payload SagaReply reply,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.debug("Received tax-invoice-pdf reply from topic {} (partition: {}, offset: {}): {}",
                topic, partition, offset, reply);

        try {
            processReply(reply);
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
                if (reply != null) {
                    log.trace("Acknowledged reply for saga {}", reply.getSagaId());
                }
            }
        } catch (Exception e) {
            log.error("Error processing tax-invoice-pdf reply for saga {}: {}",
                    reply != null ? reply.getSagaId() : "null", e.getMessage(), e);
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }
        }
    }

    /**
     * Handles replies from PDF signing service.
     * This reply may contain additional data (signedPdfUrl, signedDocumentId, etc.)
     * that is propagated to subsequent steps via DocumentMetadata.
     */
    @KafkaListener(
            topics = "${app.saga.reply.pdf-signing:saga.reply.pdf-signing}",
            groupId = "${spring.kafka.consumer.group-id:orchestrator-service}",
            containerFactory = "sagaReplyKafkaListenerContainerFactory"
    )
    public void handlePdfSigningReply(
            @Payload SagaReply reply,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.debug("Received pdf-signing reply from topic {} (partition: {}, offset: {}): {}",
                topic, partition, offset, reply);

        try {
            processReply(reply);
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
                if (reply != null) {
                    log.trace("Acknowledged reply for saga {}", reply.getSagaId());
                }
            }
        } catch (Exception e) {
            log.error("Error processing pdf-signing reply for saga {}: {}",
                    reply != null ? reply.getSagaId() : "null", e.getMessage(), e);
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }
        }
    }

    /**
     * Processes a saga reply by delegating to the application service.
     * Extracts additional data from ConcreteSagaReply (e.g., signedPdfUrl from
     * PdfSigningReplyEvent) and passes it to handleReply for metadata propagation.
     */
    private void processReply(SagaReply reply) {
        if (reply == null) {
            log.warn("Received null saga reply, ignoring");
            return;
        }

        if (reply.getSagaId() == null || reply.getSagaId().isBlank()) {
            log.warn("Received reply without sagaId, ignoring: {}", reply);
            return;
        }

        boolean isSuccess = reply.isSuccess();
        String errorMessage = reply.isFailure() ? reply.getErrorMessage() : null;

        // Extract additional data from service-specific reply fields
        Map<String, Object> resultData = null;
        if (reply instanceof ConcreteSagaReply concrete) {
            Map<String, Object> additional = concrete.getAdditionalData();
            if (additional != null && !additional.isEmpty()) {
                resultData = additional;
                log.debug("Reply for saga {} contains additional data: {}",
                        reply.getSagaId(), additional.keySet());
            }
        }

        sagaApplicationService.handleReply(
                reply.getSagaId(),
                reply.getSagaStep(),
                isSuccess,
                errorMessage,
                resultData
        );
    }
}
