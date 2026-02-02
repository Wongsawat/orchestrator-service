package com.wpanther.orchestrator.infrastructure.messaging.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.saga.domain.model.SagaCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Kafka producer for sending saga commands to services.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SagaCommandProducer {

    private final KafkaTemplate<String, SagaCommand> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.saga.command.invoice:saga.command.invoice}")
    private String invoiceCommandTopic;

    @Value("${app.saga.command.tax-invoice:saga.command.tax-invoice}")
    private String taxInvoiceCommandTopic;

    /**
     * Sends a command for invoice saga processing.
     *
     * @param sagaId The saga instance ID
     * @param command The command to send
     * @return CompletableFuture for the send operation
     */
    public CompletableFuture<SendResult<String, SagaCommand>> sendInvoiceCommand(
            String sagaId,
            SagaCommand command) {
        String topic = invoiceCommandTopic;
        log.debug("Sending invoice command to topic {}: {}", topic, command);

        try {
            String payload = objectMapper.writeValueAsString(command);
            log.trace("Command payload: {}", payload);
        } catch (Exception e) {
            log.warn("Failed to serialize command for logging", e);
        }

        return kafkaTemplate.send(topic, sagaId, command)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send invoice command for saga {}: {}",
                                sagaId, ex.getMessage());
                    } else {
                        log.debug("Successfully sent invoice command for saga {} to partition {}",
                                sagaId, result.getRecordMetadata().partition());
                    }
                });
    }

    /**
     * Sends a command for tax invoice saga processing.
     *
     * @param sagaId The saga instance ID
     * @param command The command to send
     * @return CompletableFuture for the send operation
     */
    public CompletableFuture<SendResult<String, SagaCommand>> sendTaxInvoiceCommand(
            String sagaId,
            SagaCommand command) {
        String topic = taxInvoiceCommandTopic;
        log.debug("Sending tax-invoice command to topic {}: {}", topic, command);

        try {
            String payload = objectMapper.writeValueAsString(command);
            log.trace("Command payload: {}", payload);
        } catch (Exception e) {
            log.warn("Failed to serialize command for logging", e);
        }

        return kafkaTemplate.send(topic, sagaId, command)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send tax-invoice command for saga {}: {}",
                                sagaId, ex.getMessage());
                    } else {
                        log.debug("Successfully sent tax-invoice command for saga {} to partition {}",
                                sagaId, result.getRecordMetadata().partition());
                    }
                });
    }

    /**
     * Sends a command to the appropriate topic based on document type.
     *
     * @param sagaId The saga instance ID
     * @param command The command to send
     * @param isInvoice Whether this is an invoice (true) or tax invoice (false)
     * @return CompletableFuture for the send operation
     */
    public CompletableFuture<SendResult<String, SagaCommand>> sendCommand(
            String sagaId,
            SagaCommand command,
            boolean isInvoice) {
        if (isInvoice) {
            return sendInvoiceCommand(sagaId, command);
        } else {
            return sendTaxInvoiceCommand(sagaId, command);
        }
    }
}
