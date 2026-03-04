package com.wpanther.orchestrator.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * General Spring configuration for the orchestrator service.
 */
@Configuration
@EnableTransactionManagement
public class OrchestratorConfig {

    /**
     * Configured ObjectMapper for JSON serialization/deserialization.
     * Handles Java 8 date/time types and provides ISO-8601 formatting.
     */
    @Bean
    public ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .build();
    }
}
