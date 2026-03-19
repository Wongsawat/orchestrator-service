package com.wpanther.orchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main Spring Boot application class for the Saga Orchestrator Service.
 * <p>
 * This service orchestrates multi-step document processing sagas across
 * the invoice processing microservices pipeline.
 * </p>
 */
@SpringBootApplication(scanBasePackages = {
        "com.wpanther.orchestrator",
        "com.wpanther.saga"
})
@EnableKafka
@EnableScheduling
@EnableConfigurationProperties
public class OrchestratorServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrchestratorServiceApplication.class, args);
    }
}
