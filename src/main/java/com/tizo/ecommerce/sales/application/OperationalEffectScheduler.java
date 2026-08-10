package com.tizo.ecommerce.sales.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "tizo.effects.scheduling-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class OperationalEffectScheduler {

    private final OperationalEffectWorker worker;

    public OperationalEffectScheduler(OperationalEffectWorker worker) {
        this.worker = worker;
    }

    @Scheduled(fixedDelayString = "${tizo.effects.fixed-delay:5s}")
    public void processAvailableEffects() {
        worker.runOnce();
    }
}
