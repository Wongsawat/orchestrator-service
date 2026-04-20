package com.wpanther.orchestrator.integration.config;

import jakarta.annotation.PostConstruct;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Profile;

import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Test configuration that eagerly creates Kafka topics before any consumers subscribe.
 * <p>
 * This solves the race condition where:
 * 1. test-containers-clean.sh deletes all Kafka topics
 * 2. Spring Boot context starts and SagaReplyConsumer tries to subscribe to topics that don't exist yet
 * 3. createTopics() in @BeforeAll runs AFTER context starts (too late)
 * <p>
 * By creating topics in @PostConstruct, we ensure topics exist before Kafka consumers start.
 */
@TestConfiguration
@Profile("cross-service-test")
public class CrossServiceTestConfiguration {

    @Value("${spring.kafka.bootstrap-servers:localhost:9093}")
    private String bootstrapServers;

    @PostConstruct
    void createTopics() {
        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);

        List<NewTopic> topics = List.of(
            // Orchestrator input
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
            new NewTopic("saga.command.ebms-sending", 1, (short) 1),
            // Lifecycle topics (outbox → CDC → Kafka)
            new NewTopic("saga.lifecycle.started", 1, (short) 1),
            new NewTopic("saga.lifecycle.step-completed", 1, (short) 1),
            new NewTopic("saga.lifecycle.completed", 1, (short) 1),
            new NewTopic("saga.lifecycle.failed", 1, (short) 1),
            // Dead letter queues
            new NewTopic("xml.signing.dlq", 1, (short) 1),
            new NewTopic("pdf.signing.dlq", 1, (short) 1)
        );

        try (AdminClient adminClient = AdminClient.create(props)) {
            adminClient.createTopics(topics).all().get();
        } catch (Exception e) {
            // Topics may already exist or Kafka may not be reachable yet — ignore
            // The test will fail fast with a clear error if topics truly cannot be created
        }
    }
}
