# Hexagonal Architecture Migration Design (Phase 2)

**Date:** 2026-03-09
**Service:** orchestrator-service (port 8093)
**Type:** Pure refactor — package rename + relocation + use-case split, no logic changes
**Strategy:** Phase-by-phase incremental (one commit per logical group, tests green after each)
**Predecessor:** 2026-03-03-hexagonal-architecture-design.md (already executed)

---

## Context

The 2026-03-03 migration introduced explicit hexagonal naming (`port/`, `adapter/`, `config/` at root level). This second pass aligns the orchestrator-service with the **canonical layout** established by the other four services (invoice-pdf, taxinvoice-pdf, ebms-sending, notification):

- `domain/` ← `application/` ← `infrastructure/` (strict dependency rule)
- `domain/repository/` for domain-owned outbound ports
- `application/usecase/` for inbound port interfaces
- `infrastructure/adapter/in/` and `infrastructure/adapter/out/`
- `infrastructure/config/` with concern-based sub-packages
- `adapter/in/rest/` naming (not `web/`)

**Remaining gaps after 2026-03-03:**

| Current | Target | Change |
|---|---|---|
| `port/out/` at root | `domain/repository/` | Move |
| `port/in/SagaOrchestrationService` | dissolved → 4 use-case interfaces | Split |
| `adapter/` at root | `infrastructure/adapter/` | Move |
| `config/` at root | `infrastructure/config/` | Move + sub-packages |
| `adapter/in/web/` | `infrastructure/adapter/in/rest/` | Move + rename |
| `adapter/out/messaging/SagaCommandProducer` | deleted | Remove deprecated class |

---

## Target Package Structure

```
com.wpanther.orchestrator/
├── domain/
│   ├── model/                              # unchanged
│   │   ├── SagaInstance.java
│   │   ├── DocumentMetadata.java
│   │   ├── SagaCommandRecord.java
│   │   └── enums/DocumentType.java
│   ├── repository/                         # MOVED from port/out/
│   │   ├── SagaInstanceRepository.java
│   │   └── SagaCommandRecordRepository.java
│   └── event/                              # unchanged (true domain events)
│       ├── SagaStartedEvent.java
│       ├── SagaCompletedEvent.java
│       ├── SagaFailedEvent.java
│       └── SagaStepCompletedEvent.java
│
├── application/
│   ├── usecase/
│   │   ├── StartSagaUseCase.java           # NEW — split from port/in/SagaOrchestrationService
│   │   ├── HandleSagaReplyUseCase.java     # NEW
│   │   ├── HandleCompensationUseCase.java  # NEW
│   │   ├── QuerySagaUseCase.java           # NEW
│   │   └── SagaApplicationService.java     # implements all 4 (no move — already here)
│   └── dto/                                # unchanged
│       ├── StartSagaRequest.java
│       └── SagaResponse.java
│
└── infrastructure/
    ├── adapter/
    │   ├── in/
    │   │   ├── messaging/                  # MOVED from adapter/in/messaging/
    │   │   │   ├── SagaReplyConsumer.java
    │   │   │   ├── StartSagaCommandConsumer.java
    │   │   │   ├── StartSagaCommand.java
    │   │   │   └── ConcreteSagaReply.java
    │   │   ├── rest/                       # MOVED + RENAMED from adapter/in/web/
    │   │   │   ├── OrchestratorController.java
    │   │   │   └── AuthController.java
    │   │   └── security/                   # MOVED from adapter/in/security/
    │   │       ├── JwtAuthenticationFilter.java
    │   │       ├── JwtUserDetailsService.java
    │   │       └── JwtTokenProvider.java
    │   └── out/
    │       ├── messaging/                  # MOVED from adapter/out/messaging/
    │       │   ├── SagaCommandPublisher.java   # SagaCommandProducer DELETED
    │       │   └── SagaEventPublisher.java
    │       └── persistence/                # MOVED from adapter/out/persistence/
    │           ├── SagaInstanceEntity.java
    │           ├── SagaCommandEntity.java
    │           ├── SagaInstanceMapper.java
    │           ├── JpaSagaInstanceRepository.java
    │           ├── JpaSagaCommandRepository.java
    │           ├── SpringDataSagaInstanceRepository.java
    │           ├── SpringDataSagaCommandRepository.java
    │           └── outbox/
    │               ├── OutboxEventEntity.java
    │               ├── JpaOrchestratorOutboxRepository.java
    │               └── SpringDataOrchestratorOutboxRepository.java
    └── config/
        ├── kafka/
        │   └── KafkaConfig.java
        ├── security/
        │   └── SecurityConfig.java
        ├── outbox/
        │   └── OrchestratorOutboxConfig.java
        └── OrchestratorConfig.java         # general app config — no sub-package
```

---

## Component Design

### Use-Case Split

`port/in/SagaOrchestrationService` is a single broad interface. It is dissolved and replaced by four focused interfaces in `application/usecase/`. `SagaApplicationService` implements all four — no new classes, no logic changes.

```java
// application/usecase/StartSagaUseCase.java
public interface StartSagaUseCase {
    SagaResponse startSaga(StartSagaRequest request);
}

// application/usecase/HandleSagaReplyUseCase.java
public interface HandleSagaReplyUseCase {
    void handleReply(ConcreteSagaReply reply);
}

// application/usecase/HandleCompensationUseCase.java
public interface HandleCompensationUseCase {
    void handleCompensation(String sagaId, String failedStep, String reason);
}

// application/usecase/QuerySagaUseCase.java
public interface QuerySagaUseCase {
    SagaResponse getSaga(String sagaId);
}
```

**Adapter injection table:**

| Adapter | Injects |
|---|---|
| `OrchestratorController` | `StartSagaUseCase`, `QuerySagaUseCase` |
| `StartSagaCommandConsumer` | `StartSagaUseCase` |
| `SagaReplyConsumer` | `HandleSagaReplyUseCase`, `HandleCompensationUseCase` |
| `AuthController` | none (JWT only) |

### Repository Interfaces

Moved to `domain/repository/` — package rename only, no interface changes:
- `SagaInstanceRepository` — CRUD + status queries for `SagaInstance` aggregate root
- `SagaCommandRecordRepository` — command tracking per saga step

Implemented by `JpaSagaInstanceRepository` and `JpaSagaCommandRepository` in `infrastructure/adapter/out/persistence/`.

### Deprecated Class Deletion

`SagaCommandProducer` (direct Kafka, superseded by outbox-based `SagaCommandPublisher`) is deleted in Phase 3. Any remaining injection sites are updated to `SagaCommandPublisher` before deletion.

### Config Sub-Package Split

| Class | Sub-package | Rationale |
|---|---|---|
| `KafkaConfig` | `infrastructure/config/kafka/` | Kafka producer/consumer beans |
| `SecurityConfig` | `infrastructure/config/security/` | Spring Security filter chain |
| `OrchestratorOutboxConfig` | `infrastructure/config/outbox/` | Outbox CDC wiring |
| `OrchestratorConfig` | `infrastructure/config/` (flat) | General app config |

---

## Dependency Rules

| Package | May import from | Must NOT import from |
|---|---|---|
| `domain/` | stdlib, Lombok, saga-commons | application/, infrastructure/ |
| `domain/repository/` | `domain/model/` | application/, infrastructure/ |
| `application/usecase/` | `domain/`, `application/dto/` | infrastructure/ |
| `infrastructure/adapter/in/` | `application/usecase/`, `application/dto/`, `domain/` | `infrastructure/adapter/out/` directly |
| `infrastructure/adapter/out/` | `domain/repository/`, `domain/model/` | `infrastructure/adapter/in/`, `application/usecase/` |
| `infrastructure/config/` | everything (Spring wiring — allowed) | — |

---

## Data Flow

### Start Saga (REST)
```
POST /saga
  → infrastructure/adapter/in/rest/OrchestratorController
  → StartSagaUseCase
  → application/usecase/SagaApplicationService
      ├── domain/repository/SagaInstanceRepository (save)
      └── infrastructure/adapter/out/messaging/SagaCommandPublisher (outbox)
  → saga.command.<type> → downstream service
```

### Start Saga (Kafka)
```
saga.commands.orchestrator
  → infrastructure/adapter/in/messaging/StartSagaCommandConsumer
  → StartSagaUseCase → SagaApplicationService (same path)
```

### Saga Reply
```
saga.reply.*
  → infrastructure/adapter/in/messaging/SagaReplyConsumer
  → HandleSagaReplyUseCase (success) or HandleCompensationUseCase (failure)
  → SagaApplicationService
      ├── domain/repository/SagaInstanceRepository (update step)
      └── infrastructure/adapter/out/messaging/SagaCommandPublisher (next command or saga done)
```

---

## Import Mapping (Old → New)

| Old import | New import |
|---|---|
| `port.out.SagaInstanceRepository` | `domain.repository.SagaInstanceRepository` |
| `port.out.SagaCommandRecordRepository` | `domain.repository.SagaCommandRecordRepository` |
| `port.in.SagaOrchestrationService` | dissolved → 4 `application.usecase.*` interfaces |
| `adapter.in.web.*` | `infrastructure.adapter.in.rest.*` |
| `adapter.in.messaging.*` | `infrastructure.adapter.in.messaging.*` |
| `adapter.in.security.*` | `infrastructure.adapter.in.security.*` |
| `adapter.out.messaging.*` | `infrastructure.adapter.out.messaging.*` |
| `adapter.out.persistence.*` | `infrastructure.adapter.out.persistence.*` |
| `config.KafkaConfig` | `infrastructure.config.kafka.KafkaConfig` |
| `config.SecurityConfig` | `infrastructure.config.security.SecurityConfig` |
| `config.OrchestratorOutboxConfig` | `infrastructure.config.outbox.OrchestratorOutboxConfig` |
| `config.OrchestratorConfig` | `infrastructure.config.OrchestratorConfig` |

---

## Migration Phases

| Phase | Scope | Commit message |
|---|---|---|
| 1 | Dissolve `port/` — move repos to `domain/repository/`, split `SagaOrchestrationService` into 4 use-case interfaces, add 4 new use-case tests | `Dissolve port/ — move repository interfaces to domain/repository, split SagaOrchestrationService into use-case interfaces` |
| 2 | Move `config/` → `infrastructure/config/` with concern sub-packages | `Move config/ to infrastructure/config/ with concern-based sub-packages` |
| 3 | Move `adapter/` → `infrastructure/adapter/`, rename `web/`→`rest/`, delete `SagaCommandProducer` | `Move adapter/ to infrastructure/adapter/, rename web to rest, delete deprecated SagaCommandProducer` |
| 4 | Relocate test files to mirror new structure, update JaCoCo exclusions in `pom.xml` | `Relocate test classes to mirror new hexagonal package structure, update JaCoCo exclusions` |
| 5 | Final verification — `mvn verify`, confirm no old package references remain | (no commit — verification only) |

---

## Testing Strategy

### New Tests (Phase 1)

Four use-case interface tests verify `SagaApplicationService` correctly implements each contract:

| New test class | Verifies |
|---|---|
| `StartSagaUseCaseTest` | creates saga, publishes first command |
| `HandleSagaReplyUseCaseTest` | success advances step; failure triggers compensation |
| `HandleCompensationUseCaseTest` | compensation cascades in reverse step order |
| `QuerySagaUseCaseTest` | `getSaga()` returns correct status, throws on missing ID |

All use Mockito mocks — no Testcontainers needed.

### Test Relocations (Phase 4)

| Old test path | New test path |
|---|---|
| `adapter/in/web/OrchestratorControllerTest` | `infrastructure/adapter/in/rest/` |
| `adapter/in/web/AuthControllerTest` | `infrastructure/adapter/in/rest/` |
| `adapter/in/messaging/SagaReplyConsumerTest` | `infrastructure/adapter/in/messaging/` |
| `adapter/in/messaging/StartSagaCommandConsumerTest` | `infrastructure/adapter/in/messaging/` |
| `adapter/in/security/Jwt*Test` | `infrastructure/adapter/in/security/` |
| `adapter/out/messaging/SagaCommandPublisherTest` | `infrastructure/adapter/out/messaging/` |
| `adapter/out/messaging/SagaEventPublisherTest` | `infrastructure/adapter/out/messaging/` |
| `integration/` | **untouched** |

### JaCoCo Exclusion Updates

| Old pattern | New pattern |
|---|---|
| `**/port/out/**` | `**/domain/repository/**` |
| `**/port/in/**` | removed (covered by new use-case tests) |
| `**/adapter/out/persistence/SagaInstanceEntity*` | `**/infrastructure/adapter/out/persistence/SagaInstanceEntity*` |
| `**/adapter/out/persistence/SagaCommandEntity*` | `**/infrastructure/adapter/out/persistence/SagaCommandEntity*` |
| `**/adapter/out/persistence/outbox/**` | `**/infrastructure/adapter/out/persistence/outbox/**` |
| `**/adapter/out/persistence/SpringData*` | `**/infrastructure/adapter/out/persistence/SpringData*` |
| `**/adapter/out/messaging/SagaCommandProducer*` | removed (class deleted) |
| `**/config/**` | `**/infrastructure/config/**` |

### Coverage Target

≥ 80% line coverage (`mvn verify`) maintained throughout all phases.

---

## Key Decisions

| Decision | Rationale |
|---|---|
| Split `SagaOrchestrationService` into 4 interfaces | Each adapter injects only the contract it needs; dependency inversion is explicit |
| `port/in/` fully dissolved (not renamed) | In canonical layout, use-case interfaces live in `application/usecase/`, not a separate `port/` root |
| `port/out/` → `domain/repository/` | Repository interfaces are domain-owned output ports; living in `domain/` makes ownership explicit |
| `adapter/in/web/` renamed to `rest/` | Canonical naming: `rest/` is the protocol, `web/` is ambiguous |
| Delete `SagaCommandProducer` | Deprecated + superseded by outbox-based `SagaCommandPublisher`; no compatibility risk |
| `OrchestratorConfig` stays flat in `infrastructure/config/` | It is general app config with no single named concern; a sub-package of one adds noise |
| Integration tests untouched | Already isolated in `integration/`; separate from unit test migration |
