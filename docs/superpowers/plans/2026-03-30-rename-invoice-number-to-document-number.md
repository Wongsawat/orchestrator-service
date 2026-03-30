# Rename invoiceNumber to documentNumber — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename `invoiceNumber` to `documentNumber` across orchestrator-service to align with multi-document-type support.

**Architecture:** Pure refactor — no behavior change. Rename Java fields, methods, `@JsonProperty` annotations, and metadata map keys in 8 source files and 8 test files. SagaCommandPublisher.java requires targeted edits to avoid colliding with `taxInvoiceNumber`.

**Tech Stack:** Java 21, Spring Boot 3.2.5, Jackson `@JsonProperty`, MapStruct, JUnit 5, AssertJ, Mockito

**Design spec:** `docs/superpowers/specs/2026-03-30-rename-invoice-number-to-document-number-design.md`

---

### Task 1: Rename domain events (3 files)

**Files:**
- Modify: `src/main/java/com/wpanther/orchestrator/domain/event/SagaStartedEvent.java`
- Modify: `src/main/java/com/wpanther/orchestrator/domain/event/SagaCompletedEvent.java`
- Modify: `src/main/java/com/wpanther/orchestrator/domain/event/SagaFailedEvent.java`

These files contain only `invoiceNumber` (no `taxInvoiceNumber` collision). Safe for `replace_all`.

- [ ] **Step 1: Rename in SagaStartedEvent.java**

Use Edit with `replace_all: true`:
- `old_string`: `invoiceNumber`
- `new_string`: `documentNumber`

Then fix the Javadoc comment (replace_all won't catch natural language):
- `old_string`: `Invoice/tax invoice number for reference.`
- `new_string`: `Document number for reference.`

- [ ] **Step 2: Rename in SagaCompletedEvent.java**

Use Edit with `replace_all: true`:
- `old_string`: `invoiceNumber`
- `new_string`: `documentNumber`

- [ ] **Step 3: Rename in SagaFailedEvent.java**

Use Edit with `replace_all: true`:
- `old_string`: `invoiceNumber`
- `new_string`: `documentNumber`

---

### Task 2: Rename inbound messaging (2 files)

**Files:**
- Modify: `src/main/java/com/wpanther/orchestrator/infrastructure/adapter/in/messaging/StartSagaCommand.java`
- Modify: `src/main/java/com/wpanther/orchestrator/infrastructure/adapter/in/messaging/StartSagaCommandConsumer.java`

- [ ] **Step 1: Rename in StartSagaCommand.java**

Use Edit with `replace_all: true`:
- `old_string`: `invoiceNumber`
- `new_string`: `documentNumber`

Then fix Javadoc comments:
- `old_string`: `The invoice/tax invoice number from the document.`
- `new_string`: `The document number from the document.`
- `old_string`: `@param documentNumber The invoice/tax invoice number from the document`
- `new_string`: `@param documentNumber The document number from the document`

- [ ] **Step 2: Rename in StartSagaCommandConsumer.java**

Use Edit with `replace_all: true`:
- `old_string`: `invoiceNumber`
- `new_string`: `documentNumber`

This renames: `command.getInvoiceNumber()` → `command.getDocumentNumber()` and metadata key `"invoiceNumber"` → `"documentNumber"`.

---

### Task 3: Rename application service + outbound event publisher (2 files)

**Files:**
- Modify: `src/main/java/com/wpanther/orchestrator/application/usecase/SagaApplicationService.java`
- Modify: `src/main/java/com/wpanther/orchestrator/infrastructure/adapter/out/messaging/SagaEventPublisher.java`

- [ ] **Step 1: Rename in SagaApplicationService.java**

Use Edit with `replace_all: true`:
- `old_string`: `invoiceNumber`
- `new_string`: `documentNumber`

This renames: local variables, `extractInvoiceNumber()` → `extractDocumentNumber()`, metadata key.

Then fix the Javadoc:
- `old_string`: `Extracts invoice number from metadata.`
- `new_string`: `Extracts document number from metadata.`

- [ ] **Step 2: Rename in SagaEventPublisher.java**

Use Edit with `replace_all: true`:
- `old_string`: `invoiceNumber`
- `new_string`: `documentNumber`

This renames the 3 method parameters in `publishSagaStarted`, `publishSagaCompleted`, `publishSagaFailed`.

---

### Task 4: Rename SagaCommandPublisher (1 file — targeted edits)

**Files:**
- Modify: `src/main/java/com/wpanther/orchestrator/infrastructure/adapter/out/messaging/SagaCommandPublisher.java`

This file contains `taxInvoiceNumber` which must NOT change. Use 4 targeted `replace_all` edits with patterns that cannot collide:

- [ ] **Step 1: Rename quoted JSON property names**

Use Edit with `replace_all: true`:
- `old_string`: `"invoiceNumber"`
- `new_string`: `"documentNumber"`

Safe: `"taxInvoiceNumber"` does not contain `"invoiceNumber"` as a substring (it's `"taxInvoiceNumber"`).

- [ ] **Step 2: Rename method name and call sites**

Use Edit with `replace_all: true`:
- `old_string`: `getInvoiceNumber`
- `new_string`: `getDocumentNumber`

Safe: `getTaxInvoiceId` does not contain `getInvoiceNumber`.

- [ ] **Step 3: Rename space-prefixed declarations and parameters**

Use Edit with `replace_all: true`:
- `old_string`: ` invoiceNumber`
- `new_string`: ` documentNumber`

Safe: ` taxInvoiceNumber` does not contain ` invoiceNumber` (different substring after the space).

This catches: `String invoiceNumber`, `, invoiceNumber`, `= invoiceNumber` (RHS of assignments).

- [ ] **Step 4: Rename dot-prefixed field references**

Use Edit with `replace_all: true`:
- `old_string`: `.invoiceNumber`
- `new_string`: `.documentNumber`

Safe: `.taxInvoiceNumber` does not contain `.invoiceNumber`.

This catches: `this.invoiceNumber` → `this.documentNumber`.

---

### Task 5: Rename test files (8 files)

All test files are safe for `replace_all` on `invoiceNumber` (none contain `taxInvoiceNumber`).

**Files:**
- Modify: `src/test/java/com/wpanther/orchestrator/infrastructure/adapter/in/messaging/StartSagaCommandConsumerTest.java`
- Modify: `src/test/java/com/wpanther/orchestrator/infrastructure/adapter/out/messaging/SagaCommandPublisherTest.java`
- Modify: `src/test/java/com/wpanther/orchestrator/application/usecase/SagaApplicationServiceTest.java`
- Modify: `src/test/java/com/wpanther/orchestrator/domain/model/DocumentMetadataTest.java`
- Modify: `src/test/java/com/wpanther/orchestrator/integration/OrchestratorCdcIntegrationTest.java`
- Modify: `src/test/java/com/wpanther/orchestrator/integration/AbstractKafkaConsumerTest.java`
- Modify: `src/test/java/com/wpanther/orchestrator/integration/KafkaConsumerIntegrationTest.java`
- Modify: `src/test/java/com/wpanther/orchestrator/infrastructure/config/security/SecurityConfigTest.java`

- [ ] **Step 1: Rename in all 8 test files**

For each file, use Edit with `replace_all: true`:
- `old_string`: `invoiceNumber`
- `new_string`: `documentNumber`

Batch all 8 edits in parallel.

---

### Task 6: Build verification and commit

- [ ] **Step 1: Run unit tests**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/orchestrator-service && mvn clean test
```

Expected: All tests PASS.

- [ ] **Step 2: Verify no remaining `invoiceNumber`**

```bash
grep -rn 'invoiceNumber' src/ --include='*.java' | grep -v 'taxInvoiceNumber' | grep -v 'getTaxInvoiceId' | grep -v 'getInvoiceId' | grep -v 'invoiceId'
```

Expected: No output (all `invoiceNumber` occurrences renamed, except the intentional exclusions).

- [ ] **Step 3: Commit**

```bash
git add src/
git commit -m "Rename invoiceNumber to documentNumber across orchestrator-service

Align field naming with multi-document-type support (INVOICE,
TAX_INVOICE, ABBREVIATED_TAX_INVOICE). Renames Java fields,
methods, @JsonProperty annotations, and metadata keys.

No behavior change. Downstream-specific fields (taxInvoiceNumber,
invoiceId) in saga command classes unchanged."
```
