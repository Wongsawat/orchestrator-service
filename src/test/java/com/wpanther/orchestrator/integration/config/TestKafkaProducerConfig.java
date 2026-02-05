package com.wpanther.orchestrator.integration.config;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.Map;

/**
 * Configuration for test Kafka producer used in consumption integration tests.
 * This producer is used to send test messages to reply topics for SagaReplyConsumer testing.
 * <p>
 * Only active when profile 'cdc-consumption-test' is enabled.
 */
@Configuration
@Profile({ "cdc-consumption-test", "consumer-test" })
public class TestKafkaProducerConfig {

    @Value("${app.kafka.bootstrap-servers:localhost:9093}")
    private String bootstrapServers;

    /**
     * KafkaTemplate for sending test messages to reply topics.
     * Used by tests that need to verify SagaReplyConsumer behavior.
     */
    @Bean
    public KafkaTemplate<String, String> testKafkaProducer() {
        return new KafkaTemplate<>(producerFactory());
    }

    /**
     * ProducerFactory for creating Kafka producers.
     * Configured with String serializers for key and value.
     */
    private ProducerFactory<String, String> producerFactory() {
        Map<String, Object> configProps = Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.ACKS_CONFIG, "all",
            ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 10000,
            ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 30000,
            ProducerConfig.LINGER_MS_CONFIG, 10,
            ProducerConfig.BATCH_SIZE_CONFIG, 16384,
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, false
        );
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * Raw KafkaProducer for advanced use cases.
     */
    @Bean
    public KafkaProducer<String, String> rawTestKafkaProducer() {
        return new KafkaProducer<>(Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class
        ));
    }
}
