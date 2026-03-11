package com.wpanther.orchestrator.adapter.out.persistence;

import com.wpanther.orchestrator.domain.model.SagaCommandRecord;
import com.wpanther.orchestrator.domain.repository.SagaCommandRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA implementation of SagaCommandRecordRepository.
 */
@Repository
@RequiredArgsConstructor
public class JpaSagaCommandRepository implements SagaCommandRecordRepository {

    private final SpringDataSagaCommandRepository springRepository;

    @Override
    public SagaCommandRecord save(SagaCommandRecord command) {
        SagaCommandEntity entity = toEntity(command);
        SagaCommandEntity saved = springRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<SagaCommandRecord> findById(String id) {
        return springRepository.findById(id)
                .map(this::toDomain);
    }

    @Override
    public List<SagaCommandRecord> findBySagaId(String sagaId) {
        return springRepository.findBySagaIdOrderByCreatedAtAsc(sagaId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<SagaCommandRecord> findPendingCommands() {
        return springRepository.findByStatus(SagaCommandRecord.CommandStatus.PENDING).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<SagaCommandRecord> findTimedOutCommands(int timeoutSeconds) {
        Instant timeout = Instant.now().minusSeconds(timeoutSeconds);
        return springRepository.findByStatusAndSentAtBefore(
                SagaCommandRecord.CommandStatus.SENT,
                timeout
        ).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void deleteBySagaId(String sagaId) {
        springRepository.deleteBySagaId(sagaId);
    }

    private SagaCommandEntity toEntity(SagaCommandRecord domain) {
        return SagaCommandEntity.builder()
                .id(domain.getId())
                .sagaId(domain.getSagaId())
                .commandType(domain.getCommandType())
                .targetStep(domain.getTargetStep())
                .payload(domain.getPayload())
                .status(domain.getStatus())
                .createdAt(domain.getCreatedAt())
                .sentAt(domain.getSentAt())
                .completedAt(domain.getCompletedAt())
                .errorMessage(domain.getErrorMessage())
                .correlationId(domain.getCorrelationId())
                .build();
    }

    private SagaCommandRecord toDomain(SagaCommandEntity entity) {
        return SagaCommandRecord.builder()
                .id(entity.getId())
                .sagaId(entity.getSagaId())
                .commandType(entity.getCommandType())
                .targetStep(entity.getTargetStep())
                .payload(entity.getPayload())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .sentAt(entity.getSentAt())
                .completedAt(entity.getCompletedAt())
                .errorMessage(entity.getErrorMessage())
                .correlationId(entity.getCorrelationId())
                .build();
    }
}
