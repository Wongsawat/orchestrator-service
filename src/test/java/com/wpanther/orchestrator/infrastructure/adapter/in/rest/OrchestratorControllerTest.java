package com.wpanther.orchestrator.infrastructure.adapter.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.orchestrator.application.usecase.HandleSagaReplyUseCase;
import com.wpanther.orchestrator.application.usecase.QuerySagaUseCase;
import com.wpanther.orchestrator.application.usecase.StartSagaUseCase;
import com.wpanther.orchestrator.domain.model.DocumentMetadata;
import com.wpanther.orchestrator.domain.model.SagaInstance;
import com.wpanther.orchestrator.domain.model.enums.DocumentType;
import com.wpanther.saga.domain.enums.SagaStatus;
import com.wpanther.saga.domain.enums.SagaStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for OrchestratorController using MockMvc in standalone mode.
 * Security annotations (@PreAuthorize) are not enforced in standalone mode.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrchestratorController Tests")
class OrchestratorControllerTest {

    @Mock private StartSagaUseCase startSagaUseCase;
    @Mock private QuerySagaUseCase querySagaUseCase;
    @Mock private HandleSagaReplyUseCase handleSagaReplyUseCase;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        OrchestratorController controller = new OrchestratorController(startSagaUseCase, querySagaUseCase, handleSagaReplyUseCase);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();
    }

    private SagaInstance createSaga(DocumentType type, String docId) {
        SagaInstance saga = SagaInstance.create(type, docId,
                DocumentMetadata.builder().xmlContent("<xml/>").build());
        saga.start();
        return saga;
    }

    @Nested
    @DisplayName("GET /api/saga/health")
    class HealthTests {

        @Test
        @DisplayName("returns UP status")
        void returnsUpStatus() throws Exception {
            mockMvc.perform(get("/api/saga/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"));
        }
    }

    @Nested
    @DisplayName("POST /api/saga/start")
    class StartSagaTests {

        @Test
        @DisplayName("returns 201 Created with saga response")
        void returnsCreatedSaga() throws Exception {
            SagaInstance saga = createSaga(DocumentType.INVOICE, "doc-001");
            when(startSagaUseCase.startSaga(any(DocumentType.class), anyString(), any(DocumentMetadata.class))).thenReturn(saga);

            String requestBody = """
                    {
                        "documentType": "INVOICE",
                        "documentId": "doc-001",
                        "xmlContent": "<xml/>"
                    }
                    """;

            mockMvc.perform(post("/api/saga/start")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.documentType").value("INVOICE"))
                    .andExpect(jsonPath("$.documentId").value("doc-001"))
                    .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        }
    }

    @Nested
    @DisplayName("GET /api/saga/{sagaId}")
    class GetSagaTests {

        @Test
        @DisplayName("returns saga by ID")
        void returnsSagaById() throws Exception {
            SagaInstance saga = createSaga(DocumentType.TAX_INVOICE, "doc-002");
            when(querySagaUseCase.getSagaInstance("saga-001")).thenReturn(saga);

            mockMvc.perform(get("/api/saga/saga-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.documentType").value("TAX_INVOICE"))
                    .andExpect(jsonPath("$.documentId").value("doc-002"));
        }
    }

    @Nested
    @DisplayName("GET /api/saga/active")
    class GetActiveSagasTests {

        @Test
        @DisplayName("returns list of active sagas")
        void returnsActiveSagas() throws Exception {
            SagaInstance saga1 = createSaga(DocumentType.INVOICE, "doc-001");
            SagaInstance saga2 = createSaga(DocumentType.TAX_INVOICE, "doc-002");
            when(querySagaUseCase.getActiveSagas()).thenReturn(List.of(saga1, saga2));

            mockMvc.perform(get("/api/saga/active"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        @DisplayName("returns empty list when no active sagas")
        void returnsEmptyList() throws Exception {
            when(querySagaUseCase.getActiveSagas()).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/saga/active"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    @Nested
    @DisplayName("GET /api/saga/document")
    class GetSagasForDocumentTests {

        @Test
        @DisplayName("returns sagas for a specific document")
        void returnsSagasForDocument() throws Exception {
            SagaInstance saga = createSaga(DocumentType.INVOICE, "doc-001");
            when(querySagaUseCase.getSagasForDocument(DocumentType.INVOICE, "doc-001"))
                    .thenReturn(List.of(saga));

            mockMvc.perform(get("/api/saga/document")
                            .param("documentType", "INVOICE")
                            .param("documentId", "doc-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
        }
    }

    @Nested
    @DisplayName("POST /api/saga/{sagaId}/advance")
    class AdvanceSagaTests {

        @Test
        @DisplayName("returns updated saga after advance")
        void returnsUpdatedSaga() throws Exception {
            SagaInstance saga = createSaga(DocumentType.INVOICE, "doc-001");
            when(handleSagaReplyUseCase.advanceSaga("saga-001")).thenReturn(saga);

            mockMvc.perform(post("/api/saga/saga-001/advance"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.documentId").value("doc-001"));
        }
    }

    @Nested
    @DisplayName("POST /api/saga/{sagaId}/retry")
    class RetrySagaTests {

        @Test
        @DisplayName("returns updated saga after retry")
        void returnsUpdatedSaga() throws Exception {
            SagaInstance saga = createSaga(DocumentType.INVOICE, "doc-001");
            when(handleSagaReplyUseCase.retryStep("saga-001")).thenReturn(saga);

            mockMvc.perform(post("/api/saga/saga-001/retry"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.documentId").value("doc-001"));
        }
    }

    @Nested
    @DisplayName("GET /api/saga/status/{status}")
    class GetSagasByStatusTests {

        @Test
        @DisplayName("returns active sagas for IN_PROGRESS status")
        void returnsActiveSagasForInProgress() throws Exception {
            SagaInstance saga = createSaga(DocumentType.INVOICE, "doc-001");
            when(querySagaUseCase.getActiveSagas()).thenReturn(List.of(saga));

            mockMvc.perform(get("/api/saga/status/IN_PROGRESS"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
        }

        @Test
        @DisplayName("returns empty list for COMPLETED status")
        void returnsEmptyForCompleted() throws Exception {
            mockMvc.perform(get("/api/saga/status/COMPLETED"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("returns empty list for FAILED status")
        void returnsEmptyForFailed() throws Exception {
            mockMvc.perform(get("/api/saga/status/FAILED"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }
}
