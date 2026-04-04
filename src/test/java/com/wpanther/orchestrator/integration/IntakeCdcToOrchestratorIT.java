package com.wpanther.orchestrator.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.orchestrator.infrastructure.adapter.in.messaging.StartSagaCommand;
import com.wpanther.orchestrator.integration.config.ConsumerTestConfiguration;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test: verifies the complete CDC path from intake_db → Debezium → saga.commands.orchestrator
 * → StartSagaCommandConsumer → orchestrator_db.saga_instances.
 *
 * Prerequisites (external):
 *   ./scripts/test-containers-start.sh --with-debezium --auto-deploy-connectors
 *   (intake_db schema must exist — run: cd services/document-intake-service && mvn flyway:migrate
 *     -Dflyway.url=jdbc:postgresql://localhost:5433/intake_db -Dflyway.user=postgres -Dflyway.password=postgres)
 *
 * Run:
 *   mvn test -Pintegration -Dtest=IntakeCdcToOrchestratorIT \
 *     -Dspring.profiles.active=saga-flow-test -Dintegration.tests.enabled=true
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5433/orchestrator_db",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.kafka.bootstrap-servers=localhost:9093",
        "KAFKA_BROKERS=localhost:9093"
    }
)
@ActiveProfiles("saga-flow-test")
@Import(ConsumerTestConfiguration.class)
@Tag("integration")
@DisplayName("Intake CDC → Orchestrator Consumer Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.DisplayName.class)
@EnabledIfSystemProperty(named = "integration.tests.enabled", matches = "true")
class IntakeCdcToOrchestratorIT {

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final String DEBEZIUM_URL             = "http://localhost:8083";
    private static final String TOPIC_SAGA_COMMANDS      = "saga.commands.orchestrator";
    private static final String KAFKA_BOOTSTRAP_SERVERS  = "localhost:9093";

    // ── Injected ──────────────────────────────────────────────────────────────

    @Autowired
    private JdbcTemplate jdbcTemplate;                    // orchestrator_db

    @Autowired
    @Qualifier("intakeJdbcTemplate")
    private JdbcTemplate intakeJdbcTemplate;              // intake_db

    @Autowired
    private ObjectMapper objectMapper;

    // ── Per-test spy consumer ─────────────────────────────────────────────────

    private KafkaConsumer<String, String> spyConsumer;

    // ── HTTP client for Debezium checks ───────────────────────────────────────

    private final HttpClient httpClient = HttpClient.newHttpClient();

    // ── @TestConfiguration for intakeJdbcTemplate ────────────────────────────

    @TestConfiguration
    static class IntakeDbConfig {
        @Bean("intakeJdbcTemplate")
        public JdbcTemplate intakeJdbcTemplate() {
            DriverManagerDataSource ds = new DriverManagerDataSource();
            ds.setDriverClassName("org.postgresql.Driver");
            ds.setUrl("jdbc:postgresql://localhost:5433/intake_db");
            ds.setUsername("postgres");
            ds.setPassword("postgres");
            return new JdbcTemplate(ds);
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @BeforeAll
    void waitForDebeziumConnectors() {
        await().atMost(Duration.ofMinutes(2)).pollInterval(Duration.ofSeconds(5))
            .until(() -> isConnectorRunning("outbox-connector-intake"));
        await().atMost(Duration.ofMinutes(2)).pollInterval(Duration.ofSeconds(5))
            .until(() -> isConnectorRunning("outbox-connector-orchestrator"));
    }

    @BeforeEach
    void cleanupAndCreateSpyConsumer() {
        // Clean intake outbox
        intakeJdbcTemplate.update("DELETE FROM outbox_events");
        // Clean orchestrator tables (FK order)
        jdbcTemplate.update("DELETE FROM saga_commands");
        jdbcTemplate.update("DELETE FROM outbox_events");
        jdbcTemplate.update("DELETE FROM saga_data");
        jdbcTemplate.update("DELETE FROM saga_instances");

        // Create a fresh spy consumer subscribed to saga.commands.orchestrator.
        // Seeks to end so only messages published after this point are visible.
        spyConsumer = createSpyConsumer();
    }

    @AfterEach
    void closeSpyConsumer() {
        if (spyConsumer != null) {
            spyConsumer.close();
        }
    }

    // ── Helper: insert outbox row in intake_db ────────────────────────────────

    /**
     * Inserts a StartSagaCommand row into intake_db.outbox_events exactly as the
     * document-intake-service would (confirmed from live DB inspection 2026-04-03).
     *
     * Payload shape:
     * {"eventId":"<uuid>","occurredAt":"<instant>","eventType":"StartSagaCommand",
     *  "version":1,"sagaId":null,"sagaStep":null,"correlationId":"<corr>",
     *  "documentId":"<docId>","documentType":"<type>","documentNumber":"<num>",
     *  "xmlContent":"<xml>","source":"TEST"}
     */
    protected void insertIntakeOutboxRow(String documentId, String correlationId,
                                         String documentType, String documentNumber,
                                         String xmlContent) {
        String eventId     = UUID.randomUUID().toString();
        String occurredAt  = Instant.now().toString();
        String payload = String.format(
            "{\"eventId\":\"%s\",\"occurredAt\":\"%s\",\"eventType\":\"StartSagaCommand\","
            + "\"version\":1,\"sagaId\":null,\"sagaStep\":null,\"correlationId\":\"%s\","
            + "\"documentId\":\"%s\",\"documentType\":\"%s\",\"documentNumber\":\"%s\","
            + "\"xmlContent\":\"%s\",\"source\":\"TEST\"}",
            eventId, occurredAt, correlationId,
            documentId, documentType, documentNumber,
            xmlContent.replace("\"", "\\\""));

        String headers = String.format(
            "{\"documentType\":\"%s\",\"correlationId\":\"%s\"}", documentType, correlationId);

        intakeJdbcTemplate.update(
            "INSERT INTO outbox_events "
            + "(id, aggregate_type, aggregate_id, event_type, payload, topic, "
            + " partition_key, headers, status, retry_count, created_at) "
            + "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 0, ?)",
            UUID.randomUUID().toString(),
            "IncomingDocument",
            documentId,
            "StartSagaCommand",
            payload,
            "saga.commands.orchestrator",
            correlationId,
            headers,
            java.sql.Timestamp.from(Instant.now())
        );
    }

    // ── Helper: raw spy consumer ──────────────────────────────────────────────

    private KafkaConsumer<String, String> createSpyConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_BOOTSTRAP_SERVERS);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "spy-intake-cdc-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of(TOPIC_SAGA_COMMANDS));
        // Poll until partition assignment completes
        while (consumer.assignment().isEmpty()) {
            consumer.poll(Duration.ofMillis(200));
        }
        // Seek to end so we only receive messages published after this point
        var endOffsets = consumer.endOffsets(consumer.assignment());
        for (var tp : consumer.assignment()) {
            consumer.seek(tp, endOffsets.get(tp));
        }
        return consumer;
    }

    /**
     * Polls spy consumer until a message with the given key arrives (up to timeout).
     */
    protected ConsumerRecord<String, String> awaitSpyMessage(String key) {
        List<ConsumerRecord<String, String>> found = new ArrayList<>();
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500))
            .until(() -> {
                ConsumerRecords<String, String> records = spyConsumer.poll(Duration.ofMillis(200));
                var assignment = spyConsumer.assignment();
                for (var tp : assignment) {
                    long beginning = spyConsumer.beginningOffsets(List.of(tp)).get(tp);
                    long end = spyConsumer.endOffsets(List.of(tp)).get(tp);
                    System.out.println("[SPY] topic=" + tp.topic() + " partition=" + tp.partition()
                        + " beginning=" + beginning + " end=" + end);
                }
                System.out.println("[SPY] poll returned " + records.count() + " record(s), partitions=" + assignment);
                for (ConsumerRecord<String, String> r : records) {
                    System.out.println("[SPY]   key=" + r.key() + " topic=" + r.topic() + " partition=" + r.partition() + " offset=" + r.offset());
                    if (key.equals(r.key())) found.add(r);
                }
                return !found.isEmpty();
            });
        return found.get(0);
    }

    // ── Helper: Debezium connector check ──────────────────────────────────────

    private boolean isConnectorRunning(String connectorName) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(DEBEZIUM_URL + "/connectors/" + connectorName + "/status"))
                .GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body().contains("\"state\":\"RUNNING\"");
        } catch (Exception e) {
            return false;
        }
    }

    // ── Helper: Awaitility factory ────────────────────────────────────────────

    protected ConditionFactory await() {
        return Awaitility.await()
            .atMost(Duration.ofMinutes(2))
            .pollInterval(Duration.ofSeconds(2));
    }

    // ── Test Cases ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-1: Should deliver StartSagaCommand from intake_db outbox to saga.commands.orchestrator via CDC")
    void shouldDeliverStartSagaCommandViaCdcToKafka() throws Exception {
        // Given
        String documentId   = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();

        // Spy consumer is already created and positioned at "latest" offset in @BeforeEach.
        // When — insert outbox row into intake_db (Debezium picks it up and publishes)
        insertIntakeOutboxRow(documentId, correlationId, "TAX_INVOICE", "TIV-TEST-001", "<test/>");

        // Then — spy consumer receives message with correlationId as key
        ConsumerRecord<String, String> record = awaitSpyMessage(correlationId);

        // Log raw value for investigation
        System.out.println("=== TC-1 Raw CDC payload ===");
        System.out.println("Key:   " + record.key());
        System.out.println("Value: " + record.value());
        System.out.println("============================");

        // Parse and assert payload fields
        JsonNode payload = objectMapper.readTree(record.value());

        assertThat(record.key()).isEqualTo(correlationId);
        assertThat(payload.get("documentId").asText()).isEqualTo(documentId);
        assertThat(payload.get("documentType").asText()).isEqualTo("TAX_INVOICE");
        assertThat(payload.get("correlationId").asText()).isEqualTo(correlationId);
        // Debezium's expand.json.payload re-serializes JSON, omitting null fields.
        // sagaId/sagaStep are null in the DB payload but absent from the Kafka message.
        assertThat(payload.has("sagaId")).isFalse();
        assertThat(payload.has("sagaStep")).isFalse();
    }

    @Test
    @DisplayName("TC-2: Should create saga instance in orchestrator_db from CDC message")
    void shouldOrchestratorConsumerCreateSagaFromCdcMessage() {
        // Given
        String documentId   = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();

        // When — insert outbox row into intake_db
        insertIntakeOutboxRow(documentId, correlationId, "TAX_INVOICE", "TIV-TEST-002", "<test/>");

        // Then — wait for saga instance to appear in orchestrator_db
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofSeconds(1))
            .until(() -> {
                try {
                    jdbcTemplate.queryForMap(
                        "SELECT id FROM saga_instances WHERE document_type = 'TAX_INVOICE' AND document_id = ?",
                        documentId);
                    return true;
                } catch (org.springframework.dao.EmptyResultDataAccessException e) {
                    return false;
                }
            });

        // Verify saga instance details
        Map<String, Object> saga = jdbcTemplate.queryForMap(
            "SELECT id, document_type, document_id, current_step, status, correlation_id "
            + "FROM saga_instances WHERE document_id = ?", documentId);

        assertThat(saga.get("document_type")).isEqualTo("TAX_INVOICE");
        assertThat(saga.get("current_step")).isEqualTo("PROCESS_TAX_INVOICE");
        assertThat(saga.get("status")).isEqualTo("IN_PROGRESS");
        assertThat(saga.get("correlation_id")).isEqualTo(correlationId);

        System.out.println("=== TC-2 Saga created ===");
        System.out.println("sagaId=" + saga.get("id"));
        System.out.println("documentId=" + saga.get("document_id"));
        System.out.println("status=" + saga.get("status"));
        System.out.println("currentStep=" + saga.get("current_step"));
        System.out.println("===========================");
    }

    @Test
    @DisplayName("TC-3: Should deserializable raw CDC payload by orchestrator JsonDeserializer")
    void shouldRawCdcPayloadBeDeserializableByOrchestratorConsumer() throws Exception {
        // Given
        String documentId   = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();

        // When — insert outbox row into intake_db
        insertIntakeOutboxRow(documentId, correlationId, "TAX_INVOICE", "TIV-TEST-003", "<test-xml/>");

        // Capture raw CDC message via spy consumer
        ConsumerRecord<String, String> record = awaitSpyMessage(correlationId);

        // Then — deserialize using same JsonDeserializer as KafkaConfig
        JsonDeserializer<StartSagaCommand> deserializer = new JsonDeserializer<>(
            StartSagaCommand.class, objectMapper, false);
        deserializer.addTrustedPackages("com.wpanther.orchestrator.infrastructure.adapter.in.messaging");

        StartSagaCommand command = deserializer.deserialize(
            "saga.commands.orchestrator",
            record.headers(),
            record.value().getBytes(StandardCharsets.UTF_8));

        assertThat(command).isNotNull();
        assertThat(command.getDocumentId()).isEqualTo(documentId);
        assertThat(command.getDocumentType()).isEqualTo("TAX_INVOICE");
        assertThat(command.getCorrelationId()).isEqualTo(correlationId);
        assertThat(command.getXmlContent()).isNotNull();
        assertThat(command.getDocumentNumber()).isEqualTo("TIV-TEST-003");

        System.out.println("=== TC-3 Deserialization OK ===");
        System.out.println("documentId=" + command.getDocumentId());
        System.out.println("documentType=" + command.getDocumentType());
        System.out.println("correlationId=" + command.getCorrelationId());
        System.out.println("xmlContent=" + command.getXmlContent());
        System.out.println("================================");
    }

}
