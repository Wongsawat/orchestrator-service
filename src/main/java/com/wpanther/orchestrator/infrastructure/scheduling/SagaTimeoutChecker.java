package com.wpanther.orchestrator.infrastructure.scheduling;

import com.wpanther.orchestrator.domain.model.SagaInstance;
import com.wpanther.orchestrator.domain.repository.SagaInstanceRepository;
import com.wpanther.orchestrator.infrastructure.config.SagaProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduled task to check for expired saga instances.
 * <p>
 * Runs periodically to identify sagas that have been in IN_PROGRESS status
 * longer than the configured timeout. Expired sagas are marked as failed
 * and compensation is initiated.
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
    prefix = "app.saga",
    name = "timeout-check-enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class SagaTimeoutChecker {

    private final SagaInstanceRepository sagaRepository;
    private final SagaProperties sagaProperties;

    /**
     * Checks for expired sagas at a fixed interval.
     * <p>
     * Runs every 60 seconds by default. The interval can be configured
     * via {@code app.saga.timeout-check-interval-seconds} property.
     * </p>
     */
    @Scheduled(
        fixedDelayString = "${app.saga.timeout-check-interval-seconds:60}",
        initialDelayString = "${app.saga.timeout-check-initial-delay-seconds:30}"
    )
    public void checkExpiredSagas() {
        if (!sagaProperties.isTimeoutCheckEnabled()) {
            log.trace("Timeout checking is disabled");
            return;
        }

        int timeoutMinutes = sagaProperties.getTimeoutMinutes();
        if (timeoutMinutes <= 0) {
            log.trace("Timeout is disabled (timeoutMinutes={})", timeoutMinutes);
            return;
        }

        try {
            // Convert minutes to seconds for the repository method
            int timeoutSeconds = timeoutMinutes * 60;
            List<SagaInstance> expiredSagas = sagaRepository.findTimeoutInstances(timeoutSeconds);

            if (expiredSagas.isEmpty()) {
                log.trace("No expired sagas found");
                return;
            }

            log.warn("Found {} expired sagas (timeout: {} minutes)", expiredSagas.size(), timeoutMinutes);

            for (SagaInstance saga : expiredSagas) {
                handleExpiredSaga(saga);
            }

        } catch (Exception e) {
            log.error("Error checking for expired sagas", e);
        }
    }

    /**
     * Handles an expired saga by marking it as failed and saving the updated state.
     * The actual compensation will be triggered by another scheduled task or
     * can be initiated immediately if needed.
     *
     * @param saga The expired saga instance
     */
    private void handleExpiredSaga(SagaInstance saga) {
        String errorMessage = String.format(
            "Saga timed out after %d minutes. Last updated: %s, Current step: %s",
            sagaProperties.getTimeoutMinutes(),
            saga.getUpdatedAt(),
            saga.getCurrentStep()
        );

        log.warn("Saga {} expired: {}", saga.getId(), errorMessage);

        try {
            // Mark the saga as failed
            saga.fail(errorMessage);
            sagaRepository.save(saga);

            // Note: Compensation is not initiated here to avoid cascading failures
            // in case of a system-wide issue. The saga will remain in FAILED status
            // and can be manually compensated or picked up by a separate compensation job.
            log.info("Marked expired saga {} as failed", saga.getId());

        } catch (Exception e) {
            log.error("Failed to handle expired saga {}", saga.getId(), e);
        }
    }
}
