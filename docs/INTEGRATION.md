# Orchestrator Service Integration Tests

This document describes the integration test suite for the orchestrator-service, which verifies the complete CDC (Change Data Capture) pipeline using Debezium and Kafka, Kafka consumer behavior, cross-service communication, and end-to-end saga flows.

## Overview

The orchestrator-service has **7 integration test classes** that verify different aspects of the system:

| Test Class | Purpose | Tests | Profile | External Dependencies |
|------------|---------|-------|---------|----------------------|
| `OrchestratorCdcIntegrationTest` | CDC flow: saga → outbox → Debezium → Kafka | 14 tests (4 nested classes) | `cdc-test` | PostgreSQL, Kafka, Debezium |
| `SagaDatabaseIntegrationTest` | Database schema verification | 20 tests | `cdc-test` | PostgreSQL only |
| `KafkaConsumerIntegrationTest` | Kafka consumer behavior | 23 tests (4 nested classes) | `consumer-test` | PostgreSQL, Kafka |
| `SagaReplyInvoiceTopicsIT` | Reply consumer per-topic verification with Mockito | 20 tests (2 nested classes) | `consumer-test` | PostgreSQL, Kafka |
| `TaxInvoiceSagaFlowCdcIntegrationTest` | Full tax invoice saga step-by-step CDC flow | 6 tests | `saga-flow-test` | PostgreSQL, Kafka, Debezium |
| `IntakeCdcToOrchestratorIT` | Cross-DB CDC: intake outbox → orchestrator consumer | 3 tests | `saga-flow-test` | PostgreSQL, Kafka, Debezium, intake_db |
| `XmlSigningToTaxInvoicePdfCdcIntegrationTest` | Cross-service: xml-signing-service → orchestrator | 4 tests (3 nested classes) | `cross-service-test` | PostgreSQL, Kafka, Debezium, xml-signing-service, eidasremotesigning, MinIO |

All test classes are **disabled by default** and only run when the system property `integration.tests.enabled=true` is set or when the Maven `integration` profile is active.

## Architecture

### CDC Flow

```
┌─────────────────┐     ┌──────────────┐     ┌─────────────┐     ┌───────────┐     ┌─────────────┐
│   Application   │────▶│   Database   │────▶│   Outbox    │────▶│ Debezium  │────▶│    Kafka    │
│    (Saga)       │     │  (PostgreSQL)│     │   Events    │     │   (CDC)   │     │  (Topics)   │
└─────────────────┘     └──────────────┘     └─────────────┘     └───────────┘     └─────────────┘
      │                                             │                                    │
      │ 1. Saga lifecycle changes                   │ 3. CDC reads WAL                  │ 5. Consumers
      │ 2. Transactional outbox writes              │ 4. Emits to Kafka                 │    receive events
      └──────────────────────────────────────────────────────────────────────────────────────┘
```

### Cross-Service CDC Flow (XmlSigningToTaxInvoicePdfCdcIntegrationTest)

```
┌─────────────────────┐    ┌────────────────────┐    ┌──────────────────────┐
│ orchestrator-service │───▶│ xml-signing-service │───▶│ orchestrator-service │
│ (saga.command.*)     │    │ (signs XML, stores  │    │ (saga.reply.xml-*    │
│   via CDC            │    │  in MinIO, publishes │    │   consumed, publishes│
│                      │    │  via CDC)           │    │  saga.command.tax-   │
│                      │    │                     │    │  invoice-pdf via CDC)│
└─────────────────────┘    └────────────────────┘    └──────────────────────┘
```

### Database Tables

| Table | Purpose |
|-------|---------|
| `saga_instances` | Saga aggregate root state |
| `saga_commands` | Command records sent to processing services |
| `saga_data` | Additional saga metadata |
| `outbox_events` | Events for CDC to publish to Kafka |

### Kafka Topics

| Topic | Purpose |
|-------|---------|
| `saga.lifecycle.started` | Saga started events |
| `saga.lifecycle.step-completed` | Step completion events |
| `saga.lifecycle.completed` | Saga completion events |
| `saga.lifecycle.failed` | Saga failure events |
| `saga.command.invoice` | Commands to invoice processing |
| `saga.command.tax-invoice` | Commands to tax invoice processing |
| `saga.command.xml-signing` | Commands to XML signing service |
| `saga.command.tax-invoice-pdf` | Commands to tax invoice PDF generation |
| `saga.command.pdf-signing` | Commands to PDF signing service |
| `saga.command.ebms-sending` | Commands to ebMS sending service |

## Prerequisites

### Required Infrastructure

| Component | Port | Purpose |
|-----------|------|---------|
| PostgreSQL | 5433 | Main database (orchestrator_db) |
| Kafka | 9093 | Message broker |
| Debezium Connect | 8083 | CDC platform |

### Additional Infrastructure (for specific test classes)

| Component | Port | Required By |
|-----------|------|-------------|
| intake_db (PostgreSQL) | 5433 | `IntakeCdcToOrchestratorIT` |
| xml-signing-service | — | `XmlSigningToTaxInvoicePdfCdcIntegrationTest` |
| eidasremotesigning | 9000 | `XmlSigningToTaxInvoicePdfCdcIntegrationTest` |
| MinIO | 9100 | `XmlSigningToTaxInvoicePdfCdcIntegrationTest` |

### Starting Test Containers

The project includes helper scripts to start all required containers:

```bash
cd /home/wpanther/projects/etax/invoice-microservices
./scripts/test-containers-start.sh --with-debezium --auto-deploy-connectors
```

This script:
- Starts PostgreSQL on port 5433 with all databases
- Starts Kafka on port 9093
- Starts Debezium Connect on port 8083
- Deploys Debezium connectors for outbox pattern
- Waits for all services to be healthy

For cross-service tests requiring xml-signing-service:

```bash
./scripts/test-containers-start.sh --with-eidas --with-debezium --auto-deploy-connectors
cd services/xml-signing-service && mvn spring-boot:run -Dspring-boot.run.profiles=full-integration-test &
```

### Running Database Migrations

Before running tests, ensure Flyway migrations have been applied:

```bash
cd services/orchestrator-service
mvn flyway:migrate -Dflyway.url=jdbc:postgresql://localhost:5433/orchestrator_db \
                   -Dflyway.user=postgres \
                   -Dflyway.password=postgres
```

For `IntakeCdcToOrchestratorIT`, also migrate intake_db:

```bash
cd services/document-intake-service
mvn flyway:migrate -Dflyway.url=jdbc:postgresql://localhost:5433/intake_db \
                   -Dflyway.user=postgres \
                   -Dflyway.password=postgres
```

## Running Tests

### Unit Tests Only (Fast, No External Dependencies)

```bash
cd services/orchestrator-service
mvn test
```

### Integration Tests (Requires Docker Containers)

Integration tests require running Docker containers first. From the orchestrator-service directory:

```bash
# 1. Start test containers (PostgreSQL, Kafka, Debezium)
cd /home/wpanther/projects/etax/invoice-microservices
./scripts/test-containers-start.sh --with-debezium --auto-deploy-connectors

# 2. Run integration tests (from orchestrator-service directory)
cd /home/wpanther/projects/etax/invoice-microservices/services/orchestrator-service
```

#### Run Specific Test Classes

```bash
# SagaReplyConsumer tests (per-topic verification with Mockito)
mvn clean test -Pintegration -Dtest="SagaReplyInvoiceTopicsIT" -Dintegration.tests.enabled=true -Dspring.profiles.active=consumer-test

# Tax Invoice saga CDC flow tests (step-by-step CDC verification)
mvn clean test -Pintegration -Dtest="TaxInvoiceSagaFlowCdcIntegrationTest" -Dintegration.tests.enabled=true -Dspring.profiles.active=saga-flow-test

# Orchestrator CDC tests (lifecycle events via Debezium)
mvn clean test -Pintegration -Dtest="OrchestratorCdcIntegrationTest" -Dintegration.tests.enabled=true -Dspring.profiles.active=cdc-test

# Kafka consumer tests (real consumers processing messages)
mvn clean test -Pintegration -Dtest="KafkaConsumerIntegrationTest" -Dintegration.tests.enabled=true -Dspring.profiles.active=consumer-test

# Intake CDC → Orchestrator tests (cross-DB CDC)
mvn clean test -Pintegration -Dtest="IntakeCdcToOrchestratorIT" -Dintegration.tests.enabled=true -Dspring.profiles.active=saga-flow-test

# Cross-service xml-signing tests (requires xml-signing-service running)
mvn clean test -Pintegration -Dtest="XmlSigningToTaxInvoicePdfCdcIntegrationTest" -Dintegration.tests.enabled=true -Dspring.profiles.active=cross-service-test
```

#### Run All Integration Tests

```bash
# Option 1: Using Maven profile (recommended)
mvn test -Pintegration -Dspring.profiles.active=cdc-test

# Option 2: Using system property directly
mvn test -Dintegration.tests.enabled=true -Dspring.profiles.active=cdc-test

# Option 3: Run specific test method
mvn test -Pintegration \
        -Dtest="OrchestratorCdcIntegrationTest\$CdcFlowTests#shouldPublishSagaStartedEventToKafkaViaCdc" \
        -Dspring.profiles.active=cdc-test
```

#### Stop Containers When Done

```bash
cd /home/wpanther/projects/etax/invoice-microservices
./scripts/test-containers-stop.sh
```

## Test Cases

### 1. OrchestratorCdcIntegrationTest

**Location:** `src/test/java/com/wpanther/orchestrator/integration/OrchestratorCdcIntegrationTest.java`

**Profile:** `cdc-test` | **Extends:** `AbstractCdcIntegrationTest`

**Purpose:** Verifies the complete CDC flow from saga creation through database writes, outbox pattern, and finally Kafka message publication via Debezium. Uses `@MockBean SagaCommandPublisher` to isolate CDC lifecycle event testing.

**Test Structure:**
```
OrchestratorCdcIntegrationTest
├── DatabaseWriteTests (4 tests)
├── OutboxPatternTests (4 tests)
├── CdcFlowTests (4 tests)
└── DocumentTypeTests (2 tests)
```

#### DatabaseWriteTests (4 tests)

These tests verify that saga state is correctly persisted to the database via JDBC (bypassing JPA cache).

| Test | Description | Verification |
|------|-------------|--------------|
| `shouldSaveSagaInstanceToDatabase` | Starting a saga persists state to `saga_instances` table | status = IN_PROGRESS, document_type = INVOICE, current_step = PROCESS_INVOICE, document_id matches |
| `shouldCreateCommandRecordInDatabase` | Starting a saga creates command records in `saga_commands` | saga_commands record created, target_step = PROCESS_TAX_INVOICE, status = SENT |
| `shouldCreateOutboxEventsInSameTransaction` | Outbox events are created atomically with saga state | At least 1 outbox event created, SagaStartedEvent exists |
| `shouldUpdateSagaStatusOnCompletion` | Completing all steps updates saga status | status = COMPLETED, completed_at is not null |

#### OutboxPatternTests (4 tests)

These tests verify the outbox pattern implementation for CDC, checking correct routing and metadata in outbox_events rows.

| Test | Description | Verification |
|------|-------------|--------------|
| `shouldWriteSagaStartedEventWithCorrectTopic` | SagaStartedEvent has correct routing fields | topic = saga.lifecycle.started, aggregate_type = SagaInstance, status = PENDING |
| `shouldSetSagaIdAsPartitionKey` | All lifecycle events use sagaId as partition key | partition_key = sagaId, ensures ordering per saga |
| `shouldWriteStepCompletedEventOnReply` | Handling reply creates step completion event | event_type = SagaStepCompletedEvent, topic = saga.lifecycle.step-completed |
| `shouldWriteCompletedEventOnSagaCompletion` | Saga completion creates completion event | event_type = SagaCompletedEvent, topic = saga.lifecycle.completed, payload contains durationMs |

#### CdcFlowTests (4 tests)

These tests verify the end-to-end CDC pipeline to Kafka, using Awaitility to poll for Debezium-delivered messages.

| Test | Description | Verification |
|------|-------------|--------------|
| `shouldPublishSagaStartedEventToKafkaViaCdc` | Debezium publishes SagaStartedEvent to Kafka | Message on saga.lifecycle.started, payload contains sagaId/documentType/documentId, eventType = SagaStartedEvent |
| `shouldPublishSagaCompletedEventToKafkaViaCdc` | Debezium publishes SagaCompletedEvent to Kafka | Message on saga.lifecycle.completed, payload contains durationMs |
| `shouldPreserveSagaIdAsKafkaKeyThroughCdc` | Partition key is preserved through CDC | Kafka message key = sagaId, payload sagaId matches key |
| `shouldPublishMultipleLifecycleEventsInOrder` | Multiple events are published in correct order | saga.lifecycle.started message exists, saga.lifecycle.completed message exists |

#### DocumentTypeTests (2 tests)

These tests verify handling of different document types (Invoice vs TaxInvoice).

| Test | Description | Verification |
|------|-------------|--------------|
| `shouldHandleInvoiceDocumentType` | Invoice documents use correct workflow | current_step = PROCESS_INVOICE, documentType = INVOICE, outbox payload has correct type |
| `shouldHandleTaxInvoiceDocumentType` | TaxInvoice documents use correct workflow | current_step = PROCESS_TAX_INVOICE, documentType = TAX_INVOICE, outbox payload has correct type |

---

### 2. SagaDatabaseIntegrationTest

**Location:** `src/test/java/com/wpanther/orchestrator/integration/SagaDatabaseIntegrationTest.java`

**Profile:** `cdc-test` | **Extends:** `AbstractCdcIntegrationTest`

**Purpose:** Verifies database schema structure without requiring Kafka/Debezium. Ensures tables, columns, indexes, and constraints are correctly created by Flyway migrations. All assertions use `information_schema` and `pg_indexes` system catalogs.

#### Table Existence Tests (4 tests)

| Test | Description |
|------|-------------|
| `shouldHaveSagaInstancesTable` | Verifies `saga_instances` table exists |
| `shouldHaveSagaCommandsTable` | Verifies `saga_commands` table exists |
| `shouldHaveOutboxEventsTable` | Verifies `outbox_events` table exists |
| `shouldHaveSagaDataTable` | Verifies `saga_data` table exists |

#### Column Tests (6 tests)

| Test | Description | Columns/Type Verified |
|------|-------------|----------------------|
| `shouldHaveCoreSagaInstancesColumns` | Core columns in saga_instances | id, document_type, document_id, current_step, status, created_at, updated_at |
| `shouldHaveCoreSagaCommandsColumns` | Core columns in saga_commands | id, saga_id, command_type, target_step, payload, status, created_at |
| `shouldHaveDebeziumRoutingColumns` | Debezium routing columns in outbox_events | topic, partition_key, headers |
| `shouldHaveSagaCommonsColumns` | Saga-commons columns in outbox_events | retry_count, error_message, published_at |
| `shouldVerifyStatusColumnAllowsValidValues` | Status column is VARCHAR | data_type = character varying |
| `shouldVerifyVarcharTypeForPrimaryKeys` | Primary keys are VARCHAR (UUID strings) | saga_instances.id, saga_commands.id are character varying |

#### Data Type Tests (3 tests)

| Test | Description | Verified Type |
|------|-------------|---------------|
| `shouldHaveTextMetadataColumn` | saga_data.metadata is TEXT | data_type = text |
| `shouldHaveTextPayloadColumn` | saga_commands.payload is TEXT | data_type = text |
| `shouldHaveTimestampColumnsForTracking` | saga_instances has timestamp columns | created_at, updated_at, completed_at |

#### Constraint Tests (2 tests)

| Test | Description |
|------|-------------|
| `shouldHaveForeignKeyConstraint` | saga_commands.saga_id references saga_instances.id |
| `shouldHaveSagaDataForeignKeyConstraint` | saga_data.saga_id references saga_instances.id |

#### Index Tests (5 tests)

| Test | Description | Index On |
|------|-------------|----------|
| `shouldHaveStatusIndex` | Status index for Debezium polling | outbox_events |
| `shouldHaveUpdatedAtIndex` | Updated_at index | saga_instances |
| `shouldHaveStatusIndexOnSagaInstances` | Status index | saga_instances |
| `shouldHaveCompositeDocumentIndex` | Document composite index | saga_instances |
| `shouldHaveSagaIdIndexOnCommands` | saga_id index | saga_commands |

---

### 3. KafkaConsumerIntegrationTest

**Location:** `src/test/java/com/wpanther/orchestrator/integration/KafkaConsumerIntegrationTest.java`

**Profile:** `consumer-test` | **Extends:** `AbstractKafkaConsumerTest`

**Purpose:** Verifies Kafka consumer behavior for `StartSagaCommandConsumer` and `SagaReplyConsumer`. Tests send real messages to Kafka topics and verify saga state transitions in the database. Uses `@MockBean SagaEventPublisher` (outbox assertions belong in CDC tests).

**Test Structure:**
```
KafkaConsumerIntegrationTest
├── StartSagaCommandConsumerTests (7 tests)
├── InvoiceReplyConsumerTests (7 tests)
├── TaxInvoiceReplyConsumerTests (6 tests)
└── EndToEndFlowTests (3 tests)
```

#### StartSagaCommandConsumerTests (7 tests)

Verifies that `StartSagaCommandConsumer` correctly processes commands from the `saga.commands.orchestrator` topic.

| Test | Description | Verification |
|------|-------------|--------------|
| `shouldConsumeStartSagaCommandAndCreateSaga` | Consuming command creates saga in database | status = IN_PROGRESS, correct document_type/document_id/current_step |
| `shouldRouteInvoiceToProcessInvoiceStep` | Invoice documents routed to PROCESS_INVOICE | current_step = PROCESS_INVOICE |
| `shouldRouteTaxInvoiceToProcessTaxInvoiceStep` | TaxInvoice documents routed to PROCESS_TAX_INVOICE | current_step = PROCESS_TAX_INVOICE |
| `shouldCreateOutboxEvents` | Saga DB state is IN_PROGRESS after command | status = IN_PROGRESS, current_step = PROCESS_INVOICE, created_at is not null |
| `shouldCreateCommandRecord` | Command records created in saga_commands | target_step matches document type, status = SENT |
| `shouldReuseExistingActiveSaga` | Idempotency: duplicate commands reuse existing saga | Only one saga exists per documentId |
| `shouldSkipInvalidDocumentType` | Invalid document types are skipped | No saga created for invalid type |

#### InvoiceReplyConsumerTests (7 tests)

Verifies that `SagaReplyConsumer` correctly processes replies from `saga.reply.invoice`.

| Test | Description | Verification |
|------|-------------|--------------|
| `shouldConsumeSuccessReplyAndAdvanceSaga` | Success reply advances from PROCESS_INVOICE to SIGN_XML | current_step = SIGN_XML, command marked COMPLETED |
| `shouldPublishStepCompletedEvent` | Step completion verified via DB state advance | current_step = SIGN_XML after reply |
| `shouldCompleteSagaOnFinalStep` | Completing all 5 steps marks saga COMPLETED | status = COMPLETED, all 5 commands COMPLETED |
| `shouldPublishSagaCompletedEventOnCompletion` | Completion verified via DB state | status = COMPLETED, completed_at is not null |
| `shouldIncrementRetryCountOnFailure` | Failure increments retry count | retry_count incremented, command marked FAILED |
| `shouldRetryFailedStep` | Transient failure triggers retry | Multiple command records for same step, saga advances after success |
| `shouldFailSagaAfterMaxRetries` | Max retries exceeded transitions to COMPENSATING | status = COMPENSATING (no compensation steps available for first step) |

#### TaxInvoiceReplyConsumerTests (6 tests)

Verifies `SagaReplyConsumer` behavior for `saga.reply.tax-invoice` topic with TAX_INVOICE document type.

| Test | Description | Verification |
|------|-------------|--------------|
| `shouldConsumeSuccessReplyAndAdvanceTaxInvoiceSaga` | Tax invoice reply advances saga | PROCESS_TAX_INVOICE → SIGN_XML, command marked COMPLETED |
| `shouldRouteToGenerateTaxInvoicePdfStep` | Correct PDF generation step after two steps | GENERATE_TAX_INVOICE_PDF reached |
| `shouldCompleteFullTaxInvoiceWorkflow` | All 5 tax invoice steps complete | status = COMPLETED, all 5 commands COMPLETED |
| `shouldPublishStepCompletedEventForTaxInvoice` | Step advance verified via DB state | current_step = SIGN_XML after reply |
| `shouldIncrementRetryCountOnTaxInvoiceFailure` | Retry logic works for tax invoices | retry_count incremented |
| `shouldFailTaxInvoiceSagaAfterMaxRetries` | Max retries transitions to COMPENSATING | status = COMPENSATING |

#### EndToEndFlowTests (3 tests)

Verifies complete workflows from command consumption to saga completion, starting from a `StartSagaCommand` on Kafka.

| Test | Description | Verification |
|------|-------------|--------------|
| `shouldCompleteFullInvoiceWorkflow` | Full invoice workflow from command to completion | All 5 commands COMPLETED, saga status = COMPLETED, completed_at is not null |
| `shouldRecoverFromTransientFailure` | Transient failure recovery | Saga advances after retry success, status remains IN_PROGRESS |
| `shouldMaintainStateConsistency` | State consistency across all steps | Each step transition verified, final state COMPLETED with completed_at |

---

### 4. SagaReplyInvoiceTopicsIT

**Location:** `src/test/java/com/wpanther/orchestrator/integration/SagaReplyInvoiceTopicsIT.java`

**Profile:** `consumer-test` | **Extends:** `AbstractKafkaConsumerTest`

**Purpose:** Per-topic verification of `SagaReplyConsumer` handling replies on `saga.reply.invoice` and `saga.reply.tax-invoice`. Unlike `KafkaConsumerIntegrationTest`, this class uses **Mockito `verify()`** to assert that `SagaEventPublisher` and `SagaCommandPublisher` are called with correct arguments. Tests are numbered (TC-INV-01 through TC-INV-10, TC-TAX-01 through TC-TAX-10) for traceability.

**Test Structure:**
```
SagaReplyInvoiceTopicsIT
├── InvoiceReplyTopicTests (10 tests)
└── TaxInvoiceReplyTopicTests (10 tests)
```

#### InvoiceReplyTopicTests — `saga.reply.invoice` (10 tests)

| Test ID | Test | Description | Verification |
|---------|------|-------------|--------------|
| TC-INV-01 | `shouldAdvanceSagaToSignXmlOnSuccessReply` | SUCCESS reply advances from PROCESS_INVOICE to SIGN_XML | current_step = SIGN_XML, status = IN_PROGRESS |
| TC-INV-02 | `shouldCompleteProcessInvoiceCommandAndCreateSignXmlCommand` | PROCESS_INVOICE command marked COMPLETED, new SIGN_XML command created | PROCESS_INVOICE status = COMPLETED with completed_at, SIGN_XML status = SENT |
| TC-INV-03 | `shouldPublishStepCompletedEventOnSuccess` | `SagaEventPublisher.publishSagaStepCompleted` called | Mockito verify: step = PROCESS_INVOICE |
| TC-INV-04 | `shouldPublishSignXmlCommandOnSuccess` | `SagaCommandPublisher.publishCommandForStep` called for SIGN_XML | Mockito verify: step = SIGN_XML |
| TC-INV-05 | `shouldIncrementRetryCountOnFailureReply` | FAILURE reply increments retry_count, step unchanged | retry_count = initial + 1, current_step = PROCESS_INVOICE, status = IN_PROGRESS |
| TC-INV-06 | `shouldMarkCommandFailedWithErrorMessageOnFailure` | FAILED command carries error message | error_message matches sent value |
| TC-INV-07 | `shouldRecoverAndAdvanceAfterTransientFailure` | Failure then success advances saga | current_step = SIGN_XML, status = IN_PROGRESS |
| TC-INV-08 | `shouldEnterCompensatingAfterMaxRetriesExceeded` | 4 failures exceed max retries (3) | status = COMPENSATING, error_message is not null |
| TC-INV-09 | `shouldPublishSagaFailedEventAfterMaxRetries` | `SagaEventPublisher.publishSagaFailed` called after max retries | Mockito verify: step = PROCESS_INVOICE |
| TC-INV-10 | `shouldHandleUnknownSagaIdGracefullyOnInvoiceReplyTopic` | Unknown sagaId swallowed, consumer stays healthy | No saga created, subsequent valid message processed correctly |

#### TaxInvoiceReplyTopicTests — `saga.reply.tax-invoice` (10 tests)

| Test ID | Test | Description | Verification |
|---------|------|-------------|--------------|
| TC-TAX-01 | `shouldAdvanceSagaToSignXmlOnSuccessReply` | SUCCESS reply advances from PROCESS_TAX_INVOICE to SIGN_XML | current_step = SIGN_XML, status = IN_PROGRESS |
| TC-TAX-02 | `shouldCompleteProcessTaxInvoiceCommandAndCreateSignXmlCommand` | PROCESS_TAX_INVOICE command COMPLETED, SIGN_XML command created | PROCESS_TAX_INVOICE status = COMPLETED, SIGN_XML status = SENT |
| TC-TAX-03 | `shouldPublishStepCompletedEventOnSuccess` | `SagaEventPublisher.publishSagaStepCompleted` called | Mockito verify: step = PROCESS_TAX_INVOICE |
| TC-TAX-04 | `shouldPublishSignXmlCommandOnSuccess` | `SagaCommandPublisher.publishCommandForStep` called for SIGN_XML | Mockito verify: step = SIGN_XML |
| TC-TAX-05 | `shouldIncrementRetryCountOnFailureReply` | FAILURE reply increments retry_count | retry_count = initial + 1, step unchanged, status = IN_PROGRESS |
| TC-TAX-06 | `shouldMarkCommandFailedWithErrorMessageOnFailure` | FAILED command carries error message | error_message matches sent value |
| TC-TAX-07 | `shouldRecoverAndAdvanceAfterTransientFailure` | Failure then success advances saga | current_step = SIGN_XML, status = IN_PROGRESS |
| TC-TAX-08 | `shouldEnterCompensatingAfterMaxRetriesExceeded` | Max retries exceeded | status = COMPENSATING, error_message is not null |
| TC-TAX-09 | `shouldPublishSagaFailedEventAfterMaxRetries` | `SagaEventPublisher.publishSagaFailed` called | Mockito verify: step = PROCESS_TAX_INVOICE |
| TC-TAX-10 | `shouldHandleUnknownSagaIdGracefullyOnTaxInvoiceReplyTopic` | Unknown sagaId swallowed, consumer stays healthy | No saga created, subsequent valid message processed correctly |

---

### 5. TaxInvoiceSagaFlowCdcIntegrationTest

**Location:** `src/test/java/com/wpanther/orchestrator/integration/TaxInvoiceSagaFlowCdcIntegrationTest.java`

**Profile:** `saga-flow-test` | **Standalone** (does not extend abstract base)

**Purpose:** End-to-end CDC test verifying that each saga step transition results in the correct command being published to the correct Kafka topic via Debezium CDC. Tests the simplified Tax Invoice flow (5 steps, no SIGNEDXML_STORAGE/PDF_STORAGE/STORE_DOCUMENT).

**Setup:** Each test uses `createSagaAt()` which inserts saga records directly via JdbcTemplate (avoiding JPA CLOB issues). A test Kafka consumer subscribes to all command and lifecycle topics.

**Simplified Tax Invoice Flow:**
```
PROCESS_TAX_INVOICE → SIGN_XML → GENERATE_TAX_INVOICE_PDF → SIGN_PDF → SEND_EBMS
```

| Test ID | Test | Description | Verification |
|---------|------|-------------|--------------|
| TC-01 | `tc01_startSagaCommandCreatesProcessTaxInvoiceCommandViaCdc` | StartSagaCommand received from document-intake triggers ProcessTaxInvoiceCommand via CDC | Message on saga.command.tax-invoice, payload has sagaId/documentId, saga status = IN_PROGRESS at PROCESS_TAX_INVOICE |
| TC-02 | `tc02_taxInvoiceReplySuccessCreatesSignXmlCommandViaCdc` | SUCCESS reply from tax-invoice-processing advances to SIGN_XML | Message on saga.command.xml-signing, sagaStep = sign-xml, saga advanced to SIGN_XML |
| TC-03 | `tc03_taxInvoicePdfGenerationFromSignXmlViaCdc` | SUCCESS reply from xml-signing (with signedXmlUrl) advances to GENERATE_TAX_INVOICE_PDF | Message on saga.command.tax-invoice-pdf, sagaStep = generate-tax-invoice-pdf, signedXmlUrl in metadata |
| TC-04 | `tc04_taxInvoicePdfReplySuccessCreatesSignPdfCommandViaCdc` | SUCCESS reply from taxinvoice-pdf-generation (with pdfUrl) advances to SIGN_PDF | Message on saga.command.pdf-signing, sagaStep = sign-pdf, pdfUrl/pdfSize in metadata |
| TC-05 | `tc05_pdfSigningReplySuccessCreatesEbmsSendingCommandViaCdc` | SUCCESS reply from pdf-signing (with signedPdfUrl) advances to SEND_EBMS | Message on saga.command.ebms-sending, sagaStep = send-ebms |
| TC-06 | `tc06_ebmsSendingReplySuccessCompletesTheSagaViaCdc` | SUCCESS reply from ebms-sending completes saga | Message on saga.lifecycle.completed, eventType = SagaCompletedEvent, durationMs present, saga status = COMPLETED |

---

### 6. IntakeCdcToOrchestratorIT

**Location:** `src/test/java/com/wpanther/orchestrator/integration/IntakeCdcToOrchestratorIT.java`

**Profile:** `saga-flow-test` | **Standalone** (does not extend abstract base)

**Purpose:** Verifies the cross-database CDC path from `intake_db.outbox_events` → Debezium → `saga.commands.orchestrator` → `StartSagaCommandConsumer` → `orchestrator_db.saga_instances`. Tests insert outbox rows directly into `intake_db` using a separate `intakeJdbcTemplate` and verify that the orchestrator consumer processes them.

**Unique Setup:** Connects to both `orchestrator_db` (default JdbcTemplate) and `intake_db` (dedicated `intakeJdbcTemplate` bean). Waits for both Debezium connectors to be running before tests.

| Test ID | Test | Description | Verification |
|---------|------|-------------|--------------|
| TC-1 | `shouldDeliverStartSagaCommandViaCdcToKafka` | Insert into intake_db.outbox_events → Debezium delivers to saga.commands.orchestrator | Kafka message key = correlationId, payload has documentId/documentType/correlationId, null fields (sagaId/sagaStep) are absent |
| TC-2 | `shouldOrchestratorConsumerCreateSagaFromCdcMessage` | CDC message consumed by orchestrator creates saga instance | saga_instances row: document_type = TAX_INVOICE, current_step = PROCESS_TAX_INVOICE, status = IN_PROGRESS, correlation_id matches |
| TC-3 | `shouldRawCdcPayloadBeDeserializableByOrchestratorConsumer` | Raw CDC payload deserializes correctly using orchestrator's `JsonDeserializer<StartSagaCommand>` | StartSagaCommand fields populated: documentId, documentType = TAX_INVOICE, correlationId, xmlContent, documentNumber |

---

### 7. XmlSigningToTaxInvoicePdfCdcIntegrationTest

**Location:** `src/test/java/com/wpanther/orchestrator/integration/XmlSigningToTaxInvoicePdfCdcIntegrationTest.java`

**Profile:** `cross-service-test` | **Standalone** | **Tag:** `cross-service`

**Purpose:** Cross-service CDC integration test verifying real service-to-service communication. The orchestrator publishes to `saga.command.xml-signing`, the xml-signing-service consumes it, signs the XML via eidasremotesigning (real CSC API calls), stores the signed XML in MinIO, publishes a reply via CDC, and the orchestrator's `SagaReplyConsumer` consumes the reply and publishes `saga.command.tax-invoice-pdf` via CDC.

**Prerequisites (beyond standard containers):**
- xml-signing-service must be running with `full-integration-test` profile
- eidasremotesigning service on port 9000 (bootstrapped with BCFKS credentials via `EidasRemoteSigningTestHelper`)
- MinIO on port 9100 (Docker test instance)
- Both Debezium connectors running: `outbox-connector-orchestrator` and `outbox-connector-xmlsigning`
- TaxInvoice XML sample at classpath `samples/TaxInvoice_2p1_valid.xml`

**Test Structure:**
```
XmlSigningToTaxInvoicePdfCdcIntegrationTest
├── HappyPath (1 test)
├── MinioVerification (1 test)
├── ErrorHandling (1 test)
└── MultipleSagas (1 test)
```

#### HappyPath

| Test | Description | Verification |
|------|-------------|--------------|
| TC-01: `shouldPublishTaxInvoicePdfCommandAfterXmlSigning` | Orchestrator publishes xml-signing command → xml-signing-service signs and replies → orchestrator publishes tax-invoice-pdf command via CDC | Message on saga.command.tax-invoice-pdf with sagaId/documentId/signedXmlUrl/documentNumber, saga advanced to GENERATE_TAX_INVOICE_PDF |

#### MinioVerification

| Test | Description | Verification |
|------|-------------|--------------|
| TC-02: `shouldVerifySignedXmlStoredInMinIO` | Signed XML from xml-signing-service is accessible in MinIO and contains XAdES signature | HTTP 200 from MinIO, XML contains Signature/SignedInfo/SignatureValue elements, signed XML is larger than original |

#### ErrorHandling

| Test | Description | Verification |
|------|-------------|--------------|
| TC-03: `shouldAdvanceWhenXmlSigningFails` | Invalid XML sent to xml-signing-service → saga still advances to GENERATE_TAX_INVOICE_PDF | current_step = GENERATE_TAX_INVOICE_PDF, saga.command.tax-invoice-pdf published |

#### MultipleSagas (Concurrency)

| Test | Description | Verification |
|------|-------------|--------------|
| TC-04: `shouldHandleMultipleSagasConcurrently` | Two concurrent sagas at SIGN_XML both produce saga.command.tax-invoice-pdf via CDC | Both messages received with correct sagaId/documentId, both sagas advanced to GENERATE_TAX_INVOICE_PDF |

## Test Helper Methods

### AbstractCdcIntegrationTest Helper Methods

Base class for `OrchestratorCdcIntegrationTest` and `SagaDatabaseIntegrationTest`.

| Method | Purpose |
|--------|---------|
| `await()` | Returns Awaitility for polling conditions |
| `hasMessageOnTopic(String topic, String key)` | Checks if message exists on Kafka topic |
| `getMessagesFromTopic(String topic, String key)` | Retrieves messages from Kafka topic |
| `parseJson(String json)` | Parses JSON string into JsonNode (handles Debezium double-encoding) |

### AbstractKafkaConsumerTest Helper Methods

Base class for `KafkaConsumerIntegrationTest` and `SagaReplyInvoiceTopicsIT`.

| Method | Purpose |
|--------|---------|
| `createStartSagaCommand(DocumentType, String documentId)` | Creates StartSagaCommand for testing |
| `createSagaReplyJson(String sagaId, String step, boolean success, String errorMessage)` | Creates SagaReply JSON with full ConcreteSagaReply format |
| `sendStartSagaCommand(StartSagaCommand command)` | Sends StartSagaCommand to saga.commands.orchestrator |
| `sendInvoiceReply(String sagaId, String step, boolean success, String errorMessage)` | Sends SagaReply to saga.reply.invoice |
| `sendTaxInvoiceReply(String sagaId, String step, boolean success, String errorMessage)` | Sends SagaReply to saga.reply.tax-invoice |
| `getSagaInstance(String sagaId)` | Gets saga instance from database by ID via JdbcTemplate |
| `getSagaInstanceByDocumentId(DocumentType, String documentId)` | Gets saga instance by document type and ID |
| `getCommandHistory(String sagaId)` | Gets command records for a saga |
| `getOutboxEvents(String sagaId)` | Gets outbox events for a saga |
| `awaitSagaStatus(String sagaId, String expectedStatus)` | Waits for saga to reach expected status |
| `awaitCurrentStep(String sagaId, SagaStep expectedStep)` | Waits for saga to reach expected step |
| `awaitOutboxEvent(String sagaId, String eventType)` | Waits for outbox event of specific type |
| `awaitSagaByDocumentId(DocumentType, String documentId)` | Waits for saga to exist by document ID |
| `createTestMetadata(String documentId)` | Creates test DocumentMetadata |
| `startTestSaga(DocumentType, String documentId)` | Starts a saga with mocked command publisher |
| `completeAllInvoiceSteps(String sagaId)` | Completes all 5 invoice workflow steps |
| `completeAllTaxInvoiceSteps(String sagaId)` | Completes all 5 tax invoice workflow steps |

## Saga Workflows

### Invoice Workflow (5 steps)

```
PROCESS_INVOICE → SIGN_XML → GENERATE_INVOICE_PDF → SIGN_PDF → SEND_EBMS
```

### Tax Invoice Workflow (5 steps)

```
PROCESS_TAX_INVOICE → SIGN_XML → GENERATE_TAX_INVOICE_PDF → SIGN_PDF → SEND_EBMS
```

### Abbreviated Tax Invoice Workflow (5 steps)

```
PROCESS_TAX_INVOICE → SIGN_XML → GENERATE_TAX_INVOICE_PDF → SIGN_PDF → SEND_EBMS
```

## Important Implementation Notes

### Idempotency

- `SagaInstance.complete()` is idempotent — calling multiple times has no additional effect
- `startSaga()` reuses existing non-terminal sagas for the same documentId

### Step Codes

Tests expect enum names (e.g., `PROCESS_INVOICE`) not codes (e.g., `process-invoice`) because:
- `@Enumerated(EnumType.STRING)` stores enum names in database
- Domain model uses enum names for state representation

### Transactional Outbox

- Outbox events are written in the same transaction as domain state changes
- This guarantees atomicity — no events lost if transaction rolls back
- Debezium reads the outbox table and publishes to Kafka asynchronously

### Partition Keys

- All lifecycle events use `sagaId` as partition key
- This ensures all events for a saga go to the same Kafka partition
- Maintains ordering guarantees for saga lifecycle

### Test Containers

Integration tests use port **9093** for Kafka (external access), while production uses **9092**.
Integration tests use port **5433** for PostgreSQL (test database).

### CLOB Issue with JPA

Some tests (`TaxInvoiceSagaFlowCdcIntegrationTest`, `XmlSigningToTaxInvoicePdfCdcIntegrationTest`) use raw `JdbcTemplate` to insert saga records instead of JPA, to avoid Hibernate 6.4 + PostgreSQL JDBC 42.6 CLOB handling issues with TEXT columns.

### Debezium Double-Encoding

Debezium's `expand.json.payload` setting may double-encode JSON payloads. Test JSON parsers handle this by checking if the parsed node is textual and re-parsing if needed.

### SagaReply JSON Format

Tests use the full `ConcreteSagaReply` format with IntegrationEvent fields:
```json
{
  "eventId": "...",
  "occurredAt": "...",
  "eventType": "SagaReplySuccess/SagaReplyFailure",
  "version": 1,
  "sagaId": "...",
  "sagaStep": "...",
  "correlationId": "...",
  "status": "SUCCESS/FAILURE",
  "errorMessage": "..."
}
```

### YAML Properties

Reply topics use `app.saga.reply.{invoice,tax-invoice}` path, NOT `app.kafka.topics`.

## Troubleshooting

### Tests Fail with "Connection Refused"

**Symptom:** Tests fail to connect to PostgreSQL or Kafka

**Solution:** Start test containers first:
```bash
cd /home/wpanther/projects/etax/invoice-microservices
./scripts/test-containers-start.sh --with-debezium --auto-deploy-connectors
```

### Tests Fail with "Table Does Not Exist"

**Symptom:** Tests report missing database tables

**Solution:** Run database migrations:
```bash
mvn flyway:migrate -Dflyway.url=jdbc:postgresql://localhost:5433/orchestrator_db \
                   -Dflyway.user=postgres \
                   -Dflyway.password=postgres
```

### Debezium Connector Not Working

**Symptom:** CDC tests timeout waiting for Kafka messages

**Solution:** Check connector status:
```bash
curl http://localhost:8083/connectors/orchestrator-connector/status
curl http://localhost:8083/connectors/outbox-connector-orchestrator/status
curl http://localhost:8083/connectors/outbox-connector-xmlsigning/status
```

Expected output: `"state": "RUNNING"`

### Tests Are Skipped

**Symptom:** Integration tests show as skipped

**Solution:** Ensure you're using the integration profile or system property:
```bash
mvn test -Pintegration -Dspring.profiles.active=cdc-test
```

### Cross-Service Tests Fail

**Symptom:** `XmlSigningToTaxInvoicePdfCdcIntegrationTest` fails

**Solution:** Ensure xml-signing-service and eidasremotesigning are running:
```bash
# Start with eidasremotesigning support
./scripts/test-containers-start.sh --with-eidas --with-debezium --auto-deploy-connectors

# Start xml-signing-service
cd services/xml-signing-service && mvn spring-boot:run -Dspring-boot.run.profiles=full-integration-test
```

## References

- **AbstractCdcIntegrationTest:** Base class providing test infrastructure, Kafka consumer utilities, and common test helpers for CDC tests
- **AbstractKafkaConsumerTest:** Base class providing Kafka message creation, sending, and database verification helpers for consumer tests
- **CdcTestConfiguration:** Test configuration excluding KafkaAutoConfiguration, enabling manual control of messaging components
- **ConsumerTestConfiguration:** Test configuration enabling `@EnableKafka` and loading real consumers
- **CrossServiceTestConfiguration:** Test configuration for cross-service tests with both orchestrator and xml-signing Debezium connectors
- **TestKafkaConsumerConfig:** Test Kafka consumer configuration for port 9093
- **TestKafkaProducerConfig:** Test Kafka producer configuration for port 9093
- **EidasRemoteSigningTestHelper:** Bootstraps OAuth2 credentials and BCFKS signing certificates for real signing in cross-service tests
- **application-cdc-test.yml:** Test configuration with PostgreSQL (5433), Kafka (9093) connection settings
- **application-consumer-test.yml:** Consumer test configuration with real Kafka consumers
- **application-saga-flow-test.yml:** Saga flow test configuration for step-by-step CDC verification
- **application-cross-service-test.yml:** Cross-service test configuration
