package com.tizo.ecommerce.demo.domain;

import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "tizo.demo.enabled", havingValue = "true")
public class DemoScenarioState {

    private final AtomicReference<DemoScenario> current = new AtomicReference<>(DemoScenario.NORMAL);

    public DemoScenario current() {
        return current.get();
    }

    public void activate(DemoScenario scenario) {
        current.set(scenario);
    }
}
