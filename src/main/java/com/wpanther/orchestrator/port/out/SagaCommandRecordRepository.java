package com.wpanther.orchestrator.port.out;

import com.wpanther.orchestrator.domain.model.SagaCommandRecord;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing saga command records.
 */
public interface SagaCommandRecordRepository {

    /**
     * Saves a saga command record.
     *
     * @param command The command record to save
     * @return The saved command record
     */
    SagaCommandRecord save(SagaCommandRecord command);

    /**
     * Finds a command record by its ID.
     *
     * @param id The command ID
     * @return Optional containing the command record if found
     */
    Optional<SagaCommandRecord> findById(String id);

    /**
     * Finds all command records for a saga instance.
     *
     * @param sagaId The saga instance ID
     * @return List of command records for the saga
     */
    List<SagaCommandRecord> findBySagaId(String sagaId);

    /**
     * Finds pending commands that need to be retried.
     *
     * @return List of pending commands
     */
    List<SagaCommandRecord> findPendingCommands();

    /**
     * Finds sent commands that have not received a reply.
     *
     * @param timeoutSeconds The timeout in seconds
     * @return List of commands that may have timed out
     */
    List<SagaCommandRecord> findTimedOutCommands(int timeoutSeconds);

    /**
     * Deletes all command records for a saga instance.
     *
     * @param sagaId The saga instance ID
     */
    void deleteBySagaId(String sagaId);
}
