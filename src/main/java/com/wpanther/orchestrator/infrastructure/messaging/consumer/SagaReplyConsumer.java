package com.wpanther.orchestrator.infrastructure.messaging.consumer;

import com.wpanther.orchestrator.application.service.SagaApplicationService;
import com.wpanther.saga.domain.model.SagaReply;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

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
                log.trace("Acknowledged reply for saga {}", reply.getSagaId());
            }
        } catch (Exception e) {
            log.error("Error processing invoice reply for saga {}: {}",
                    reply.getSagaId(), e.getMessage(), e);
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
                log.trace("Acknowledged reply for saga {}", reply.getSagaId());
            }
        } catch (Exception e) {
            log.error("Error processing tax-invoice reply for saga {}: {}",
                    reply.getSagaId(), e.getMessage(), e);
            // Still acknowledge to avoid retry loop
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }
        }
    }

    /**
     * Legacy method for ConsumerRecord-based handling.
     */
    @KafkaListener(
            topics = "${app.saga.reply.invoice:saga.reply.invoice}",
            groupId = "${spring.kafka.consumer.group-id:orchestrator-service}"
    )
    public void handleInvoiceReplyRecord(ConsumerRecord<String, SagaReply> record) {
        log.debug("Received invoice reply: {}", record.value());
        processReply(record.value());
    }

    /**
     * Legacy method for ConsumerRecord-based handling for tax invoice.
     */
    @KafkaListener(
            topics = "${app.saga.reply.tax-invoice:saga.reply.tax-invoice}",
            groupId = "${spring.kafka.consumer.group-id:orchestrator-service}"
    )
    public void handleTaxInvoiceReplyRecord(ConsumerRecord<String, SagaReply> record) {
        log.debug("Received tax-invoice reply: {}", record.value());
        processReply(record.value());
    }

    /**
     * Processes a saga reply by delegating to the application service.
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

        sagaApplicationService.handleReply(
                reply.getSagaId(),
                reply.getSagaStep(),
                isSuccess,
                errorMessage
        );
    }
}
