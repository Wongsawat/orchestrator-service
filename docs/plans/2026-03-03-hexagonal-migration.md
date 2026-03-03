# Hexagonal Architecture Migration Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Migrate orchestrator-service from DDD layered packages to explicit hexagonal (ports & adapters) structure with no logic changes.

**Architecture:** Pure package rename. `infrastructure/` is dissolved into `adapter/in/`, `adapter/out/`, and `config/`. Repository and service interfaces move from `domain/` to `port/out/` and `port/in/`. The domain stays framework-free. All existing tests pass throughout.

**Tech Stack:** Java 21, Spring Boot 3.2.5, Maven, JUnit 5, JaCoCo 0.8.12

**Design doc:** `docs/plans/2026-03-03-hexagonal-architecture-design.md`

---

## Pre-flight

Before starting, confirm all unit tests pass:

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/orchestrator-service
mvn test
```

Expected: BUILD SUCCESS. If not, stop and fix first.

---

## Task 1: Create `port/out/` — move repository interfaces

This is the first move. Output port interfaces live in `port/out/`, not `domain/repository/`. Moving them first unblocks all subsequent tasks.

**Files:**
- Move: `src/main/java/com/wpanther/orchestrator/domain/repository/SagaInstanceRepository.java`
  → `src/main/java/com/wpanther/orchestrator/port/out/SagaInstanceRepository.java`
- Move: `src/main/java/com/wpanther/orchestrator/domain/repository/SagaCommandRecordRepository.java`
  → `src/main/java/com/wpanther/orchestrator/port/out/SagaCommandRecordRepository.java`
- Update imports in: `application/service/SagaApplicationService.java`, `infrastructure/persistence/JpaSagaInstanceRepository.java`, `infrastructure/persistence/JpaSagaCommandRepository.java`

**Step 1: Create the target directory**

```bash
mkdir -p src/main/java/com/wpanther/orchestrator/port/out
```

**Step 2: Move `SagaInstanceRepository.java` — update its package declaration**

Open `src/main/java/com/wpanther/orchestrator/domain/repository/SagaInstanceRepository.java`.
Change line 1:

```java
// Before:
package com.wpanther.orchestrator.domain.repository;

// After:
package com.wpanther.orchestrator.port.out;
```

Move the file:
```bash
mv src/main/java/com/wpanther/orchestrator/domain/repository/SagaInstanceRepository.java \
   src/main/java/com/wpanther/orchestrator/port/out/SagaInstanceRepository.java
```

**Step 3: Move `SagaCommandRecordRepository.java` — update its package declaration**

Same pattern:
```java
// Before:
package com.wpanther.orchestrator.domain.repository;

// After:
package com.wpanther.orchestrator.port.out;
```

```bash
mv src/main/java/com/wpanther/orchestrator/domain/repository/SagaCommandRecordRepository.java \
   src/main/java/com/wpanther/orchestrator/port/out/SagaCommandRecordRepository.java
```

**Step 4: Delete the now-empty `domain/repository/` directory**

```bash
rmdir src/main/java/com/wpanther/orchestrator/domain/repository
```

**Step 5: Update imports in `SagaApplicationService.java`**

Find and replace in `src/main/java/com/wpanther/orchestrator/application/service/SagaApplicationService.java`:
```java
// Before:
import com.wpanther.orchestrator.domain.repository.SagaInstanceRepository;
import com.wpanther.orchestrator.domain.repository.SagaCommandRecordRepository;

// After:
import com.wpanther.orchestrator.port.out.SagaInstanceRepository;
import com.wpanther.orchestrator.port.out.SagaCommandRecordRepository;
```

**Step 6: Update imports in `JpaSagaInstanceRepository.java`**

Find and replace in `src/main/java/com/wpanther/orchestrator/infrastructure/persistence/JpaSagaInstanceRepository.java`:
```java
// Before:
import com.wpanther.orchestrator.domain.repository.SagaInstanceRepository;

// After:
import com.wpanther.orchestrator.port.out.SagaInstanceRepository;
```

**Step 7: Update imports in `JpaSagaCommandRepository.java`**

```java
// Before:
import com.wpanther.orchestrator.domain.repository.SagaCommandRecordRepository;

// After:
import com.wpanther.orchestrator.port.out.SagaCommandRecordRepository;
```

**Step 8: Compile to verify no broken imports**

```bash
mvn compile -q
```

Expected: BUILD SUCCESS. Fix any "cannot find symbol" errors before continuing.

**Step 9: Run tests**

```bash
mvn test -q
```

Expected: BUILD SUCCESS.

**Step 10: Commit**

```bash
git add src/main/java/com/wpanther/orchestrator/port/ \
        src/main/java/com/wpanther/orchestrator/application/service/SagaApplicationService.java \
        src/main/java/com/wpanther/orchestrator/infrastructure/persistence/JpaSagaInstanceRepository.java \
        src/main/java/com/wpanther/orchestrator/infrastructure/persistence/JpaSagaCommandRepository.java
git rm src/main/java/com/wpanther/orchestrator/domain/repository/SagaInstanceRepository.java 2>/dev/null || true
git rm src/main/java/com/wpanther/orchestrator/domain/repository/SagaCommandRecordRepository.java 2>/dev/null || true
git commit -m "Move repository interfaces from domain/repository to port/out"
```

---

## Task 2: Create `port/in/` — move the input port interface

**Files:**
- Move: `src/main/java/com/wpanther/orchestrator/domain/service/SagaOrchestrationService.java`
  → `src/main/java/com/wpanther/orchestrator/port/in/SagaOrchestrationService.java`
- Update imports in: `application/service/SagaApplicationService.java`, any controller or consumer that uses it

**Step 1: Create the target directory**

```bash
mkdir -p src/main/java/com/wpanther/orchestrator/port/in
```

**Step 2: Move `SagaOrchestrationService.java` — update package declaration**

```java
// Before:
package com.wpanther.orchestrator.domain.service;

// After:
package com.wpanther.orchestrator.port.in;
```

```bash
mv src/main/java/com/wpanther/orchestrator/domain/service/SagaOrchestrationService.java \
   src/main/java/com/wpanther/orchestrator/port/in/SagaOrchestrationService.java
rmdir src/main/java/com/wpanther/orchestrator/domain/service
```

**Step 3: Find all files that import `domain.service.SagaOrchestrationService`**

```bash
grep -rl "domain.service.SagaOrchestrationService" src/
```

Update each found file:
```java
// Before:
import com.wpanther.orchestrator.domain.service.SagaOrchestrationService;

// After:
import com.wpanther.orchestrator.port.in.SagaOrchestrationService;
```

**Step 4: Compile and test**

```bash
mvn test -q
```

Expected: BUILD SUCCESS.

**Step 5: Commit**

```bash
git add src/main/java/com/wpanther/orchestrator/port/in/
git rm -r src/main/java/com/wpanther/orchestrator/domain/service/ 2>/dev/null || true
git add -u
git commit -m "Move SagaOrchestrationService from domain/service to port/in"
```

---

## Task 3: Move `StartSagaCommand` out of `domain/event/` into `adapter/in/messaging/`

`StartSagaCommand` is a Kafka message DTO (it arrives over Kafka from document-intake-service). It is not a domain event. Moving it to `adapter/in/messaging/` aligns it with where it is used.

**Files:**
- Move: `src/main/java/com/wpanther/orchestrator/domain/event/StartSagaCommand.java`
  → `src/main/java/com/wpanther/orchestrator/adapter/in/messaging/StartSagaCommand.java`
- Update imports in: `infrastructure/messaging/consumer/StartSagaCommandConsumer.java` (and any other file that imports it — check with grep)

**Step 1: Create the target directory**

```bash
mkdir -p src/main/java/com/wpanther/orchestrator/adapter/in/messaging
```

**Step 2: Move and update package declaration**

```java
// Before:
package com.wpanther.orchestrator.domain.event;

// After:
package com.wpanther.orchestrator.adapter.in.messaging;
```

```bash
mv src/main/java/com/wpanther/orchestrator/domain/event/StartSagaCommand.java \
   src/main/java/com/wpanther/orchestrator/adapter/in/messaging/StartSagaCommand.java
```

**Step 3: Find all importers**

```bash
grep -rl "domain.event.StartSagaCommand" src/
```

Update each:
```java
// Before:
import com.wpanther.orchestrator.domain.event.StartSagaCommand;

// After:
import com.wpanther.orchestrator.adapter.in.messaging.StartSagaCommand;
```

**Step 4: Compile and test**

```bash
mvn test -q
```

Expected: BUILD SUCCESS.

**Step 5: Commit**

```bash
git add src/main/java/com/wpanther/orchestrator/adapter/in/messaging/StartSagaCommand.java
git rm src/main/java/com/wpanther/orchestrator/domain/event/StartSagaCommand.java
git add -u
git commit -m "Move StartSagaCommand from domain/event to adapter/in/messaging (Kafka DTO, not domain event)"
```

---

## Task 4: Rename `application/service/` → `application/usecase/`

**Files:**
- Move: `src/main/java/com/wpanther/orchestrator/application/service/SagaApplicationService.java`
  → `src/main/java/com/wpanther/orchestrator/application/usecase/SagaApplicationService.java`
- Update imports in any file that imports `application.service.SagaApplicationService`

**Step 1: Create target directory**

```bash
mkdir -p src/main/java/com/wpanther/orchestrator/application/usecase
```

**Step 2: Move and update package declaration**

```java
// Before:
package com.wpanther.orchestrator.application.service;

// After:
package com.wpanther.orchestrator.application.usecase;
```

```bash
mv src/main/java/com/wpanther/orchestrator/application/service/SagaApplicationService.java \
   src/main/java/com/wpanther/orchestrator/application/usecase/SagaApplicationService.java
rmdir src/main/java/com/wpanther/orchestrator/application/service
```

**Step 3: Find all importers**

```bash
grep -rl "application.service.SagaApplicationService" src/
```

Update each:
```java
// Before:
import com.wpanther.orchestrator.application.service.SagaApplicationService;

// After:
import com.wpanther.orchestrator.application.usecase.SagaApplicationService;
```

**Step 4: Compile and test**

```bash
mvn test -q
```

Expected: BUILD SUCCESS.

**Step 5: Commit**

```bash
git add src/main/java/com/wpanther/orchestrator/application/usecase/
git rm src/main/java/com/wpanther/orchestrator/application/service/SagaApplicationService.java 2>/dev/null || true
git add -u
git commit -m "Rename application/service to application/usecase"
```

---

## Task 5: Move `application/controller/` → `adapter/in/web/`

**Files:**
- Move: `application/controller/OrchestratorController.java` → `adapter/in/web/OrchestratorController.java`
- Move: `application/controller/AuthController.java` → `adapter/in/web/AuthController.java`
- Update imports in any file referencing `application.controller.*`

**Step 1: Create target directory**

```bash
mkdir -p src/main/java/com/wpanther/orchestrator/adapter/in/web
```

**Step 2: Move and update package declarations**

For both `OrchestratorController.java` and `AuthController.java`:
```java
// Before:
package com.wpanther.orchestrator.application.controller;

// After:
package com.wpanther.orchestrator.adapter.in.web;
```

```bash
mv src/main/java/com/wpanther/orchestrator/application/controller/OrchestratorController.java \
   src/main/java/com/wpanther/orchestrator/adapter/in/web/OrchestratorController.java

mv src/main/java/com/wpanther/orchestrator/application/controller/AuthController.java \
   src/main/java/com/wpanther/orchestrator/adapter/in/web/AuthController.java

rmdir src/main/java/com/wpanther/orchestrator/application/controller
```

**Step 3: Find all importers**

```bash
grep -rl "application.controller" src/
```

Update any found file:
```java
// Before:
import com.wpanther.orchestrator.application.controller.*;

// After:
import com.wpanther.orchestrator.adapter.in.web.*;
```

Note: Spring's `@ComponentScan` scans `com.wpanther.orchestrator` and all sub-packages by default, so the moved controllers are discovered automatically.

**Step 4: Compile and test**

```bash
mvn test -q
```

Expected: BUILD SUCCESS.

**Step 5: Commit**

```bash
git add src/main/java/com/wpanther/orchestrator/adapter/in/web/
git rm src/main/java/com/wpanther/orchestrator/application/controller/OrchestratorController.java
git rm src/main/java/com/wpanther/orchestrator/application/controller/AuthController.java
git add -u
git commit -m "Move REST controllers from application/controller to adapter/in/web"
```

---

## Task 6: Move messaging consumers → `adapter/in/messaging/`

**Files to move:**
- `infrastructure/messaging/consumer/SagaReplyConsumer.java` → `adapter/in/messaging/`
- `infrastructure/messaging/consumer/StartSagaCommandConsumer.java` → `adapter/in/messaging/`
- `infrastructure/messaging/ConcreteSagaReply.java` → `adapter/in/messaging/`

**Step 1: Move `SagaReplyConsumer.java`**

```java
// Before:
package com.wpanther.orchestrator.infrastructure.messaging.consumer;

// After:
package com.wpanther.orchestrator.adapter.in.messaging;
```

```bash
mv src/main/java/com/wpanther/orchestrator/infrastructure/messaging/consumer/SagaReplyConsumer.java \
   src/main/java/com/wpanther/orchestrator/adapter/in/messaging/SagaReplyConsumer.java
```

**Step 2: Move `StartSagaCommandConsumer.java`**

```java
// Before:
package com.wpanther.orchestrator.infrastructure.messaging.consumer;

// After:
package com.wpanther.orchestrator.adapter.in.messaging;
```

```bash
mv src/main/java/com/wpanther/orchestrator/infrastructure/messaging/consumer/StartSagaCommandConsumer.java \
   src/main/java/com/wpanther/orchestrator/adapter/in/messaging/StartSagaCommandConsumer.java

rmdir src/main/java/com/wpanther/orchestrator/infrastructure/messaging/consumer
```

**Step 3: Move `ConcreteSagaReply.java`**

```java
// Before:
package com.wpanther.orchestrator.infrastructure.messaging;

// After:
package com.wpanther.orchestrator.adapter.in.messaging;
```

```bash
mv src/main/java/com/wpanther/orchestrator/infrastructure/messaging/ConcreteSagaReply.java \
   src/main/java/com/wpanther/orchestrator/adapter/in/messaging/ConcreteSagaReply.java
```

**Step 4: Find and update all importers**

```bash
grep -rl "infrastructure.messaging.consumer\|infrastructure.messaging.ConcreteSagaReply" src/main/
```

Update each:
```java
// Before:
import com.wpanther.orchestrator.infrastructure.messaging.consumer.SagaReplyConsumer;
import com.wpanther.orchestrator.infrastructure.messaging.consumer.StartSagaCommandConsumer;
import com.wpanther.orchestrator.infrastructure.messaging.ConcreteSagaReply;

// After:
import com.wpanther.orchestrator.adapter.in.messaging.SagaReplyConsumer;
import com.wpanther.orchestrator.adapter.in.messaging.StartSagaCommandConsumer;
import com.wpanther.orchestrator.adapter.in.messaging.ConcreteSagaReply;
```

**Step 5: Compile and test**

```bash
mvn test -q
```

Expected: BUILD SUCCESS.

**Step 6: Commit**

```bash
git add src/main/java/com/wpanther/orchestrator/adapter/in/messaging/
git rm -r src/main/java/com/wpanther/orchestrator/infrastructure/messaging/consumer/ 2>/dev/null || true
git rm src/main/java/com/wpanther/orchestrator/infrastructure/messaging/ConcreteSagaReply.java 2>/dev/null || true
git add -u
git commit -m "Move Kafka consumers from infrastructure/messaging to adapter/in/messaging"
```

---

## Task 7: Move messaging producers → `adapter/out/messaging/`

**Files to move:**
- `infrastructure/messaging/producer/SagaCommandPublisher.java` → `adapter/out/messaging/`
- `infrastructure/messaging/producer/SagaEventPublisher.java` → `adapter/out/messaging/`
- `infrastructure/messaging/producer/SagaCommandProducer.java` → `adapter/out/messaging/` (deprecated, retained)

**Step 1: Create target directory**

```bash
mkdir -p src/main/java/com/wpanther/orchestrator/adapter/out/messaging
```

**Step 2: Move all three files — update package declarations**

For each of `SagaCommandPublisher.java`, `SagaEventPublisher.java`, `SagaCommandProducer.java`:
```java
// Before:
package com.wpanther.orchestrator.infrastructure.messaging.producer;

// After:
package com.wpanther.orchestrator.adapter.out.messaging;
```

```bash
mv src/main/java/com/wpanther/orchestrator/infrastructure/messaging/producer/SagaCommandPublisher.java \
   src/main/java/com/wpanther/orchestrator/adapter/out/messaging/SagaCommandPublisher.java

mv src/main/java/com/wpanther/orchestrator/infrastructure/messaging/producer/SagaEventPublisher.java \
   src/main/java/com/wpanther/orchestrator/adapter/out/messaging/SagaEventPublisher.java

mv src/main/java/com/wpanther/orchestrator/infrastructure/messaging/producer/SagaCommandProducer.java \
   src/main/java/com/wpanther/orchestrator/adapter/out/messaging/SagaCommandProducer.java

rmdir src/main/java/com/wpanther/orchestrator/infrastructure/messaging/producer
# If infrastructure/messaging dir is now empty:
rmdir src/main/java/com/wpanther/orchestrator/infrastructure/messaging 2>/dev/null || true
```

**Step 3: Find and update all importers**

```bash
grep -rl "infrastructure.messaging.producer" src/main/
```

Update each:
```java
// Before:
import com.wpanther.orchestrator.infrastructure.messaging.producer.SagaCommandPublisher;
import com.wpanther.orchestrator.infrastructure.messaging.producer.SagaEventPublisher;
import com.wpanther.orchestrator.infrastructure.messaging.producer.SagaCommandProducer;

// After:
import com.wpanther.orchestrator.adapter.out.messaging.SagaCommandPublisher;
import com.wpanther.orchestrator.adapter.out.messaging.SagaEventPublisher;
import com.wpanther.orchestrator.adapter.out.messaging.SagaCommandProducer;
```

**Step 4: Compile and test**

```bash
mvn test -q
```

Expected: BUILD SUCCESS.

**Step 5: Commit**

```bash
git add src/main/java/com/wpanther/orchestrator/adapter/out/messaging/
git rm -r src/main/java/com/wpanther/orchestrator/infrastructure/messaging/producer/ 2>/dev/null || true
git add -u
git commit -m "Move Kafka producers from infrastructure/messaging to adapter/out/messaging"
```

---

## Task 8: Move `infrastructure/persistence/` → `adapter/out/persistence/`

This is the largest move — includes JPA entities, Spring Data interfaces, MapStruct mapper, and the outbox subpackage.

**Files to move:**
- `SagaInstanceEntity.java`, `SagaCommandEntity.java`, `SagaInstanceMapper.java`
- `JpaSagaInstanceRepository.java`, `JpaSagaCommandRepository.java`
- `SpringDataSagaInstanceRepository.java`, `SpringDataSagaCommandRepository.java`
- `outbox/OutboxEventEntity.java`, `outbox/JpaOrchestratorOutboxRepository.java`, `outbox/SpringDataOrchestratorOutboxRepository.java`

**Step 1: Create target directories**

```bash
mkdir -p src/main/java/com/wpanther/orchestrator/adapter/out/persistence/outbox
```

**Step 2: Move all persistence files — update package declarations**

For all files directly in `infrastructure/persistence/`:
```java
// Before:
package com.wpanther.orchestrator.infrastructure.persistence;

// After:
package com.wpanther.orchestrator.adapter.out.persistence;
```

For all files in `infrastructure/persistence/outbox/`:
```java
// Before:
package com.wpanther.orchestrator.infrastructure.persistence.outbox;

// After:
package com.wpanther.orchestrator.adapter.out.persistence.outbox;
```

Move commands:
```bash
for f in SagaInstanceEntity SagaCommandEntity SagaInstanceMapper \
          JpaSagaInstanceRepository JpaSagaCommandRepository \
          SpringDataSagaInstanceRepository SpringDataSagaCommandRepository; do
  mv "src/main/java/com/wpanther/orchestrator/infrastructure/persistence/${f}.java" \
     "src/main/java/com/wpanther/orchestrator/adapter/out/persistence/${f}.java"
done

for f in OutboxEventEntity JpaOrchestratorOutboxRepository SpringDataOrchestratorOutboxRepository; do
  mv "src/main/java/com/wpanther/orchestrator/infrastructure/persistence/outbox/${f}.java" \
     "src/main/java/com/wpanther/orchestrator/adapter/out/persistence/outbox/${f}.java"
done

rmdir src/main/java/com/wpanther/orchestrator/infrastructure/persistence/outbox
rmdir src/main/java/com/wpanther/orchestrator/infrastructure/persistence
```

**Step 3: Update cross-package imports within the persistence package**

Inside `JpaSagaInstanceRepository.java` — it imports `SagaInstanceEntity` and `SagaInstanceMapper`. Since these are now in the same package (`adapter.out.persistence`), those imports may not exist (same-package imports are not needed in Java). Verify.

Inside `JpaOrchestratorOutboxRepository.java` — it imports `OutboxEventEntity`. Since it moved to the same package (`adapter.out.persistence.outbox`), no import needed.

**Step 4: Find all importers of persistence classes outside the persistence package**

```bash
grep -rl "infrastructure.persistence" src/main/
```

Update each occurrence:
```java
// Before:
import com.wpanther.orchestrator.infrastructure.persistence.*;
import com.wpanther.orchestrator.infrastructure.persistence.outbox.*;

// After:
import com.wpanther.orchestrator.adapter.out.persistence.*;
import com.wpanther.orchestrator.adapter.out.persistence.outbox.*;
```

**Step 5: Compile and test**

```bash
mvn test -q
```

Expected: BUILD SUCCESS.

**Step 6: Commit**

```bash
git add src/main/java/com/wpanther/orchestrator/adapter/out/persistence/
git rm -r src/main/java/com/wpanther/orchestrator/infrastructure/persistence/ 2>/dev/null || true
git add -u
git commit -m "Move JPA persistence classes from infrastructure/persistence to adapter/out/persistence"
```

---

## Task 9: Move `infrastructure/security/` → `adapter/in/security/`

**Files to move:**
- `JwtTokenProvider.java`, `JwtAuthenticationFilter.java`, `JwtUserDetailsService.java`

**Step 1: Create target directory**

```bash
mkdir -p src/main/java/com/wpanther/orchestrator/adapter/in/security
```

**Step 2: Move files — update package declarations**

For all three files:
```java
// Before:
package com.wpanther.orchestrator.infrastructure.security;

// After:
package com.wpanther.orchestrator.adapter.in.security;
```

```bash
for f in JwtTokenProvider JwtAuthenticationFilter JwtUserDetailsService; do
  mv "src/main/java/com/wpanther/orchestrator/infrastructure/security/${f}.java" \
     "src/main/java/com/wpanther/orchestrator/adapter/in/security/${f}.java"
done
rmdir src/main/java/com/wpanther/orchestrator/infrastructure/security
```

**Step 3: Find all importers**

```bash
grep -rl "infrastructure.security" src/main/
```

The main consumer is `infrastructure/config/SecurityConfig.java`. Update:
```java
// Before:
import com.wpanther.orchestrator.infrastructure.security.JwtTokenProvider;
import com.wpanther.orchestrator.infrastructure.security.JwtAuthenticationFilter;
import com.wpanther.orchestrator.infrastructure.security.JwtUserDetailsService;

// After:
import com.wpanther.orchestrator.adapter.in.security.JwtTokenProvider;
import com.wpanther.orchestrator.adapter.in.security.JwtAuthenticationFilter;
import com.wpanther.orchestrator.adapter.in.security.JwtUserDetailsService;
```

**Step 4: Compile and test**

```bash
mvn test -q
```

Expected: BUILD SUCCESS.

**Step 5: Commit**

```bash
git add src/main/java/com/wpanther/orchestrator/adapter/in/security/
git rm -r src/main/java/com/wpanther/orchestrator/infrastructure/security/ 2>/dev/null || true
git add -u
git commit -m "Move JWT security classes from infrastructure/security to adapter/in/security"
```

---

## Task 10: Move `infrastructure/config/` → `config/`

**Files to move:**
- `KafkaConfig.java`, `OrchestratorConfig.java`, `OrchestratorOutboxConfig.java`, `SecurityConfig.java`

**Step 1: Create target directory**

```bash
mkdir -p src/main/java/com/wpanther/orchestrator/config
```

**Step 2: Move files — update package declarations**

For all four files:
```java
// Before:
package com.wpanther.orchestrator.infrastructure.config;

// After:
package com.wpanther.orchestrator.config;
```

```bash
for f in KafkaConfig OrchestratorConfig OrchestratorOutboxConfig SecurityConfig; do
  mv "src/main/java/com/wpanther/orchestrator/infrastructure/config/${f}.java" \
     "src/main/java/com/wpanther/orchestrator/config/${f}.java"
done
rmdir src/main/java/com/wpanther/orchestrator/infrastructure/config
# If infrastructure dir is now empty:
rmdir src/main/java/com/wpanther/orchestrator/infrastructure 2>/dev/null || true
```

**Step 3: Find all importers**

```bash
grep -rl "infrastructure.config" src/main/
```

Update each:
```java
// Before:
import com.wpanther.orchestrator.infrastructure.config.KafkaConfig;
// etc.

// After:
import com.wpanther.orchestrator.config.KafkaConfig;
// etc.
```

**Step 4: Compile and test**

```bash
mvn test -q
```

Expected: BUILD SUCCESS. The `infrastructure/` directory should now be completely gone.

**Step 5: Verify `infrastructure/` is empty**

```bash
find src/main/java/com/wpanther/orchestrator/infrastructure -type f 2>/dev/null | wc -l
```

Expected: `0` (or the `find` command returns nothing). If any files remain, move them before committing.

**Step 6: Commit**

```bash
git add src/main/java/com/wpanther/orchestrator/config/
git rm -r src/main/java/com/wpanther/orchestrator/infrastructure/ 2>/dev/null || true
git add -u
git commit -m "Move Spring @Configuration classes from infrastructure/config to config"
```

---

## Task 11: Update JaCoCo exclusion patterns in `pom.xml`

JaCoCo excludes specific paths from coverage enforcement. Those paths reference old package locations and must be updated.

**Files:**
- Modify: `pom.xml` (jacoco-maven-plugin `<excludes>` section)

**Step 1: Find the current exclusions**

Open `pom.xml` and find the `jacoco-maven-plugin` configuration with `<excludes>`.

**Step 2: Update each exclusion pattern**

Apply these replacements (the exact pattern strings in your `pom.xml` may vary slightly — match by intent):

| Old pattern | New pattern |
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

**Step 3: Run full coverage verification**

```bash
mvn verify -q
```

Expected: BUILD SUCCESS with line coverage ≥ 80% (should still be ~88%).

If coverage drops, check if any test files are missing updated imports (they may not compile).

**Step 4: Commit**

```bash
git add pom.xml
git commit -m "Update JaCoCo exclusion patterns for new hexagonal package paths"
```

---

## Task 12: Relocate test files to mirror new structure

Every production package move has a corresponding test move. The integration tests in `src/test/java/com/wpanther/orchestrator/integration/` are NOT touched.

**Step 1: Create target test directories**

```bash
mkdir -p src/test/java/com/wpanther/orchestrator/application/usecase
mkdir -p src/test/java/com/wpanther/orchestrator/adapter/in/web
mkdir -p src/test/java/com/wpanther/orchestrator/adapter/in/messaging
mkdir -p src/test/java/com/wpanther/orchestrator/adapter/in/security
mkdir -p src/test/java/com/wpanther/orchestrator/adapter/out/messaging
```

**Step 2: Move test files**

```bash
# application/service → application/usecase
mv src/test/java/com/wpanther/orchestrator/application/service/SagaApplicationServiceTest.java \
   src/test/java/com/wpanther/orchestrator/application/usecase/SagaApplicationServiceTest.java
rmdir src/test/java/com/wpanther/orchestrator/application/service 2>/dev/null || true

# application/controller → adapter/in/web
mv src/test/java/com/wpanther/orchestrator/application/controller/OrchestratorControllerTest.java \
   src/test/java/com/wpanther/orchestrator/adapter/in/web/OrchestratorControllerTest.java
mv src/test/java/com/wpanther/orchestrator/application/controller/AuthControllerTest.java \
   src/test/java/com/wpanther/orchestrator/adapter/in/web/AuthControllerTest.java
rmdir src/test/java/com/wpanther/orchestrator/application/controller 2>/dev/null || true

# infrastructure/messaging/consumer → adapter/in/messaging
mv src/test/java/com/wpanther/orchestrator/infrastructure/messaging/consumer/SagaReplyConsumerTest.java \
   src/test/java/com/wpanther/orchestrator/adapter/in/messaging/SagaReplyConsumerTest.java
mv src/test/java/com/wpanther/orchestrator/infrastructure/messaging/consumer/StartSagaCommandConsumerTest.java \
   src/test/java/com/wpanther/orchestrator/adapter/in/messaging/StartSagaCommandConsumerTest.java
mv src/test/java/com/wpanther/orchestrator/infrastructure/messaging/ConcreteSagaReplyTest.java \
   src/test/java/com/wpanther/orchestrator/adapter/in/messaging/ConcreteSagaReplyTest.java 2>/dev/null || true

# infrastructure/messaging/producer → adapter/out/messaging
mv src/test/java/com/wpanther/orchestrator/infrastructure/messaging/producer/SagaCommandPublisherTest.java \
   src/test/java/com/wpanther/orchestrator/adapter/out/messaging/SagaCommandPublisherTest.java
mv src/test/java/com/wpanther/orchestrator/infrastructure/messaging/producer/SagaEventPublisherTest.java \
   src/test/java/com/wpanther/orchestrator/adapter/out/messaging/SagaEventPublisherTest.java

# infrastructure/security → adapter/in/security
mv src/test/java/com/wpanther/orchestrator/infrastructure/security/JwtTokenProviderTest.java \
   src/test/java/com/wpanther/orchestrator/adapter/in/security/JwtTokenProviderTest.java
mv src/test/java/com/wpanther/orchestrator/infrastructure/security/JwtAuthenticationFilterTest.java \
   src/test/java/com/wpanther/orchestrator/adapter/in/security/JwtAuthenticationFilterTest.java
mv src/test/java/com/wpanther/orchestrator/infrastructure/security/JwtUserDetailsServiceTest.java \
   src/test/java/com/wpanther/orchestrator/adapter/in/security/JwtUserDetailsServiceTest.java
mv src/test/java/com/wpanther/orchestrator/infrastructure/security/SecurityConfigTest.java \
   src/test/java/com/wpanther/orchestrator/adapter/in/security/SecurityConfigTest.java
```

**Step 3: Update package declarations in every moved test file**

For each moved test file, update the `package` declaration to match the new location.

Example for `SagaApplicationServiceTest.java`:
```java
// Before:
package com.wpanther.orchestrator.application.service;

// After:
package com.wpanther.orchestrator.application.usecase;
```

Apply the same pattern for each file (package = directory path under `com/wpanther/orchestrator/`).

**Step 4: Update imports in test files**

Test files import production classes. Search for any remaining old-path imports:

```bash
grep -rl "infrastructure\.\(messaging\|persistence\|security\|config\)\|application\.service\|application\.controller\|domain\.service\|domain\.repository\|domain\.event\.StartSagaCommand" src/test/
```

Update all found imports using the same mapping table from Task 1–10.

**Step 5: Run tests**

```bash
mvn test -q
```

Expected: BUILD SUCCESS, same test count as before.

**Step 6: Run coverage verification**

```bash
mvn verify -q
```

Expected: BUILD SUCCESS, ≥ 80% line coverage.

**Step 7: Commit**

```bash
git add src/test/java/com/wpanther/orchestrator/application/usecase/ \
        src/test/java/com/wpanther/orchestrator/adapter/
git rm -r src/test/java/com/wpanther/orchestrator/infrastructure/ 2>/dev/null || true
git rm -r src/test/java/com/wpanther/orchestrator/application/controller/ 2>/dev/null || true
git rm -r src/test/java/com/wpanther/orchestrator/application/service/ 2>/dev/null || true
git add -u
git commit -m "Relocate test classes to mirror new hexagonal package structure"
```

---

## Task 13: Final verification

**Step 1: Confirm `infrastructure/` is gone from both src and test**

```bash
find src/ -path "*/orchestrator/infrastructure*" -type f
```

Expected: no output.

**Step 2: Confirm target directories exist and contain files**

```bash
find src/ -path "*/orchestrator/port/*" -type f
find src/ -path "*/orchestrator/adapter/*" -type f
find src/ -path "*/orchestrator/config/*" -type f
```

Expected: all three produce file listings.

**Step 3: Run full test suite with coverage**

```bash
mvn verify
```

Expected: BUILD SUCCESS, line coverage ≥ 80% (was 88.2%).

**Step 4: Confirm no old package references remain in main source**

```bash
grep -r "com.wpanther.orchestrator.infrastructure" src/main/ | grep -v ".class"
grep -r "com.wpanther.orchestrator.domain.repository" src/main/ | grep -v ".class"
grep -r "com.wpanther.orchestrator.domain.service" src/main/ | grep -v ".class"
grep -r "com.wpanther.orchestrator.application.service" src/main/ | grep -v ".class"
grep -r "com.wpanther.orchestrator.application.controller" src/main/ | grep -v ".class"
```

Expected: no output from any command.

**Step 5: Done**

The migration is complete. All production classes live in their hexagonal locations. The `infrastructure/` layer is gone. Ports and adapters are explicit.
