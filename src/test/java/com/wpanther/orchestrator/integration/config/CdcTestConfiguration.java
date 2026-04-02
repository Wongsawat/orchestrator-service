package com.wpanther.orchestrator.integration.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.EnableTransactionManagement;


/**
 * Test configuration for CDC integration tests.
 * <p>
 * This configuration:
 * - Excludes Kafka auto-configuration to prevent application consumers from starting
 * - Loads JPA repositories for database access
 * - Uses real SagaCommandPublisher and SagaEventPublisher (write to outbox via CDC)
 * - Tests verify CDC flow by consuming from Kafka directly via TestKafkaConsumerConfig
 * <p>
 * For consumption tests, additionally import TestKafkaProducerConfig.
 */
@TestConfiguration
@EnableAutoConfiguration(exclude = {
    KafkaAutoConfiguration.class
})
@EnableJpaRepositories(basePackages = "com.wpanther.orchestrator")
@EntityScan(basePackages = "com.wpanther.orchestrator")
@ComponentScan(
    basePackages = {
        "com.wpanther.orchestrator.domain",
        "com.wpanther.orchestrator.application",
        "com.wpanther.orchestrator.infrastructure.adapter.out.persistence",
        "com.wpanther.orchestrator.infrastructure.config",
        "com.wpanther.orchestrator.infrastructure.adapter.out.messaging",
        "com.wpanther.saga.infrastructure"
    },
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*Consumer.*"),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*KafkaConfig.*")
    }
)
@EnableTransactionManagement
@Import(TestKafkaConsumerConfig.class)
public class CdcTestConfiguration {

    /**
     * ObjectMapper for JSON parsing in tests.
     * Includes JavaTimeModule for java.time.Instant serialization.
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    /**
     * JdbcTemplate for direct database access in tests.
     */
    @Bean
    public JdbcTemplate jdbcTemplate(javax.sql.DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /**
     * Configuration for consumption tests that need a real Kafka producer.
     * This is only active when 'cdc-consumption-test' profile is enabled.
     */
    @TestConfiguration
    @Profile("cdc-consumption-test")
    @Import(TestKafkaProducerConfig.class)
    static class ConsumptionTestConfiguration {
        // TestKafkaProducerConfig provides testKafkaProducer bean
    }
}