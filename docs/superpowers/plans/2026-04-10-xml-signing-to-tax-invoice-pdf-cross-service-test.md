# Cross-Service Test: saga.reply.xml-signing → saga.command.tax-invoice-pdf

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix four root causes so TC-01 of `XmlSigningToTaxInvoicePdfCdcIntegrationTest` passes end-to-end and create a single-command runner script.

**Architecture:** Three production/test fixes address data issues (null metadata guard, missing metadata fields, wrong JSON field name); one shell script fix addresses Debezium's stale producer cache after `test-containers-clean.sh` deletes Kafka topics. All changes are independent and committed separately.

**Tech Stack:** Java 21, Spring Boot 3.2.5, JUnit 5, Mockito, AssertJ, Jackson, Bash, Kafka Connect REST API, psql

---

## File Map

| File | Action | What changes |
|------|--------|-------------|
| `src/main/java/com/wpanther/orchestrator/application/usecase/SagaApplicationService.java` | Modify | Initialise empty `DocumentMetadata` when null before merging `resultData` |
| `src/main/java/com/wpanther/orchestrator/infrastructure/adapter/out/messaging/SagaCommandPublisher.java` | Modify | Rename `taxInvoiceNumber` → `documentNumber` in inner `ProcessTaxInvoicePdfCommand` |
| `src/test/java/com/wpanther/orchestrator/application/usecase/SagaApplicationServiceTest.java` | Modify | Add test for null-metadata resultData merge |
| `src/test/java/com/wpanther/orchestrator/infrastructure/adapter/out/messaging/SagaCommandPublisherTest.java` | Create | Test that `ProcessTaxInvoicePdfCommand` serialises field as `"documentNumber"` |
| `src/test/java/com/wpanther/orchestrator/integration/XmlSigningToTaxInvoicePdfCdcIntegrationTest.java` | Modify | Seed metadata in `createSagaAtSignXml()`; expand TC-01 assertions |
| `../../scripts/run-cross-service-test.sh` | Create | Single-command test runner (relative to orchestrator-service dir) |

---

## Task 1: Unit test + fix null-metadata guard in `handleReply`

**Files:**
- Modify: `src/test/java/com/wpanther/orchestrator/application/usecase/SagaApplicationServiceTest.java`
- Modify: `src/main/java/com/wpanther/orchestrator/application/usecase/SagaApplicationService.java`

- [ ] **Step 1.1: Add failing test to `SagaApplicationServiceTest.HandleReplyTests`**

Open `SagaApplicationServiceTest.java`. Locate the `HandleReplyTests` nested class (line ~216). Add this test immediately after `marksCommandRecordAsFailed_onFailure`:

```java
@Test
@DisplayName("merges resultData into metadata even when saga documentMetadata starts as null")
void success_mergesResultDataWhenDocumentMetadataIsNull() {
    // Saga is loaded from DB without documentMetadata (null — simulates JdbcTemplate partial load)
    SagaInstance saga = createSaga(SagaStatus.IN_PROGRESS, "saga-null-meta");
    saga.setDocumentType(DocumentType.TAX_INVOICE);
    saga.advanceTo(SagaStep.SIGN_XML);
    saga.setDocumentMetadata(null);   // explicitly null — the bug condition

    when(jdbcTemplate.queryForObject(anyString(),
            any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
        .thenReturn(saga);
    when(commandRepository.findBySagaId("saga-null-meta")).thenReturn(createCommandRecords(saga));
    when(commandRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    Map<String, Object> resultData = Map.of("signedXmlUrl", "http://minio/signed-xml-documents/2026/04/10/TAX_INVOICE/signed.xml");

    // handleReply with 5-param form (sagaId, step, success, errorMessage, resultData)
    service.handleReply("saga-null-meta", SagaStep.SIGN_XML.getCode(), true, null, resultData);

    // The metadata must be initialised and the URL stored
    assertThat(saga.getDocumentMetadata()).isNotNull();
    assertThat(saga.getDocumentMetadata().getMetadataValue("signedXmlUrl"))
        .isEqualTo("http://minio/signed-xml-documents/2026/04/10/TAX_INVOICE/signed.xml");
}
```

- [ ] **Step 1.2: Run test to confirm it fails**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/orchestrator-service
mvn test -Dtest="SagaApplicationServiceTest#mergesResultDataWhenDocumentMetadataIsNull" -q
```

Expected: **FAIL** — `saga.getDocumentMetadata()` is null because the current guard skips the merge.

- [ ] **Step 1.3: Fix the null-metadata guard in `SagaApplicationService`**

Open `SagaApplicationService.java`. Find the block at approximately line 179 (search for `if (resultData != null && !resultData.isEmpty() && success)`). Replace:

```java
        if (resultData != null && !resultData.isEmpty() && success) {
            DocumentMetadata metadata = instance.getDocumentMetadata();
            if (metadata != null) {
                // Apply all resultData entries immutably using withMetadataValue pattern
                DocumentMetadata updatedMetadata = metadata;
                for (Map.Entry<String, Object> entry : resultData.entrySet()) {
                    updatedMetadata = updatedMetadata.withMetadataValue(entry.getKey(), entry.getValue());
                }
                instance.setDocumentMetadata(updatedMetadata);
                log.debug("Merged {} result data fields into saga {} metadata: {}",
                        resultData.size(), sagaId, resultData.keySet());
            }
        }
```

With:

```java
        if (resultData != null && !resultData.isEmpty() && success) {
            DocumentMetadata metadata = instance.getDocumentMetadata();
            if (metadata == null) {
                // Initialise empty metadata so resultData (e.g. signedXmlUrl) is never silently dropped
                metadata = DocumentMetadata.builder().build();
                instance.setDocumentMetadata(metadata);
            }
            // Apply all resultData entries immutably using withMetadataValue pattern
            DocumentMetadata updatedMetadata = metadata;
            for (Map.Entry<String, Object> entry : resultData.entrySet()) {
                updatedMetadata = updatedMetadata.withMetadataValue(entry.getKey(), entry.getValue());
            }
            instance.setDocumentMetadata(updatedMetadata);
            log.debug("Merged {} result data fields into saga {} metadata: {}",
                    resultData.size(), sagaId, resultData.keySet());
        }
```

- [ ] **Step 1.4: Run test to confirm it passes**

```bash
mvn test -Dtest="SagaApplicationServiceTest" -q
```

Expected: **All tests PASS** including the new `mergesResultDataWhenDocumentMetadataIsNull`.

- [ ] **Step 1.5: Commit**

```bash
git add src/main/java/com/wpanther/orchestrator/application/usecase/SagaApplicationService.java \
        src/test/java/com/wpanther/orchestrator/application/usecase/SagaApplicationServiceTest.java
git commit -m "fix: initialise DocumentMetadata when null before merging reply resultData in handleReply"
```

---

## Task 2: Unit test + fix `taxInvoiceNumber` → `documentNumber` in `ProcessTaxInvoicePdfCommand`

**Files:**
- Create: `src/test/java/com/wpanther/orchestrator/infrastructure/adapter/out/messaging/SagaCommandPublisherTest.java`
- Modify: `src/main/java/com/wpanther/orchestrator/infrastructure/adapter/out/messaging/SagaCommandPublisher.java`

- [ ] **Step 2.1: Create failing test for command serialisation**

Create `src/test/java/com/wpanther/orchestrator/infrastructure/adapter/out/messaging/SagaCommandPublisherTest.java`:

```java
package com.wpanther.orchestrator.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.saga.domain.enums.SagaStep;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that inner command DTOs in SagaCommandPublisher serialise their JSON
 * field names correctly so consuming services can deserialise them.
 */
class SagaCommandPublisherTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules();

    @Test
    void processTaxInvoicePdfCommand_serialisesDocumentNumberField() throws Exception {
        // taxinvoice-pdf-generation-service deserialises @JsonProperty("documentNumber"),
        // NOT "taxInvoiceNumber". Verify the orchestrator publishes the correct key.
        SagaCommandPublisher.ProcessTaxInvoicePdfCommand cmd =
                new SagaCommandPublisher.ProcessTaxInvoicePdfCommand(
                        "saga-001",
                        SagaStep.GENERATE_TAX_INVOICE_PDF,
                        "corr-001",
                        "doc-001",
                        "taxinv-uuid",
                        "TIV-TEST-001",       // documentNumber
                        "http://minio/signed.xml",
                        "{}"
                );

        String json = objectMapper.writeValueAsString(cmd);
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.has("documentNumber"))
                .as("JSON must contain 'documentNumber' key (not 'taxInvoiceNumber')")
                .isTrue();
        assertThat(node.get("documentNumber").asText()).isEqualTo("TIV-TEST-001");
        assertThat(node.has("taxInvoiceNumber"))
                .as("JSON must NOT contain legacy 'taxInvoiceNumber' key")
                .isFalse();
    }
}
```

- [ ] **Step 2.2: Run test to confirm it fails**

```bash
mvn test -Dtest="SagaCommandPublisherTest" -q
```

Expected: **FAIL** — assertion `node.has("documentNumber")` fails; only `taxInvoiceNumber` key exists.

- [ ] **Step 2.3: Rename field in `ProcessTaxInvoicePdfCommand`**

Open `SagaCommandPublisher.java`. Search for `ProcessTaxInvoicePdfCommand` (inner static class, approx line 933). Make the following four replacements inside that class only:

**Replace the field declaration:**
```java
        // Before:
        @JsonProperty("taxInvoiceNumber")
        private final String taxInvoiceNumber;

        // After:
        @JsonProperty("documentNumber")
        private final String documentNumber;
```

**Replace the short convenience constructor** (the one without `@JsonCreator`):
```java
        // Before:
        public ProcessTaxInvoicePdfCommand(String sagaId, SagaStep sagaStep, String correlationId,
                                            String documentId, String taxInvoiceId, String taxInvoiceNumber,
                                            String signedXmlUrl, String taxInvoiceDataJson) {
            super(sagaId, sagaStep, correlationId);
            this.documentId = documentId;
            this.taxInvoiceId = taxInvoiceId;
            this.taxInvoiceNumber = taxInvoiceNumber;
            this.signedXmlUrl = signedXmlUrl;
            this.taxInvoiceDataJson = taxInvoiceDataJson;
        }

        // After:
        public ProcessTaxInvoicePdfCommand(String sagaId, SagaStep sagaStep, String correlationId,
                                            String documentId, String taxInvoiceId, String documentNumber,
                                            String signedXmlUrl, String taxInvoiceDataJson) {
            super(sagaId, sagaStep, correlationId);
            this.documentId = documentId;
            this.taxInvoiceId = taxInvoiceId;
            this.documentNumber = documentNumber;
            this.signedXmlUrl = signedXmlUrl;
            this.taxInvoiceDataJson = taxInvoiceDataJson;
        }
```

**Replace the `@JsonCreator` constructor parameter and assignment:**
```java
        // Before (in @JsonCreator constructor):
                @JsonProperty("taxInvoiceNumber") String taxInvoiceNumber,
            ...
            this.taxInvoiceNumber = taxInvoiceNumber;

        // After:
                @JsonProperty("documentNumber") String documentNumber,
            ...
            this.documentNumber = documentNumber;
```

The call site `publishGenerateTaxInvoicePdfCommand` passes `getDocumentNumber(saga)` positionally — no change needed there.

- [ ] **Step 2.4: Run test to confirm it passes**

```bash
mvn test -Dtest="SagaCommandPublisherTest" -q
```

Expected: **PASS**

- [ ] **Step 2.5: Run full unit test suite to confirm no regressions**

```bash
mvn test -q
```

Expected: **All tests PASS**

- [ ] **Step 2.6: Commit**

```bash
git add src/main/java/com/wpanther/orchestrator/infrastructure/adapter/out/messaging/SagaCommandPublisher.java \
        src/test/java/com/wpanther/orchestrator/infrastructure/adapter/out/messaging/SagaCommandPublisherTest.java
git commit -m "fix: rename taxInvoiceNumber to documentNumber in ProcessTaxInvoicePdfCommand to match consumer DTO"
```

---

## Task 3: Fix `createSagaAtSignXml()` and expand TC-01 assertions

**Files:**
- Modify: `src/test/java/com/wpanther/orchestrator/integration/XmlSigningToTaxInvoicePdfCdcIntegrationTest.java`

- [ ] **Step 3.1: Update `SagaSetup` record to carry `taxInvoiceId` and `documentNumber`**

Open `XmlSigningToTaxInvoicePdfCdcIntegrationTest.java`. Find the `SagaSetup` record (approx line 360):

```java
    // Before:
    record SagaSetup(String sagaId, String documentId, String correlationId) {}

    // After:
    record SagaSetup(String sagaId, String documentId, String correlationId,
                     String taxInvoiceId, String documentNumber) {}
```

- [ ] **Step 3.2: Fix `createSagaAtSignXml()` to seed initial metadata**

Find `createSagaAtSignXml` (approx line 373). Replace the entire method body with:

```java
    private SagaSetup createSagaAtSignXml(String documentId) {
        String sagaId        = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();
        String commandId     = UUID.randomUUID().toString();
        String taxInvoiceId  = UUID.randomUUID().toString();
        String docNumber     = "TIV-TEST-" + documentId.substring(0, 8).toUpperCase();

        // Seed initial metadata so the orchestrator can build a complete
        // ProcessTaxInvoicePdfCommand after the SIGN_XML reply arrives.
        // taxInvoiceDataJson is "{}" — empty but non-null, which is enough for TC-01.
        String metadataJson = String.format(
                "{\"documentNumber\":\"%s\",\"taxInvoiceId\":\"%s\",\"taxInvoiceDataJson\":\"{}\"}",
                docNumber, taxInvoiceId);

        Timestamp now = new Timestamp(System.currentTimeMillis());

        // Insert saga instance — use ?::jsonb cast so PostgreSQL treats the string as JSONB
        jdbcTemplate.update("""
                INSERT INTO saga_instances
                (id, document_type, document_id, current_step, status, created_at, updated_at,
                 xml_content, metadata, correlation_id, retry_count, max_retries, version)
                VALUES (?, ?, ?, 'SIGN_XML', 'IN_PROGRESS', ?, ?,
                 NULL, ?::jsonb, ?, 0, 3, 0)
                """,
                sagaId, "TAX_INVOICE", documentId, now, now, metadataJson, correlationId);

        // Insert saga_data record
        jdbcTemplate.update("""
                INSERT INTO saga_data
                (saga_id, file_path, xml_content, metadata, created_at)
                VALUES (?, ?, NULL, NULL, ?)
                """,
                sagaId, "/test/" + documentId + ".xml", now);

        // Insert the SIGN_XML command record (status SENT)
        jdbcTemplate.update("""
                INSERT INTO saga_commands
                (id, saga_id, command_type, target_step, payload, status, created_at, correlation_id)
                VALUES (?, ?, 'SIGN_XML_command', 'SIGN_XML', '{}', 'SENT', ?, ?)
                """,
                commandId, sagaId, now, correlationId);

        return new SagaSetup(sagaId, documentId, correlationId, taxInvoiceId, docNumber);
    }
```

- [ ] **Step 3.3: Expand TC-01 assertions to verify downstream-compatible payload**

Find the TC-01 test method `shouldPublishTaxInvoicePdfCommandAfterXmlSigning` (approx line 189). After:

```java
            // Verify signedXmlUrl is populated (comes from xml-signing-service reply)
            assertThat(payload.has("signedXmlUrl")).isTrue();
            assertThat(payload.get("signedXmlUrl").asText()).isNotBlank();
```

Add:

```java
            // ── Verify all fields required by taxinvoice-pdf-generation-service ────
            // documentId
            assertThat(payload.has("documentId")).isTrue();
            assertThat(payload.get("documentId").asText()).isEqualTo(saga.documentId());

            // documentNumber (seeded in createSagaAtSignXml metadata)
            assertThat(payload.has("documentNumber")).isTrue();
            assertThat(payload.get("documentNumber").asText()).isEqualTo(saga.documentNumber());

            // taxInvoiceId (seeded in createSagaAtSignXml metadata)
            assertThat(payload.has("taxInvoiceId")).isTrue();
            assertThat(payload.get("taxInvoiceId").asText()).isEqualTo(saga.taxInvoiceId());

            // taxInvoiceDataJson present (value may be "{}" — acceptable for TC-01)
            assertThat(payload.has("taxInvoiceDataJson")).isTrue();
```

- [ ] **Step 3.4: Commit**

```bash
git add src/test/java/com/wpanther/orchestrator/integration/XmlSigningToTaxInvoicePdfCdcIntegrationTest.java
git commit -m "test: seed initial metadata in createSagaAtSignXml and expand TC-01 downstream payload assertions"
```

---

## Task 4: Create `run-cross-service-test.sh`

**Files:**
- Create: `/home/wpanther/projects/etax/invoice-microservices/scripts/run-cross-service-test.sh`

- [ ] **Step 4.1: Write the script**

Create `/home/wpanther/projects/etax/invoice-microservices/scripts/run-cross-service-test.sh`:

```bash
#!/bin/bash
# =============================================================================
# Run Cross-Service Integration Test:
#   saga.reply.xml-signing → orchestrator → saga.command.tax-invoice-pdf
#
# Usage:
#   ./scripts/run-cross-service-test.sh [--skip-build] [--skip-containers]
#
# --skip-build:      skip mvn clean package (use if already built)
# --skip-containers: skip container start (use if containers are already running)
#
# Requires: docker, mvn, curl, psql, jq
# =============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
SERVICES_DIR="$PROJECT_DIR/services"
SAGA_COMMONS_DIR="$PROJECT_DIR/../saga-commons"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'

SKIP_BUILD=false
SKIP_CONTAINERS=false
for arg in "$@"; do
    case $arg in
        --skip-build)      SKIP_BUILD=true ;;
        --skip-containers) SKIP_CONTAINERS=true ;;
    esac
done

XML_SIGNING_PID=""
TEST_EXIT_CODE=0

# ── Always kill background xml-signing-service on exit ──────────────────────
cleanup() {
    if [ -n "$XML_SIGNING_PID" ]; then
        echo -e "${YELLOW}Stopping xml-signing-service (PID $XML_SIGNING_PID)...${NC}"
        kill "$XML_SIGNING_PID" 2>/dev/null || true
        wait "$XML_SIGNING_PID" 2>/dev/null || true
        echo -e "${GREEN}xml-signing-service stopped.${NC}"
    fi
}
trap cleanup EXIT

# ── Step 1: Clean build ──────────────────────────────────────────────────────
if [ "$SKIP_BUILD" = false ]; then
    echo -e "${YELLOW}[1/7] Building saga-commons...${NC}"
    (cd "$SAGA_COMMONS_DIR" && mvn clean install -q -DskipTests)

    echo -e "${YELLOW}[1/7] Building xml-signing-service...${NC}"
    (cd "$SERVICES_DIR/xml-signing-service" && mvn clean package -q -DskipTests)

    echo -e "${YELLOW}[1/7] Building orchestrator-service...${NC}"
    (cd "$SERVICES_DIR/orchestrator-service" && mvn clean package -q -DskipTests)

    echo -e "${GREEN}Build complete.${NC}"
fi

# ── Step 2: Start containers ─────────────────────────────────────────────────
if [ "$SKIP_CONTAINERS" = false ]; then
    echo -e "${YELLOW}[2/7] Starting containers (--full-integration)...${NC}"
    "$SCRIPT_DIR/test-containers-start.sh" --full-integration
    echo -e "${GREEN}Containers are running.${NC}"
fi

# ── Step 3: Clean DB + Kafka ─────────────────────────────────────────────────
echo -e "${YELLOW}[3/7] Cleaning DB and Kafka topics...${NC}"
"$SCRIPT_DIR/test-containers-clean.sh"
echo -e "${GREEN}DB and Kafka cleaned.${NC}"

# ── Step 4: Restart Debezium connectors ──────────────────────────────────────
# test-containers-clean.sh deletes Kafka topics. Debezium's internal Kafka
# producer caches topic metadata (default TTL 5 min). Restarting connectors
# with ?includeTasks=true flushes the stale cache so outbox events are
# published to the newly-recreated topics immediately.
echo -e "${YELLOW}[4/7] Restarting Debezium connectors to flush stale producer cache...${NC}"

restart_connector() {
    local name=$1
    curl -sf -X POST "http://localhost:8083/connectors/${name}/restart?includeTasks=true" \
        -H "Content-Type: application/json" || true
    local retries=20
    until curl -sf "http://localhost:8083/connectors/${name}/status" 2>/dev/null \
               | grep -q '"state":"RUNNING"'; do
        retries=$((retries - 1))
        if [ $retries -eq 0 ]; then
            echo -e "${RED}ERROR: connector ${name} did not return to RUNNING after restart${NC}"
            exit 1
        fi
        sleep 3
    done
    echo -e "  ${GREEN}${name} is RUNNING${NC}"
}

restart_connector "outbox-connector-xmlsigning"
restart_connector "outbox-connector-orchestrator"
echo -e "${YELLOW}Waiting 5 s for producers to establish connections...${NC}"
sleep 5
echo -e "${GREEN}Debezium connectors restarted.${NC}"

# ── Step 5: Bootstrap eidasremotesigning credentials ─────────────────────────
# Replicates EidasRemoteSigningTestHelper.setupOnce() in shell:
#   1. POST /client-registration  → clientId + clientSecret
#   2. INSERT signing_certificate → credentialId
echo -e "${YELLOW}[5/7] Bootstrapping eidasremotesigning credentials...${NC}"

EIDAS_URL="http://localhost:9000"
PG_HOST="localhost"
PG_PORT="5433"
PG_USER="postgres"
PG_PASS="postgres"
EIDAS_DB="eidasremotesigning"
BCFKS_PATH="/app/keystores/eidas-signing.bfks"
BCFKS_PASS="eidas-signing-2024"
BCFKS_ALIAS="signing-key"

# Register OAuth2 client
REGISTRATION_RESPONSE=$(curl -sf -X POST "${EIDAS_URL}/client-registration" \
    -H "Content-Type: application/json" \
    -d '{"clientName":"run-cross-service-test","scopes":["signing"],"grantTypes":["client_credentials"]}')

CSC_CLIENT_ID=$(echo "$REGISTRATION_RESPONSE" | jq -r '.clientId')
CSC_CLIENT_SECRET=$(echo "$REGISTRATION_RESPONSE" | jq -r '.clientSecret')

if [ -z "$CSC_CLIENT_ID" ] || [ "$CSC_CLIENT_ID" = "null" ]; then
    echo -e "${RED}ERROR: failed to register OAuth2 client in eidasremotesigning${NC}"
    echo "Response: $REGISTRATION_RESPONSE"
    exit 1
fi
echo -e "  OAuth2 client registered: ${CSC_CLIENT_ID}"

# Insert BCFKS signing credential directly into the eidasremotesigning DB
CSC_CREDENTIAL_ID=$(PGPASSWORD="$PG_PASS" psql -h "$PG_HOST" -p "$PG_PORT" \
    -U "$PG_USER" -d "$EIDAS_DB" -t -c \
    "INSERT INTO signing_certificate
       (id, storage_type, keystore_path, keystore_password, certificate_alias, client_id, created_at)
     VALUES (gen_random_uuid(), 'BCFKS', '${BCFKS_PATH}', '${BCFKS_PASS}', '${BCFKS_ALIAS}', '${CSC_CLIENT_ID}', now())
     RETURNING id;" | tr -d ' \n')

if [ -z "$CSC_CREDENTIAL_ID" ]; then
    echo -e "${RED}ERROR: failed to insert signing credential into eidasremotesigning DB${NC}"
    exit 1
fi
echo -e "  Signing credential created: ${CSC_CREDENTIAL_ID}"
echo -e "${GREEN}eidasremotesigning credentials ready.${NC}"

export CSC_CLIENT_ID
export CSC_CREDENTIAL_ID

# ── Step 6: Start xml-signing-service in background ──────────────────────────
echo -e "${YELLOW}[6/7] Starting xml-signing-service (background)...${NC}"

(cd "$SERVICES_DIR/xml-signing-service" && \
    CSC_CLIENT_ID="$CSC_CLIENT_ID" \
    CSC_CREDENTIAL_ID="$CSC_CREDENTIAL_ID" \
    mvn spring-boot:run \
        -Dspring-boot.run.profiles=full-integration-test \
        -Dspring-boot.run.jvmArguments="-Dapp.csc.client-id=${CSC_CLIENT_ID} -Dapp.csc.credential-id=${CSC_CREDENTIAL_ID}" \
        -q 2>&1 | tee /tmp/xml-signing-service.log &)
XML_SIGNING_PID=$!

echo -e "${YELLOW}Waiting for xml-signing-service to be UP (max 90 s)...${NC}"
RETRIES=30
until curl -sf http://localhost:8086/actuator/health 2>/dev/null | grep -q '"status":"UP"'; do
    RETRIES=$((RETRIES - 1))
    if [ $RETRIES -eq 0 ]; then
        echo -e "${RED}ERROR: xml-signing-service did not start within 90 s${NC}"
        echo "Last log lines:"
        tail -20 /tmp/xml-signing-service.log 2>/dev/null || true
        exit 1
    fi
    sleep 3
done
echo -e "${GREEN}xml-signing-service is UP.${NC}"

# ── Step 7: Run orchestrator integration tests ────────────────────────────────
echo -e "${YELLOW}[7/7] Running XmlSigningToTaxInvoicePdfCdcIntegrationTest...${NC}"

set +e
(cd "$SERVICES_DIR/orchestrator-service" && \
    mvn test \
        -Pintegration \
        -Dtest=XmlSigningToTaxInvoicePdfCdcIntegrationTest \
        -Dspring.profiles.active=cross-service-test \
        -Dintegration.tests.enabled=true \
        -Dlogging.level.com.wpanther.orchestrator=DEBUG)
TEST_EXIT_CODE=$?
set -e

if [ $TEST_EXIT_CODE -eq 0 ]; then
    echo -e "${GREEN}=============================${NC}"
    echo -e "${GREEN} ALL TESTS PASSED${NC}"
    echo -e "${GREEN}=============================${NC}"
else
    echo -e "${RED}=============================${NC}"
    echo -e "${RED} TESTS FAILED (exit $TEST_EXIT_CODE)${NC}"
    echo -e "${RED}=============================${NC}"
fi

echo ""
echo "Containers are still running. To stop them:"
echo "  ./scripts/test-containers-stop.sh"
echo ""
echo "xml-signing-service log: /tmp/xml-signing-service.log"

exit $TEST_EXIT_CODE
```

- [ ] **Step 4.2: Make the script executable**

```bash
chmod +x /home/wpanther/projects/etax/invoice-microservices/scripts/run-cross-service-test.sh
```

- [ ] **Step 4.3: Commit**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/orchestrator-service
git add ../../scripts/run-cross-service-test.sh
git commit -m "feat: add run-cross-service-test.sh for end-to-end TC-01 execution"
```

---

## Task 5: Run the test end-to-end

- [ ] **Step 5.1: Run the full script**

```bash
cd /home/wpanther/projects/etax/invoice-microservices
./scripts/run-cross-service-test.sh
```

Expected: Script prints `ALL TESTS PASSED`. TC-01 `shouldPublishTaxInvoicePdfCommandAfterXmlSigning` passes with all assertions green.

- [ ] **Step 5.2: If TC-01 still fails — check Debezium connector task status**

```bash
curl -s http://localhost:8083/connectors/outbox-connector-xmlsigning/status | jq .
curl -s http://localhost:8083/connectors/outbox-connector-orchestrator/status | jq .
```

Both `connector.state` and each entry in `tasks[].state` must be `"RUNNING"`. If a task shows `"FAILED"`, restart it:

```bash
curl -X POST "http://localhost:8083/connectors/outbox-connector-xmlsigning/tasks/0/restart"
```

- [ ] **Step 5.3: If TC-01 fails on `signedXmlUrl` assertion — verify xml-signing-service processed the command**

```bash
PGPASSWORD=postgres psql -h localhost -p 5433 -U postgres -d xmlsigning_db \
  -c "SELECT document_id, status, signed_xml_url, error_message FROM signed_xml_documents ORDER BY created_at DESC LIMIT 5;"
```

If `status = FAILED` and `error_message` contains `401` or `Unauthorized`, the eidasremotesigning credential is wrong. Re-run step 5 of the script manually:

```bash
# Check registered clients
PGPASSWORD=postgres psql -h localhost -p 5433 -U postgres -d eidasremotesigning \
  -c "SELECT id, client_id FROM signing_certificate ORDER BY created_at DESC LIMIT 5;"
```

If `status = COMPLETED` and `signed_xml_url` is populated but the orchestrator test consumer never sees `saga.command.tax-invoice-pdf`, the Debezium connector restart did not take effect — re-run Task 4 steps 4.1 connector restart commands manually.

- [ ] **Step 5.4: Final commit message if all passes**

If TC-01 passes, no further commit needed — all fixes were committed in Tasks 1–4.

---

## Self-Review Checklist

- **Spec coverage:**
  - ✅ Problem 1 (null metadata) → Task 1
  - ✅ Problem 2 (missing metadata fields) → Task 3 (createSagaAtSignXml seeds documentNumber, taxInvoiceId, taxInvoiceDataJson)
  - ✅ Problem 3 (taxInvoiceNumber/documentNumber mismatch) → Task 2
  - ✅ Problem 4 (Debezium stale cache) → Task 4 script step 4
  - ✅ TC-01 expanded assertions for downstream compatibility → Task 3 step 3.3
  - ✅ run-cross-service-test.sh including eidasremotesigning bootstrap → Task 4
- **No placeholders:** All code blocks are complete and runnable
- **Type consistency:** `SagaSetup` record gains `taxInvoiceId()` and `documentNumber()` in Task 3 step 3.1; used correctly in Task 3 step 3.3 assertions
