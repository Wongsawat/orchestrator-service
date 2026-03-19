package com.wpanther.orchestrator.infrastructure.scheduling;

import com.wpanther.orchestrator.domain.model.DocumentMetadata;
import com.wpanther.orchestrator.domain.model.SagaInstance;
import com.wpanther.orchestrator.domain.model.enums.DocumentType;
import com.wpanther.orchestrator.domain.repository.SagaInstanceRepository;
import com.wpanther.orchestrator.infrastructure.config.SagaProperties;
import com.wpanther.saga.domain.enums.SagaStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SagaTimeoutChecker Tests")
class SagaTimeoutCheckerTest {

    @Mock
    private SagaInstanceRepository sagaRepository;

    @Mock
    private SagaProperties sagaProperties;

    private SagaTimeoutChecker timeoutChecker;

    @BeforeEach
    void setUp() {
        timeoutChecker = new SagaTimeoutChecker(sagaRepository, sagaProperties);
    }

    @Nested
    @DisplayName("checkExpiredSagas()")
    class CheckExpiredSagasTests {

        @Test
        void whenNoExpiredSags_doesNothing() {
            when(sagaProperties.isTimeoutCheckEnabled()).thenReturn(true);
            when(sagaProperties.getTimeoutMinutes()).thenReturn(30);
            when(sagaRepository.findTimeoutInstances(1800)).thenReturn(List.of());

            timeoutChecker.checkExpiredSagas();

            verify(sagaRepository).findTimeoutInstances(1800);
            verify(sagaRepository, never()).save(any());
        }

        @Test
        void whenExpiredSagasExist_marksThemAsFailed() {
            SagaInstance expiredSaga = createExpiredSaga();
            List<SagaInstance> expiredSagas = List.of(expiredSaga);

            when(sagaProperties.isTimeoutCheckEnabled()).thenReturn(true);
            when(sagaProperties.getTimeoutMinutes()).thenReturn(30);
            when(sagaRepository.findTimeoutInstances(1800)).thenReturn(expiredSagas);
            when(sagaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            timeoutChecker.checkExpiredSagas();

            ArgumentCaptor<SagaInstance> savedCaptor = ArgumentCaptor.forClass(SagaInstance.class);
            verify(sagaRepository).save(savedCaptor.capture());

            SagaInstance saved = savedCaptor.getValue();
            assertThat(saved.getStatus()).isEqualTo(SagaStatus.FAILED);
            assertThat(saved.getErrorMessage()).contains("timed out after 30 minutes");
        }

        @Test
        void whenMultipleExpiredSags_marksAllAsFailed() {
            SagaInstance saga1 = createExpiredSaga("saga-1");
            SagaInstance saga2 = createExpiredSaga("saga-2");
            SagaInstance saga3 = createExpiredSaga("saga-3");
            List<SagaInstance> expiredSagas = List.of(saga1, saga2, saga3);

            when(sagaProperties.isTimeoutCheckEnabled()).thenReturn(true);
            when(sagaProperties.getTimeoutMinutes()).thenReturn(30);
            when(sagaRepository.findTimeoutInstances(1800)).thenReturn(expiredSagas);
            when(sagaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            timeoutChecker.checkExpiredSagas();

            verify(sagaRepository, times(3)).save(any());
        }

        @Test
        void whenTimeoutCheckDisabled_doesNothing() {
            when(sagaProperties.isTimeoutCheckEnabled()).thenReturn(false);

            timeoutChecker.checkExpiredSagas();

            verify(sagaRepository, never()).findTimeoutInstances(anyInt());
            verify(sagaRepository, never()).save(any());
        }

        @Test
        void whenTimeoutIsZero_doesNothing() {
            when(sagaProperties.isTimeoutCheckEnabled()).thenReturn(true);
            when(sagaProperties.getTimeoutMinutes()).thenReturn(0);

            timeoutChecker.checkExpiredSagas();

            verify(sagaRepository, never()).findTimeoutInstances(anyInt());
        }

        @Test
        void whenTimeoutIsNegative_doesNothing() {
            when(sagaProperties.isTimeoutCheckEnabled()).thenReturn(true);
            when(sagaProperties.getTimeoutMinutes()).thenReturn(-1);

            timeoutChecker.checkExpiredSagas();

            verify(sagaRepository, never()).findTimeoutInstances(anyInt());
        }

        @Test
        void whenRepositoryThrowsException_logsError() {
            when(sagaProperties.isTimeoutCheckEnabled()).thenReturn(true);
            when(sagaProperties.getTimeoutMinutes()).thenReturn(30);
            when(sagaRepository.findTimeoutInstances(1800))
                    .thenThrow(new RuntimeException("Database error"));

            // Should not throw exception
            assertThatCode(() -> timeoutChecker.checkExpiredSagas())
                    .doesNotThrowAnyException();

            verify(sagaRepository, never()).save(any());
        }

        @Test
        void whenSaveFails_continuesWithNextSaga() {
            SagaInstance saga1 = createExpiredSaga("saga-1");
            SagaInstance saga2 = createExpiredSaga("saga-2");
            List<SagaInstance> expiredSagas = new ArrayList<>(List.of(saga1, saga2));

            when(sagaProperties.isTimeoutCheckEnabled()).thenReturn(true);
            when(sagaProperties.getTimeoutMinutes()).thenReturn(30);
            when(sagaRepository.findTimeoutInstances(1800)).thenReturn(expiredSagas);
            when(sagaRepository.save(any()))
                    .thenThrow(new RuntimeException("Save error"))
                    .thenAnswer(i -> i.getArgument(0));

            timeoutChecker.checkExpiredSagas();

            // Both sagas were attempted, even though first failed
            verify(sagaRepository, times(2)).save(any());
        }
    }

    private SagaInstance createExpiredSaga() {
        return createExpiredSaga("expired-saga-1");
    }

    private SagaInstance createExpiredSaga(String id) {
        DocumentMetadata metadata = DocumentMetadata.builder()
                .xmlContent("<xml/>")
                .build();
        SagaInstance saga = SagaInstance.create(DocumentType.INVOICE, "doc-001", metadata);
        saga.start();

        // Use reflection to set the ID (normally set by persistence layer)
        try {
            var field = SagaInstance.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(saga, id);
        } catch (Exception e) {
            // Ignore for test purposes
        }

        // Set updatedAt to 31 minutes ago to simulate expiration
        Instant past = Instant.now().minusSeconds(31 * 60);
        saga.setUpdatedAt(past);

        return saga;
    }
}
