package com.wpanther.orchestrator.infrastructure.adapter.out.persistence;

import com.wpanther.orchestrator.domain.model.DocumentMetadata;
import com.wpanther.orchestrator.domain.model.SagaInstance;
import com.wpanther.orchestrator.domain.model.enums.DocumentType;
import com.wpanther.saga.domain.enums.SagaStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JpaSagaInstanceRepository Tests")
class JpaSagaInstanceRepositoryTest {

    @Mock
    private SpringDataSagaInstanceRepository springRepository;

    @Mock
    private SagaInstanceMapper mapper;

    @InjectMocks
    private JpaSagaInstanceRepository repository;

    private SagaInstanceEntity entity1;
    private SagaInstanceEntity entity2;
    private SagaInstance domain1;
    private SagaInstance domain2;

    @BeforeEach
    void setUp() {
        DocumentMetadata metadata = DocumentMetadata.builder()
                .xmlContent("<xml/>")
                .build();

        domain1 = SagaInstance.create(DocumentType.INVOICE, "doc-001", metadata);
        domain1.start();

        domain2 = SagaInstance.create(DocumentType.TAX_INVOICE, "doc-002", metadata);
        domain2.start();

        entity1 = SagaInstanceEntity.builder()
                .id(domain1.getId())
                .documentType(DocumentType.INVOICE)
                .documentId("doc-001")
                .status(SagaStatus.IN_PROGRESS)
                .build();

        entity2 = SagaInstanceEntity.builder()
                .id(domain2.getId())
                .documentType(DocumentType.TAX_INVOICE)
                .documentId("doc-002")
                .status(SagaStatus.IN_PROGRESS)
                .build();
    }

    @Nested
    @DisplayName("findByIdIn()")
    class FindByIdInTests {

        @Test
        void withMultipleIds_returnsBatchLoadedSagas() {
            List<String> ids = List.of(domain1.getId(), domain2.getId());

            when(springRepository.findAllById(ids)).thenReturn(List.of(entity1, entity2));
            when(mapper.toDomainBatch(any())).thenReturn(List.of(domain1, domain2));

            List<SagaInstance> result = repository.findByIdIn(ids);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getId()).isEqualTo(domain1.getId());
            assertThat(result.get(1).getId()).isEqualTo(domain2.getId());

            verify(springRepository).findAllById(ids);
            verify(mapper).toDomainBatch(List.of(entity1, entity2));
        }

        @Test
        void withEmptyList_returnsEmptyList() {
            List<SagaInstance> result = repository.findByIdIn(List.of());

            assertThat(result).isEmpty();

            verify(springRepository, never()).findAllById(any());
            verify(mapper, never()).toDomainBatch(any());
        }

        @Test
        void withNullList_returnsEmptyList() {
            List<SagaInstance> result = repository.findByIdIn(null);

            assertThat(result).isEmpty();

            verify(springRepository, never()).findAllById(any());
            verify(mapper, never()).toDomainBatch(any());
        }

        @Test
        void withNonExistentIds_returnsEmptyList() {
            List<String> ids = List.of("non-existent-1", "non-existent-2");

            when(springRepository.findAllById(ids)).thenReturn(List.of());
            when(mapper.toDomainBatch(any())).thenReturn(List.of());

            List<SagaInstance> result = repository.findByIdIn(ids);

            assertThat(result).isEmpty();

            verify(springRepository).findAllById(ids);
            verify(mapper).toDomainBatch(List.of());
        }

        @Test
        void withPartialExistingIds_returnsOnlyFoundSagas() {
            List<String> ids = List.of(domain1.getId(), "non-existent");

            when(springRepository.findAllById(ids)).thenReturn(List.of(entity1));
            when(mapper.toDomainBatch(any())).thenReturn(List.of(domain1));

            List<SagaInstance> result = repository.findByIdIn(ids);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(domain1.getId());
        }

        @Test
        void usesToDomainBatch_forSingleId() {
            List<String> ids = List.of(domain1.getId());

            when(springRepository.findAllById(ids)).thenReturn(List.of(entity1));
            when(mapper.toDomainBatch(any())).thenReturn(List.of(domain1));

            repository.findByIdIn(ids);

            // Verify toDomainBatch is used (not toDomain)
            verify(mapper).toDomainBatch(List.of(entity1));
            verify(mapper, never()).toDomain(any());
        }
    }

    @Nested
    @DisplayName("findById() - for comparison")
    class FindByIdTests {

        @Test
        void usesToDomain_notToDomainBatch() {
            when(springRepository.findById(domain1.getId()))
                    .thenReturn(Optional.of(entity1));
            when(mapper.toDomain(entity1)).thenReturn(domain1);

            Optional<SagaInstance> result = repository.findById(domain1.getId());

            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(domain1.getId());

            // Verify toDomain is used (not toDomainBatch)
            verify(mapper).toDomain(entity1);
            verify(mapper, never()).toDomainBatch(any());
        }
    }
}
