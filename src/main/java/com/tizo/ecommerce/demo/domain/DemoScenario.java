package com.tizo.ecommerce.demo.domain;

import com.tizo.ecommerce.generated.model.MockScenario;

public enum DemoScenario {
    NORMAL,
    SLOW,
    EMPTY,
    SERVER_ERROR,
    TIMEOUT_BEFORE_COMMIT,
    TIMEOUT_AFTER_COMMIT,
    CONCURRENT_RESOLUTION,
    DISPATCHED_ORDER;

    public static DemoScenario from(MockScenario scenario) {
        if (scenario == null) {
            return NORMAL;
        }
        return switch (scenario) {
            case NORMAL -> NORMAL;
            case SLOW -> SLOW;
            case EMPTY -> EMPTY;
            case SERVER_ERROR -> SERVER_ERROR;
            case TIMEOUT_BEFORE_COMMIT -> TIMEOUT_BEFORE_COMMIT;
            case TIMEOUT_AFTER_COMMIT -> TIMEOUT_AFTER_COMMIT;
            case CONCURRENT_RESOLUTION -> CONCURRENT_RESOLUTION;
            case DISPATCHED_ORDER -> DISPATCHED_ORDER;
        };
    }

    public MockScenario toContract() {
        return switch (this) {
            case NORMAL -> MockScenario.NORMAL;
            case SLOW -> MockScenario.SLOW;
            case EMPTY -> MockScenario.EMPTY;
            case SERVER_ERROR -> MockScenario.SERVER_ERROR;
            case TIMEOUT_BEFORE_COMMIT -> MockScenario.TIMEOUT_BEFORE_COMMIT;
            case TIMEOUT_AFTER_COMMIT -> MockScenario.TIMEOUT_AFTER_COMMIT;
            case CONCURRENT_RESOLUTION -> MockScenario.CONCURRENT_RESOLUTION;
            case DISPATCHED_ORDER -> MockScenario.DISPATCHED_ORDER;
        };
    }
}
