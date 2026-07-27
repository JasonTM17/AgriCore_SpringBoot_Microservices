package com.agricore.sales.infrastructure.recovery;

import com.agricore.sales.application.service.SalesSagaRecoveryPolicy;
import com.agricore.sales.application.service.SalesSagaRecoveryService;
import com.agricore.sales.application.service.SalesSagaRecoveryStateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@ConditionalOnProperty(
        name = "agricore.saga.recovery.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class SalesSagaRecoveryJob {

    private static final Logger log = LoggerFactory.getLogger(SalesSagaRecoveryJob.class);

    private final SalesSagaRecoveryStateService stateService;
    private final SalesSagaRecoveryService recoveryService;
    private final SalesSagaRecoveryPolicy policy;

    public SalesSagaRecoveryJob(
            SalesSagaRecoveryStateService stateService,
            SalesSagaRecoveryService recoveryService,
            SalesSagaRecoveryPolicy policy
    ) {
        this.stateService = stateService;
        this.recoveryService = recoveryService;
        this.policy = policy;
    }

    @Scheduled(fixedDelayString = "${agricore.saga.recovery.poll-ms:5000}")
    public void recover() {
        Instant now = Instant.now();
        for (var orderId : stateService.findRecoverableOrderIds(
                now,
                policy.staleBefore(now),
                policy.batchSize()
        )) {
            try {
                recoveryService.recover(orderId, now);
            } catch (RuntimeException failure) {
                log.warn(
                        "sales_saga_recovery_failed orderId={} errorType={}",
                        orderId,
                        failure.getClass().getSimpleName()
                );
            }
        }
    }
}
