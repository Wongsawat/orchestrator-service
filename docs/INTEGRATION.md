# Orchestrator Service Integration Tests

This document describes the integration test suite for the orchestrator-service, which verifies the complete CDC (Change Data Capture) pipeline using Debezium and Kafka.

## Overview

The orchestrator-service has two integration test classes that verify different aspects of the system:

| Test Class | Purpose | Tests | External Dependencies |
|------------|---------|-------|----------------------|
| `OrchestratorCdcIntegrationTest` | Full CDC flow verification | 14 tests (4 nested classes) | PostgreSQL, Kafka, Debezium |
| `SagaDatabaseIntegrationTest` | Database schema verification | 20 tests | PostgreSQL only |

Both test classes are **disabled by default** and only run when the system property `integration.tests.enabled=true` is set or when the Maven `integration` profile is active.

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

## Prerequisites

### Required Infrastructure

| Component | Port | Purpose |
|-----------|------|---------|
| PostgreSQL | 5433 | Main database (orchestrator_db) |
| Kafka | 9093 | Message broker |
| Debezium Connect | 8083 | CDC platform |

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

### Running Database Migrations

Before running tests, ensure Flyway migrations have been applied:

```bash
cd services/orchestrator-service
mvn flyway:migrate -Dflyway.url=jdbc:postgresql://localhost:5433/orchestrator_db \
                   -Dflyway.user=postgres \
                   -Dflyway.password=postgres
```

## Running Tests

### Unit Tests Only (Fast, No External Dependencies)

```bash
cd services/orchestrator-service
mvn test
```

**Result:** 87 unit tests run, 24 CDC integration tests skipped

### Integration Tests Only (Requires Containers)

```bash
# Option 1: Using Maven profile (recommended)
mvn test -Pintegration -Dspring.profiles.active=cdc-test

# Option 2: Using system property directly
mvn test -Dintegration.tests.enabled=true -Dspring.profiles.active=cdc-test

# Option 3: Run specific test class
mvn test -Pintegration -Dtest=OrchestratorCdcIntegrationTest -Dspring.profiles.active=cdc-test
mvn test -Pintegration -Dtest=SagaDatabaseIntegrationTest -Dspring.profiles.active=cdc-test

# Option 4: Run specific test method
mvn test -Pintegration \
        -Dtest="OrchestratorCdcIntegrationTest\$CdcFlowTests#shouldPublishSagaStartedEventToKafkaViaCdc" \
        -Dspring.profiles.active=cdc-test
```

**Result:** 121 tests total (87 unit + 24 CDC integration)

### Stopping Containers

```bash
cd /home/wpanther/projects/etax/invoice-microservices
./scripts/test-containers-stop.sh
```

## Test Cases

### OrchestratorCdcIntegrationTest

**Location:** `src/test/java/com/wpanther/orchestrator/integration/OrchestratorCdcIntegrationTest.java`

**Purpose:** Verifies the complete CDC flow from saga creation through database writes, outbox pattern, and finally Kafka message publication via Debezium.

**Test Structure:**
```
OrchestratorCdcIntegrationTest
├── DatabaseWriteTests (4 tests)
├── OutboxPatternTests (4 tests)
├── CdcFlowTests (4 tests)
└── DocumentTypeTests (2 tests)
```

#### DatabaseWriteTests (4 tests)

These tests verify that saga state is correctly persisted to the database.

| Test | Description | Verification |
|------|-------------|--------------|
| `shouldSaveSagaInstanceToDatabase` | Starting a saga persists state to `saga_instances` table | • status = IN_PROGRESS<br>• document_type = INVOICE<br>• current_step = PROCESS_INVOICE<br>• document_id matches |
| `shouldCreateCommandRecordInDatabase` | Starting a saga creates command records | • saga_commands record created<br>• target_step = PROCESS_TAX_INVOICE<br>• status = SENT |
| `shouldCreateOutboxEventsInSameTransaction` | Outbox events are created atomically with saga state | • At least 1 outbox event created<br>• SagaStartedEvent exists |
| `shouldUpdateSagaStatusOnCompletion` | Completing all steps updates saga status | • status = COMPLETED<br>• completed_at is not null |

#### OutboxPatternTests (4 tests)

These tests verify the outbox pattern implementation for CDC.

| Test | Description | Verification |
|------|-------------|--------------|
| `shouldWriteSagaStartedEventWithCorrectTopic` | SagaStartedEvent has correct routing | • topic = saga.lifecycle.started<br>• aggregate_type = SagaInstance<br>• status = PENDING |
| `shouldSetSagaIdAsPartitionKey` | All lifecycle events use sagaId as partition key | • partition_key = sagaId<br>• Ensures ordering per saga |
| `shouldWriteStepCompletedEventOnReply` | Handling reply creates step completion event | • event_type = SagaStepCompletedEvent<br>• topic = saga.lifecycle.step-completed |
| `shouldWriteCompletedEventOnSagaCompletion` | Saga completion creates completion event | • event_type = SagaCompletedEvent<br>• topic = saga.lifecycle.completed<br>• payload contains durationMs |

#### CdcFlowTests (4 tests)

These tests verify the end-to-end CDC pipeline to Kafka.

| Test | Description | Verification |
|------|-------------|--------------|
| `shouldPublishSagaStartedEventToKafkaViaCdc` | Debezium publishes SagaStartedEvent to Kafka | • Message on saga.lifecycle.started<br>• payload contains sagaId, documentType, documentId<br>• eventType = SagaStartedEvent |
| `shouldPublishSagaCompletedEventToKafkaViaCdc` | Debezium publishes SagaCompletedEvent to Kafka | • Message on saga.lifecycle.completed<br>• payload contains durationMs |
| `shouldPreserveSagaIdAsKafkaKeyThroughCdc` | Partition key is preserved through CDC | • Kafka message key = sagaId<br>• Payload sagaId matches key |
| `shouldPublishMultipleLifecycleEventsInOrder` | Multiple events are published in correct order | • saga.lifecycle.started message exists<br>• saga.lifecycle.completed message exists |

#### DocumentTypeTests (2 tests)

These tests verify handling of different document types (Invoice vs TaxInvoice).

| Test | Description | Verification |
|------|-------------|--------------|
| `shouldHandleInvoiceDocumentType` | Invoice documents use correct workflow | • current_step = PROCESS_INVOICE<br>• documentType = INVOICE<br>• Outbox payload has correct type |
| `shouldHandleTaxInvoiceDocumentType` | TaxInvoice documents use correct workflow | • current_step = PROCESS_TAX_INVOICE<br>• documentType = TAX_INVOICE<br>• Outbox payload has correct type |

### SagaDatabaseIntegrationTest

**Location:** `src/test/java/com/wpanther/orchestrator/integration/SagaDatabaseIntegrationTest.java`

**Purpose:** Verifies database schema structure without requiring Kafka/Debezium. Ensures tables, columns, indexes, and constraints are correctly created by Flyway migrations.

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
| `shouldVerifyVarcharTypeForPrimaryKeys` | Primary keys are VARCHAR | saga_instances.id, saga_commands.id are character varying |

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
| `shouldHaveStatusIndex` | Status index exists for Debezium polling | outbox_events |
| `shouldHaveUpdatedAtIndex` | Updated_at index exists | saga_instances |
| `shouldHaveStatusIndexOnSagaInstances` | Status index exists | saga_instances |
| `shouldHaveCompositeDocumentIndex` | Document composite index exists | saga_instances |
| `shouldHaveSagaIdIndexOnCommands` | saga_id index exists | saga_commands |

## Test Helper Methods

### OrchestratorCdcIntegrationTest Helper Methods

| Method | Purpose |
|--------|---------|
| `createTestMetadata(String documentId)` | Creates test DocumentMetadata with sample XML, file path, and metadata |
| `startTestSaga(DocumentType, String documentId)` | Starts a saga with mocked command publisher (bypasses actual Kafka sending) |
| `completeAllInvoiceSteps(String sagaId)` | Simulates completing all 6 invoice workflow steps |
| `completeAllTaxInvoiceSteps(String sagaId)` | Simulates completing all 6 tax invoice workflow steps |

### AbstractCdcIntegrationTest Helper Methods

| Method | Purpose |
|--------|---------|
| `await()` | Returns Awaitility for polling conditions |
| `hasMessageOnTopic(String topic, String key)` | Checks if message exists on Kafka topic |
| `getMessagesFromTopic(String topic, String key)` | Retrieves messages from Kafka topic |
| `parseJson(String json)` | Parses JSON string into JsonNode |
| `createSuccessReply(String sagaId, String step)` | Creates a success SagaReply message |
| `sendInvoiceReply(String sagaId, String step)` | Sends invoice reply to Kafka |

## Saga Workflows

### Invoice Workflow (6 steps)

```
PROCESS_INVOICE → SIGN_XML → GENERATE_INVOICE_PDF → SIGN_PDF → STORE_DOCUMENT → SEND_EBMS
```

### Tax Invoice Workflow (6 steps)

```
PROCESS_TAX_INVOICE → SIGN_XML → GENERATE_TAX_INVOICE_PDF → SIGN_PDF → STORE_DOCUMENT → SEND_EBMS
```

## Important Implementation Notes

### Idempotency

- `SagaInstance.complete()` is idempotent - calling multiple times has no additional effect
- `startSaga()` reuses existing non-terminal sagas for the same documentId

### Step Codes

Tests expect enum names (e.g., `PROCESS_INVOICE`) not codes (e.g., `process-invoice`) because:
- `@Enumerated(EnumType.STRING)` stores enum names in database
- Domain model uses enum names for state representation

### Transactional Outbox

- Outbox events are written in the same transaction as domain state changes
- This guarantees atomicity - no events lost if transaction rolls back
- Debezium reads the outbox table and publishes to Kafka asynchronously

### Partition Keys

- All lifecycle events use `sagaId` as partition key
- This ensures all events for a saga go to the same Kafka partition
- Maintains ordering guarantees for saga lifecycle

### Test Containers

Integration tests use port **9093** for Kafka (external access), while production uses **9092**.
Integration tests use port **5433** for PostgreSQL (test database).

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
```

Expected output: `"state": "RUNNING"`

### Tests Are Skipped

**Symptom:** Integration tests show as skipped

**Solution:** Ensure you're using the integration profile or system property:
```bash
mvn test -Pintegration -Dspring.profiles.active=cdc-test
```

## References

- **AbstractCdcIntegrationTest:** Base class providing test infrastructure, Kafka consumer utilities, and common test helpers
- **application-cdc-test.yml:** Test configuration with PostgreSQL (5433), Kafka (9093) connection settings
- **CdcTestConfiguration:** Test configuration excluding KafkaAutoConfiguration, enabling manual control of messaging components
- **TestKafkaConsumerConfig:** Test Kafka consumer configuration for port 9093
