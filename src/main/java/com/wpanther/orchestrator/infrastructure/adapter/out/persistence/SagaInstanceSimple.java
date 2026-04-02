package com.wpanther.orchestrator.infrastructure.adapter.out.persistence;

import com.wpanther.orchestrator.domain.model.enums.DocumentType;
import com.wpanther.saga.domain.enums.SagaStatus;
import com.wpanther.saga.domain.enums.SagaStep;

import java.time.Instant;

/**
 * Projection interface for loading saga instances without CLOB columns.
 * Used to avoid PostgreSQL JDBC LOB API issues when loading large TEXT columns.
 * <p>
 * This projection includes all columns except:
 * - xml_content (TEXT - treated as CLOB by Hibernate)
 * - metadata (TEXT - treated as CLOB by Hibernate)
 * - version (managed by JPA @Version, not needed for reply handling)
 */
public interface SagaInstanceSimple {

    String getId();

    DocumentType getDocumentType();

    String getDocumentId();

    SagaStep getCurrentStep();

    SagaStatus getStatus();

    Instant getCreatedAt();

    Instant getUpdatedAt();

    Instant getCompletedAt();

    String getErrorMessage();

    String getCorrelationId();

    Integer getRetryCount();

    Integer getMaxRetries();
}
