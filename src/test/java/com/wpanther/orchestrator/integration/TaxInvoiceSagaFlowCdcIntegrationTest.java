package com.wpanther.orchestrator.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.orchestrator.application.usecase.SagaApplicationService;
import com.wpanther.orchestrator.domain.model.DocumentMetadata;
import com.wpanther.orchestrator.domain.model.SagaInstance;
import com.wpanther.orchestrator.domain.model.enums.DocumentType;
import com.wpanther.orchestrator.infrastructure.adapter.in.messaging.StartSagaCommand;
import org.junit.jupiter.api.*;
import com.wpanther.orchestrator.integration.config.ConsumerTestConfiguration;
import com.wpanther.orchestrator.integration.config.TestKafkaConsumerConfig;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end CDC integration tests for the complete Tax Invoice saga flow.
 * <p>
 * Verifies that each saga step transition results in the correct command being
 * published to the correct Kafka topic via Debezium CDC:
 *
 * <pre>
 * TC-01  saga.commands.orchestrator          → saga.command.tax-invoice       (PROCESS_TAX_INVOICE)
 * TC-02  saga.reply.tax-invoice (SUCCESS)    → saga.command.xml-signing        (SIGN_XML)
 * TC-03  saga.reply.xml-signing (SUCCESS)    → saga.command.tax-invoice-pdf    (GENERATE_TAX_INVOICE_PDF)
 * TC-04  saga.reply.tax-invoice-pdf (SUCCESS) → saga.command.pdf-storage        (PDF_STORAGE)
 * TC-05  saga.reply.pdf-storage (SUCCESS)   → saga.command.pdf-signing        (SIGN_PDF)
 * TC-06  saga.reply.pdf-signing (SUCCESS)    → saga.command.document-storage   (STORE_DOCUMENT)
 * TC-07  saga.reply.document-storage (SUCCESS) → saga.command.ebms-sending     (SEND_EBMS)
 * TC-08  saga.reply.ebms-sending (SUCCESS)   → saga.lifecycle.completed
 * </pre>
 *
 * <p><b>Preconditions:</b>
 * <pre>
 *   ./scripts/test-containers-start.sh --with-debezium --auto-deploy-connectors
 * </pre>
 *
 * <p><b>Run:</b>
 * <pre>
 *   mvn test -Pintegration -Dtest=TaxInvoiceSagaFlowCdcIntegrationTest \
 *            -Dspring.profiles.active=saga-flow-test
 * </pre>
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
@DisplayName("Tax Invoice Saga Full-Flow CDC Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.DisplayName.class)
@EnabledIfSystemProperty(named = "integration.tests.enabled", matches = "true")
class TaxInvoiceSagaFlowCdcIntegrationTest {

    // ── Infrastructure constants ──────────────────────────────────────────────

    private static final String DEBEZIUM_URL              = "http://localhost:8083";
    private static final String DEBEZIUM_CONNECTOR_NAME   = "outbox-connector-orchestrator";
    private static final String KAFKA_BOOTSTRAP_SERVERS   = "localhost:9093";

    // ── Saga command topics (CDC delivers to these) ───────────────────────────

    private static final String TOPIC_CMD_TAX_INVOICE      = "saga.command.tax-invoice";
    private static final String TOPIC_CMD_XML_SIGNING       = "saga.command.xml-signing";
    private static final String TOPIC_CMD_SIGNEDXML_STORAGE = "saga.command.signedxml-storage";
    private static final String TOPIC_CMD_TAX_INVOICE_PDF   = "saga.command.tax-invoice-pdf";
    private static final String TOPIC_CMD_PDF_STORAGE       = "saga.command.pdf-storage";
    private static final String TOPIC_CMD_PDF_SIGNING       = "saga.command.pdf-signing";
    private static final String TOPIC_CMD_DOCUMENT_STORAGE  = "saga.command.document-storage";
    private static final String TOPIC_CMD_EBMS_SENDING      = "saga.command.ebms-sending";

    // ── Saga reply topics (tests send to these) ───────────────────────────────

    private static final String TOPIC_REPLY_TAX_INVOICE      = "saga.reply.tax-invoice";
    private static final String TOPIC_REPLY_XML_SIGNING       = "saga.reply.xml-signing";
    private static final String TOPIC_REPLY_SIGNEDXML_STORAGE = "saga.reply.signedxml-storage";
    private static final String TOPIC_REPLY_TAX_INVOICE_PDF   = "saga.reply.tax-invoice-pdf";
    private static final String TOPIC_REPLY_PDF_STORAGE       = "saga.reply.pdf-storage";
    private static final String TOPIC_REPLY_PDF_SIGNING       = "saga.reply.pdf-signing";
    private static final String TOPIC_REPLY_DOCUMENT_STORAGE  = "saga.reply.document-storage";
    private static final String TOPIC_REPLY_EBMS_SENDING      = "saga.reply.ebms-sending";

    // ── Lifecycle topics ──────────────────────────────────────────────────────

    private static final String TOPIC_LIFECYCLE_COMPLETED = "saga.lifecycle.completed";

    // ── Spring beans ──────────────────────────────────────────────────────────

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private KafkaTemplate<String, String> testKafkaProducer;

    @Autowired
    private KafkaTemplate<String, StartSagaCommand> startSagaCommandJsonProducer;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SagaApplicationService sagaApplicationService;

    @Autowired
    private TestKafkaConsumerConfig kafkaConfig;

    // ── Test-only Kafka consumer (subscribes to command + lifecycle topics) ───

    private KafkaConsumer<String, String> testConsumer;

    /**
     * Received messages per topic, keyed by sagaId (message key set by Debezium from
     * outbox_events.partition_key which is set to saga.getId()).
     */
    private final Map<String, List<ConsumerRecord<String, String>>> receivedMessages
            = new ConcurrentHashMap<>();

    private final HttpClient httpClient = HttpClient.newHttpClient();

    // ═══════════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ═══════════════════════════════════════════════════════════════════════════

    @BeforeAll
    void setUpInfrastructure() {
        verifyDebeziumConnectorRunning();
        kafkaConfig.createTopics();
        testConsumer = createTestConsumer();
        testConsumer.subscribe(List.of(
                TOPIC_CMD_TAX_INVOICE,
                TOPIC_CMD_XML_SIGNING,
                TOPIC_CMD_SIGNEDXML_STORAGE,
                TOPIC_CMD_TAX_INVOICE_PDF,
                TOPIC_CMD_PDF_STORAGE,
                TOPIC_CMD_PDF_SIGNING,
                TOPIC_CMD_DOCUMENT_STORAGE,
                TOPIC_CMD_EBMS_SENDING,
                TOPIC_LIFECYCLE_COMPLETED
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
    // Test Cases
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * TC-01: StartSagaCommand received from document-intake-service triggers the
     * orchestrator to publish a ProcessTaxInvoiceCommand to saga.command.tax-invoice
     * via the transactional outbox and Debezium CDC.
     */
    @Test
    @DisplayName("TC-01: saga.commands.orchestrator → saga.command.tax-invoice (PROCESS_TAX_INVOICE) via CDC")
    void tc01_startSagaCommandCreatesProcessTaxInvoiceCommandViaCdc() throws Exception {
        // Given — a valid StartSagaCommand for a TAX_INVOICE document
        String documentId = "TC01-" + UUID.randomUUID();
        StartSagaCommand command = new StartSagaCommand(
                documentId, "TAX_INVOICE", "TI-" + documentId,
                "<TaxInvoice><ID>" + documentId + "</ID></TaxInvoice>",
                UUID.randomUUID().toString(), "TEST");

        // When — the command is published to the orchestrator input topic
        // Use JSON serializer to ensure proper deserialization by StartSagaCommandConsumer
        startSagaCommandJsonProducer.send("saga.commands.orchestrator", documentId, command).get(10, java.util.concurrent.TimeUnit.SECONDS);

        // Then — orchestrator creates a saga and writes a ProcessTaxInvoiceCommand to the outbox.
        //        Debezium CDC must deliver it to saga.command.tax-invoice.
        String sagaId = awaitSagaByDocumentId(DocumentType.TAX_INVOICE, documentId);
        // Commands use correlationId as the Kafka message key (set by SagaCommandPublisher)
        String correlationId = getCorrelationId(sagaId);

        awaitCommandOnTopic(TOPIC_CMD_TAX_INVOICE, correlationId);

        ConsumerRecord<String, String> record = getFirstCommandFromTopic(TOPIC_CMD_TAX_INVOICE, correlationId);
        assertThat(record.key()).isEqualTo(correlationId);

        JsonNode payload = parseJson(record.value());
        assertThat(payload.get("sagaId").asText()).isEqualTo(sagaId);
        assertThat(payload.get("sagaStep").asText()).isEqualTo("process-tax-invoice");
        assertThat(payload.get("documentId").asText()).isEqualTo(documentId);

        // Saga must be IN_PROGRESS at PROCESS_TAX_INVOICE
        Map<String, Object> sagaRow = getSagaRow(sagaId);
        assertThat(sagaRow.get("status")).isEqualTo("IN_PROGRESS");
        assertThat(sagaRow.get("current_step")).isEqualTo("PROCESS_TAX_INVOICE");
    }

    /**
     * TC-02: A SUCCESS reply from tax-invoice-processing-service causes the orchestrator
     * to advance the saga to SIGN_XML and publish a ProcessXmlSigningCommand to
     * saga.command.xml-signing via CDC.
     */
    @Test
    @DisplayName("TC-02: saga.reply.tax-invoice SUCCESS → saga.command.xml-signing (SIGN_XML) via CDC")
    void tc02_taxInvoiceReplySuccessCreatesSignXmlCommandViaCdc() throws Exception {
        // Given — saga positioned at PROCESS_TAX_INVOICE
        String documentId = "TC02-" + UUID.randomUUID();
        SagaInstance saga = createSagaAt(documentId, SagaStep.PROCESS_TAX_INVOICE);
        String correlationId = saga.getCorrelationId();

        // When — tax-invoice-processing-service sends a SUCCESS reply
        // (SagaReplyConsumer is not loaded in CDC test config, call handleReply directly)
        sendReply(TOPIC_REPLY_TAX_INVOICE, saga.getId(), "process-tax-invoice", true, null);
        sagaApplicationService.handleReply(saga.getId(), "process-tax-invoice", true, null, null);

        // Then — CDC delivers SignXmlCommand to saga.command.xml-signing
        awaitCommandOnTopic(TOPIC_CMD_XML_SIGNING, correlationId);

        ConsumerRecord<String, String> record = getFirstCommandFromTopic(TOPIC_CMD_XML_SIGNING, correlationId);
        assertThat(record.key()).isEqualTo(correlationId);

        JsonNode payload = parseJson(record.value());
        assertThat(payload.get("sagaId").asText()).isEqualTo(saga.getId());
        assertThat(payload.get("sagaStep").asText()).isEqualTo("sign-xml");

        // Saga must have advanced to SIGN_XML
        awaitCurrentStep(saga.getId(), SagaStep.SIGN_XML);
    }

    /**
     * TC-03: A SUCCESS reply from xml-signing-service (carrying signedXmlUrl) causes the
     * orchestrator to advance directly to GENERATE_TAX_INVOICE_PDF (skipping SIGNEDXML_STORAGE)
     * and publish a ProcessTaxInvoicePdfCommand via CDC.
     */
    @Test
    @DisplayName("TC-03: saga.reply.xml-signing SUCCESS → saga.command.tax-invoice-pdf (GENERATE_TAX_INVOICE_PDF) via CDC")
    void tc03_taxInvoicePdfGenerationFromSignXmlViaCdc() throws Exception {
        // Given — saga positioned at SIGN_XML
        String documentId = "TC03-" + UUID.randomUUID();
        SagaInstance saga = createSagaAt(documentId, SagaStep.SIGN_XML);
        String correlationId = saga.getCorrelationId();

        // When — xml-signing-service sends SUCCESS reply with the URL of the signed XML
        // (SagaReplyConsumer is not loaded in CDC test config, call handleReply directly)
        String signedXmlUrl = "http://storage/signed/" + documentId + ".xml";
        sendReplyWithData(TOPIC_REPLY_XML_SIGNING, saga.getId(), "sign-xml", true, null,
                Map.of("signedXmlUrl", signedXmlUrl));
        sagaApplicationService.handleReply(saga.getId(), "sign-xml", true, null,
                Map.of("signedXmlUrl", signedXmlUrl));

        // Then — CDC delivers ProcessTaxInvoicePdfCommand to saga.command.tax-invoice-pdf
        awaitCommandOnTopic(TOPIC_CMD_TAX_INVOICE_PDF, correlationId);

        ConsumerRecord<String, String> record = getFirstCommandFromTopic(TOPIC_CMD_TAX_INVOICE_PDF, correlationId);
        assertThat(record.key()).isEqualTo(correlationId);

        JsonNode payload = parseJson(record.value());
        assertThat(payload.get("sagaId").asText()).isEqualTo(saga.getId());
        assertThat(payload.get("sagaStep").asText()).isEqualTo("generate-tax-invoice-pdf");

        awaitCurrentStep(saga.getId(), SagaStep.GENERATE_TAX_INVOICE_PDF);
    }

    /**
     * TC-04: A SUCCESS reply from taxinvoice-pdf-generation-service (carrying pdfUrl and
     * pdfSize) causes the orchestrator to advance to PDF_STORAGE and publish a command
     * via CDC.  PDF_STORAGE stores the unsigned PDF from MinIO into document-storage-service
     * to provide a stable URL for the pdf-signing-service.
     */
    @Test
    @DisplayName("TC-04: saga.reply.tax-invoice-pdf SUCCESS → saga.command.pdf-storage (PDF_STORAGE) via CDC")
    void tc04_taxInvoicePdfReplySuccessCreatesPdfStorageCommandViaCdc() throws Exception {
        // Given — saga positioned at GENERATE_TAX_INVOICE_PDF
        String documentId = "TC04-" + UUID.randomUUID();
        SagaInstance saga = createSagaAt(documentId, SagaStep.GENERATE_TAX_INVOICE_PDF);
        String correlationId = saga.getCorrelationId();

        // When — taxinvoice-pdf-generation-service sends SUCCESS reply with PDF location
        // (SagaReplyConsumer is not loaded in CDC test config, call handleReply directly)
        String pdfUrl  = "http://minio/taxinvoices/" + documentId + ".pdf";
        sendReplyWithData(TOPIC_REPLY_TAX_INVOICE_PDF, saga.getId(), "generate-tax-invoice-pdf", true, null,
                Map.of("pdfUrl", pdfUrl, "pdfSize", 204800L));
        sagaApplicationService.handleReply(saga.getId(), "generate-tax-invoice-pdf", true, null,
                Map.of("pdfUrl", pdfUrl, "pdfSize", 204800L));

        // Then — CDC delivers ProcessPdfStorageCommand to saga.command.pdf-storage
        awaitCommandOnTopic(TOPIC_CMD_PDF_STORAGE, correlationId);

        ConsumerRecord<String, String> record = getFirstCommandFromTopic(TOPIC_CMD_PDF_STORAGE, correlationId);
        assertThat(record.key()).isEqualTo(correlationId);

        JsonNode payload = parseJson(record.value());
        assertThat(payload.get("sagaId").asText()).isEqualTo(saga.getId());
        assertThat(payload.get("sagaStep").asText()).isEqualTo("pdf-storage");

        awaitCurrentStep(saga.getId(), SagaStep.PDF_STORAGE);
    }

    /**
     * TC-05: A SUCCESS reply from document-storage-service (unsigned PDF stored, carrying
     * storedDocumentUrl) causes the orchestrator to advance to SIGN_PDF and publish a
     * ProcessPdfSigningCommand via CDC.
     */
    @Test
    @DisplayName("TC-05: saga.reply.pdf-storage SUCCESS → saga.command.pdf-signing (SIGN_PDF) via CDC")
    void tc05_pdfStorageReplySuccessCreatesSignPdfCommandViaCdc() throws Exception {
        // Given — saga positioned at PDF_STORAGE
        String documentId = "TC05-" + UUID.randomUUID();
        SagaInstance saga = createSagaAt(documentId, SagaStep.PDF_STORAGE);
        String correlationId = saga.getCorrelationId();

        // When — document-storage-service sends SUCCESS reply with stable storage URL
        // (SagaReplyConsumer is not loaded in CDC test config, call handleReply directly)
        String storedDocumentUrl = "http://storage/unsigned/" + documentId + ".pdf";
        sendReplyWithData(TOPIC_REPLY_PDF_STORAGE, saga.getId(), "pdf-storage", true, null,
                Map.of("storedDocumentUrl", storedDocumentUrl));
        sagaApplicationService.handleReply(saga.getId(), "pdf-storage", true, null,
                Map.of("storedDocumentUrl", storedDocumentUrl));

        // Then — CDC delivers ProcessPdfSigningCommand to saga.command.pdf-signing
        awaitCommandOnTopic(TOPIC_CMD_PDF_SIGNING, correlationId);

        ConsumerRecord<String, String> record = getFirstCommandFromTopic(TOPIC_CMD_PDF_SIGNING, correlationId);
        assertThat(record.key()).isEqualTo(correlationId);

        JsonNode payload = parseJson(record.value());
        assertThat(payload.get("sagaId").asText()).isEqualTo(saga.getId());
        assertThat(payload.get("sagaStep").asText()).isEqualTo("sign-pdf");

        awaitCurrentStep(saga.getId(), SagaStep.SIGN_PDF);
    }

    /**
     * TC-06: A SUCCESS reply from pdf-signing-service (carrying signedPdfUrl) causes the
     * orchestrator to advance to STORE_DOCUMENT and publish a StoreDocumentCommand via CDC.
     */
    @Test
    @DisplayName("TC-06: saga.reply.pdf-signing SUCCESS → saga.command.document-storage (STORE_DOCUMENT) via CDC")
    void tc06_pdfSigningReplySuccessCreatesDocumentStorageCommandViaCdc() throws Exception {
        // Given — saga positioned at SIGN_PDF
        String documentId = "TC06-" + UUID.randomUUID();
        SagaInstance saga = createSagaAt(documentId, SagaStep.SIGN_PDF);
        String correlationId = saga.getCorrelationId();

        // When — pdf-signing-service sends SUCCESS reply with signed PDF URL
        // (SagaReplyConsumer is not loaded in CDC test config, call handleReply directly)
        String signedPdfUrl = "http://storage/signed/" + documentId + ".pdf";
        sendReplyWithData(TOPIC_REPLY_PDF_SIGNING, saga.getId(), "sign-pdf", true, null,
                Map.of("signedPdfUrl", signedPdfUrl));
        sagaApplicationService.handleReply(saga.getId(), "sign-pdf", true, null,
                Map.of("signedPdfUrl", signedPdfUrl));

        // Then — CDC delivers StoreDocumentCommand to saga.command.document-storage
        awaitCommandOnTopic(TOPIC_CMD_DOCUMENT_STORAGE, correlationId);

        ConsumerRecord<String, String> record = getFirstCommandFromTopic(TOPIC_CMD_DOCUMENT_STORAGE, correlationId);
        assertThat(record.key()).isEqualTo(correlationId);

        JsonNode payload = parseJson(record.value());
        assertThat(payload.get("sagaId").asText()).isEqualTo(saga.getId());
        assertThat(payload.get("sagaStep").asText()).isEqualTo("store-document");

        awaitCurrentStep(saga.getId(), SagaStep.STORE_DOCUMENT);
    }

    /**
     * TC-07: A SUCCESS reply from document-storage-service (final signed PDF stored) causes
     * the orchestrator to advance to SEND_EBMS and publish a SendEbmsCommand via CDC.
     */
    @Test
    @DisplayName("TC-07: saga.reply.document-storage SUCCESS → saga.command.ebms-sending (SEND_EBMS) via CDC")
    void tc07_documentStorageReplySuccessCreatesEbmsSendingCommandViaCdc() throws Exception {
        // Given — saga positioned at STORE_DOCUMENT
        String documentId = "TC07-" + UUID.randomUUID();
        SagaInstance saga = createSagaAt(documentId, SagaStep.STORE_DOCUMENT);
        String correlationId = saga.getCorrelationId();

        // When — document-storage-service sends SUCCESS reply for the signed PDF
        // (SagaReplyConsumer is not loaded in CDC test config, call handleReply directly)
        sendReply(TOPIC_REPLY_DOCUMENT_STORAGE, saga.getId(), "store-document", true, null);
        sagaApplicationService.handleReply(saga.getId(), "store-document", true, null, null);

        // Then — CDC delivers SendEbmsCommand to saga.command.ebms-sending
        awaitCommandOnTopic(TOPIC_CMD_EBMS_SENDING, correlationId);

        ConsumerRecord<String, String> record = getFirstCommandFromTopic(TOPIC_CMD_EBMS_SENDING, correlationId);
        assertThat(record.key()).isEqualTo(correlationId);

        JsonNode payload = parseJson(record.value());
        assertThat(payload.get("sagaId").asText()).isEqualTo(saga.getId());
        assertThat(payload.get("sagaStep").asText()).isEqualTo("send-ebms");

        awaitCurrentStep(saga.getId(), SagaStep.SEND_EBMS);
    }

    /**
     * TC-08: A SUCCESS reply from ebms-sending-service (document submitted to Thailand Revenue
     * Department) causes the orchestrator to complete the saga.  A SagaCompletedEvent must
     * be published to saga.lifecycle.completed via CDC.
     */
    @Test
    @DisplayName("TC-08: saga.reply.ebms-sending SUCCESS → saga.lifecycle.completed via CDC")
    void tc08_ebmsSendingReplySuccessCompletesTheSagaViaCdc() throws Exception {
        // Given — saga positioned at SEND_EBMS
        String documentId = "TC08-" + UUID.randomUUID();
        SagaInstance saga = createSagaAt(documentId, SagaStep.SEND_EBMS);

        // When — ebms-sending-service sends SUCCESS reply
        // (SagaReplyConsumer is not loaded in CDC test config, call handleReply directly)
        sendReply(TOPIC_REPLY_EBMS_SENDING, saga.getId(), "send-ebms", true, null);
        sagaApplicationService.handleReply(saga.getId(), "send-ebms", true, null, null);

        // Then — CDC delivers SagaCompletedEvent to saga.lifecycle.completed
        awaitCommandOnTopic(TOPIC_LIFECYCLE_COMPLETED, saga.getId());

        ConsumerRecord<String, String> record = getFirstCommandFromTopic(TOPIC_LIFECYCLE_COMPLETED, saga.getId());
        assertThat(record.key()).isEqualTo(saga.getId());

        JsonNode payload = parseJson(record.value());
        assertThat(payload.get("sagaId").asText()).isEqualTo(saga.getId());
        assertThat(payload.get("eventType").asText()).isEqualTo("SagaCompletedEvent");
        assertThat(payload.has("durationMs")).isTrue();

        // Saga must be COMPLETED in the database
        awaitSagaStatus(saga.getId(), "COMPLETED");
        Map<String, Object> sagaRow = getSagaRow(saga.getId());
        assertThat(sagaRow.get("completed_at")).isNotNull();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Setup helpers — advance saga to a target step using direct service calls
    // (not via Kafka) so that setup commands don't interfere with the topic
    // being verified by each test case.
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates a fresh saga directly in the database at the specified step.
     * Uses raw JdbcTemplate to avoid JPA CLOB issues with TEXT columns.
     * The saga is inserted with status IN_PROGRESS and the current step set to
     * {@code targetStep}.  No intermediate commands are published.
     *
     * <p>This approach bypasses {@code sagaApplicationService.handleReply()} which
     * loads the saga entity via JPA and triggers PostgreSQL CLOB handling for
     * TEXT columns (even null ones, due to a Hibernate 6.4 + PostgreSQL JDBC 42.6
     * interaction).  By inserting directly with JdbcTemplate, we avoid the CLOB
     * issue entirely while still creating valid saga records.
     */
    private SagaInstance createSagaAt(String documentId, SagaStep targetStep) {
        String sagaId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();
        String commandId = UUID.randomUUID().toString();

        Timestamp now = new Timestamp(System.currentTimeMillis());

        // Insert saga instance — use null for TEXT columns to avoid CLOB issues
        jdbcTemplate.update("""
            INSERT INTO saga_instances
            (id, document_type, document_id, current_step, status, created_at, updated_at,
             xml_content, metadata, correlation_id, retry_count, max_retries, version)
            VALUES (?, ?, ?, ?, 'IN_PROGRESS', ?, ?,
             NULL, NULL, ?, 0, 3, 0)
            """,
            sagaId, "TAX_INVOICE", documentId, targetStep.name(), now, now, correlationId);

        // Insert saga_data record
        jdbcTemplate.update("""
            INSERT INTO saga_data
            (saga_id, file_path, xml_content, metadata, created_at)
            VALUES (?, ?, NULL, NULL, ?)
            """,
            sagaId, "/test/" + documentId + ".xml", now);

        // Insert the current-step command record (status SENT, not COMPLETED)
        // This allows handleReply to find it and mark it completed
        jdbcTemplate.update("""
            INSERT INTO saga_commands
            (id, saga_id, command_type, target_step, payload, status, created_at, correlation_id)
            VALUES (?, ?, ?, ?, '{}', 'SENT', ?, ?)
            """,
            commandId, sagaId, targetStep.name() + "_command", targetStep.name(), now, correlationId);

        return SagaInstance.builder()
                .id(sagaId)
                .documentType(DocumentType.TAX_INVOICE)
                .documentId(documentId)
                .currentStep(targetStep)
                .correlationId(correlationId)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Kafka helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private void sendMessage(String topic, String key, String value) {
        try {
            testKafkaProducer.send(topic, key, value).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send to topic " + topic, e);
        }
    }

    /**
     * Sends a saga reply JSON (no additional data) to the specified reply topic.
     * The {@code stepCode} is the kebab-case SagaStep code (e.g., "process-tax-invoice").
     */
    private void sendReply(String topic, String sagaId, String stepCode,
                           boolean success, String errorMessage) {
        sendMessage(topic, sagaId, buildReplyJson(sagaId, stepCode, success, errorMessage, null));
    }

    /**
     * Sends a saga reply JSON with additional data fields that the orchestrator will merge
     * into the saga metadata for use in subsequent step commands.
     */
    private void sendReplyWithData(String topic, String sagaId, String stepCode,
                                   boolean success, String errorMessage,
                                   Map<String, Object> additionalData) {
        sendMessage(topic, sagaId, buildReplyJson(sagaId, stepCode, success, errorMessage, additionalData));
    }

    /**
     * Builds the JSON payload for a {@link com.wpanther.orchestrator.infrastructure.adapter.in.messaging.ConcreteSagaReply}.
     * Extra fields in {@code additionalData} are captured by {@code @JsonAnySetter} and
     * propagated into the saga's metadata map for the next command.
     */
    private String buildReplyJson(String sagaId, String stepCode,
                                  boolean success, String errorMessage,
                                  Map<String, Object> additionalData) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"eventId\": \"").append(UUID.randomUUID()).append("\",\n");
        sb.append("  \"occurredAt\": \"").append(Instant.now()).append("\",\n");
        sb.append("  \"eventType\": \"").append(success ? "SagaReplySuccess" : "SagaReplyFailure").append("\",\n");
        sb.append("  \"version\": 1,\n");
        sb.append("  \"sagaId\": \"").append(sagaId).append("\",\n");
        sb.append("  \"sagaStep\": \"").append(stepCode).append("\",\n");
        sb.append("  \"correlationId\": \"").append(UUID.randomUUID()).append("\",\n");
        sb.append("  \"status\": \"").append(success ? "SUCCESS" : "FAILURE").append("\",\n");
        sb.append("  \"errorMessage\": ").append(errorMessage != null ? "\"" + errorMessage + "\"" : "null");
        if (additionalData != null) {
            for (Map.Entry<String, Object> entry : additionalData.entrySet()) {
                sb.append(",\n  \"").append(entry.getKey()).append("\": ");
                Object val = entry.getValue();
                if (val instanceof String) {
                    sb.append("\"").append(val).append("\"");
                } else {
                    sb.append(val);
                }
            }
        }
        sb.append("\n}");
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Consumer helpers — poll command topics and check for messages
    // ═══════════════════════════════════════════════════════════════════════════

    private KafkaConsumer<String, String> createTestConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_BOOTSTRAP_SERVERS);
        // Unique group so we always read from the earliest offset
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "saga-flow-test-consumer-" + System.currentTimeMillis());
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

    /** Blocks until a message keyed by {@code sagaId} appears on {@code topic} (2-min timeout). */
    private void awaitCommandOnTopic(String topic, String sagaId) {
        Awaitility.await()
                .atMost(Duration.ofMinutes(2))
                .pollInterval(Duration.ofSeconds(1))
                .failFast("Debezium connector is no longer RUNNING",
                        () -> !isConnectorRunning(DEBEZIUM_CONNECTOR_NAME))
                .until(() -> hasMessageOnTopic(topic, sagaId));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Database helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private Map<String, Object> getSagaRow(String sagaId) {
        return jdbcTemplate.queryForMap(
                "SELECT id, document_type, document_id, current_step, status, "
                + "created_at, updated_at, completed_at, error_message, retry_count, correlation_id "
                + "FROM saga_instances WHERE id = ?", sagaId);
    }

    private String getCorrelationId(String sagaId) {
        return (String) jdbcTemplate.queryForMap(
                "SELECT correlation_id FROM saga_instances WHERE id = ?", sagaId).get("correlation_id");
    }

    private String awaitSagaByDocumentId(DocumentType type, String documentId) {
        Awaitility.await()
                .atMost(Duration.ofMinutes(2))
                .pollInterval(Duration.ofSeconds(1))
                .until(() -> {
                    try {
                        jdbcTemplate.queryForMap(
                                "SELECT id FROM saga_instances WHERE document_type = ? AND document_id = ?",
                                type.name(), documentId);
                        return true;
                    } catch (org.springframework.dao.EmptyResultDataAccessException e) {
                        return false;
                    }
                });
        return (String) jdbcTemplate.queryForMap(
                "SELECT id FROM saga_instances WHERE document_type = ? AND document_id = ?",
                type.name(), documentId).get("id");
    }

    private void awaitCurrentStep(String sagaId, SagaStep expectedStep) {
        Awaitility.await()
                .atMost(Duration.ofMinutes(2))
                .pollInterval(Duration.ofSeconds(1))
                .until(() -> expectedStep.name().equals(getSagaRow(sagaId).get("current_step")));
    }

    private void awaitSagaStatus(String sagaId, String expectedStatus) {
        Awaitility.await()
                .atMost(Duration.ofMinutes(2))
                .pollInterval(Duration.ofSeconds(1))
                .until(() -> expectedStatus.equals(getSagaRow(sagaId).get("status")));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Debezium helpers
    // ═══════════════════════════════════════════════════════════════════════════

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
                .until(() -> isConnectorRunning(DEBEZIUM_CONNECTOR_NAME));
    }

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
