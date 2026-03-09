# Orchestrator-Service Hexagonal Architecture Phase 2 Migration

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Align orchestrator-service with the canonical hexagonal layout used by all other services — dissolve `port/` into proper canonical locations, move `adapter/` and `config/` under `infrastructure/`, split `SagaOrchestrationService` into four focused use-case interfaces.

**Architecture:** Pure package rename + relocation + use-case split. No logic changes. `domain/` ← `application/` ← `infrastructure/` strict dependency rule. Repository interfaces move to `domain/repository/`. Four use-case interfaces replace the single `port/in/SagaOrchestrationService`. All adapters move under `infrastructure/adapter/`. Config moves to `infrastructure/config/` with concern-based sub-packages.

**Tech Stack:** Java 21, Spring Boot 3.2.5, Maven, JUnit 5, Mockito, JaCoCo 0.8.12

**Design doc:** `docs/plans/2026-03-09-hexagonal-architecture-migration-design.md`

---

## Pre-flight

Before starting, confirm all unit tests pass:

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/orchestrator-service
mvn test -q
```

Expected: BUILD SUCCESS. If not, stop and fix first.

---

## Phase 1 — Dissolve `port/`

### Task 1: Create `domain/repository/` and move repository interfaces

Repository interfaces are domain-owned output ports. They belong in `domain/repository/`, not a separate `port/out/` root.

**Files:**
- Move: `src/main/java/com/wpanther/orchestrator/port/out/SagaInstanceRepository.java` → `src/main/java/com/wpanther/orchestrator/domain/repository/SagaInstanceRepository.java`
- Move: `src/main/java/com/wpanther/orchestrator/port/out/SagaCommandRecordRepository.java` → `src/main/java/com/wpanther/orchestrator/domain/repository/SagaCommandRecordRepository.java`
- Modify: `src/main/java/com/wpanther/orchestrator/application/usecase/SagaApplicationService.java`
- Modify: `src/main/java/com/wpanther/orchestrator/adapter/out/persistence/JpaSagaInstanceRepository.java`
- Modify: `src/main/java/com/wpanther/orchestrator/adapter/out/persistence/JpaSagaCommandRepository.java`

**Step 1: Create target directory**

```bash
mkdir -p src/main/java/com/wpanther/orchestrator/domain/repository
```

**Step 2: Move `SagaInstanceRepository.java` — update package declaration**

Open `src/main/java/com/wpanther/orchestrator/port/out/SagaInstanceRepository.java`.

Change line 1:
```java
// Before:
package com.wpanther.orchestrator.port.out;

// After:
package com.wpanther.orchestrator.domain.repository;
```

Move the file:
```bash
mv src/main/java/com/wpanther/orchestrator/port/out/SagaInstanceRepository.java \
   src/main/java/com/wpanther/orchestrator/domain/repository/SagaInstanceRepository.java
```

**Step 3: Move `SagaCommandRecordRepository.java` — update package declaration**

Change line 1:
```java
// Before:
package com.wpanther.orchestrator.port.out;

// After:
package com.wpanther.orchestrator.domain.repository;
```

Move the file:
```bash
mv src/main/java/com/wpanther/orchestrator/port/out/SagaCommandRecordRepository.java \
   src/main/java/com/wpanther/orchestrator/domain/repository/SagaCommandRecordRepository.java
rmdir src/main/java/com/wpanther/orchestrator/port/out
```

**Step 4: Update all importers**

Find all files that import from `port.out`:
```bash
grep -rl "orchestrator.port.out" src/main/
```

In each found file, replace:
```java
// Before:
import com.wpanther.orchestrator.port.out.SagaInstanceRepository;
import com.wpanther.orchestrator.port.out.SagaCommandRecordRepository;

// After:
import com.wpanther.orchestrator.domain.repository.SagaInstanceRepository;
import com.wpanther.orchestrator.domain.repository.SagaCommandRecordRepository;
```

**Step 5: Compile**

```bash
mvn compile -q
```

Expected: BUILD SUCCESS. Fix any "cannot find symbol" before continuing.

**Step 6: Commit**

```bash
git add src/main/java/com/wpanther/orchestrator/domain/repository/ \
        src/main/java/com/wpanther/orchestrator/application/usecase/SagaApplicationService.java \
        src/main/java/com/wpanther/orchestrator/adapter/out/persistence/JpaSagaInstanceRepository.java \
        src/main/java/com/wpanther/orchestrator/adapter/out/persistence/JpaSagaCommandRepository.java
git rm src/main/java/com/wpanther/orchestrator/port/out/SagaInstanceRepository.java 2>/dev/null || true
git rm src/main/java/com/wpanther/orchestrator/port/out/SagaCommandRecordRepository.java 2>/dev/null || true
git add -u
git commit -m "Move repository interfaces from port/out to domain/repository"
```

---

### Task 2: Create four use-case interfaces and update `SagaApplicationService`

`port/in/SagaOrchestrationService` is a broad 9-method interface. It is dissolved into four focused interfaces. `SagaApplicationService` implements all four — **no logic changes**, only the `implements` declaration changes.

**Files:**
- Create: `src/main/java/com/wpanther/orchestrator/application/usecase/StartSagaUseCase.java`
- Create: `src/main/java/com/wpanther/orchestrator/application/usecase/HandleSagaReplyUseCase.java`
- Create: `src/main/java/com/wpanther/orchestrator/application/usecase/HandleCompensationUseCase.java`
- Create: `src/main/java/com/wpanther/orchestrator/application/usecase/QuerySagaUseCase.java`
- Modify: `src/main/java/com/wpanther/orchestrator/application/usecase/SagaApplicationService.java`
- Modify: `src/main/java/com/wpanther/orchestrator/adapter/in/web/OrchestratorController.java`
- Modify: `src/main/java/com/wpanther/orchestrator/adapter/in/messaging/StartSagaCommandConsumer.java`
- Modify: `src/main/java/com/wpanther/orchestrator/adapter/in/messaging/SagaReplyConsumer.java`
- Delete: `src/main/java/com/wpanther/orchestrator/port/in/SagaOrchestrationService.java`

**Step 1: Create `StartSagaUseCase.java`**

```java
package com.wpanther.orchestrator.application.usecase;

import com.wpanther.orchestrator.domain.model.DocumentMetadata;
import com.wpanther.orchestrator.domain.model.SagaInstance;
import com.wpanther.orchestrator.domain.model.enums.DocumentType;

public interface StartSagaUseCase {

    SagaInstance startSaga(DocumentType documentType, String documentId, DocumentMetadata metadata);
}
```

**Step 2: Create `HandleSagaReplyUseCase.java`**

```java
package com.wpanther.orchestrator.application.usecase;

import com.wpanther.orchestrator.domain.model.SagaInstance;
import java.util.Map;

public interface HandleSagaReplyUseCase {

    SagaInstance handleReply(String sagaId, String step, boolean success, String errorMessage);

    SagaInstance handleReply(String sagaId, String step, boolean success,
                             String errorMessage, Map<String, Object> resultData);

    SagaInstance advanceSaga(String sagaId);

    SagaInstance retryStep(String sagaId);
}
```

**Step 3: Create `HandleCompensationUseCase.java`**

```java
package com.wpanther.orchestrator.application.usecase;

import com.wpanther.orchestrator.domain.model.SagaInstance;

public interface HandleCompensationUseCase {

    SagaInstance initiateCompensation(String sagaId, String errorMessage);
}
```

**Step 4: Create `QuerySagaUseCase.java`**

```java
package com.wpanther.orchestrator.application.usecase;

import com.wpanther.orchestrator.domain.model.SagaInstance;
import com.wpanther.orchestrator.domain.model.enums.DocumentType;
import java.util.List;

public interface QuerySagaUseCase {

    SagaInstance getSagaInstance(String sagaId);

    List<SagaInstance> getActiveSagas();

    List<SagaInstance> getSagasForDocument(DocumentType documentType, String documentId);
}
```

**Step 5: Update `SagaApplicationService` — change `implements` declaration**

In `src/main/java/com/wpanther/orchestrator/application/usecase/SagaApplicationService.java`:

Find the class declaration and `implements` line. Replace:
```java
// Before:
import com.wpanther.orchestrator.port.in.SagaOrchestrationService;
// ...
public class SagaApplicationService implements SagaOrchestrationService {

// After:
import com.wpanther.orchestrator.application.usecase.HandleCompensationUseCase;
import com.wpanther.orchestrator.application.usecase.HandleSagaReplyUseCase;
import com.wpanther.orchestrator.application.usecase.QuerySagaUseCase;
import com.wpanther.orchestrator.application.usecase.StartSagaUseCase;
// ...
public class SagaApplicationService implements StartSagaUseCase, HandleSagaReplyUseCase,
        HandleCompensationUseCase, QuerySagaUseCase {
```

Remove the `import com.wpanther.orchestrator.port.in.SagaOrchestrationService;` line.
All method bodies remain **completely unchanged**.

**Step 6: Update `OrchestratorController` — inject specific interfaces**

In `src/main/java/com/wpanther/orchestrator/adapter/in/web/OrchestratorController.java`:

Replace the injected `SagaOrchestrationService` field with the two interfaces it needs:
```java
// Before:
import com.wpanther.orchestrator.port.in.SagaOrchestrationService;
// ...
private final SagaOrchestrationService sagaOrchestrationService;

public OrchestratorController(SagaOrchestrationService sagaOrchestrationService) {
    this.sagaOrchestrationService = sagaOrchestrationService;
}

// After:
import com.wpanther.orchestrator.application.usecase.StartSagaUseCase;
import com.wpanther.orchestrator.application.usecase.QuerySagaUseCase;
// ...
private final StartSagaUseCase startSagaUseCase;
private final QuerySagaUseCase querySagaUseCase;

public OrchestratorController(StartSagaUseCase startSagaUseCase, QuerySagaUseCase querySagaUseCase) {
    this.startSagaUseCase = startSagaUseCase;
    this.querySagaUseCase = querySagaUseCase;
}
```

Update all method bodies: rename `sagaOrchestrationService.startSaga(...)` → `startSagaUseCase.startSaga(...)`, and `sagaOrchestrationService.getSagaInstance(...)` / `getActiveSagas()` / `getSagasForDocument(...)` → `querySagaUseCase.*`.

**Step 7: Update `StartSagaCommandConsumer` — inject `StartSagaUseCase`**

In `src/main/java/com/wpanther/orchestrator/adapter/in/messaging/StartSagaCommandConsumer.java`:

```java
// Before:
import com.wpanther.orchestrator.port.in.SagaOrchestrationService;
// ...
private final SagaOrchestrationService sagaOrchestrationService;

// After:
import com.wpanther.orchestrator.application.usecase.StartSagaUseCase;
// ...
private final StartSagaUseCase startSagaUseCase;
```

Update the constructor and all call sites accordingly (rename field reference).

**Step 8: Update `SagaReplyConsumer` — inject `HandleSagaReplyUseCase` and `HandleCompensationUseCase`**

In `src/main/java/com/wpanther/orchestrator/adapter/in/messaging/SagaReplyConsumer.java`:

```java
// Before:
import com.wpanther.orchestrator.port.in.SagaOrchestrationService;
// ...
private final SagaOrchestrationService sagaOrchestrationService;

public SagaReplyConsumer(SagaOrchestrationService sagaOrchestrationService, ...) {
    this.sagaOrchestrationService = sagaOrchestrationService;
    ...
}

// After:
import com.wpanther.orchestrator.application.usecase.HandleSagaReplyUseCase;
import com.wpanther.orchestrator.application.usecase.HandleCompensationUseCase;
// ...
private final HandleSagaReplyUseCase handleSagaReplyUseCase;
private final HandleCompensationUseCase handleCompensationUseCase;

public SagaReplyConsumer(HandleSagaReplyUseCase handleSagaReplyUseCase,
                         HandleCompensationUseCase handleCompensationUseCase, ...) {
    this.handleSagaReplyUseCase = handleSagaReplyUseCase;
    this.handleCompensationUseCase = handleCompensationUseCase;
    ...
}
```

Update all call sites: `sagaOrchestrationService.handleReply(...)` → `handleSagaReplyUseCase.handleReply(...)`, `sagaOrchestrationService.initiateCompensation(...)` → `handleCompensationUseCase.initiateCompensation(...)`, etc.

**Step 9: Delete `port/in/SagaOrchestrationService.java`**

```bash
git rm src/main/java/com/wpanther/orchestrator/port/in/SagaOrchestrationService.java
rmdir src/main/java/com/wpanther/orchestrator/port/in 2>/dev/null || true
rmdir src/main/java/com/wpanther/orchestrator/port 2>/dev/null || true
```

**Step 10: Compile**

```bash
mvn compile -q
```

Expected: BUILD SUCCESS. If not, grep for remaining `port.in` imports:
```bash
grep -r "orchestrator.port.in" src/main/
```

Fix any found before continuing.

**Step 11: Run tests**

```bash
mvn test -q
```

Expected: BUILD SUCCESS.

**Step 12: Commit**

```bash
git add src/main/java/com/wpanther/orchestrator/application/usecase/
git add -u
git commit -m "Dissolve port/in: split SagaOrchestrationService into four use-case interfaces, update all adapters"
```

---

### Task 3: Add four use-case contract tests

These tests confirm `SagaApplicationService` correctly implements each interface and verify key method contracts. All use Mockito — no database or Kafka needed.

**Files:**
- Create: `src/test/java/com/wpanther/orchestrator/application/usecase/StartSagaUseCaseTest.java`
- Create: `src/test/java/com/wpanther/orchestrator/application/usecase/HandleSagaReplyUseCaseTest.java`
- Create: `src/test/java/com/wpanther/orchestrator/application/usecase/HandleCompensationUseCaseTest.java`
- Create: `src/test/java/com/wpanther/orchestrator/application/usecase/QuerySagaUseCaseTest.java`

**Step 1: Look up `SagaApplicationServiceTest` to understand mock setup**

Open `src/test/java/com/wpanther/orchestrator/application/usecase/SagaApplicationServiceTest.java` and note:
- The list of `@Mock` fields (repository and publisher mocks)
- The `@InjectMocks SagaApplicationService service` declaration
- The test helper methods used to build `SagaInstance` fixtures

Use exactly the same mock setup in all four new test classes.

**Step 2: Create `StartSagaUseCaseTest.java`**

This test verifies the interface contract type-safe: `SagaApplicationService` can be held as `StartSagaUseCase`.

```java
package com.wpanther.orchestrator.application.usecase;

import com.wpanther.orchestrator.domain.model.DocumentMetadata;
import com.wpanther.orchestrator.domain.model.SagaInstance;
import com.wpanther.orchestrator.domain.model.enums.DocumentType;
import com.wpanther.orchestrator.domain.repository.SagaInstanceRepository;
import com.wpanther.orchestrator.domain.repository.SagaCommandRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StartSagaUseCaseTest {

    @Mock
    private SagaInstanceRepository sagaInstanceRepository;

    @Mock
    private SagaCommandRecordRepository sagaCommandRecordRepository;

    // Add any other @Mock fields matching SagaApplicationServiceTest setup

    @InjectMocks
    private SagaApplicationService service;

    @Test
    void sagaApplicationService_implementsStartSagaUseCase() {
        assertThat(service).isInstanceOf(StartSagaUseCase.class);
    }

    @Test
    void startSaga_savesAndReturnsNewSagaInstance() {
        // Arrange
        DocumentMetadata metadata = DocumentMetadata.builder()
                .documentId("doc-123")
                .build();
        SagaInstance expected = SagaInstance.builder()
                .sagaId("saga-123")
                .documentType(DocumentType.TAX_INVOICE)
                .documentId("doc-123")
                .build();
        when(sagaInstanceRepository.save(any())).thenReturn(expected);

        // Act
        StartSagaUseCase useCase = service;
        SagaInstance result = useCase.startSaga(DocumentType.TAX_INVOICE, "doc-123", metadata);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getDocumentId()).isEqualTo("doc-123");
    }
}
```

> **Note:** Adjust the builder calls and mock stubs to match the exact field names in `SagaInstance` and `DocumentMetadata` — check the existing `SagaApplicationServiceTest` for the correct setup pattern.

**Step 3: Create `HandleSagaReplyUseCaseTest.java`**

```java
package com.wpanther.orchestrator.application.usecase;

import com.wpanther.orchestrator.domain.model.SagaInstance;
import com.wpanther.orchestrator.domain.repository.SagaInstanceRepository;
import com.wpanther.orchestrator.domain.repository.SagaCommandRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HandleSagaReplyUseCaseTest {

    @Mock
    private SagaInstanceRepository sagaInstanceRepository;

    @Mock
    private SagaCommandRecordRepository sagaCommandRecordRepository;

    // Add any other @Mock fields matching SagaApplicationServiceTest setup

    @InjectMocks
    private SagaApplicationService service;

    @Test
    void sagaApplicationService_implementsHandleSagaReplyUseCase() {
        assertThat(service).isInstanceOf(HandleSagaReplyUseCase.class);
    }

    @Test
    void handleReply_successPath_returnsSagaInstance() {
        // Arrange — use existing SagaInstance fixture from sagaInstanceRepository
        SagaInstance existing = buildExistingSagaInstance();
        when(sagaInstanceRepository.findById(anyString())).thenReturn(Optional.of(existing));
        when(sagaInstanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        HandleSagaReplyUseCase useCase = service;
        SagaInstance result = useCase.handleReply("saga-123", "SIGN_XML", true, null);

        // Assert
        assertThat(result).isNotNull();
    }

    @Test
    void handleReply_withResultData_mergesIntoMetadata() {
        SagaInstance existing = buildExistingSagaInstance();
        when(sagaInstanceRepository.findById(anyString())).thenReturn(Optional.of(existing));
        when(sagaInstanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HandleSagaReplyUseCase useCase = service;
        SagaInstance result = useCase.handleReply("saga-123", "SIGN_XML", true, null,
                Map.of("signedXmlUrl", "http://example.com/signed.xml"));

        assertThat(result).isNotNull();
    }

    private SagaInstance buildExistingSagaInstance() {
        // Adjust to match actual SagaInstance builder API
        return SagaInstance.builder()
                .sagaId("saga-123")
                .build();
    }
}
```

**Step 4: Create `HandleCompensationUseCaseTest.java`**

```java
package com.wpanther.orchestrator.application.usecase;

import com.wpanther.orchestrator.domain.model.SagaInstance;
import com.wpanther.orchestrator.domain.repository.SagaInstanceRepository;
import com.wpanther.orchestrator.domain.repository.SagaCommandRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HandleCompensationUseCaseTest {

    @Mock
    private SagaInstanceRepository sagaInstanceRepository;

    @Mock
    private SagaCommandRecordRepository sagaCommandRecordRepository;

    // Add any other @Mock fields matching SagaApplicationServiceTest setup

    @InjectMocks
    private SagaApplicationService service;

    @Test
    void sagaApplicationService_implementsHandleCompensationUseCase() {
        assertThat(service).isInstanceOf(HandleCompensationUseCase.class);
    }

    @Test
    void initiateCompensation_returnsSagaInstanceInCompensatingState() {
        SagaInstance existing = SagaInstance.builder()
                .sagaId("saga-123")
                .build();
        when(sagaInstanceRepository.findById(anyString())).thenReturn(Optional.of(existing));
        when(sagaInstanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HandleCompensationUseCase useCase = service;
        SagaInstance result = useCase.initiateCompensation("saga-123", "Processing failed");

        assertThat(result).isNotNull();
    }
}
```

**Step 5: Create `QuerySagaUseCaseTest.java`**

```java
package com.wpanther.orchestrator.application.usecase;

import com.wpanther.orchestrator.domain.model.SagaInstance;
import com.wpanther.orchestrator.domain.model.enums.DocumentType;
import com.wpanther.orchestrator.domain.repository.SagaInstanceRepository;
import com.wpanther.orchestrator.domain.repository.SagaCommandRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuerySagaUseCaseTest {

    @Mock
    private SagaInstanceRepository sagaInstanceRepository;

    @Mock
    private SagaCommandRecordRepository sagaCommandRecordRepository;

    // Add any other @Mock fields matching SagaApplicationServiceTest setup

    @InjectMocks
    private SagaApplicationService service;

    @Test
    void sagaApplicationService_implementsQuerySagaUseCase() {
        assertThat(service).isInstanceOf(QuerySagaUseCase.class);
    }

    @Test
    void getSagaInstance_returnsInstance_whenFound() {
        SagaInstance expected = SagaInstance.builder().sagaId("saga-123").build();
        when(sagaInstanceRepository.findById("saga-123")).thenReturn(Optional.of(expected));

        QuerySagaUseCase useCase = service;
        SagaInstance result = useCase.getSagaInstance("saga-123");

        assertThat(result.getSagaId()).isEqualTo("saga-123");
    }

    @Test
    void getSagaInstance_throwsException_whenNotFound() {
        when(sagaInstanceRepository.findById(anyString())).thenReturn(Optional.empty());

        QuerySagaUseCase useCase = service;
        assertThatThrownBy(() -> useCase.getSagaInstance("missing-id"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void getActiveSagas_returnsList() {
        when(sagaInstanceRepository.findAllActive()).thenReturn(List.of());

        QuerySagaUseCase useCase = service;
        List<SagaInstance> result = useCase.getActiveSagas();

        assertThat(result).isNotNull();
    }

    @Test
    void getSagasForDocument_returnsList() {
        when(sagaInstanceRepository.findByDocumentTypeAndDocumentId(
                DocumentType.TAX_INVOICE, "doc-123")).thenReturn(List.of());

        QuerySagaUseCase useCase = service;
        List<SagaInstance> result = useCase.getSagasForDocument(DocumentType.TAX_INVOICE, "doc-123");

        assertThat(result).isNotNull();
    }
}
```

> **Note on mock stubs:** Check `SagaInstanceRepository` for the exact method names used (`findById`, `findAllActive`, `findByDocumentTypeAndDocumentId`) and adjust above accordingly. Check `SagaApplicationServiceTest` for the full list of `@Mock` fields needed in `SagaApplicationService` — add all of them to each new test class or the injection will fail.

**Step 6: Run new tests**

```bash
mvn test -Dtest="StartSagaUseCaseTest,HandleSagaReplyUseCaseTest,HandleCompensationUseCaseTest,QuerySagaUseCaseTest" -q
```

Expected: BUILD SUCCESS. Fix any injection failures before continuing.

**Step 7: Run full test suite**

```bash
mvn test -q
```

Expected: BUILD SUCCESS, same pass count as before plus 4 new test classes.

**Step 8: Commit**

```bash
git add src/test/java/com/wpanther/orchestrator/application/usecase/StartSagaUseCaseTest.java \
        src/test/java/com/wpanther/orchestrator/application/usecase/HandleSagaReplyUseCaseTest.java \
        src/test/java/com/wpanther/orchestrator/application/usecase/HandleCompensationUseCaseTest.java \
        src/test/java/com/wpanther/orchestrator/application/usecase/QuerySagaUseCaseTest.java
git commit -m "Add use-case contract tests for StartSaga, HandleSagaReply, HandleCompensation, QuerySaga"
```

---

## Phase 2 — Move `config/` → `infrastructure/config/`

### Task 4: Move config classes to `infrastructure/config/` with concern sub-packages

**Files:**
- Move: `src/main/java/com/wpanther/orchestrator/config/KafkaConfig.java` → `src/main/java/com/wpanther/orchestrator/infrastructure/config/kafka/KafkaConfig.java`
- Move: `src/main/java/com/wpanther/orchestrator/config/SecurityConfig.java` → `src/main/java/com/wpanther/orchestrator/infrastructure/config/security/SecurityConfig.java`
- Move: `src/main/java/com/wpanther/orchestrator/config/OrchestratorOutboxConfig.java` → `src/main/java/com/wpanther/orchestrator/infrastructure/config/outbox/OrchestratorOutboxConfig.java`
- Move: `src/main/java/com/wpanther/orchestrator/config/OrchestratorConfig.java` → `src/main/java/com/wpanther/orchestrator/infrastructure/config/OrchestratorConfig.java`
- Test: `src/test/java/com/wpanther/orchestrator/config/SecurityConfigTest.java` → `src/test/java/com/wpanther/orchestrator/infrastructure/config/security/SecurityConfigTest.java`

**Step 1: Create target directories**

```bash
mkdir -p src/main/java/com/wpanther/orchestrator/infrastructure/config/kafka
mkdir -p src/main/java/com/wpanther/orchestrator/infrastructure/config/security
mkdir -p src/main/java/com/wpanther/orchestrator/infrastructure/config/outbox
mkdir -p src/test/java/com/wpanther/orchestrator/infrastructure/config/security
```

**Step 2: Move and update package declarations**

For `KafkaConfig.java`:
```java
// Before:
package com.wpanther.orchestrator.config;
// After:
package com.wpanther.orchestrator.infrastructure.config.kafka;
```
```bash
mv src/main/java/com/wpanther/orchestrator/config/KafkaConfig.java \
   src/main/java/com/wpanther/orchestrator/infrastructure/config/kafka/KafkaConfig.java
```

For `SecurityConfig.java`:
```java
// Before:
package com.wpanther.orchestrator.config;
// After:
package com.wpanther.orchestrator.infrastructure.config.security;
```
```bash
mv src/main/java/com/wpanther/orchestrator/config/SecurityConfig.java \
   src/main/java/com/wpanther/orchestrator/infrastructure/config/security/SecurityConfig.java
```

For `OrchestratorOutboxConfig.java`:
```java
// Before:
package com.wpanther.orchestrator.config;
// After:
package com.wpanther.orchestrator.infrastructure.config.outbox;
```
```bash
mv src/main/java/com/wpanther/orchestrator/config/OrchestratorOutboxConfig.java \
   src/main/java/com/wpanther/orchestrator/infrastructure/config/outbox/OrchestratorOutboxConfig.java
```

For `OrchestratorConfig.java`:
```java
// Before:
package com.wpanther.orchestrator.config;
// After:
package com.wpanther.orchestrator.infrastructure.config;
```
```bash
mv src/main/java/com/wpanther/orchestrator/config/OrchestratorConfig.java \
   src/main/java/com/wpanther/orchestrator/infrastructure/config/OrchestratorConfig.java
rmdir src/main/java/com/wpanther/orchestrator/config
```

**Step 3: Find all importers of old config paths**

```bash
grep -rl "orchestrator.config\." src/main/
```

For each found file, update:
```java
// Before:
import com.wpanther.orchestrator.config.KafkaConfig;
import com.wpanther.orchestrator.config.SecurityConfig;
import com.wpanther.orchestrator.config.OrchestratorOutboxConfig;
import com.wpanther.orchestrator.config.OrchestratorConfig;

// After:
import com.wpanther.orchestrator.infrastructure.config.kafka.KafkaConfig;
import com.wpanther.orchestrator.infrastructure.config.security.SecurityConfig;
import com.wpanther.orchestrator.infrastructure.config.outbox.OrchestratorOutboxConfig;
import com.wpanther.orchestrator.infrastructure.config.OrchestratorConfig;
```

**Step 4: Move `SecurityConfigTest.java` — update package declaration**

```java
// Before:
package com.wpanther.orchestrator.config;
// After:
package com.wpanther.orchestrator.infrastructure.config.security;
```
```bash
mv src/test/java/com/wpanther/orchestrator/config/SecurityConfigTest.java \
   src/test/java/com/wpanther/orchestrator/infrastructure/config/security/SecurityConfigTest.java
rmdir src/test/java/com/wpanther/orchestrator/config
```

Update any import in `SecurityConfigTest.java` that references old config paths.

**Step 5: Compile and test**

```bash
mvn test -q
```

Expected: BUILD SUCCESS.

**Step 6: Verify `config/` is fully gone**

```bash
find src/ -path "*/orchestrator/config*" -type f
```

Expected: no output.

**Step 7: Commit**

```bash
git add src/main/java/com/wpanther/orchestrator/infrastructure/config/ \
        src/test/java/com/wpanther/orchestrator/infrastructure/config/
git rm -r src/main/java/com/wpanther/orchestrator/config/ 2>/dev/null || true
git rm -r src/test/java/com/wpanther/orchestrator/config/ 2>/dev/null || true
git add -u
git commit -m "Move config/ to infrastructure/config/ with concern-based sub-packages"
```

---

## Phase 3 — Move `adapter/` → `infrastructure/adapter/`

### Task 5: Move all adapters under `infrastructure/adapter/`, rename `web/` to `rest/`, delete deprecated `SagaCommandProducer`

This is the largest move. All five adapter sub-packages move. `adapter/in/web/` is renamed `rest/`. `SagaCommandProducer` is deleted before the move.

**Files to move:**
- `adapter/in/messaging/` → `infrastructure/adapter/in/messaging/` (4 files)
- `adapter/in/security/` → `infrastructure/adapter/in/security/` (3 files)
- `adapter/in/web/` → `infrastructure/adapter/in/rest/` (2 files)
- `adapter/out/messaging/` → `infrastructure/adapter/out/messaging/` (2 files, after deleting `SagaCommandProducer`)
- `adapter/out/persistence/` → `infrastructure/adapter/out/persistence/` (7 files + `outbox/` 3 files)

**Step 1: Delete `SagaCommandProducer` before moving**

Check for any remaining injection sites:
```bash
grep -rl "SagaCommandProducer" src/main/
```

If any class still injects `SagaCommandProducer` (not `SagaCommandPublisher`), update it first to use `SagaCommandPublisher`.

Then delete:
```bash
git rm src/main/java/com/wpanther/orchestrator/adapter/out/messaging/SagaCommandProducer.java
```

**Step 2: Create target directories**

```bash
mkdir -p src/main/java/com/wpanther/orchestrator/infrastructure/adapter/in/messaging
mkdir -p src/main/java/com/wpanther/orchestrator/infrastructure/adapter/in/security
mkdir -p src/main/java/com/wpanther/orchestrator/infrastructure/adapter/in/rest
mkdir -p src/main/java/com/wpanther/orchestrator/infrastructure/adapter/out/messaging
mkdir -p src/main/java/com/wpanther/orchestrator/infrastructure/adapter/out/persistence/outbox
```

**Step 3: Move `adapter/in/messaging/` — update package declarations**

For all four files (`SagaReplyConsumer`, `StartSagaCommandConsumer`, `StartSagaCommand`, `ConcreteSagaReply`):
```java
// Before:
package com.wpanther.orchestrator.adapter.in.messaging;
// After:
package com.wpanther.orchestrator.infrastructure.adapter.in.messaging;
```
```bash
for f in SagaReplyConsumer StartSagaCommandConsumer StartSagaCommand ConcreteSagaReply; do
  mv "src/main/java/com/wpanther/orchestrator/adapter/in/messaging/${f}.java" \
     "src/main/java/com/wpanther/orchestrator/infrastructure/adapter/in/messaging/${f}.java"
done
rmdir src/main/java/com/wpanther/orchestrator/adapter/in/messaging
```

**Step 4: Move `adapter/in/security/` — update package declarations**

For all three files (`JwtAuthenticationFilter`, `JwtUserDetailsService`, `JwtTokenProvider`):
```java
// Before:
package com.wpanther.orchestrator.adapter.in.security;
// After:
package com.wpanther.orchestrator.infrastructure.adapter.in.security;
```
```bash
for f in JwtAuthenticationFilter JwtUserDetailsService JwtTokenProvider; do
  mv "src/main/java/com/wpanther/orchestrator/adapter/in/security/${f}.java" \
     "src/main/java/com/wpanther/orchestrator/infrastructure/adapter/in/security/${f}.java"
done
rmdir src/main/java/com/wpanther/orchestrator/adapter/in/security
```

**Step 5: Move `adapter/in/web/` → `infrastructure/adapter/in/rest/` — update package declarations**

For both files (`OrchestratorController`, `AuthController`):
```java
// Before:
package com.wpanther.orchestrator.adapter.in.web;
// After:
package com.wpanther.orchestrator.infrastructure.adapter.in.rest;
```
```bash
for f in OrchestratorController AuthController; do
  mv "src/main/java/com/wpanther/orchestrator/adapter/in/web/${f}.java" \
     "src/main/java/com/wpanther/orchestrator/infrastructure/adapter/in/rest/${f}.java"
done
rmdir src/main/java/com/wpanther/orchestrator/adapter/in/web
rmdir src/main/java/com/wpanther/orchestrator/adapter/in
```

**Step 6: Move `adapter/out/messaging/` — update package declarations**

For both remaining files (`SagaCommandPublisher`, `SagaEventPublisher`):
```java
// Before:
package com.wpanther.orchestrator.adapter.out.messaging;
// After:
package com.wpanther.orchestrator.infrastructure.adapter.out.messaging;
```
```bash
for f in SagaCommandPublisher SagaEventPublisher; do
  mv "src/main/java/com/wpanther/orchestrator/adapter/out/messaging/${f}.java" \
     "src/main/java/com/wpanther/orchestrator/infrastructure/adapter/out/messaging/${f}.java"
done
rmdir src/main/java/com/wpanther/orchestrator/adapter/out/messaging
```

**Step 7: Move `adapter/out/persistence/` — update package declarations**

For the 7 main files:
```java
// Before:
package com.wpanther.orchestrator.adapter.out.persistence;
// After:
package com.wpanther.orchestrator.infrastructure.adapter.out.persistence;
```
```bash
for f in SagaInstanceEntity SagaCommandEntity SagaInstanceMapper \
          JpaSagaInstanceRepository JpaSagaCommandRepository \
          SpringDataSagaInstanceRepository SpringDataSagaCommandRepository; do
  mv "src/main/java/com/wpanther/orchestrator/adapter/out/persistence/${f}.java" \
     "src/main/java/com/wpanther/orchestrator/infrastructure/adapter/out/persistence/${f}.java"
done
```

For the 3 outbox files:
```java
// Before:
package com.wpanther.orchestrator.adapter.out.persistence.outbox;
// After:
package com.wpanther.orchestrator.infrastructure.adapter.out.persistence.outbox;
```
```bash
for f in OutboxEventEntity JpaOrchestratorOutboxRepository SpringDataOrchestratorOutboxRepository; do
  mv "src/main/java/com/wpanther/orchestrator/adapter/out/persistence/outbox/${f}.java" \
     "src/main/java/com/wpanther/orchestrator/infrastructure/adapter/out/persistence/outbox/${f}.java"
done
rmdir src/main/java/com/wpanther/orchestrator/adapter/out/persistence/outbox
rmdir src/main/java/com/wpanther/orchestrator/adapter/out/persistence
rmdir src/main/java/com/wpanther/orchestrator/adapter/out
rmdir src/main/java/com/wpanther/orchestrator/adapter 2>/dev/null || true
```

**Step 8: Find and update all cross-package importers**

```bash
grep -rl "orchestrator.adapter\." src/main/
```

Update each found file using the mapping:
```java
// Before → After:
com.wpanther.orchestrator.adapter.in.messaging.*   → com.wpanther.orchestrator.infrastructure.adapter.in.messaging.*
com.wpanther.orchestrator.adapter.in.security.*    → com.wpanther.orchestrator.infrastructure.adapter.in.security.*
com.wpanther.orchestrator.adapter.in.web.*         → com.wpanther.orchestrator.infrastructure.adapter.in.rest.*
com.wpanther.orchestrator.adapter.out.messaging.*  → com.wpanther.orchestrator.infrastructure.adapter.out.messaging.*
com.wpanther.orchestrator.adapter.out.persistence.* → com.wpanther.orchestrator.infrastructure.adapter.out.persistence.*
```

**Step 9: Compile**

```bash
mvn compile -q
```

Expected: BUILD SUCCESS. If not, run:
```bash
grep -r "orchestrator.adapter\." src/main/ | grep -v ".class"
```
Fix any remaining old imports before continuing.

**Step 10: Run tests**

```bash
mvn test -q
```

Expected: BUILD SUCCESS.

**Step 11: Verify `adapter/` is fully gone**

```bash
find src/main/ -path "*/orchestrator/adapter*" -type f
```

Expected: no output.

**Step 12: Commit**

```bash
git add src/main/java/com/wpanther/orchestrator/infrastructure/adapter/
git rm -r src/main/java/com/wpanther/orchestrator/adapter/ 2>/dev/null || true
git add -u
git commit -m "Move adapter/ to infrastructure/adapter/, rename web/ to rest/, delete deprecated SagaCommandProducer"
```

---

## Phase 4 — Test Relocation and JaCoCo Updates

### Task 6: Relocate test files to mirror new structure

**Files to move:**

| Old path | New path |
|---|---|
| `adapter/in/messaging/SagaReplyConsumerTest.java` | `infrastructure/adapter/in/messaging/` |
| `adapter/in/messaging/StartSagaCommandConsumerTest.java` | `infrastructure/adapter/in/messaging/` |
| `adapter/in/messaging/ConcreteSagaReplyTest.java` | `infrastructure/adapter/in/messaging/` |
| `adapter/in/security/JwtTokenProviderTest.java` | `infrastructure/adapter/in/security/` |
| `adapter/in/security/JwtAuthenticationFilterTest.java` | `infrastructure/adapter/in/security/` |
| `adapter/in/security/JwtUserDetailsServiceTest.java` | `infrastructure/adapter/in/security/` |
| `adapter/in/web/AuthControllerTest.java` | `infrastructure/adapter/in/rest/` |
| `adapter/out/messaging/SagaCommandPublisherTest.java` | `infrastructure/adapter/out/messaging/` |
| `adapter/out/messaging/SagaEventPublisherTest.java` | `infrastructure/adapter/out/messaging/` |

**NOT moved:** `integration/` (completely untouched).

**Step 1: Create target test directories**

```bash
mkdir -p src/test/java/com/wpanther/orchestrator/infrastructure/adapter/in/messaging
mkdir -p src/test/java/com/wpanther/orchestrator/infrastructure/adapter/in/security
mkdir -p src/test/java/com/wpanther/orchestrator/infrastructure/adapter/in/rest
mkdir -p src/test/java/com/wpanther/orchestrator/infrastructure/adapter/out/messaging
```

**Step 2: Move messaging tests**

```bash
for f in SagaReplyConsumerTest StartSagaCommandConsumerTest ConcreteSagaReplyTest; do
  mv "src/test/java/com/wpanther/orchestrator/adapter/in/messaging/${f}.java" \
     "src/test/java/com/wpanther/orchestrator/infrastructure/adapter/in/messaging/${f}.java"
done
rmdir src/test/java/com/wpanther/orchestrator/adapter/in/messaging
```

Update package declaration in each moved file:
```java
// Before:
package com.wpanther.orchestrator.adapter.in.messaging;
// After:
package com.wpanther.orchestrator.infrastructure.adapter.in.messaging;
```

**Step 3: Move security tests**

```bash
for f in JwtTokenProviderTest JwtAuthenticationFilterTest JwtUserDetailsServiceTest; do
  mv "src/test/java/com/wpanther/orchestrator/adapter/in/security/${f}.java" \
     "src/test/java/com/wpanther/orchestrator/infrastructure/adapter/in/security/${f}.java"
done
rmdir src/test/java/com/wpanther/orchestrator/adapter/in/security
rmdir src/test/java/com/wpanther/orchestrator/adapter/in
```

Update package declaration in each moved file:
```java
// Before:
package com.wpanther.orchestrator.adapter.in.security;
// After:
package com.wpanther.orchestrator.infrastructure.adapter.in.security;
```

**Step 4: Move web → rest test**

```bash
mv src/test/java/com/wpanther/orchestrator/adapter/in/web/AuthControllerTest.java \
   src/test/java/com/wpanther/orchestrator/infrastructure/adapter/in/rest/AuthControllerTest.java
rmdir src/test/java/com/wpanther/orchestrator/adapter/in/web 2>/dev/null || true
```

Update package declaration:
```java
// Before:
package com.wpanther.orchestrator.adapter.in.web;
// After:
package com.wpanther.orchestrator.infrastructure.adapter.in.rest;
```

**Step 5: Move out/messaging tests**

```bash
for f in SagaCommandPublisherTest SagaEventPublisherTest; do
  mv "src/test/java/com/wpanther/orchestrator/adapter/out/messaging/${f}.java" \
     "src/test/java/com/wpanther/orchestrator/infrastructure/adapter/out/messaging/${f}.java"
done
rmdir src/test/java/com/wpanther/orchestrator/adapter/out/messaging
rmdir src/test/java/com/wpanther/orchestrator/adapter/out
rmdir src/test/java/com/wpanther/orchestrator/adapter 2>/dev/null || true
```

Update package declaration in each moved file:
```java
// Before:
package com.wpanther.orchestrator.adapter.out.messaging;
// After:
package com.wpanther.orchestrator.infrastructure.adapter.out.messaging;
```

**Step 6: Update imports in moved test files**

Find any remaining old-path imports in test files:
```bash
grep -rl "orchestrator\.\(adapter\|config\)\." src/test/
```

Apply the same import mapping from Task 5 Step 8 to each found file.

**Step 7: Run tests to verify**

```bash
mvn test -q
```

Expected: BUILD SUCCESS, same test count as before.

### Task 7: Update JaCoCo exclusion patterns in `pom.xml`

**File:** `pom.xml`

**Step 1: Open `pom.xml` and find the `<excludes>` block under `jacoco-maven-plugin`**

**Step 2: Replace exclusion patterns**

Apply these replacements (the exact strings may vary slightly — match by intent):

| Old pattern | New pattern |
|---|---|
| `com/wpanther/orchestrator/config/**` | `com/wpanther/orchestrator/infrastructure/config/**` |
| `com/wpanther/orchestrator/adapter/out/persistence/SagaInstanceEntity.class` | `com/wpanther/orchestrator/infrastructure/adapter/out/persistence/SagaInstanceEntity.class` |
| `com/wpanther/orchestrator/adapter/out/persistence/SagaCommandEntity.class` | `com/wpanther/orchestrator/infrastructure/adapter/out/persistence/SagaCommandEntity.class` |
| `com/wpanther/orchestrator/adapter/out/persistence/outbox/OutboxEventEntity.class` | `com/wpanther/orchestrator/infrastructure/adapter/out/persistence/outbox/OutboxEventEntity.class` |
| `com/wpanther/orchestrator/adapter/out/persistence/SpringData*.class` | `com/wpanther/orchestrator/infrastructure/adapter/out/persistence/SpringData*.class` |
| `com/wpanther/orchestrator/adapter/out/persistence/outbox/SpringData*.class` | `com/wpanther/orchestrator/infrastructure/adapter/out/persistence/outbox/SpringData*.class` |
| `com/wpanther/orchestrator/adapter/out/persistence/Jpa*.class` | `com/wpanther/orchestrator/infrastructure/adapter/out/persistence/Jpa*.class` |
| `com/wpanther/orchestrator/adapter/out/persistence/outbox/Jpa*.class` | `com/wpanther/orchestrator/infrastructure/adapter/out/persistence/outbox/Jpa*.class` |
| `com/wpanther/orchestrator/adapter/out/persistence/SagaInstanceMapper*.class` | `com/wpanther/orchestrator/infrastructure/adapter/out/persistence/SagaInstanceMapper*.class` |
| `com/wpanther/orchestrator/adapter/out/messaging/SagaCommandProducer.class` | **REMOVE** (class deleted) |
| `com/wpanther/orchestrator/port/in/**` | **REMOVE** (covered by new use-case tests) |
| `com/wpanther/orchestrator/port/out/**` | `com/wpanther/orchestrator/domain/repository/**` |

**Step 3: Run full coverage verification**

```bash
mvn verify -q
```

Expected: BUILD SUCCESS with line coverage ≥ 80%.

If coverage drops below 80%, check:
1. Do moved test files compile? (`mvn test-compile -q`)
2. Are any test imports still pointing at old package paths?

**Step 4: Commit**

```bash
git add src/test/java/com/wpanther/orchestrator/infrastructure/ \
        pom.xml
git rm -r src/test/java/com/wpanther/orchestrator/adapter/ 2>/dev/null || true
git add -u
git commit -m "Relocate test classes to infrastructure/adapter/, update JaCoCo exclusions"
```

---

## Phase 5 — Final Verification

### Task 8: Confirm clean state

**Step 1: No old package references remain in main source**

```bash
grep -r "com.wpanther.orchestrator.infrastructure\b" src/main/ | grep -c "\.java:" || true
grep -r "com.wpanther.orchestrator.adapter\." src/main/ | grep "\.java:" && echo "FOUND OLD REFS" || echo "CLEAN"
grep -r "com.wpanther.orchestrator.config\." src/main/ | grep "\.java:" && echo "FOUND OLD REFS" || echo "CLEAN"
grep -r "com.wpanther.orchestrator.port\." src/main/ | grep "\.java:" && echo "FOUND OLD REFS" || echo "CLEAN"
```

Expected: all `CLEAN`.

**Step 2: No old directories remain**

```bash
find src/ -path "*/orchestrator/adapter*" -type f
find src/ -path "*/orchestrator/port*" -type f
find src/ -path "*/orchestrator/config*" -name "*.java" ! -path "*/infrastructure/*"
```

Expected: no output from any command.

**Step 3: Confirm new directories exist and contain files**

```bash
find src/main/ -path "*/orchestrator/domain/repository*" -type f
find src/main/ -path "*/orchestrator/infrastructure/adapter*" -type f | wc -l
find src/main/ -path "*/orchestrator/infrastructure/config*" -type f | wc -l
```

Expected: repository interfaces present; adapter count ≥ 15; config count ≥ 4.

**Step 4: Run full test suite with coverage**

```bash
mvn verify
```

Expected: BUILD SUCCESS, line coverage ≥ 80%.

**Step 5: Done**

All production classes and test classes are in their canonical hexagonal locations. `infrastructure/` fully contains all adapters and config. `domain/repository/` holds the output port interfaces. `application/usecase/` holds the four focused use-case interfaces. The `port/`, `adapter/`, and `config/` root packages are gone.
