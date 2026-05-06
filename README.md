# Orchestrator Service

Saga orchestration service for the Thai e-Tax Invoice Processing Pipeline. Coordinates all processing steps across microservices using the Saga Orchestration Pattern with transactional outbox (Debezium CDC).

## Overview

| Property | Value |
|----------|-------|
| **Port** | 8100 |
| **Database** | PostgreSQL `orchestrator_db` |
| **Java** | 21 |
| **Spring Boot** | 3.2.5 |

The orchestrator service coordinates document processing through multiple microservices:

```
DOCUMENT_INTAKE → PROCESSING → SIGNING → STORAGE → PDF_GENERATION → PDF_SIGNING → EBMS_SENDING
```

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.6+
- PostgreSQL 16+ with database `orchestrator_db`
- Kafka on `localhost:9092`
- saga-commons library installed

### Build and Run

```bash
# Install dependencies (first time only)
cd ../../saga-commons && mvn clean install

# Build the service
mvn clean package

# Run the service
mvn spring-boot:run

# Run database migrations
mvn flyway:migrate
```

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `KAFKA_BROKERS` | `localhost:9092` | Kafka bootstrap servers |
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `orchestrator_db` | Database name |
| `DB_USERNAME` | `postgres` | PostgreSQL username |
| `DB_PASSWORD` | `postgres` | PostgreSQL password |
| `ORCHESTRATOR_API_KEYS` | *(required)* | Comma-separated API keys for admin access |

## Architecture

### Hexagonal Architecture

The service follows hexagonal (ports and adapters) architecture:

```
com.wpanther.orchestrator/
├── domain/
│   ├── model/       # SagaInstance (aggregate root), DocumentMetadata, SagaCommandRecord
│   │   └── enums/   # DocumentType (INVOICE, TAX_INVOICE, ABBREVIATED_TAX_INVOICE, RECEIPT, CANCELLATION_NOTE, DEBIT_NOTE, CREDIT_NOTE)
│   ├── repository/  # Repository interfaces (ports)
│   ├── service/     # Domain services: SagaStepFlowStrategy
│   └── event/       # Domain events: SagaStartedEvent, SagaCompletedEvent, SagaFailedEvent, SagaStepCompletedEvent
├── application/
│   ├── dto/         # Request/Response DTOs (records)
│   └── usecase/     # Use cases: StartSagaUseCase, HandleSagaReplyUseCase, QuerySagaUseCase, SagaManagementUseCase, HandleCompensationUseCase
└── infrastructure/
    ├── adapter/
    │   ├── in/      # Driving adapters
    │   │   ├── rest/      # OrchestratorController (REST API)
    │   │   ├── messaging/ # StartSagaCommandConsumer, SagaReplyConsumer
    │   │   └── security/  # ApiKeyAuthenticationFilter
    │   └── out/     # Driven adapters
    │       ├── messaging/  # SagaCommandPublisher, SagaEventPublisher
    │       └── persistence/ # JPA entities, repositories, MapStruct mappers, outbox
    ├── config/      # Spring configuration, Kafka, security, saga properties
    ├── metrics/     # SagaMetrics (Micrometer)
    └── scheduling/  # SagaTimeoutChecker
```

### Saga Orchestration Pattern

The orchestrator uses the Saga Orchestration Pattern to coordinate distributed transactions:

1. **Command Phase**: Orchestrator sends commands to services via `saga.command.*` topics
2. **Reply Phase**: Services respond via `saga.reply.*` topics
3. **Compensation**: If any step fails, compensating transactions run in reverse order

### Saga State Machine

```
STARTED → IN_PROGRESS → COMPLETED
               ↓
          COMPENSATING → FAILED
```

### Document Type Flows

All document types follow a simplified 5-step flow:

**Invoice:**
```
PROCESS_INVOICE → SIGN_XML → GENERATE_INVOICE_PDF → SIGN_PDF → SEND_EBMS
```

**Tax Invoice:**
```
PROCESS_TAX_INVOICE → SIGN_XML → GENERATE_TAX_INVOICE_PDF → SIGN_PDF → SEND_EBMS
```

**Abbreviated Tax Invoice:**
```
PROCESS_ABBREVIATED_TAX_INVOICE → SIGN_XML → GENERATE_ABBREVIATED_TAX_INVOICE_PDF → SIGN_PDF → SEND_EBMS
```

**Receipt:**
```
PROCESS_RECEIPT → SIGN_XML → GENERATE_RECEIPT_PDF → SIGN_PDF → SEND_EBMS
```

**Cancellation Note:**
```
PROCESS_CANCELLATION_NOTE → SIGN_XML → GENERATE_CANCELLATION_NOTE_PDF → SIGN_PDF → SEND_EBMS
```

**Debit Note / Credit Note:**
```
PROCESS_DEBIT_CREDIT_NOTE → SIGN_XML → GENERATE_DEBIT_CREDIT_NOTE_PDF → SIGN_PDF → SEND_EBMS
```

The step ordering is defined declaratively in `DocumentType.FLOW` (a `Map<DocumentType, List<SagaStep>>` in the enum), which serves as the single source of truth for all routing logic.

## REST API

All endpoints require API key authentication via `X-API-Key` header.

### Start Saga

```bash
curl -X POST http://localhost:8100/api/saga/start \
  -H "X-API-Key: your-api-key" \
  -H "Content-Type: application/json" \
  -d '{
    "documentType": "TAX_INVOICE",
    "documentId": "doc-123",
    "xmlContent": "...",
    "metadata": {"key": "value"}
  }'
```

### Query Saga

```bash
# Get saga by ID
curl http://localhost:8100/api/saga/{sagaId} \
  -H "X-API-Key: your-api-key"

# Get active sagas
curl http://localhost:8100/api/saga/active \
  -H "X-API-Key: your-api-key"

# Query by document
curl "http://localhost:8100/api/saga/document?documentType=TAX_INVOICE&documentId=doc-123" \
  -H "X-API-Key: your-api-key"
```

### Management Operations

```bash
# Retry failed saga
curl -X POST http://localhost:8100/api/saga/{sagaId}/retry \
  -H "X-API-Key: your-api-key"

# Manually advance saga (for testing)
curl -X POST http://localhost:8100/api/saga/{sagaId}/advance \
  -H "X-API-Key: your-api-key"
```

## Configuration

### Saga Settings

```yaml
app:
  saga:
    # Retry configuration
    max-retries: 3
    retry-delay-seconds: 5
    compensation-timeout-seconds: 300

    # Timeout protection
    timeout-minutes: 30
    timeout-check-enabled: true
    timeout-check-interval-seconds: 60
    timeout-check-initial-delay-seconds: 30
```

### Security

```yaml
app:
  admin:
    # API keys for admin access (set via environment variable)
    api-keys: ${ORCHESTRATOR_API_KEYS}
  cors:
    allowed-origins: http://localhost:3000,http://localhost:8080,http://localhost:4200
```

## Database Schema

Four tables managed by Flyway:

| Table | Purpose |
|-------|---------|
| `saga_instances` | Aggregate root: status, current_step, retry counts |
| `saga_commands` | Command history for audit and compensation |
| `saga_data` | Document metadata: xml_content (TEXT), metadata (JSONB) |
| `outbox_events` | Transactional outbox for CDC |

### Migrations

```bash
# View migration status
mvn flyway:info

# Run migrations
mvn flyway:migrate
```

## Testing

### Unit Tests

```bash
# Run all unit tests (always use 'clean' to avoid Lombok stale compilation)
mvn clean test

# Run specific test class
mvn test -Dtest=SagaInstanceTest

# Run specific test method
mvn test -Dtest=SagaInstanceTest#testCreateSaga

# Run with coverage verification (80% line coverage requirement)
mvn verify
```

### Integration Tests

Integration tests require running containers:

```bash
# Start test containers
cd /home/wpanther/projects/etax/invoice-microservices
./scripts/test-containers-start.sh --with-debezium --auto-deploy-connectors

# Run integration tests (requires running test containers)
mvn clean test -Pintegration -Dspring.profiles.active=cdc-test

# Stop containers
./scripts/test-containers-stop.sh
```

## Monitoring

### Actuator Endpoints

| Endpoint | Access | Description |
|----------|--------|-------------|
| `/actuator/health` | Public | Health check |
| `/actuator/info` | Public | Build information |
| `/actuator/metrics` | Authenticated | Micrometer metrics |

### Health Endpoint

```bash
curl http://localhost:8100/actuator/health
```

## Development

### Adding New Saga Steps

1. Add step to `SagaStep` enum (in saga-commons)
2. Add command/reply/compensation topics to `application.yml` under `app.saga.command.*`, `app.saga.reply.*`, and `app.saga.compensation.*`
3. Add `DocumentType.FLOW` entry in `DocumentType` enum (key: document type → value: ordered `List<SagaStep>`)
4. Register routing in `SagaCommandPublisher.initRouters()` (command and compensation registries)
5. Add reply topic to `SagaReplyConsumer` @KafkaListener annotation

## License

Proprietary - All rights reserved
