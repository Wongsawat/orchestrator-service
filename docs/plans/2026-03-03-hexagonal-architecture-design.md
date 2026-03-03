# Hexagonal Architecture Migration Design

**Date:** 2026-03-03
**Service:** orchestrator-service (port 8093)
**Type:** Pure refactor — package rename + relocation, no logic changes
**Strategy:** Big-bang single commit

---

## Context

The orchestrator-service currently uses a DDD layered structure
(`domain/` → `application/` → `infrastructure/`). This is semantically correct but
does not make the **port/adapter boundary** explicit. The migration introduces the
hexagonal (ports & adapters) naming convention so that:

- Input and output contracts are visible as `port/in/` and `port/out/`
- Adapters are clearly separated by direction (`adapter/in/` vs `adapter/out/`)
- `infrastructure/` is fully dissolved into `adapter/` + `config/`
- The domain remains framework-free

No classes are added or deleted. No logic changes. All 88%+ test coverage is
maintained throughout.

---

## Target Package Structure

```
com.wpanther.orchestrator/
│
├── domain/                             Pure domain model — no Spring, no infra
│   ├── model/
│   │   ├── SagaInstance.java           Aggregate root
│   │   ├── DocumentMetadata.java       Value object
│   │   ├── SagaCommandRecord.java
│   │   └── enums/
│   │       └── DocumentType.java
│   └── event/                          Domain events only
│       ├── SagaStartedEvent.java
│       ├── SagaCompletedEvent.java
│       ├── SagaFailedEvent.java
│       └── SagaStepCompletedEvent.java
│
├── port/
│   ├── in/                             Input ports (use case contracts)
│   │   └── SagaOrchestrationService.java   MOVED from domain/service/
│   └── out/                            Output ports (driven contracts)
│       ├── SagaInstanceRepository.java     MOVED from domain/repository/
│       └── SagaCommandRecordRepository.java MOVED from domain/repository/
│
├── application/
│   ├── usecase/                        RENAMED from application/service/
│   │   └── SagaApplicationService.java implements port/in/SagaOrchestrationService
│   └── dto/                            Unchanged
│       ├── StartSagaRequest.java
│       └── SagaResponse.java
│
├── adapter/
│   ├── in/
│   │   ├── web/                        MOVED from application/controller/
│   │   │   ├── OrchestratorController.java
│   │   │   └── AuthController.java
│   │   ├── messaging/                  MOVED from infrastructure/messaging/consumer/
│   │   │   ├── SagaReplyConsumer.java
│   │   │   ├── StartSagaCommandConsumer.java
│   │   │   ├── StartSagaCommand.java   MOVED from domain/event/ (Kafka DTO, not domain event)
│   │   │   └── ConcreteSagaReply.java  MOVED from infrastructure/messaging/
│   │   └── security/                   MOVED from infrastructure/security/
│   │       ├── JwtAuthenticationFilter.java
│   │       ├── JwtUserDetailsService.java
│   │       └── JwtTokenProvider.java   MOVED from infrastructure/security/
│   └── out/
│       ├── persistence/                MOVED from infrastructure/persistence/
│       │   ├── SagaInstanceEntity.java
│       │   ├── SagaCommandEntity.java
│       │   ├── SagaInstanceMapper.java
│       │   ├── JpaSagaInstanceRepository.java
│       │   ├── JpaSagaCommandRepository.java
│       │   ├── SpringDataSagaInstanceRepository.java
│       │   ├── SpringDataSagaCommandRepository.java
│       │   └── outbox/
│       │       ├── OutboxEventEntity.java
│       │       ├── JpaOrchestratorOutboxRepository.java
│       │       └── SpringDataOrchestratorOutboxRepository.java
│       └── messaging/                  MOVED from infrastructure/messaging/producer/
│           ├── SagaCommandPublisher.java
│           ├── SagaEventPublisher.java
│           └── SagaCommandProducer.java  DEPRECATED — retained for compatibility
│
├── config/                             MOVED from infrastructure/config/
│   ├── KafkaConfig.java
│   ├── OrchestratorConfig.java
│   ├── OrchestratorOutboxConfig.java
│   └── SecurityConfig.java
│
└── OrchestratorServiceApplication.java Unchanged
```

---

## Dependency Rules

The hexagonal contract for allowed imports:

| Package | May import from | Must NOT import from |
|---------|----------------|----------------------|
| `domain/` | nothing (stdlib, Lombok, saga-commons) | port/, application/, adapter/, config/ |
| `port/` | domain/ | application/, adapter/, config/ |
| `application/usecase/` | domain/, port/ | adapter/, config/ |
| `adapter/in/` | port/in/, application/dto/, domain/ | adapter/out/ directly |
| `adapter/out/` | port/out/, domain/ | adapter/in/, application/ |
| `config/` | everything (Spring wiring — allowed) | — |

---

## Import Mapping (Old → New)

| Old import | New import |
|---|---|
| `domain.repository.SagaInstanceRepository` | `port.out.SagaInstanceRepository` |
| `domain.repository.SagaCommandRecordRepository` | `port.out.SagaCommandRecordRepository` |
| `domain.service.SagaOrchestrationService` | `port.in.SagaOrchestrationService` |
| `domain.event.StartSagaCommand` | `adapter.in.messaging.StartSagaCommand` |
| `application.controller.*` | `adapter.in.web.*` |
| `application.service.*` | `application.usecase.*` |
| `infrastructure.persistence.*` | `adapter.out.persistence.*` |
| `infrastructure.messaging.consumer.*` | `adapter.in.messaging.*` |
| `infrastructure.messaging.producer.*` | `adapter.out.messaging.*` |
| `infrastructure.messaging.ConcreteSagaReply` | `adapter.in.messaging.ConcreteSagaReply` |
| `infrastructure.security.*` | `adapter.in.security.*` |
| `infrastructure.config.*` | `config.*` |

---

## Migration Steps (Big-Bang)

1. Create target directory tree (empty packages)
2. Move `domain/model/` contents — unchanged
3. Move `domain/event/` domain events — unchanged
4. Move `StartSagaCommand.java` from `domain/event/` → `adapter/in/messaging/`
5. Create `port/in/` — move `SagaOrchestrationService.java`, update package declaration
6. Create `port/out/` — move repository interfaces, update package declarations
7. Rename `application/service/` → `application/usecase/` — update package + `implements` import
8. Move `application/controller/` → `adapter/in/web/` — update package + imports
9. Move `infrastructure/messaging/consumer/` + `ConcreteSagaReply.java` → `adapter/in/messaging/` — update package + imports
10. Move `infrastructure/messaging/producer/` → `adapter/out/messaging/` — update package + imports
11. Move `infrastructure/persistence/` → `adapter/out/persistence/` (including `outbox/`) — update package + imports
12. Move `infrastructure/security/` → `adapter/in/security/` — update package + imports
13. Move `infrastructure/config/` → `config/` — update package + imports
14. Global import sweep: replace all old `com.wpanther.orchestrator.*` imports with new paths
15. Update JaCoCo exclusion patterns in `pom.xml` (see section below)
16. Run `mvn test` — all unit tests must be green
17. Relocate test files to mirror new structure (same mechanical steps)
18. Run `mvn test` — green again
19. Single commit: `"Migrate to hexagonal architecture (ports & adapters)"`

---

## JaCoCo Exclusion Pattern Updates

Current exclusions reference old paths. These must be updated in `pom.xml`:

| Old exclusion pattern | New exclusion pattern |
|---|---|
| `**/infrastructure/config/**` | `**/config/**` |
| `**/infrastructure/persistence/SagaInstanceEntity*` | `**/adapter/out/persistence/SagaInstanceEntity*` |
| `**/infrastructure/persistence/SagaCommandEntity*` | `**/adapter/out/persistence/SagaCommandEntity*` |
| `**/infrastructure/persistence/outbox/OutboxEventEntity*` | `**/adapter/out/persistence/outbox/OutboxEventEntity*` |
| `**/infrastructure/persistence/SpringData*` | `**/adapter/out/persistence/SpringData*` |
| `**/infrastructure/persistence/SagaInstanceMapper*` | `**/adapter/out/persistence/SagaInstanceMapper*` |
| `**/infrastructure/messaging/producer/SagaCommandProducer*` | `**/adapter/out/messaging/SagaCommandProducer*` |
| `**/domain/repository/**` | `**/port/out/**` |
| `**/domain/service/**` | `**/port/in/**` |

---

## Test Package Structure

```
src/test/java/com/wpanther/orchestrator/
│
├── domain/
│   ├── model/
│   │   ├── SagaInstanceTest.java
│   │   └── DocumentMetadataTest.java
│   └── event/
│       └── SagaDomainEventsTest.java
│
├── application/
│   ├── usecase/
│   │   └── SagaApplicationServiceTest.java
│   └── dto/
│       ├── StartSagaRequestTest.java
│       └── SagaResponseTest.java
│
├── adapter/
│   ├── in/
│   │   ├── web/
│   │   │   ├── OrchestratorControllerTest.java
│   │   │   └── AuthControllerTest.java
│   │   ├── messaging/
│   │   │   ├── SagaReplyConsumerTest.java
│   │   │   ├── StartSagaCommandConsumerTest.java
│   │   │   └── ConcreteSagaReplyTest.java
│   │   └── security/
│   │       ├── JwtTokenProviderTest.java
│   │       ├── JwtAuthenticationFilterTest.java
│   │       ├── JwtUserDetailsServiceTest.java
│   │       └── SecurityConfigTest.java
│   └── out/
│       └── messaging/
│           ├── SagaCommandPublisherTest.java
│           └── SagaEventPublisherTest.java
│
└── integration/                    Unchanged
    ├── config/
    ├── AbstractCdcIntegrationTest.java
    ├── AbstractKafkaConsumerTest.java
    ├── OrchestratorCdcIntegrationTest.java
    ├── SagaDatabaseIntegrationTest.java
    └── KafkaConsumerIntegrationTest.java
```

---

## Key Decisions

| Decision | Rationale |
|---|---|
| `SagaOrchestrationService` name kept (not renamed to `*UseCase`) | Minimize blast radius; relocation to `port/in/` already communicates role |
| `StartSagaCommand` moved to `adapter/in/messaging/` | It is a Kafka message DTO, not a domain event; domain/ should have no Kafka knowledge |
| `JwtTokenProvider` in `adapter/in/security/` (not `config/`) | Contains token validation logic = adapter behavior; `SecurityConfig` (pure `@Bean`) stays in `config/` |
| `infrastructure/` fully dissolved | Replaced by `adapter/` + `config/`; no hybrid structure |
| `domain/repository/` dissolved into `port/out/` | Repository interfaces are output ports by definition |
| Integration tests untouched | Already isolated in `integration/`; separate from unit test migration |
| No ArchUnit added | Out of scope for this migration; can be added as a follow-up |
