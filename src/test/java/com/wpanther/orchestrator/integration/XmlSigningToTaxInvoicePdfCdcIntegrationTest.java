package com.wpanther.orchestrator.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.saga.domain.enums.SagaStep;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cross-service CDC integration tests verifying real service-to-service communication:
 * xml-signing-service signs XML, publishes reply via CDC, orchestrator consumes reply
 * and publishes the next command (ProcessTaxInvoicePdfCommand) via CDC.
 *
 * <pre>
 * TC-01  saga.command.xml-signing → xml-signing-service → saga.reply.xml-signing (CDC)
 *        → orchestrator SagaReplyConsumer → saga.command.tax-invoice-pdf (CDC)
 * TC-02  saga.command.xml-signing with invalid XML → xml-signing-service → saga.reply.xml-signing (FAILURE)
 *        → orchestrator SagaReplyConsumer → saga starts compensation
 * TC-03  saga.command.xml-signing → xml-signing-service → saga.reply.xml-signing (SUCCESS)
 *        → orchestrator advances to GENERATE_TAX_INVOICE_PDF → saga.command.tax-invoice-pdf (CDC)
 * TC-04  saga.command.xml-signing replay → idempotent handling by xml-signing-service
 * </pre>
 *
 * <p><b>Preconditions:</b>
 * <pre>
 *   ./scripts/test-containers-start.sh --with-eidas --with-debezium --auto-deploy-connectors
 *   cd services/xml-signing-service && mvn spring-boot:run -Dspring-boot.run.profiles=full-integration-test &
 * </pre>
 *
 * <p><b>Run:</b>
 * <pre>
 *   mvn test -Pintegration -Dtest=XmlSigningToTaxInvoicePdfCdcIntegrationTest \
 *            -Dspring.profiles.active=cross-service-test
 * </pre>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.kafka.bootstrap-servers=localhost:9093",
                "KAFKA_BROKERS=localhost:9093"
        }
)
@ActiveProfiles("cross-service-test")
@Import(com.wpanther.orchestrator.integration.config.TestKafkaProducerConfig.class)
@Tag("cross-service")
@DisplayName("Cross-Service: saga.reply.xml-signing \u2192 saga.command.tax-invoice-pdf")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.DisplayName.class)
@EnabledIfSystemProperty(named = "integration.tests.enabled", matches = "true")
class XmlSigningToTaxInvoicePdfCdcIntegrationTest {

    // ── Infrastructure constants ──────────────────────────────────────────────

    private static final String DEBEZIUM_URL                = "http://localhost:8083";
    private static final String DEBEZIUM_CONNECTOR_ORCH     = "outbox-connector-orchestrator";
    private static final String DEBEZIUM_CONNECTOR_XMLSIGN  = "outbox-connector-xmlsigning";
    private static final String KAFKA_BOOTSTRAP_SERVERS     = "localhost:9093";

    // ── Saga command topics (CDC delivers to these) ───────────────────────────

    private static final String TOPIC_CMD_XML_SIGNING       = "saga.command.xml-signing";
    private static final String TOPIC_CMD_TAX_INVOICE_PDF   = "saga.command.tax-invoice-pdf";

    // ── Saga reply topics (services publish to these via CDC) ─────────────────

    private static final String TOPIC_REPLY_XML_SIGNING     = "saga.reply.xml-signing";

    // ── Lifecycle topics ──────────────────────────────────────────────────────

    private static final String TOPIC_LIFECYCLE_COMPLETED   = "saga.lifecycle.completed";
    private static final String TOPIC_LIFECYCLE_FAILED      = "saga.lifecycle.failed";

    // ── Classpath resource ────────────────────────────────────────────────────

    private static final String TAX_INVOICE_SAMPLE = "samples/TaxInvoice_2p1_valid.xml";

    // ── Spring beans ──────────────────────────────────────────────────────────

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private KafkaTemplate<String, String> testKafkaProducer;

    @Autowired
    private ObjectMapper objectMapper;

    // ── Test-only Kafka consumer (subscribes to command + reply topics) ───────

    private KafkaConsumer<String, String> testConsumer;

    /**
     * Received messages per topic, keyed by sagaId (message key set by Debezium from
     * outbox_events.partition_key which is set to saga.getId()).
     */
    private final Map<String, List<ConsumerRecord<String, String>>> receivedMessages
            = new ConcurrentHashMap<>();

    private final HttpClient httpClient = HttpClient.newHttpClient();

    // ── External service endpoints (Docker test containers) ───────────────────

    private static final String MINIO_ENDPOINT = "http://localhost:9100";

    // ── TaxInvoice XML content (loaded once) ─────────────────────────────────

    private String taxInvoiceXmlContent;

    // ═══════════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ═══════════════════════════════════════════════════════════════════════════

    @BeforeAll
    void setUpInfrastructure() throws Exception {
        // Load TaxInvoice XML from classpath resource
        taxInvoiceXmlContent = new ClassPathResource(TAX_INVOICE_SAMPLE)
                .getContentAsString(StandardCharsets.UTF_8);

        // Verify BOTH Debezium connectors (orchestrator AND xml-signing) are running
        verifyDebeziumConnectorRunning();

        testConsumer = createTestConsumer();
        testConsumer.subscribe(List.of(
                TOPIC_CMD_XML_SIGNING,
                TOPIC_CMD_TAX_INVOICE_PDF,
                TOPIC_REPLY_XML_SIGNING,
                TOPIC_LIFECYCLE_COMPLETED,
                TOPIC_LIFECYCLE_FAILED
        ));
    }

    @AfterAll
    void tearDownInfrastructure() {
        if (testConsumer != null) {
            testConsumer.close();
        }
    }

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM saga_commands");
        jdbcTemplate.update("DELETE FROM outbox_events");
        jdbcTemplate.update("DELETE FROM saga_data");
        jdbcTemplate.update("DELETE FROM saga_instances");
        receivedMessages.clear();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test Cases (to be implemented in subsequent tasks)
    // ═══════════════════════════════════════════════════════════════════════════

    // ═══════════════════════════════════════════════════════════════════════════
    // TC-01: Happy Path
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Happy Path")
    class HappyPath {

        @Test
        @DisplayName("TC-01: xml-signing -> orchestrator consumes reply -> publishes saga.command.tax-invoice-pdf")
        void shouldPublishTaxInvoicePdfCommandAfterXmlSigning() throws Exception {
            // Given -- saga positioned at SIGN_XML with valid TaxInvoice XML
            SagaSetup saga = createSagaAtSignXml(UUID.randomUUID().toString());

            // When -- publish saga.command.xml-signing to Kafka
            // xml-signing-service consumes, signs via eidasremotesigning, stores in MinIO,
            // and publishes saga.reply.xml-signing via CDC
            sendXmlSigningCommand(saga.sagaId(), saga.documentId(), saga.correlationId(), taxInvoiceXmlContent);

            // Then -- wait for orchestrator to process reply and publish saga.command.tax-invoice-pdf via CDC
            awaitCommandOnTopic(TOPIC_CMD_TAX_INVOICE_PDF, saga.correlationId());

            ConsumerRecord<String, String> record = getFirstCommandFromTopic(
                    TOPIC_CMD_TAX_INVOICE_PDF, saga.correlationId());
            assertThat(record).isNotNull();
            assertThat(record.key()).isEqualTo(saga.correlationId());

            // Verify command payload
            JsonNode payload = parseJson(record.value());
            assertThat(payload.get("sagaId").asText()).isEqualTo(saga.sagaId());
            assertThat(payload.get("sagaStep").asText()).isEqualTo("generate-tax-invoice-pdf");
            assertThat(payload.get("documentId").asText()).isEqualTo(saga.documentId());

            // Verify signedXmlUrl is populated (comes from xml-signing-service reply)
            assertThat(payload.has("signedXmlUrl")).isTrue();
            assertThat(payload.get("signedXmlUrl").asText()).isNotBlank();

            // Verify saga state in DB
            awaitCurrentStep(saga.sagaId(), SagaStep.GENERATE_TAX_INVOICE_PDF);
            Map<String, Object> sagaRow = getSagaRow(saga.sagaId());
            assertThat(sagaRow.get("status")).isEqualTo("IN_PROGRESS");
            assertThat(sagaRow.get("current_step")).isEqualTo("GENERATE_TAX_INVOICE_PDF");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TC-02: MinIO Verification
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("MinIO Verification")
    class MinioVerification {

        @Test
        @DisplayName("TC-02: signed XML from command is accessible in MinIO and contains XAdES signature")
        void shouldVerifySignedXmlStoredInMinIO() throws Exception {
            // Given -- saga positioned at SIGN_XML
            SagaSetup saga = createSagaAtSignXml(UUID.randomUUID().toString());

            // When -- publish xml-signing command
            sendXmlSigningCommand(saga.sagaId(), saga.documentId(), saga.correlationId(), taxInvoiceXmlContent);

            // Then -- wait for saga.command.tax-invoice-pdf to appear via CDC
            awaitCommandOnTopic(TOPIC_CMD_TAX_INVOICE_PDF, saga.correlationId());
            ConsumerRecord<String, String> record = getFirstCommandFromTopic(
                    TOPIC_CMD_TAX_INVOICE_PDF, saga.correlationId());
            assertThat(record).isNotNull();

            JsonNode payload = parseJson(record.value());
            String signedXmlUrl = payload.get("signedXmlUrl").asText();
            assertThat(signedXmlUrl).isNotBlank();

            // Fetch signed XML from MinIO
            String minioPath = signedXmlUrl.replace(MINIO_ENDPOINT + "/signed-xml-documents/", "");
            HttpRequest getRequest = HttpRequest.newBuilder()
                    .uri(URI.create(MINIO_ENDPOINT + "/signed-xml-documents/" + minioPath))
                    .GET().build();
            HttpResponse<String> getResponse = httpClient.send(getRequest, HttpResponse.BodyHandlers.ofString());
            assertThat(getResponse.statusCode()).isEqualTo(200);

            String signedXml = getResponse.body();
            // Verify XAdES signature elements present
            assertThat(signedXml).contains("Signature");
            assertThat(signedXml).contains("SignedInfo");
            assertThat(signedXml).contains("SignatureValue");
            // Signed XML should be larger than original (signature adds content)
            assertThat(signedXml.length()).isGreaterThan(taxInvoiceXmlContent.length());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TC-03: Error Handling
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        @DisplayName("TC-03: xml-signing failure does NOT advance saga to GENERATE_TAX_INVOICE_PDF")
        void shouldNotAdvanceWhenXmlSigningFails() throws Exception {
            // Given -- saga positioned at SIGN_XML
            SagaSetup saga = createSagaAtSignXml(UUID.randomUUID().toString());

            // When -- publish xml-signing command with invalid XML (too short, fails validation)
            String invalidXml = "<invalid>too short to pass validation minimum</invalid>";
            sendXmlSigningCommand(saga.sagaId(), saga.documentId(), saga.correlationId(), invalidXml);

            // Then -- wait briefly and verify saga stays at SIGN_XML
            Awaitility.await()
                    .atMost(Duration.ofSeconds(30))
                    .pollInterval(Duration.ofSeconds(2))
                    .untilAsserted(() -> {
                        Map<String, Object> sagaRow = getSagaRow(saga.sagaId());
                        assertThat(sagaRow.get("current_step")).isEqualTo("SIGN_XML");
                    });

            // Verify saga.command.tax-invoice-pdf was NOT published
            // Poll multiple times to ensure no message arrives
            for (int i = 0; i < 6; i++) {
                pollMessages();
                Thread.sleep(2000);
            }
            boolean hasPdfCommand = hasMessageOnTopic(TOPIC_CMD_TAX_INVOICE_PDF, saga.correlationId());
            assertThat(hasPdfCommand).as("saga.command.tax-invoice-pdf should NOT be published for failed signing").isFalse();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TC-04: Concurrent Sagas
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Concurrency")
    class MultipleSagas {

        @Test
        @DisplayName("TC-04: two concurrent sagas both produce saga.command.tax-invoice-pdf")
        void shouldHandleMultipleSagasConcurrently() throws Exception {
            // Given -- two sagas at SIGN_XML
            SagaSetup saga1 = createSagaAtSignXml(UUID.randomUUID().toString());
            SagaSetup saga2 = createSagaAtSignXml(UUID.randomUUID().toString());

            // When -- publish both xml-signing commands
            sendXmlSigningCommand(saga1.sagaId(), saga1.documentId(), saga1.correlationId(), taxInvoiceXmlContent);
            sendXmlSigningCommand(saga2.sagaId(), saga2.documentId(), saga2.correlationId(), taxInvoiceXmlContent);

            // Then -- both should produce saga.command.tax-invoice-pdf via CDC
            awaitCommandOnTopic(TOPIC_CMD_TAX_INVOICE_PDF, saga1.correlationId());
            awaitCommandOnTopic(TOPIC_CMD_TAX_INVOICE_PDF, saga2.correlationId());

            // Verify saga1 command
            ConsumerRecord<String, String> record1 = getFirstCommandFromTopic(
                    TOPIC_CMD_TAX_INVOICE_PDF, saga1.correlationId());
            assertThat(record1).isNotNull();
            JsonNode payload1 = parseJson(record1.value());
            assertThat(payload1.get("sagaId").asText()).isEqualTo(saga1.sagaId());
            assertThat(payload1.get("documentId").asText()).isEqualTo(saga1.documentId());

            // Verify saga2 command
            ConsumerRecord<String, String> record2 = getFirstCommandFromTopic(
                    TOPIC_CMD_TAX_INVOICE_PDF, saga2.correlationId());
            assertThat(record2).isNotNull();
            JsonNode payload2 = parseJson(record2.value());
            assertThat(payload2.get("sagaId").asText()).isEqualTo(saga2.sagaId());
            assertThat(payload2.get("documentId").asText()).isEqualTo(saga2.documentId());

            // Verify both sagas advanced in DB
            awaitCurrentStep(saga1.sagaId(), SagaStep.GENERATE_TAX_INVOICE_PDF);
            awaitCurrentStep(saga2.sagaId(), SagaStep.GENERATE_TAX_INVOICE_PDF);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Saga setup helpers
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Record holding saga data created by {@link #createSagaAtSignXml(String)}.
     */
    record SagaSetup(String sagaId, String documentId, String correlationId) {}

    /**
     * Creates a fresh TAX_INVOICE saga directly in the database at the SIGN_XML step.
     * Uses raw JdbcTemplate to avoid JPA CLOB issues with TEXT columns.
     *
     * <p>The saga is inserted with status IN_PROGRESS and current_step SIGN_XML.
     * A saga_data record is inserted with file_path, and a saga_commands record
     * is inserted for the SIGN_XML step (status SENT).
     *
     * @param documentId the document ID for this saga
     * @return a {@link SagaSetup} record with the saga IDs
     */
    private SagaSetup createSagaAtSignXml(String documentId) {
        String sagaId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();
        String commandId = UUID.randomUUID().toString();

        Timestamp now = new Timestamp(System.currentTimeMillis());

        // Insert saga instance — use null for TEXT columns to avoid CLOB issues
        jdbcTemplate.update("""
            INSERT INTO saga_instances
            (id, document_type, document_id, current_step, status, created_at, updated_at,
             xml_content, metadata, correlation_id, retry_count, max_retries, version)
            VALUES (?, ?, ?, 'SIGN_XML', 'IN_PROGRESS', ?, ?,
             NULL, NULL, ?, 0, 3, 0)
            """,
            sagaId, "TAX_INVOICE", documentId, now, now, correlationId);

        // Insert saga_data record
        jdbcTemplate.update("""
            INSERT INTO saga_data
            (saga_id, file_path, xml_content, metadata, created_at)
            VALUES (?, ?, NULL, NULL, ?)
            """,
            sagaId, "/test/" + documentId + ".xml", now);

        // Insert the SIGN_XML command record (status SENT, not COMPLETED)
        jdbcTemplate.update("""
            INSERT INTO saga_commands
            (id, saga_id, command_type, target_step, payload, status, created_at, correlation_id)
            VALUES (?, ?, 'SIGN_XML_command', 'SIGN_XML', '{}', 'SENT', ?, ?)
            """,
            commandId, sagaId, now, correlationId);

        return new SagaSetup(sagaId, documentId, correlationId);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Kafka helpers
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Sends a ProcessXmlSigningCommand JSON to {@code saga.command.xml-signing}.
     *
     * <p>Builds a JSON matching {@code ProcessXmlSigningCommand} fields:
     * <pre>
     * {
     *   "eventId": "uuid",
     *   "occurredAt": "instant",
     *   "eventType": "ProcessXmlSigningCommand",
     *   "version": 1,
     *   "sagaId": "...",
     *   "sagaStep": "SIGN_XML",
     *   "correlationId": "...",
     *   "documentId": "...",
     *   "xmlContent": "...(escaped JSON string)...",
     *   "documentNumber": "TIV-TEST",
     *   "documentType": "TAX_INVOICE"
     * }
     * </pre>
     *
     * @param sagaId        the saga instance ID
     * @param documentId    the document ID
     * @param correlationId the correlation ID (also used as Kafka message key)
     * @param xmlContent    the XML content to sign
     */
    private void sendXmlSigningCommand(String sagaId, String documentId,
                                        String correlationId, String xmlContent) {
        String documentNumber = "TIV-TEST";
        String eventType = "ProcessXmlSigningCommand";
        String sagaStep = "SIGN_XML";

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"eventId\": \"").append(UUID.randomUUID()).append("\",\n");
        sb.append("  \"occurredAt\": \"").append(Instant.now()).append("\",\n");
        sb.append("  \"eventType\": \"").append(eventType).append("\",\n");
        sb.append("  \"version\": 1,\n");
        sb.append("  \"sagaId\": \"").append(sagaId).append("\",\n");
        sb.append("  \"sagaStep\": \"").append(sagaStep).append("\",\n");
        sb.append("  \"correlationId\": \"").append(correlationId).append("\",\n");
        sb.append("  \"documentId\": \"").append(documentId).append("\",\n");
        sb.append("  \"xmlContent\": \"").append(escapeJson(xmlContent)).append("\",\n");
        sb.append("  \"documentNumber\": \"").append(documentNumber).append("\",\n");
        sb.append("  \"documentType\": \"TAX_INVOICE\"\n");
        sb.append("}");

        sendMessage(TOPIC_CMD_XML_SIGNING, correlationId, sb.toString());
    }

    /** Sends a raw string message to a Kafka topic with the given key. */
    private void sendMessage(String topic, String key, String value) {
        try {
            testKafkaProducer.send(topic, key, value).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send to topic " + topic, e);
        }
    }

    /** Escapes a string for embedding as a JSON string value. */
    private String escapeJson(String raw) {
        return raw.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Consumer helpers — poll topics and check for messages
    // ═══════════════════════════════════════════════════════════════════════════

    /** Creates a test KafkaConsumer with unique group ID and earliest offset reset. */
    private KafkaConsumer<String, String> createTestConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_BOOTSTRAP_SERVERS);
        // Unique group so we always read from the earliest offset
        props.put(ConsumerConfig.GROUP_ID_CONFIG,
                "cross-service-test-cdc-" + System.currentTimeMillis());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        return new KafkaConsumer<>(props);
    }

    /** Drains pending records from the test consumer and caches them by topic. */
    private void pollMessages() {
        ConsumerRecords<String, String> records = testConsumer.poll(Duration.ofMillis(500));
        for (ConsumerRecord<String, String> record : records) {
            receivedMessages
                    .computeIfAbsent(record.topic(), k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(record);
        }
    }

    /** Returns true if at least one message with key {@code sagaId} exists on {@code topic}. */
    private boolean hasMessageOnTopic(String topic, String sagaId) {
        pollMessages();
        List<ConsumerRecord<String, String>> msgs = receivedMessages.get(topic);
        return msgs != null && msgs.stream().anyMatch(r -> sagaId.equals(r.key()));
    }

    /**
     * Returns the first record keyed by {@code sagaId} on {@code topic}, or
     * {@code null} if none are present.
     */
    private ConsumerRecord<String, String> getFirstCommandFromTopic(String topic, String sagaId) {
        pollMessages();
        List<ConsumerRecord<String, String>> msgs = receivedMessages.get(topic);
        if (msgs == null) return null;
        return msgs.stream().filter(r -> sagaId.equals(r.key())).findFirst().orElse(null);
    }

    /**
     * Blocks until a message keyed by {@code sagaId} appears on {@code topic}
     * (3-minute timeout — CDC through two services is slower).
     */
    private void awaitCommandOnTopic(String topic, String sagaId) {
        Awaitility.await()
                .atMost(Duration.ofMinutes(3))
                .pollInterval(Duration.ofSeconds(1))
                .failFast("Debezium connector is no longer RUNNING",
                        () -> !isConnectorRunning(DEBEZIUM_CONNECTOR_ORCH))
                .failFast("Debezium connector is no longer RUNNING",
                        () -> !isConnectorRunning(DEBEZIUM_CONNECTOR_XMLSIGN))
                .until(() -> hasMessageOnTopic(topic, sagaId));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Database helpers
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Returns the saga row from saga_instances with selected columns
     * (avoids CLOB issues with TEXT columns).
     */
    private Map<String, Object> getSagaRow(String sagaId) {
        return jdbcTemplate.queryForMap(
                "SELECT id, document_type, document_id, current_step, status, "
                + "created_at, updated_at, completed_at, error_message, retry_count, correlation_id "
                + "FROM saga_instances WHERE id = ?", sagaId);
    }

    /**
     * Blocks until the saga's current_step matches {@code expectedStep}
     * (3-minute timeout).
     */
    private void awaitCurrentStep(String sagaId, SagaStep expectedStep) {
        Awaitility.await()
                .atMost(Duration.ofMinutes(3))
                .pollInterval(Duration.ofSeconds(1))
                .until(() -> expectedStep.name().equals(getSagaRow(sagaId).get("current_step")));
    }

    /**
     * Blocks until the saga's status matches {@code expectedStatus}
     * (3-minute timeout).
     */
    private void awaitSagaStatus(String sagaId, String expectedStatus) {
        Awaitility.await()
                .atMost(Duration.ofMinutes(3))
                .pollInterval(Duration.ofSeconds(1))
                .until(() -> expectedStatus.equals(getSagaRow(sagaId).get("status")));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Debezium helpers
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Verifies that BOTH Debezium connectors (orchestrator and xml-signing) are
     * in the RUNNING state before proceeding with tests.
     */
    private void verifyDebeziumConnectorRunning() {
        Awaitility.await()
                .atMost(Duration.ofMinutes(2))
                .pollInterval(Duration.ofSeconds(5))
                .failFast("Debezium Connect endpoint is unreachable at " + DEBEZIUM_URL, () -> {
                    try {
                        HttpRequest req = HttpRequest.newBuilder()
                                .uri(URI.create(DEBEZIUM_URL + "/connectors"))
                                .GET().build();
                        return httpClient.send(req, HttpResponse.BodyHandlers.ofString())
                                .statusCode() != 200;
                    } catch (Exception e) {
                        return false; // still trying
                    }
                })
                .until(() -> isConnectorRunning(DEBEZIUM_CONNECTOR_ORCH)
                        && isConnectorRunning(DEBEZIUM_CONNECTOR_XMLSIGN));
    }

    /**
     * Checks whether a Debezium connector with the given name is in RUNNING state.
     *
     * @param connectorName the Debezium connector name
     * @return true if the connector state is RUNNING, false otherwise
     */
    private boolean isConnectorRunning(String connectorName) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(DEBEZIUM_URL + "/connectors/" + connectorName + "/status"))
                    .GET().build();
            return httpClient.send(req, HttpResponse.BodyHandlers.ofString())
                    .body().contains("\"state\":\"RUNNING\"");
        } catch (Exception e) {
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // JSON helper
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Parses the raw string to a {@link JsonNode}.
     * Handles Debezium's double-encoded payload (where the value is a JSON-string
     * wrapping the real JSON object).
     */
    private JsonNode parseJson(String raw) throws Exception {
        JsonNode node = objectMapper.readTree(raw);
        return node.isTextual() ? objectMapper.readTree(node.asText()) : node;
    }
}
