package com.wpanther.orchestrator.infrastructure.adapter.in.messaging;

import com.wpanther.orchestrator.application.usecase.HandleSagaReplyUseCase;
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
import java.util.regex.Pattern;

/**
 * Kafka consumer for handling saga replies from services.
 * Uses a unified handler pattern to avoid code duplication across multiple reply topics.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SagaReplyConsumer {

    private final HandleSagaReplyUseCase handleSagaReplyUseCase;

    /**
     * Prefix for all saga reply topics.
     * Topics follow the pattern: saga.reply.{step-name}
     */
    private static final String REPLY_TOPIC_PREFIX = "saga.reply.";

    /**
     * Regex pattern to extract the step name from a topic name.
     * Uses Pattern.quote() to safely escape the topic prefix.
     */
    private static final Pattern TOPIC_NAME_EXTRACTOR =
            Pattern.compile(Pattern.quote(REPLY_TOPIC_PREFIX) + "(.+)");

    /**
     * Unified handler for all saga reply topics.
     * Uses topic patterns to route all replies through a single method.
     * <p>
     * Topics handled:
     * - saga.reply.invoice (Invoice processing)
     * - saga.reply.tax-invoice (Tax invoice processing)
     * - saga.reply.document-storage (Document storage - final PDF)
     * - saga.reply.xml-signing (XML signing)
     * - saga.reply.signedxml-storage (Signed XML storage)
     * - saga.reply.invoice-pdf (Invoice PDF generation)
     * - saga.reply.tax-invoice-pdf (Tax invoice PDF generation)
     * - saga.reply.pdf-storage (Unsigned PDF storage for tax invoices)
     * - saga.reply.pdf-signing (PDF signing)
     * - saga.reply.ebms-sending (ebMS sending to Revenue Department)
     * </p>
     */
    @KafkaListener(
            topics = {
                    "${app.saga.reply.invoice:saga.reply.invoice}",
                    "${app.saga.reply.tax-invoice:saga.reply.tax-invoice}",
                    "${app.saga.reply.document-storage:saga.reply.document-storage}",
                    "${app.saga.reply.xml-signing:saga.reply.xml-signing}",
                    "${app.saga.reply.signedxml-storage:saga.reply.signedxml-storage}",
                    "${app.saga.reply.invoice-pdf:saga.reply.invoice-pdf}",
                    "${app.saga.reply.tax-invoice-pdf:saga.reply.tax-invoice-pdf}",
                    "${app.saga.reply.pdf-storage:saga.reply.pdf-storage}",
                    "${app.saga.reply.pdf-signing:saga.reply.pdf-signing}",
                    "${app.saga.reply.ebms-sending:saga.reply.ebms-sending}"
            },
            groupId = "${spring.kafka.consumer.group-id:orchestrator-service}",
            containerFactory = "sagaReplyKafkaListenerContainerFactory"
    )
    public void handleSagaReply(
            @Payload SagaReply reply,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        String stepName = extractStepName(topic);
        log.debug("Received {} reply from topic {} (partition: {}, offset: {}): {}",
                stepName, topic, partition, offset, reply);

        try {
            processReply(reply);
            acknowledgeSafely(acknowledgment, reply, stepName);
        } catch (Exception e) {
            log.error("Error processing {} reply for saga {}: {}",
                    stepName, getSagaIdSafe(reply), e.getMessage(), e);
            // Always acknowledge to avoid retry loop - the saga will be marked as failed internally
            acknowledgeSafely(acknowledgment, null, stepName);
        }
    }

    /**
     * Extracts a human-readable step name from the Kafka topic.
     * Converts "saga.reply.tax-invoice-pdf" to "tax-invoice-pdf"
     */
    private String extractStepName(String topic) {
        var matcher = TOPIC_NAME_EXTRACTOR.matcher(topic);
        return matcher.matches() ? matcher.group(1) : topic;
    }

    /**
     * Safely extracts saga ID from reply, returning "null" if reply is null.
     */
    private String getSagaIdSafe(SagaReply reply) {
        return reply != null ? reply.getSagaId() : "null";
    }

    /**
     * Safely acknowledges a message, logging a trace message on success.
     */
    private void acknowledgeSafely(Acknowledgment acknowledgment, SagaReply reply, String stepName) {
        if (acknowledgment != null) {
            acknowledgment.acknowledge();
            if (reply != null) {
                log.trace("Acknowledged {} reply for saga {}", stepName, reply.getSagaId());
            }
        }
    }

    /**
     * Processes a saga reply by delegating to the application service.
     * Extracts additional data from ConcreteSagaReply (e.g., signedPdfUrl from
     * PdfSigningReplyEvent) and passes it to handleReply for metadata propagation.
     */
    private void processReply(SagaReply reply) {
        if (reply == null || reply.getSagaId() == null || reply.getSagaId().isBlank()) {
            log.warn("Received invalid saga reply, ignoring");
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

        handleSagaReplyUseCase.handleReply(
                reply.getSagaId(),
                reply.getSagaStep().getCode(),
                isSuccess,
                errorMessage,
                resultData
        );
    }
}
