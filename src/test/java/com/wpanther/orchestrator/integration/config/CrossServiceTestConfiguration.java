package com.wpanther.orchestrator.integration.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

import java.util.Properties;

/**
 * Test configuration for cross-service integration tests.
 *
 * <p>Eagerly creates Kafka topics when the Spring context starts, before any
 * consumers subscribe. This avoids the race condition where topics don't exist
 * yet after {@code test-containers-clean.sh} deletes them.
 *
 * <p>Note: The CDC-aware saga reply deserialization is now handled by the
 * production {@link com.wpanther.orchestrator.infrastructure.config.kafka.CdcAwareSagaReplyDeserializer}
 * in the main {@code KafkaConfig}, so no test-specific deserializer override is needed.
 */
@TestConfiguration
@Profile("cross-service-test")
public class CrossServiceTestConfiguration {

    @Value("${app.kafka.bootstrap-servers:localhost:9093}")
    private String bootstrapServers;

    @PostConstruct
    void init() {
        org.slf4j.LoggerFactory.getLogger(CrossServiceTestConfiguration.class)
                .info("Creating Kafka topics for cross-service test (eager @PostConstruct init)");
        createTopics();
    }

    @Bean
    public Properties kafkaAdminProperties() {
        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        return props;
    }

    private void createTopics() {
        org.apache.kafka.clients.admin.AdminClient adminClient = null;
        try {
            adminClient = org.apache.kafka.clients.admin.AdminClient.create(kafkaAdminProperties());
            adminClient.createTopics(java.util.List.of(
                    // Orchestrator input
                    new org.apache.kafka.clients.admin.NewTopic("saga.commands.orchestrator", 1, (short) 1),
                    // Reply topics (services → orchestrator)
                    new org.apache.kafka.clients.admin.NewTopic("saga.reply.invoice", 1, (short) 1),
                    new org.apache.kafka.clients.admin.NewTopic("saga.reply.tax-invoice", 1, (short) 1),
                    new org.apache.kafka.clients.admin.NewTopic("saga.reply.xml-signing", 1, (short) 1),
                    new org.apache.kafka.clients.admin.NewTopic("saga.reply.signedxml-storage", 1, (short) 1),
                    new org.apache.kafka.clients.admin.NewTopic("saga.reply.invoice-pdf", 1, (short) 1),
                    new org.apache.kafka.clients.admin.NewTopic("saga.reply.tax-invoice-pdf", 1, (short) 1),
                    new org.apache.kafka.clients.admin.NewTopic("saga.reply.pdf-storage", 1, (short) 1),
                    new org.apache.kafka.clients.admin.NewTopic("saga.reply.pdf-signing", 1, (short) 1),
                    new org.apache.kafka.clients.admin.NewTopic("saga.reply.document-storage", 1, (short) 1),
                    new org.apache.kafka.clients.admin.NewTopic("saga.reply.ebms-sending", 1, (short) 1),
                    // Command topics (orchestrator → services, via Debezium CDC)
                    new org.apache.kafka.clients.admin.NewTopic("saga.command.invoice", 1, (short) 1),
                    new org.apache.kafka.clients.admin.NewTopic("saga.command.tax-invoice", 1, (short) 1),
                    new org.apache.kafka.clients.admin.NewTopic("saga.command.xml-signing", 1, (short) 1),
                    new org.apache.kafka.clients.admin.NewTopic("saga.command.signedxml-storage", 1, (short) 1),
                    new org.apache.kafka.clients.admin.NewTopic("saga.command.invoice-pdf", 1, (short) 1),
                    new org.apache.kafka.clients.admin.NewTopic("saga.command.tax-invoice-pdf", 1, (short) 1),
                    new org.apache.kafka.clients.admin.NewTopic("saga.command.pdf-storage", 1, (short) 1),
                    new org.apache.kafka.clients.admin.NewTopic("saga.command.pdf-signing", 1, (short) 1),
                    new org.apache.kafka.clients.admin.NewTopic("saga.command.document-storage", 1, (short) 1),
                    new org.apache.kafka.clients.admin.NewTopic("saga.command.ebms-sending", 1, (short) 1),
                    // Lifecycle topics (outbox → CDC → Kafka)
                    new org.apache.kafka.clients.admin.NewTopic("saga.lifecycle.started", 1, (short) 1),
                    new org.apache.kafka.clients.admin.NewTopic("s.lifecycle.step-completed", 1, (short) 1),
                    new org.apache.kafka.clients.admin.NewTopic("saga.lifecycle.completed", 1, (short) 1),
                    new org.apache.kafka.clients.admin.NewTopic("saga.lifecycle.failed", 1, (short) 1),
                    // Dead letter queues
                    new org.apache.kafka.clients.admin.NewTopic("xml.signing.dlq", 1, (short) 1),
                    new org.apache.kafka.clients.admin.NewTopic("pdf.signing.dlq", 1, (short) 1)
            )).all().get();
        } catch (Exception e) {
            // Topics may already exist or Kafka may not be reachable yet — ignore
        } finally {
            if (adminClient != null) {
                adminClient.close();
            }
        }
    }
}