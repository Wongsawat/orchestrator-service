# Intake CDC → Orchestrator Consumer Integration Test — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create `IntakeCdcToOrchestratorIT` — a three-test integration class in `orchestrator-service` that inserts a row into `intake_db.outbox_events`, then verifies Debezium delivers the message to `saga.commands.orchestrator` and the `StartSagaCommandConsumer` creates a saga instance.

**Architecture:** The test uses the full Spring Boot app context (with real `StartSagaCommandConsumer`) via `@ActiveProfiles("saga-flow-test")` and `@Import(ConsumerTestConfiguration.class)`. A nested `@TestConfiguration` declares a second `JdbcTemplate` pointing at `intake_db`. A plain `KafkaConsumer<String, String>` spy is created per-test to capture raw Debezium output from `saga.commands.orchestrator`.

**Tech Stack:** Spring Boot 3.2.5, JUnit 5, Awaitility, Apache Kafka Client, Jackson `JsonDeserializer`, JdbcTemplate, Debezium (external), PostgreSQL (external, localhost:5433), Kafka (external, localhost:9093).

---

## File Map

| File | Action | Purpose |
|------|--------|---------|
| `src/test/java/com/wpanther/orchestrator/integration/IntakeCdcToOrchestratorIT.java` | **Create** | All three test methods + helpers + nested config |

No other files change.

---

## Prerequisite Check

Before running any task, confirm containers are running:

```bash
docker exec test-postgres pg_isready -U postgres   # should print: accepting connections
docker exec test-kafka kafka-broker-api-versions --bootstrap-server=localhost:9093 2>&1 | grep -c "ApiVersions"
curl -s http://localhost:8083/connectors | python3 -m json.tool
```

Also confirm `intake_db.outbox_events` table exists (was created by running Flyway migrations earlier):

```bash
docker exec test-postgres psql -U postgres -d intake_db -c "\dt outbox_events"
```

Expected output: `outbox_events | table | postgres`

---

## Task 1: Create Class Skeleton with Infrastructure

**File:**
- Create: `src/test/java/com/wpanther/orchestrator/integration/IntakeCdcToOrchestratorIT.java`

- [ ] **Step 1.1: Create the file with class structure, annotations, and infrastructure helpers**

```java
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
```

- [ ] **Step 1.2: Compile the skeleton to verify it builds**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/orchestrator-service
mvn test-compile -q
```

Expected: `BUILD SUCCESS` (no test methods yet, just compilation).

- [ ] **Step 1.3: Commit skeleton**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/orchestrator-service
git add src/test/java/com/wpanther/orchestrator/integration/IntakeCdcToOrchestratorIT.java
git commit -m "test: add IntakeCdcToOrchestratorIT skeleton with infrastructure helpers"
```

---

## Task 2: TC-1 — Verify CDC Delivers to Kafka

**File:**
- Modify: `src/test/java/com/wpanther/orchestrator/integration/IntakeCdcToOrchestratorIT.java`

- [ ] **Step 2.1: Add TC-1 test method**

Add this test method inside the class body (replace the `// TC-1 placeholder` comment):

```java
@Test
@DisplayName("TC-1: Should deliver StartSagaCommand from intake_db outbox to saga.commands.orchestrator via CDC")
void shouldDeliverStartSagaCommandViaCdcToKafka() throws Exception {
    // Given
    String documentId   = UUID.randomUUID().toString();
    String correlationId = UUID.randomUUID().toString();

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
    // Confirm sagaId/sagaStep are present and null (intake payload format)
    assertThat(payload.has("sagaId")).isTrue();
    assertThat(payload.get("sagaId").isNull()).isTrue();
    assertThat(payload.has("sagaStep")).isTrue();
    assertThat(payload.get("sagaStep").isNull()).isTrue();
}
```

- [ ] **Step 2.2: Run TC-1 against live containers**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/orchestrator-service
mvn test -Pintegration \
  -Dtest="IntakeCdcToOrchestratorIT#shouldDeliverStartSagaCommandViaCdcToKafka" \
  -Dspring.profiles.active=saga-flow-test \
  -Dintegration.tests.enabled=true \
  -DDB_HOST=localhost -DDB_PORT=5433 \
  2>&1 | grep -E "PASS|FAIL|ERROR|TC-1|payload|Key:|Value:"
```

Expected: `PASS`. The `=== TC-1 Raw CDC payload ===` block printed to stdout shows the exact JSON Debezium delivers.

If `FAIL` with timeout: confirm `outbox-connector-intake` is RUNNING:
```bash
curl -s http://localhost:8083/connectors/outbox-connector-intake/status | python3 -m json.tool
```

- [ ] **Step 2.3: Commit TC-1**

```bash
git add src/test/java/com/wpanther/orchestrator/integration/IntakeCdcToOrchestratorIT.java
git commit -m "test: add TC-1 verify CDC delivers StartSagaCommand to Kafka"
```

---

## Task 3: TC-2 — Verify Orchestrator Consumer Creates Saga

**File:**
- Modify: `src/test/java/com/wpanther/orchestrator/integration/IntakeCdcToOrchestratorIT.java`

- [ ] **Step 3.1: Add TC-2 test method**

Add this method inside the class body:

```java
@Test
@DisplayName("TC-2: Should create saga instance in orchestrator_db after CDC message processed by StartSagaCommandConsumer")
void shouldOrchestratorConsumerCreateSagaFromCdcMessage() {
    // Given
    String documentId    = UUID.randomUUID().toString();
    String correlationId = UUID.randomUUID().toString();

    // When — insert outbox row; StartSagaCommandConsumer (running in Spring context) will process it
    insertIntakeOutboxRow(documentId, correlationId, "TAX_INVOICE", "TIV-TEST-002", "<test/>");

    // Then — saga instance created in orchestrator_db
    await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofSeconds(1))
        .until(() -> {
            try {
                jdbcTemplate.queryForMap(
                    "SELECT id FROM saga_instances WHERE document_id = ?", documentId);
                return true;
            } catch (org.springframework.dao.EmptyResultDataAccessException e) {
                return false;
            }
        });

    Map<String, Object> saga = jdbcTemplate.queryForMap(
        "SELECT id, document_type, document_id, current_step, status "
        + "FROM saga_instances WHERE document_id = ?",
        documentId);

    System.out.println("=== TC-2 Saga created ===");
    System.out.println("sagaId:       " + saga.get("id"));
    System.out.println("documentType: " + saga.get("document_type"));
    System.out.println("currentStep:  " + saga.get("current_step"));
    System.out.println("status:       " + saga.get("status"));
    System.out.println("=========================");

    assertThat(saga.get("document_type")).isEqualTo("TAX_INVOICE");
    assertThat(saga.get("document_id")).isEqualTo(documentId);
    assertThat(saga.get("current_step")).isEqualTo("PROCESS_TAX_INVOICE");
    assertThat(saga.get("status")).isEqualTo("IN_PROGRESS");
}
```

- [ ] **Step 3.2: Run TC-2 against live containers**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/orchestrator-service
mvn test -Pintegration \
  -Dtest="IntakeCdcToOrchestratorIT#shouldOrchestratorConsumerCreateSagaFromCdcMessage" \
  -Dspring.profiles.active=saga-flow-test \
  -Dintegration.tests.enabled=true \
  -DDB_HOST=localhost -DDB_PORT=5433 \
  2>&1 | grep -E "PASS|FAIL|ERROR|TC-2|sagaId|currentStep|status"
```

Expected: `PASS`. The `=== TC-2 Saga created ===` block confirms the saga was created with correct step/status.

If `FAIL` with `EmptyResultDataAccessException` timeout: the consumer is not processing. Check the orchestrator service logs:
```bash
tail -50 /tmp/orchestrator-service.log | grep -E "StartSagaCommand|ERROR|WARN"
```

- [ ] **Step 3.3: Commit TC-2**

```bash
git add src/test/java/com/wpanther/orchestrator/integration/IntakeCdcToOrchestratorIT.java
git commit -m "test: add TC-2 verify StartSagaCommandConsumer processes CDC message and creates saga"
```

---

## Task 4: TC-3 — Verify Raw Payload Deserializable by Consumer's JsonDeserializer

**File:**
- Modify: `src/test/java/com/wpanther/orchestrator/integration/IntakeCdcToOrchestratorIT.java`

- [ ] **Step 4.1: Add TC-3 test method**

Add this method inside the class body:

```java
@Test
@DisplayName("TC-3: Should deserialize raw CDC payload into StartSagaCommand using same JsonDeserializer as KafkaConfig")
void shouldRawCdcPayloadBeDeserializableByOrchestratorConsumer() throws Exception {
    // Given
    String documentId    = UUID.randomUUID().toString();
    String correlationId = UUID.randomUUID().toString();
    String documentNumber = "TIV-TEST-003";

    insertIntakeOutboxRow(documentId, correlationId, "TAX_INVOICE", documentNumber, "<invoice/>");

    // Capture raw CDC message
    ConsumerRecord<String, String> record = awaitSpyMessage(correlationId);
    String rawJson = record.value();

    // When — deserialize using the same config as KafkaConfig.startSagaCommandConsumerFactory()
    Map<String, Object> props = new HashMap<>();
    props.put(JsonDeserializer.TRUSTED_PACKAGES,
        "com.wpanther.orchestrator.infrastructure.adapter.in.messaging");

    JsonDeserializer<StartSagaCommand> deserializer =
        new JsonDeserializer<>(StartSagaCommand.class, objectMapper, false);
    deserializer.configure(props, false);

    byte[] rawBytes = rawJson.getBytes(StandardCharsets.UTF_8);

    // Then — no exception thrown, all fields populated
    assertThatNoException().isThrownBy(() -> {
        StartSagaCommand command = deserializer.deserialize(TOPIC_SAGA_COMMANDS, rawBytes);

        System.out.println("=== TC-3 Deserialized StartSagaCommand ===");
        System.out.println("documentId:     " + command.getDocumentId());
        System.out.println("documentType:   " + command.getDocumentType());
        System.out.println("correlationId:  " + command.getCorrelationId());
        System.out.println("documentNumber: " + command.getDocumentNumber());
        System.out.println("xmlContent:     " + command.getXmlContent());
        System.out.println("==========================================");

        assertThat(command.getDocumentId()).isEqualTo(documentId);
        assertThat(command.getDocumentType()).isEqualTo("TAX_INVOICE");
        assertThat(command.getCorrelationId()).isEqualTo(correlationId);
        assertThat(command.getDocumentNumber()).isEqualTo(documentNumber);
        assertThat(command.getXmlContent()).isNotNull();
    });

    deserializer.close();
}
```

- [ ] **Step 4.2: Run TC-3 against live containers**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/orchestrator-service
mvn test -Pintegration \
  -Dtest="IntakeCdcToOrchestratorIT#shouldRawCdcPayloadBeDeserializableByOrchestratorConsumer" \
  -Dspring.profiles.active=saga-flow-test \
  -Dintegration.tests.enabled=true \
  -DDB_HOST=localhost -DDB_PORT=5433 \
  2>&1 | grep -E "PASS|FAIL|ERROR|TC-3|documentId|documentType"
```

Expected: `PASS`. The `=== TC-3 Deserialized StartSagaCommand ===` block confirms every field round-trips correctly.

- [ ] **Step 4.3: Commit TC-3**

```bash
git add src/test/java/com/wpanther/orchestrator/integration/IntakeCdcToOrchestratorIT.java
git commit -m "test: add TC-3 verify raw CDC payload deserializes correctly into StartSagaCommand"
```

---

## Task 5: Run All Three Tests Together and Final Commit

- [ ] **Step 5.1: Run all three tests in one pass**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/orchestrator-service
mvn test -Pintegration \
  -Dtest=IntakeCdcToOrchestratorIT \
  -Dspring.profiles.active=saga-flow-test \
  -Dintegration.tests.enabled=true \
  -DDB_HOST=localhost -DDB_PORT=5433 \
  2>&1 | grep -E "Tests run|PASS|FAIL|ERROR|BUILD"
```

Expected:
```
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

- [ ] **Step 5.2: If all pass, tag the commit**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/orchestrator-service
git log --oneline -4
```

All three test commits should be visible. No further action needed — the implementation is complete.

---

## Notes on Failure Modes

| Symptom | Likely cause | Fix |
|---------|-------------|-----|
| TC-1 times out (no message on Kafka) | `outbox-connector-intake` not RUNNING | `curl http://localhost:8083/connectors/outbox-connector-intake/status` — restart connector if FAILED |
| TC-2 times out (no saga in DB) | `StartSagaCommandConsumer` not in Spring context | Verify `@ActiveProfiles("saga-flow-test")` and `@Import(ConsumerTestConfiguration.class)` are present |
| TC-3 deserialization error | JSON field mismatch between intake payload and orchestrator `StartSagaCommand` | Print `rawJson` to stdout and compare against `@JsonCreator` params in `StartSagaCommand.java:60-74` |
| `DataIntegrityViolationException` on insert | `intake_db.outbox_events` table missing | Run: `cd services/document-intake-service && mvn flyway:migrate -Dflyway.url=jdbc:postgresql://localhost:5433/intake_db -Dflyway.user=postgres -Dflyway.password=postgres` |
| Bean conflict: duplicate `jdbcTemplate` | `ConsumerTestConfiguration` + `IntakeDbConfig` both declare JdbcTemplate | The intake one uses qualifier `"intakeJdbcTemplate"` — no conflict; check `@Qualifier` annotation |
