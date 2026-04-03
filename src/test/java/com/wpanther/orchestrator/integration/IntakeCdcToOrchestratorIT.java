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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

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
        // Uses "latest" offset so only messages published after subscribe() are visible.
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
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of(TOPIC_SAGA_COMMANDS));
        // Initial poll to trigger partition assignment and advance to latest offset
        consumer.poll(Duration.ofMillis(500));
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
                for (ConsumerRecord<String, String> r : records) {
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

    // ── Placeholder methods — filled in Tasks 2, 3, 4 ───────────────────────

    // TC-1, TC-2, TC-3 added in subsequent tasks

}
