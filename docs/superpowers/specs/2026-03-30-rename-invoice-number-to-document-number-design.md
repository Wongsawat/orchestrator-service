# Rename `invoiceNumber` to `documentNumber`

**Date:** 2026-03-30
**Status:** Approved
**Approach:** Orchestrator-only rename (Approach A)

## Motivation

The orchestrator-service supports multiple document types (INVOICE, TAX_INVOICE, ABBREVIATED_TAX_INVOICE). The field name `invoiceNumber` is invoice-specific and doesn't represent the generic concept correctly. Renaming to `documentNumber` aligns the domain language with the actual multi-document-type support.

## Scope

16 files in orchestrator-service only. No changes to other services.

## What Changes

### Domain Events (3 files)

- `SagaStartedEvent.java` — field, `@JsonProperty("documentNumber")`, `getDocumentNumber()`, constructors, Javadoc
- `SagaCompletedEvent.java` — field, `@JsonProperty("documentNumber")`, `getDocumentNumber()`, constructors
- `SagaFailedEvent.java` — field, `@JsonProperty("documentNumber")`, `getDocumentNumber()`, constructors

### Inbound Messaging (2 files)

- `StartSagaCommand.java` — field, `@JsonProperty("documentNumber")`, `getDocumentNumber()`, constructors, Javadoc
- `StartSagaCommandConsumer.java` — metadata key `"documentNumber"`, method call `getDocumentNumber()`

### Application Layer (1 file)

- `SagaApplicationService.java` — local variables `documentNumber`, rename `extractInvoiceNumber()` to `extractDocumentNumber()`, metadata key `"documentNumber"`

### Outbound Messaging (2 files)

- `SagaEventPublisher.java` — 3 method parameters renamed from `invoiceNumber` to `documentNumber`
- `SagaCommandPublisher.java` — rename `getInvoiceNumber()` to `getDocumentNumber()`, `@JsonProperty("documentNumber")` in 8 inner command classes (ProcessInvoiceCommand, ProcessTaxInvoiceCommand, StoreDocumentCommand, SendEbmsCommand, ProcessXmlSigningCommand, ProcessSignedXmlStorageCommand, ProcessInvoicePdfCommand, ProcessPdfSigningCommand, ProcessPdfStorageCommand)

### Tests (8 files)

- `StartSagaCommandConsumerTest.java` — comment, test data value
- `SagaCommandPublisherTest.java` — metadata map key
- `SagaApplicationServiceTest.java` — metadata map key, assertion
- `DocumentMetadataTest.java` — metadata map key, assertion
- `OrchestratorCdcIntegrationTest.java` — metadata map key
- `AbstractKafkaConsumerTest.java` — metadata map key
- `KafkaConsumerIntegrationTest.java` — JSON string
- `SecurityConfigTest.java` — JSON string

## What Does NOT Change

- `taxInvoiceNumber`, `taxInvoiceId` in `ProcessTaxInvoicePdfCommand` (taxinvoice-pdf-generation-service contract)
- `invoiceId` in `ProcessInvoicePdfCommand` (invoice-pdf-generation-service contract)
- `getInvoiceId()`, `getTaxInvoiceId()` helper methods in `SagaCommandPublisher`
- `ProcessTaxInvoicePdfCommand` class (no `invoiceNumber` field to rename)

## Technical Strategy

### Safe Files (13 files)

Files that do NOT contain `taxInvoiceNumber`: use `replace_all` on `invoiceNumber` → `documentNumber`. The Edit tool's replace_all handles all substrings correctly (e.g., `getInvoiceNumber` → `getDocumentNumber`, `extractInvoiceNumber` → `extractDocumentNumber`).

### SagaCommandPublisher.java (targeted edits)

This file contains `taxInvoiceNumber` which must NOT be renamed. Use 4 targeted replacements:
1. `"invoiceNumber"` → `"documentNumber"` (quoted JSON property names)
2. `getInvoiceNumber` → `getDocumentNumber` (method definition and all call sites)
3. ` invoiceNumber` → ` documentNumber` (space-prefixed: declarations, parameters, assignment RHS)
4. `.invoiceNumber` → `.documentNumber` (dot-prefixed: field references like `this.invoiceNumber`)

Each pattern is safe because `taxInvoiceNumber` does not match any of these four specific string patterns.

### Comment/Javadoc Updates

Separate Edit calls for comments that use natural language ("invoice number", "invoice/tax invoice number") rather than the Java identifier token:
- `SagaStartedEvent.java` — "Invoice/tax invoice number for reference." → "Document number for reference."
- `StartSagaCommand.java` — "The invoice/tax invoice number from the document." → "The document number."
- `SagaApplicationService.java` — "Extracts invoice number from metadata." → "Extracts document number from metadata."

## Verification

After all changes:
1. `mvn clean test` — compilation and all unit tests pass
2. Grep for any remaining `invoiceNumber` (excluding `taxInvoiceNumber`, `invoiceId`, `getInvoiceId`, `getTaxInvoiceId`)
