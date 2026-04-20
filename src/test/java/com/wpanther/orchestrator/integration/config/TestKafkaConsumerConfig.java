package com.wpanther.orchestrator.integration.config;

import jakarta.annotation.PostConstruct;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;
import java.util.Properties;

/**
 * Kafka configuration for CDC integration tests.
 * Provides consumers to verify messages published via Debezium CDC.
 */
@Configuration
@Profile({ "cdc-test", "consumer-test", "saga-flow-test", "cross-service-test" })
public class TestKafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(TestKafkaConsumerConfig.class);

    @Value("${app.kafka.bootstrap-servers:localhost:9093}")
    private String bootstrapServers;

    /**
     * Eagerly creates Kafka topics when the Spring context starts.
     * This runs BEFORE any Kafka consumers subscribe, avoiding the race condition
     * where topics don't exist yet after test-containers-clean.sh deletes them.
     */
    @PostConstruct
    void init() {
        log.info("Creating Kafka topics for cross-service test (eager @PostConstruct init)");
        createTopics();
    }

    @Bean
    public Properties kafkaAdminProperties() {
        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        return props;
    }

    @Bean
    public KafkaConsumer<String, String> testKafkaConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "cdc-test-consumer-" + System.currentTimeMillis());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        return new KafkaConsumer<>(props);
    }

    /**
     * Create Kafka topics needed for tests.
     * Called from AbstractCdcIntegrationTest.setupInfrastructure().
     */
    public void createTopics() {
        try (AdminClient adminClient = AdminClient.create(kafkaAdminProperties())) {
            List<NewTopic> topics = List.of(
                // Lifecycle topics
                new NewTopic("saga.lifecycle.started", 1, (short) 1),
                new NewTopic("saga.lifecycle.step-completed", 1, (short) 1),
                new NewTopic("saga.lifecycle.completed", 1, (short) 1),
                new NewTopic("saga.lifecycle.failed", 1, (short) 1),
                // Orchestrator input topic
                new NewTopic("saga.commands.orchestrator", 1, (short) 1),
                // Reply topics (services → orchestrator)
                new NewTopic("saga.reply.invoice", 1, (short) 1),
                new NewTopic("saga.reply.tax-invoice", 1, (short) 1),
                new NewTopic("saga.reply.xml-signing", 1, (short) 1),
                new NewTopic("saga.reply.signedxml-storage", 1, (short) 1),
                new NewTopic("saga.reply.invoice-pdf", 1, (short) 1),
                new NewTopic("saga.reply.tax-invoice-pdf", 1, (short) 1),
                new NewTopic("saga.reply.pdf-storage", 1, (short) 1),
                new NewTopic("saga.reply.pdf-signing", 1, (short) 1),
                new NewTopic("saga.reply.document-storage", 1, (short) 1),
                new NewTopic("saga.reply.ebms-sending", 1, (short) 1),
                // Command topics (orchestrator → services, via Debezium CDC)
                new NewTopic("saga.command.invoice", 1, (short) 1),
                new NewTopic("saga.command.tax-invoice", 1, (short) 1),
                new NewTopic("saga.command.xml-signing", 1, (short) 1),
                new NewTopic("saga.command.signedxml-storage", 1, (short) 1),
                new NewTopic("saga.command.invoice-pdf", 1, (short) 1),
                new NewTopic("saga.command.tax-invoice-pdf", 1, (short) 1),
                new NewTopic("saga.command.pdf-storage", 1, (short) 1),
                new NewTopic("saga.command.pdf-signing", 1, (short) 1),
                new NewTopic("saga.command.document-storage", 1, (short) 1),
                new NewTopic("saga.command.ebms-sending", 1, (short) 1)
            );
            adminClient.createTopics(topics).all().get();
        } catch (Exception e) {
            // Topics may already exist - ignore
        }
    }
}