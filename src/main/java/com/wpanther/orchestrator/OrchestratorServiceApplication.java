package com.wpanther.orchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

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
public class OrchestratorServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrchestratorServiceApplication.class, args);
    }
}
