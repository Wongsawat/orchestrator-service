# Design: Intake CDC → Orchestrator Consumer Integration Test

**Date:** 2026-04-03
**Location:** `orchestrator-service` test module
**Test class:** `IntakeCdcToOrchestratorIT`

---

## Problem

The CDC flow from `document-intake-service` to `orchestrator-service` was reported as failing to process messages. Specifically, Debezium publishes events from `intake_db.outbox_events` to `saga.commands.orchestrator`, but the orchestrator `StartSagaCommandConsumer` was suspected of being unable to process them.

No existing test exercises the **complete CDC path** from `intake_db → Debezium → saga.commands.orchestrator → StartSagaCommandConsumer`. All existing tests (`KafkaConsumerIntegrationTest`, `TaxInvoiceSagaFlowCdcIntegrationTest`) send messages directly to Kafka via a test producer, bypassing Debezium entirely.

---

## Goals

1. Insert an `outbox_events` row into `intake_db` exactly as the intake service would
2. Observe the raw Kafka message Debezium delivers to `saga.commands.orchestrator`
3. Confirm the orchestrator `StartSagaCommandConsumer` processes it and creates a saga instance
4. Provide a regression test for this cross-service CDC path

---

## Background: Outbox Payload Format

From live inspection of `intake_db.outbox_events`, the `StartSagaCommand` payload is:

```json
{
  "eventId": "<uuid>",
  "occurredAt": "<instant>",
  "eventType": "StartSagaCommand",
  "version": 1,
  "sagaId": null,
  "sagaStep": null,
  "correlationId": "<correlationId>",
  "documentId": "<documentId>",
  "documentType": "TAX_INVOICE",
  "documentNumber": "<number>",
  "xmlContent": "<xml>",
  "source": "REST"
}
```

Key observations:
- `sagaId: null` and `sagaStep: null` are present because intake's `StartSagaCommand` extends `SagaCommand`
- `partition_key` is `correlationId` (not `documentId`)
- `headers` is `{"documentType":"TAX_INVOICE","correlationId":"<correlationId>"}`
- The orchestrator `StartSagaCommand` (extends `InboundCommand`) handles `sagaId`/`sagaStep` via `@JsonIgnoreProperties(ignoreUnknown=true)`

Confirmed working in live run: saga `36fcdf67` was created for document `a16c608b` via this exact path.

---

## Debezium Connector Config (intake-connector)

- `transforms.outbox.table.expand.json.payload`: `true` — Debezium parses the `payload` column as JSON and publishes it as a JSON object (not a string-within-string)
- `value.converter`: `JsonConverter` with `schemas.enable: false` — raw JSON value, no schema wrapper
- `transforms.outbox.table.field.event.key`: `partition_key` — Kafka message key is the `partition_key` column value (i.e. `correlationId`)

---

## Design

### Test Module Location

`orchestrator-service/src/test/java/com/wpanther/orchestrator/integration/IntakeCdcToOrchestratorIT.java`

Extends `AbstractCdcIntegrationTest` to reuse:
- `jdbcTemplate` → `orchestrator_db`
- `await()` factory (2-minute timeout, 2-second poll)
- `isConnectorRunning(String)` helper
- `objectMapper`

Note: `AbstractCdcIntegrationTest.verifyDebeziumConnectorRunning()` checks `outbox-connector-orchestrator`. This test additionally requires `outbox-connector-intake` to be RUNNING. Override `@BeforeAll setupInfrastructure()` to call `super.setupInfrastructure()` and then verify `isConnectorRunning("outbox-connector-intake")` with the same `await()` wait.

### Additional Infrastructure

A nested `@TestConfiguration` declares a second `JdbcTemplate` bean (qualifier: `intakeJdbcTemplate`) pointing at `intake_db` on `localhost:5433`. No new config file needed.

### Outbox Row Insert Helper

```
insertIntakeOutboxRow(documentId, correlationId, documentType, documentNumber, xmlContent)
  → inserts one row into intake_db.outbox_events
  → payload constructed as JSON string matching live format
  → topic = "saga.commands.orchestrator"
  → partition_key = correlationId
  → headers = {"documentType":"<type>","correlationId":"<corr>"}
  → status = "PENDING"
```

### Raw Kafka Spy

A plain `KafkaConsumer<String, String>` with `StringDeserializer` for both key and value — no Jackson involved. Subscribed to `saga.commands.orchestrator` before each test. Used to capture exactly what Debezium delivers.

### Cleanup

`@BeforeEach`:
- `intakeJdbcTemplate`: `DELETE FROM outbox_events`
- `intakeJdbcTemplate`: `DELETE FROM incoming_documents` (if needed for FK)
- inherited `jdbcTemplate`: `DELETE FROM saga_commands`, `DELETE FROM outbox_events`, `DELETE FROM saga_data`, `DELETE FROM saga_instances`

### Test Methods

#### TC-1: `shouldDeliverStartSagaCommandViaCdcToKafka`
**Purpose:** Verify Debezium picks up the outbox row and publishes to `saga.commands.orchestrator`.

1. Insert outbox row (TAX_INVOICE, minimal `xmlContent="<test/>"`)
2. `await()` until raw spy consumer receives a message with key = correlationId
3. Log raw JSON value
4. Assert payload contains `documentId`, `documentType="TAX_INVOICE"`, `correlationId`
5. Assert `sagaId` field is null (confirms intake payload format)

#### TC-2: `shouldOrchestratorConsumerCreateSagaFromCdcMessage`
**Purpose:** End-to-end verification that the consumer processes the CDC message and creates a saga.

1. Insert outbox row (TAX_INVOICE)
2. `await()` until `orchestrator_db.saga_instances` contains a row with `document_id = documentId`
3. Assert `document_type = "TAX_INVOICE"`, `current_step = "PROCESS_TAX_INVOICE"`, `status = "IN_PROGRESS"`

#### TC-3: `shouldRawCdcPayloadBeDeserializableByOrchestratorConsumer`
**Purpose:** Confirm the raw CDC JSON can be deserialized by the same `JsonDeserializer<StartSagaCommand>` used in `KafkaConfig`.

1. Insert outbox row
2. Capture raw Kafka message via spy consumer
3. Instantiate `JsonDeserializer<StartSagaCommand>` with same config as `KafkaConfig.startSagaCommandConsumerFactory()`
4. Deserialize raw bytes — assert no exception
5. Assert `getDocumentId()`, `getDocumentType()`, `getCorrelationId()` return expected values
6. Assert `getXmlContent()` is not null

---

## Test Profile

Runs under `@ActiveProfiles("cdc-test")` (same as `OrchestratorCdcIntegrationTest` and `SagaDatabaseIntegrationTest`). Requires:
- PostgreSQL on `localhost:5433`
- Kafka on `localhost:9093`
- Debezium on `localhost:8083` with `outbox-connector-intake` in RUNNING state

Enabled via `@EnabledIfSystemProperty(named = "integration.tests.enabled", matches = "true")`.

Run command:
```bash
mvn test -Pintegration \
  -Dtest=IntakeCdcToOrchestratorIT \
  -Dspring.profiles.active=cdc-test \
  -Dintegration.tests.enabled=true
```

---

## Files Changed

| File | Action |
|------|--------|
| `src/test/java/com/wpanther/orchestrator/integration/IntakeCdcToOrchestratorIT.java` | New |

No changes to production code. No new config files.

---

## Out of Scope

- Testing the full saga flow beyond saga creation (covered by `TaxInvoiceSagaFlowCdcIntegrationTest`)
- Testing `INVOICE` or `ABBREVIATED_TAX_INVOICE` document types (covered by existing consumer tests)
- Changes to the `StartSagaCommandConsumer` or `KafkaConfig`
