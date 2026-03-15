package com.wpanther.orchestrator.application.dto;

/**
 * Health status response for the orchestrator service.
 */
public record HealthStatus(String status, String message) {
}
