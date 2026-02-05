package com.wpanther.orchestrator.integration.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Test configuration for Kafka consumer integration tests.
 * <p>
 * This configuration provides additional beans for testing:
 * - JdbcTemplate for direct database access
 * - TestKafkaConsumerConfig and TestKafkaProducerConfig for Kafka operations
 * <p>
 * The main application class (OrchestratorServiceApplication) provides:
 * - All component scanning (domain, application, infrastructure)
 * - Kafka consumers (StartSagaCommandConsumer, SagaReplyConsumer)
 * - Repository and service beans
 * - ObjectMapper with proper Jackson configuration
 * <p>
 * SagaCommandPublisher is mocked via @MockBean in AbstractKafkaConsumerTest
 * to avoid sending real commands during tests.
 */
@TestConfiguration
@Import({TestKafkaConsumerConfig.class, TestKafkaProducerConfig.class})
public class ConsumerTestConfiguration {

    /**
     * JdbcTemplate for direct database access in tests.
     * Bypasses JPA cache for reliable state verification.
     */
    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
