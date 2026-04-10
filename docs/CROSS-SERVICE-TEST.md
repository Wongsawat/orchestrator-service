# Cross-Service Integration Test: saga.reply.xml-signing → saga.command.tax-invoice-pdf

This integration test verifies the cross-service flow:
1. `saga.command.xml-signing` is published to Kafka
2. xml-signing-service consumes it, signs the XML via eidasremotesigning, stores in MinIO, publishes `saga.reply.xml-signing` via Debezium CDC
3. Orchestrator's SagaReplyConsumer consumes the reply, advances saga, publishes `saga.command.tax-invoice-pdf` via Debezium CDC

## Prerequisites

The following must be running:
- PostgreSQL (5433), Kafka (9093), Debezium (8083), MinIO (9100), eidasremotesigning (9000)
- xml-signing-service (8086) with `full-integration-test` profile

## Quick Run (Single Command)

```bash
cd /home/wpanther/projects/etax/invoice-microservices
./scripts/run-cross-service-test.sh
```

Options: `--skip-build` (skip mvn), `--skip-containers` (skip container start).

The script handles: build → start containers → clean → restart Debezium connectors →
bootstrap eidasremotesigning credentials → start xml-signing-service → run tests → cleanup.

## Running the Tests Manually

### Step 1: Clean databases and Kafka topics

```bash
cd /home/wpanther/projects/etax/invoice-microservices
./scripts/test-containers-clean.sh
```

### Step 2: Build dependencies

```bash
# Build saga-commons
cd /home/wpanther/projects/etax/saga-commons
mvn clean install

# Build xml-signing-service
cd /home/wpanther/projects/etax/invoice-microservices/services/xml-signing-service
mvn clean package -DskipTests

# Build orchestrator-service
cd /home/wpanther/projects/etax/invoice-microservices/services/orchestrator-service
mvn clean package -DskipTests
```

### Step 3: Start containers

```bash
cd /home/wpanther/projects/etax/invoice-microservices
./scripts/test-containers-start.sh --with-eidas --with-debezium --auto-deploy-connectors
```

This starts: PostgreSQL (5433), Kafka (9093), Debezium (8083), MinIO (9100), eidasremotesigning (9000), and deploys Debezium connectors for both orchestrator and xml-signing outbox tables.

### Step 3.5: Baseline Flyway for xml-signing-service

After `test-containers-clean.sh` runs, the `flyway_schema_history` table is dropped for each database.
xml-signing-service needs Flyway to baseline the existing schema before starting:

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/xml-signing-service
mvn flyway:baseline flyway:migrate \
  -Dflyway.url="jdbc:postgresql://localhost:5433/xmlsigning_db" \
  -Dflyway.user=postgres -Dflyway.password=postgres
```

### Step 4: Start xml-signing-service

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/xml-signing-service
mvn spring-boot:run -Dspring-boot.run.profiles=full-integration-test &
XML_SIGNING_PID=$!
```

Wait for it to be ready (look for "Started Application" in the logs):
```bash
sleep 30
# Or check: curl -s http://localhost:8086/actuator/health
```

### Step 5: Run the integration tests

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/orchestrator-service
mvn test -Pintegration \
  -Dtest=XmlSigningToTaxInvoicePdfCdcIntegrationTest \
  -Dspring.profiles.active=cross-service-test
```

### Step 6: Cleanup

```bash
# Stop xml-signing-service
kill $XML_SIGNING_PID

# Stop containers
cd /home/wpanther/projects/etax/invoice-microservices
./scripts/test-containers-stop.sh
```

## Test Cases

| TC | Name | What it verifies |
|----|------|----------------|
| TC-01 | Happy Path | Full flow: command published, reply consumed, next command published via CDC, DB state correct |
| TC-02 | MinIO Verification | Signed XML is accessible in MinIO and contains XAdES signature elements |
| TC-03 | Error Handling | Invalid XML signing does not advance saga |
| TC-04 | Concurrency | Two concurrent sagas both produce correct commands |

## Troubleshooting

**Test hangs waiting for CDC message:**
- Check Debezium connectors are RUNNING: `curl http://localhost:8083/connectors/outbox-connector-orchestrator/status`
- Check xml-signing Debezium connector: `curl http://localhost:8083/connectors/outbox-connector-xmlsigning/status`
- Check xml-signing-service logs for signing errors
- Check Kafka topics: `kafka-topics.sh --bootstrap-server localhost:9093 --list`

**Saga stays at SIGN_XML step:**
- xml-signing-service may have failed to sign (check eidasremotesigning connectivity)
- Check MinIO connectivity from xml-signing-service
- Saga may have entered retry loop (check saga_instances.retry_count in orchestrator_db)

**Compilation errors about Lombok:**
```bash
cd /home/wpanther/projects/etax/saga-commons && mvn clean install
cd /home/wpanther/projects/etax/invoice-microservices/services/orchestrator-service
mvn clean compile test-compile
```
