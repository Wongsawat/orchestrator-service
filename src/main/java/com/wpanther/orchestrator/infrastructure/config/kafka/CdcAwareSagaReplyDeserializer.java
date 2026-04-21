package com.wpanther.orchestrator.infrastructure.config.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.saga.domain.model.SagaReply;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Deserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * Kafka value deserializer that handles Debezium CDC double-encoded payloads.
 *
 * <p>When the outbox {@code payload} column is TEXT, Debezium CDC publishes
 * the value as a JSON-quoted string (e.g. {@code "{\"eventId\":...}"}).
 * The standard {@code JsonDeserializer} cannot unwrap this and fails with
 * {@link com.fasterxml.jackson.databind.exc.MismatchedInputException}.
 *
 * <p>This deserializer detects double-encoded payloads and unwraps them before
 * delegating to Jackson for {@code SagaReply} deserialization.
 *
 * <p>Also handles the optional Debezium CDC envelope:
 * <pre>
 * {"schema": {...}, "payload": &lt;actual JSON&gt;}
 * </pre>
 */
public class CdcAwareSagaReplyDeserializer implements Deserializer<SagaReply> {

    private static final Logger log = LoggerFactory.getLogger(CdcAwareSagaReplyDeserializer.class);

    private final ObjectMapper objectMapper;

    public CdcAwareSagaReplyDeserializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public SagaReply deserialize(String topic, Headers headers, byte[] data) {
        String raw = new String(data, StandardCharsets.UTF_8).trim();
        try {
            // Handle Debezium CDC double-encoded TEXT payload:
            // The byte content is a JSON-quoted string, e.g. "{\"eventId\":...}"
            if (raw.startsWith("\"")) {
                String unwrapped = objectMapper.readValue(raw, String.class);
                log.trace("Unwrapped double-encoded CDC payload ({} bytes -> {} bytes)",
                        raw.length(), unwrapped.length());
                raw = unwrapped;
            }

            // Handle optional CDC envelope: {"schema": {...}, "payload": <payload>}
            JsonNode node = objectMapper.readTree(raw);
            if (node.has("payload") && node.has("schema")) {
                JsonNode payload = node.get("payload");
                if (payload.isTextual()) {
                    raw = payload.asText();
                } else {
                    raw = objectMapper.writeValueAsString(payload);
                }
            }

            return objectMapper.readValue(raw, SagaReply.class);
        } catch (Exception e) {
            log.error("Failed to deserialize SagaReply from topic {}: {}", topic, raw, e);
            throw new org.apache.kafka.common.errors.SerializationException(
                    "Cannot deserialize SagaReply", e);
        }
    }

    @Override
    public SagaReply deserialize(String topic, byte[] data) {
        return deserialize(topic, null, data);
    }
}