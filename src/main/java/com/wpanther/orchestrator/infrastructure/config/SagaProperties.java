package com.wpanther.orchestrator.infrastructure.config;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for saga orchestration.
 * Reads values from app.saga.* properties in application.yml.
 */
@Component
@ConfigurationProperties(prefix = "app.saga")
@Getter
public class SagaProperties {

    /**
     * Maximum number of retry attempts per saga step.
     */
    private int maxRetries = 3;

    /**
     * Delay between retry attempts in seconds.
     */
    private int retryDelaySeconds = 5;

    /**
     * Timeout for compensation operations in seconds.
     */
    private int compensationTimeoutSeconds = 300;

    /**
     * Timeout for saga execution in minutes.
     * If a saga remains in IN_PROGRESS status longer than this duration,
     * it will be marked as failed and compensation will be initiated.
     * Set to 0 to disable timeout checking.
     */
    private int timeoutMinutes = 30;

    /**
     * Whether timeout checking is enabled.
     * If false, the scheduled timeout checker will not run.
     */
    private boolean timeoutCheckEnabled = true;
}
