package com.tizo.ecommerce.demo.domain;

import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!production")
@ConditionalOnProperty(
        name = {"tizo.demo.enabled", "tizo.demo.tools-enabled"},
        havingValue = "true")
public class DemoScenarioState {

    private final AtomicReference<DemoScenario> current = new AtomicReference<>(DemoScenario.NORMAL);

    public DemoScenario current() {
        return current.get();
    }

    public void activate(DemoScenario scenario) {
        current.set(scenario);
    }
}
