package com.wpanther.orchestrator.infrastructure.persistence;

import com.wpanther.orchestrator.domain.model.SagaInstance;
import com.wpanther.orchestrator.domain.model.enums.DocumentType;
import com.wpanther.orchestrator.domain.repository.SagaInstanceRepository;
import com.wpanther.saga.domain.enums.SagaStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA implementation of SagaInstanceRepository.
 */
@Repository
@RequiredArgsConstructor
public class JpaSagaInstanceRepository implements SagaInstanceRepository {

    private final SpringDataSagaInstanceRepository springRepository;
    private final SagaInstanceMapper mapper;

    @Override
    public SagaInstance save(SagaInstance instance) {
        SagaInstanceEntity entity = mapper.toEntity(instance);
        SagaInstanceEntity saved = springRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<SagaInstance> findById(String id) {
        return springRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<SagaInstance> findByDocumentTypeAndDocumentId(DocumentType documentType, String documentId) {
        return springRepository.findByDocumentTypeAndDocumentId(documentType, documentId)
                .map(mapper::toDomain);
    }

    @Override
    public List<SagaInstance> findByStatus(SagaStatus status) {
        return springRepository.findByStatus(status).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<SagaInstance> findByDocumentType(DocumentType documentType) {
        return springRepository.findByDocumentType(documentType).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<SagaInstance> findTimeoutInstances(int timeoutSeconds) {
        Instant timeout = Instant.now().minusSeconds(timeoutSeconds);
        return springRepository.findByStatusInAndUpdatedAtBefore(
                        List.of(SagaStatus.IN_PROGRESS, SagaStatus.STARTED),
                        timeout
                ).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public void deleteById(String id) {
        springRepository.deleteById(id);
    }

    @Override
    public long count() {
        return springRepository.count();
    }
}
