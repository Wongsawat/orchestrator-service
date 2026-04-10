# Design: Cross-Service Integration Test — saga.reply.xml-signing → saga.command.tax-invoice-pdf

**Date:** 2026-04-10
**Scope:** orchestrator-service cross-service integration tests (TC-01 happy path) + run automation script

---

## Goal

Make TC-01 of `XmlSigningToTaxInvoicePdfCdcIntegrationTest` pass end-to-end:

1. Orchestrator saga at `SIGN_XML` receives `saga.reply.xml-signing` (published by xml-signing-service via Debezium CDC)
2. Orchestrator advances the saga to `GENERATE_TAX_INVOICE_PDF` and publishes `saga.command.tax-invoice-pdf` via its own outbox/CDC
3. The published command contains all fields required by `taxinvoice-pdf-generation-service` (`documentId`, `taxInvoiceId`, `documentNumber`, `signedXmlUrl`, `taxInvoiceDataJson`)

---

## Root Causes of Current Failures

### Problem 1 — NULL metadata silently drops `signedXmlUrl`

`createSagaAtSignXml()` inserts `metadata = NULL` into `saga_instances`. `SagaApplicationService.handleReply()` guards `if (metadata != null)` before merging `resultData` (which contains `signedXmlUrl` from the xml-signing reply). When metadata is null the guard silently skips the merge. `publishGenerateTaxInvoicePdfCommand()` then reads null from metadata → the published command has a blank `signedXmlUrl` → TC-01 assertion fails.

### Problem 2 — Missing initial metadata fields

`createSagaAtSignXml()` does not seed `taxInvoiceId`, `documentNumber`, or `taxInvoiceDataJson` in metadata. The orchestrator reads all three from metadata when building `ProcessTaxInvoicePdfCommand`. All three are null in the published command, making it unusable by `taxinvoice-pdf-generation-service`.

### Problem 3 — `taxInvoiceNumber` vs `documentNumber` field name mismatch

`ProcessTaxInvoicePdfCommand` (orchestrator) serialises the document number as `@JsonProperty("taxInvoiceNumber")`. `KafkaTaxInvoiceProcessCommand` (taxinvoice-pdf-generation-service) deserialises it as `@JsonProperty("documentNumber")`. The names do not match; the consumer always receives a null document number.

### Problem 4 — Debezium stale producer cache after topic deletion

`test-containers-clean.sh` deletes all Kafka topics (including `saga.reply.xml-signing`). Debezium's internal Kafka producer holds a metadata cache with a default TTL of 5 minutes. When topics are recreated by `CrossServiceTestConfiguration.createTopics()`, the producer still sees stale metadata and silently drops or buffers outbox events written by the xml-signing-service. The xml-signing-service's own integration test (`SagaCommandFullIntegrationTest`) never deletes topics mid-run, so it is unaffected.

---

## Files Changed

| File | Type | Change |
|------|------|--------|
| `orchestrator-service/.../SagaApplicationService.java` | Production | Harden null-metadata guard in `handleReply` |
| `orchestrator-service/.../SagaCommandPublisher.java` | Production | Rename `taxInvoiceNumber` → `documentNumber` in `ProcessTaxInvoicePdfCommand` |
| `orchestrator-service/.../XmlSigningToTaxInvoicePdfCdcIntegrationTest.java` | Test | Fix `createSagaAtSignXml()`; expand TC-01 assertions |
| `invoice-microservices/scripts/run-cross-service-test.sh` | Script | New single-command test runner |

`application-cross-service-test.yml` requires no changes.

---

## Fix Details

### Fix 1 — Harden null-metadata guard (`SagaApplicationService.handleReply`)

```java
// Before (silently drops resultData when metadata is null):
DocumentMetadata metadata = instance.getDocumentMetadata();
if (metadata != null) {
    for (Map.Entry<String, Object> entry : resultData.entrySet()) {
        updatedMetadata = updatedMetadata.withMetadataValue(entry.getKey(), entry.getValue());
    }
    instance.setDocumentMetadata(updatedMetadata);
}

// After (initialises empty metadata so signedXmlUrl is always stored):
DocumentMetadata metadata = instance.getDocumentMetadata();
if (metadata == null) {
    metadata = new DocumentMetadata(new HashMap<>());
    instance.setDocumentMetadata(metadata);
}
DocumentMetadata updatedMetadata = metadata;
for (Map.Entry<String, Object> entry : resultData.entrySet()) {
    updatedMetadata = updatedMetadata.withMetadataValue(entry.getKey(), entry.getValue());
}
instance.setDocumentMetadata(updatedMetadata);
```

### Fix 2 — Field name mismatch (`SagaCommandPublisher.ProcessTaxInvoicePdfCommand`)

```java
// Before:
@JsonProperty("taxInvoiceNumber") private final String taxInvoiceNumber;

// After (matches @JsonProperty("documentNumber") in KafkaTaxInvoiceProcessCommand):
@JsonProperty("documentNumber") private final String documentNumber;
```

The constructor parameter and getter are renamed from `taxInvoiceNumber` to `documentNumber`. The call site `getDocumentNumber(saga)` in `publishGenerateTaxInvoicePdfCommand()` remains unchanged.

### Fix 3 — Seed initial metadata (`createSagaAtSignXml`)

```java
String taxInvoiceId  = UUID.randomUUID().toString();
String docNumber     = "TIV-TEST-" + documentId.substring(0, 8).toUpperCase();
String metadataJson  = String.format(
    "{\"documentNumber\":\"%s\",\"taxInvoiceId\":\"%s\",\"taxInvoiceDataJson\":\"{}\"}",
    docNumber, taxInvoiceId);

jdbcTemplate.update("""
    INSERT INTO saga_instances
    (id, document_type, document_id, current_step, status, created_at, updated_at,
     xml_content, metadata, correlation_id, retry_count, max_retries, version)
    VALUES (?, ?, ?, 'SIGN_XML', 'IN_PROGRESS', ?, ?, NULL, ?::jsonb, ?, 0, 3, 0)
    """,
    sagaId, "TAX_INVOICE", documentId, now, now, metadataJson, correlationId);
```

`SagaSetup` record gains `taxInvoiceId` and `documentNumber` fields so TC-01 assertions can verify them.

### Fix 4 — Expanded TC-01 assertions

After the existing `signedXmlUrl` check, add:

```java
assertThat(payload.has("documentId")).isTrue();
assertThat(payload.get("documentId").asText()).isEqualTo(saga.documentId());
assertThat(payload.has("documentNumber")).isTrue();
assertThat(payload.get("documentNumber").asText()).isEqualTo(saga.documentNumber());
assertThat(payload.has("taxInvoiceId")).isTrue();
assertThat(payload.get("taxInvoiceId").asText()).isEqualTo(saga.taxInvoiceId());
assertThat(payload.has("signedXmlUrl")).isTrue();
assertThat(payload.get("signedXmlUrl").asText()).isNotBlank();
assertThat(payload.has("taxInvoiceDataJson")).isTrue();
```

These assertions verify the command is complete and usable by `taxinvoice-pdf-generation-service`.

---

## Debezium Connector Restart Mitigation

After `test-containers-clean.sh` deletes Kafka topics, `run-cross-service-test.sh` restarts both Debezium connectors with `?includeTasks=true` to force the Kafka Connect task (and its internal producer) to reconnect and refresh topic metadata:

```bash
restart_connector() {
    local name=$1
    curl -sf -X POST "http://localhost:8083/connectors/${name}/restart?includeTasks=true" || true
    local retries=20
    until curl -sf "http://localhost:8083/connectors/${name}/status" \
               | grep -q '"state":"RUNNING"'; do
        retries=$((retries - 1))
        [ $retries -eq 0 ] && echo "ERROR: ${name} failed to restart" && exit 1
        sleep 3
    done
    echo "${name} is RUNNING"
}

restart_connector "outbox-connector-xmlsigning"
restart_connector "outbox-connector-orchestrator"
sleep 5   # let producers establish connection before first outbox write
```

`?includeTasks=true` is required: connector-level restart alone does not restart the Kafka Connect task that holds the stale producer.

---

## `run-cross-service-test.sh` — Full Execution Flow

```
invoice-microservices/scripts/run-cross-service-test.sh
```

```
1. Clean build
   ├── cd ../../saga-commons        → mvn clean install -q
   ├── cd xml-signing-service       → mvn clean package -DskipTests -q
   └── cd orchestrator-service      → mvn clean package -DskipTests -q

2. Start containers (idempotent — safe to re-run if already running)
   └── ./scripts/test-containers-start.sh --full-integration
       (PostgreSQL:5433, Kafka:9093, MinIO:9100, eidasremotesigning:9000, Debezium:8083)

3. Clean DB + Kafka
   └── ./scripts/test-containers-clean.sh

4. Restart Debezium connectors (flush stale producer cache after topic deletion)
   ├── POST /connectors/outbox-connector-xmlsigning/restart?includeTasks=true → wait RUNNING
   ├── POST /connectors/outbox-connector-orchestrator/restart?includeTasks=true → wait RUNNING
   └── sleep 5s

5. Bootstrap eidasremotesigning credentials (replicate EidasRemoteSigningTestHelper in shell)
   ├── POST http://localhost:9000/client-registration
   │     body: {"clientName":"run-cross-service-test","scopes":["signing"],"grantTypes":["client_credentials"]}
   │     → parse clientId + clientSecret from response; export as CSC_CLIENT_ID
   ├── INSERT INTO eidasremotesigning.signing_certificate via psql (localhost:5433)
   │     storage_type=BCFKS, keystore_path=/app/keystores/eidas-signing.bfks,
   │     keystore_password=eidas-signing-2024, certificate_alias=signing-key, client_id=$CSC_CLIENT_ID
   │     → parse inserted UUID; export as CSC_CREDENTIAL_ID
   └── (BCFKS keystore is already mounted in the container by docker-compose.test.yml)

6. Start xml-signing-service in background
   └── cd xml-signing-service
       CSC_CLIENT_ID=$CSC_CLIENT_ID CSC_CREDENTIAL_ID=$CSC_CREDENTIAL_ID \
         mvn spring-boot:run -Dspring-boot.run.profiles=full-integration-test &
       XML_SIGNING_PID=$!
       poll http://localhost:8086/actuator/health until {"status":"UP"} (60s max)

7. Run orchestrator integration tests
   └── cd orchestrator-service
       mvn test -Pintegration \
                -Dtest=XmlSigningToTaxInvoicePdfCdcIntegrationTest \
                -Dspring.profiles.active=cross-service-test \
                -Dintegration.tests.enabled=true
       capture $? as TEST_EXIT_CODE

8. Stop xml-signing-service (via trap EXIT — always runs even on failure)
   └── kill $XML_SIGNING_PID 2>/dev/null || true

9. Exit with test exit code
   └── exit $TEST_EXIT_CODE
```

**Script properties:**
- `trap 'kill $XML_SIGNING_PID 2>/dev/null || true' EXIT` ensures the background service is always cleaned up
- Containers are NOT stopped at the end (developer may want to inspect state)
- Script exits with Maven's exit code so CI passes/fails correctly

---

## Saga Command Payload — Downstream Compatibility

The `ProcessTaxInvoicePdfCommand` published by the orchestrator to `saga.command.tax-invoice-pdf` will contain:

| JSON field | Source | Required by taxinvoice-pdf-generation-service |
|---|---|---|
| `sagaId` | saga instance ID | yes |
| `sagaStep` | `"generate-tax-invoice-pdf"` | yes |
| `correlationId` | saga correlation ID | yes |
| `documentId` | `saga_instances.document_id` | yes |
| `taxInvoiceId` | `metadata.taxInvoiceId` (seeded in test) | yes |
| `documentNumber` | `metadata.documentNumber` (seeded in test; field renamed from `taxInvoiceNumber`) | yes |
| `signedXmlUrl` | `metadata.signedXmlUrl` (populated from xml-signing reply) | yes |
| `taxInvoiceDataJson` | `metadata.taxInvoiceDataJson` (seeded as `"{}"` in test) | yes |

---

## Not in Scope

- TC-02, TC-03, TC-04 are not addressed in this design. Only TC-01 (happy path) is the target.
- The `taxinvoice-pdf-generation-service` is not started as part of this test run. The design only verifies that the command payload is correct for future downstream use.
- No changes to `test-containers-clean.sh` itself.
