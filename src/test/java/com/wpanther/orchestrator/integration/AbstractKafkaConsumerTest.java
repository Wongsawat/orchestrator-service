package com.wpanther.orchestrator.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.orchestrator.application.usecase.SagaApplicationService;
import com.wpanther.orchestrator.adapter.in.messaging.StartSagaCommand;
import com.wpanther.orchestrator.domain.model.DocumentMetadata;
import com.wpanther.orchestrator.domain.model.SagaInstance;
import com.wpanther.orchestrator.domain.model.enums.DocumentType;
import com.wpanther.orchestrator.port.out.SagaInstanceRepository;
import com.wpanther.orchestrator.adapter.out.messaging.SagaCommandPublisher;
import com.wpanther.orchestrator.integration.config.ConsumerTestConfiguration;
import com.wpanther.orchestrator.integration.config.TestKafkaProducerConfig;
import com.wpanther.saga.domain.enums.SagaStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import org.awaitility.Awaitility;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Base class for Kafka consumer integration tests.
 * <p>
 * Provides common infrastructure for testing StartSagaCommandConsumer and SagaReplyConsumer.
 * Tests require external infrastructure: PostgreSQL (5433), Kafka (9093), Debezium (8083).
 * <p>
 * To run: mvn test -Dintegration.tests.enabled=true -Dspring.profiles.active=consumer-test
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.kafka.bootstrap-servers=localhost:9093",
        "KAFKA_BROKERS=localhost:9093"
    }
)
@ActiveProfiles("consumer-test")
@Import({TestKafkaProducerConfig.class, ConsumerTestConfiguration.class})
@EnabledIfSystemProperty(named = "integration.tests.enabled", matches = "true")
public abstract class AbstractKafkaConsumerTest {

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected KafkaTemplate<String, String> testKafkaProducer;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected SagaInstanceRepository sagaInstanceRepository;

    @Autowired
    protected SagaApplicationService sagaApplicationService;

    @MockBean
    protected SagaCommandPublisher sagaCommandPublisher;

    /**
     * Awaitility factory for async operations.
     * 2-minute timeout with 1-second poll interval.
     */
    protected static org.awaitility.core.ConditionFactory await() {
        return Awaitility.await()
            .atMost(Duration.ofMinutes(2))
            .pollInterval(Duration.ofSeconds(1));
    }

    /**
     * Cleanup database before each test.
     * Deletes in correct order to respect foreign key constraints.
     */
    @BeforeEach
    void cleanupDatabase() {
        jdbcTemplate.update("DELETE FROM saga_commands");
        jdbcTemplate.update("DELETE FROM outbox_events");
        jdbcTemplate.update("DELETE FROM saga_data");
        jdbcTemplate.update("DELETE FROM saga_instances");
    }

    // ==================== Message Creation Helpers ====================

    /**
     * Creates a StartSagaCommand for testing.
     *
     * @param type Document type (INVOICE or TAX_INVOICE)
     * @param documentId External document identifier
     * @return StartSagaCommand ready to send
     */
    protected StartSagaCommand createStartSagaCommand(DocumentType type, String documentId) {
        return new StartSagaCommand(
            documentId,
            type.name(),
            "INV-" + documentId,
            "<test>content</test>",
            UUID.randomUUID().toString(),
            "TEST"
        );
    }

    /**
     * Creates a SagaReply JSON string for testing.
     * <p>
     * Uses the full ConcreteSagaReply format required by the consumer:
     * - eventId, occurredAt, eventType, version (from IntegrationEvent)
     * - sagaId, sagaStep, correlationId (reply-specific fields)
     * - status: ReplyStatus enum (SUCCESS or FAILURE)
     * - errorMessage: optional error details
     *
     * @param sagaId Saga instance ID
     * @param step Step code (e.g., "process-invoice")
     * @param success Whether the step succeeded
     * @param errorMessage Error message if failed (can be null)
     * @return JSON string ready to send
     */
    protected String createSagaReplyJson(String sagaId, String step, boolean success, String errorMessage) {
        String eventType = success ? "SagaReplySuccess" : "SagaReplyFailure";
        String status = success ? "SUCCESS" : "FAILURE";
        String errorJson = errorMessage == null ? "null" : "\"" + errorMessage + "\"";
        return String.format("""
            {
                "eventId": "%s",
                "occurredAt": "%s",
                "eventType": "%s",
                "version": 1,
                "sagaId": "%s",
                "sagaStep": "%s",
                "correlationId": "%s",
                "status": "%s",
                "errorMessage": %s
            }
            """,
            UUID.randomUUID(),
            java.time.Instant.now().toString(),
            eventType,
            sagaId,
            step,
            UUID.randomUUID(),
            status,
            errorJson
        );
    }

    // ==================== Message Sending Helpers ====================

    /**
     * Sends a StartSagaCommand to saga.commands.orchestrator topic.
     *
     * @param command Command to send
     */
    protected void sendStartSagaCommand(StartSagaCommand command) {
        try {
            String json = objectMapper.writeValueAsString(command);
            testKafkaProducer.send("saga.commands.orchestrator", command.getDocumentId(), json)
                .get(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send StartSagaCommand", e);
        }
    }

    /**
     * Sends a SagaReply to saga.reply.invoice topic.
     *
     * @param sagaId Saga instance ID
     * @param step Step code
     * @param success Whether the step succeeded
     * @param errorMessage Error message if failed
     */
    protected void sendInvoiceReply(String sagaId, String step, boolean success, String errorMessage) {
        String reply = createSagaReplyJson(sagaId, step, success, errorMessage);
        testKafkaProducer.send("saga.reply.invoice", sagaId, reply);
        // Small delay to ensure message is committed
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Sends a SagaReply to saga.reply.tax-invoice topic.
     *
     * @param sagaId Saga instance ID
     * @param step Step code
     * @param success Whether the step succeeded
     * @param errorMessage Error message if failed
     */
    protected void sendTaxInvoiceReply(String sagaId, String step, boolean success, String errorMessage) {
        String reply = createSagaReplyJson(sagaId, step, success, errorMessage);
        testKafkaProducer.send("saga.reply.tax-invoice", sagaId, reply);
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ==================== Database Verification Helpers ====================

    /**
     * Gets saga instance from database by ID.
     * Selects specific columns to avoid CLOB issues with large text fields.
     *
     * @param sagaId Saga instance ID
     * @return Map of column values
     */
    protected Map<String, Object> getSagaInstance(String sagaId) {
        return jdbcTemplate.queryForMap(
            "SELECT id, document_type, document_id, current_step, status, created_at, updated_at, completed_at, error_message, retry_count, max_retries FROM saga_instances WHERE id = ?",
            sagaId);
    }

    /**
     * Gets saga instance from database by document type and ID.
     * Selects specific columns to avoid CLOB issues with large text fields.
     *
     * @param type Document type
     * @param documentId Document ID
     * @return Map of column values
     */
    protected Map<String, Object> getSagaInstanceByDocumentId(DocumentType type, String documentId) {
        return jdbcTemplate.queryForMap(
            "SELECT id, document_type, document_id, current_step, status, created_at, updated_at, completed_at, error_message, retry_count, max_retries FROM saga_instances WHERE document_type = ? AND document_id = ?",
            type.name(), documentId);
    }

    /**
     * Gets command history for a saga from database.
     * Selects specific columns to avoid CLOB issues with large payload fields.
     *
     * @param sagaId Saga instance ID
     * @return List of command records ordered by created_at
     */
    protected List<Map<String, Object>> getCommandHistory(String sagaId) {
        return jdbcTemplate.queryForList(
            "SELECT id, saga_id, command_type, target_step, status, created_at, completed_at, error_message FROM saga_commands WHERE saga_id = ? ORDER BY created_at",
            sagaId);
    }

    /**
     * Gets outbox events for a saga from database.
     *
     * @param sagaId Saga instance ID
     * @return List of outbox events ordered by created_at
     */
    protected List<Map<String, Object>> getOutboxEvents(String sagaId) {
        return jdbcTemplate.queryForList(
            "SELECT * FROM outbox_events WHERE aggregate_id = ? ORDER BY created_at",
            sagaId);
    }

    // ==================== Async Polling Helpers ====================

    /**
     * Waits for saga to reach expected status.
     *
     * @param sagaId Saga instance ID
     * @param expectedStatus Expected status value
     */
    protected void awaitSagaStatus(String sagaId, String expectedStatus) {
        await().until(() -> {
            Map<String, Object> saga = getSagaInstance(sagaId);
            return expectedStatus.equals(saga.get("status"));
        });
    }

    /**
     * Waits for saga to reach expected current step.
     *
     * @param sagaId Saga instance ID
     * @param expectedStep Expected step enum value
     */
    protected void awaitCurrentStep(String sagaId, SagaStep expectedStep) {
        await().until(() -> {
            Map<String, Object> saga = getSagaInstance(sagaId);
            return expectedStep.name().equals(saga.get("current_step"));
        });
    }

    /**
     * Waits for outbox event of specific type to be created.
     *
     * @param sagaId Saga instance ID
     * @param eventType Expected event type (e.g., "SagaStartedEvent")
     */
    protected void awaitOutboxEvent(String sagaId, String eventType) {
        await().until(() -> {
            List<Map<String, Object>> events = getOutboxEvents(sagaId);
            return events.stream()
                .anyMatch(e -> eventType.equals(e.get("event_type")));
        });
    }

    /**
     * Waits for a saga to exist in database by document type and ID.
     * Returns just the saga ID to avoid CLOB issues with large text fields.
     *
     * @param type Document type
     * @param documentId Document ID
     * @return The saga ID of the found saga
     */
    protected String awaitSagaByDocumentId(DocumentType type, String documentId) {
        await().until(() -> {
            try {
                getSagaInstanceByDocumentId(type, documentId);
                return true;
            } catch (org.springframework.dao.EmptyResultDataAccessException e) {
                return false;
            }
        });
        Map<String, Object> saga = getSagaInstanceByDocumentId(type, documentId);
        return (String) saga.get("id");
    }

    // ==================== Test Data Helpers ====================

    /**
     * Creates test DocumentMetadata.
     *
     * @param documentId Document ID
     * @return DocumentMetadata instance
     */
    protected DocumentMetadata createTestMetadata(String documentId) {
        return DocumentMetadata.builder()
            .filePath("/test/path/" + documentId + ".xml")
            .xmlContent("<test>content</test>")
            .metadata(java.util.Map.of("invoiceNumber", "INV-" + documentId))
            .fileSize(1024L)
            .mimeType("application/xml")
            .checksum("abc123")
            .build();
    }

    /**
     * Starts a test saga with mocked command publisher.
     * This prevents sending real commands to downstream services.
     *
     * @param type Document type
     * @param documentId Document ID
     * @return Created saga ID (String)
     */
    protected String startTestSaga(DocumentType type, String documentId) {
        // Mock command publisher to avoid sending real commands
        doAnswer(invocation -> null).when(sagaCommandPublisher).publishCommandForStep(any(), any(), any());
        doAnswer(invocation -> null).when(sagaCommandPublisher).publishCompensationCommand(any(), any(), any());

        DocumentMetadata metadata = createTestMetadata(documentId);
        SagaInstance saga = sagaApplicationService.startSaga(type, documentId, metadata);
        return saga.getId();
    }

    // ==================== Step Completion Helpers ====================

    /**
     * Complete all invoice workflow steps via replies.
     *
     * @param sagaId Saga instance ID
     */
    protected void completeAllInvoiceSteps(String sagaId) {
        String[] steps = {"process-invoice", "sign-xml", "generate-invoice-pdf",
                          "sign-pdf", "store-document", "send-ebms"};
        for (String step : steps) {
            sendInvoiceReply(sagaId, step, true, null);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Complete all tax invoice workflow steps via replies.
     *
     * @param sagaId Saga instance ID
     */
    protected void completeAllTaxInvoiceSteps(String sagaId) {
        String[] steps = {"process-tax-invoice", "sign-xml", "generate-tax-invoice-pdf",
                          "sign-pdf", "store-document", "send-ebms"};
        for (String step : steps) {
            sendTaxInvoiceReply(sagaId, step, true, null);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}