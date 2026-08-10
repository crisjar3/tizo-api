package com.tizo.ecommerce.architecture;

import com.tizo.ecommerce.TizoApiApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularArchitectureTest {

    @Test
    void businessModulesRespectDeclaredDependencies() {
        ApplicationModules.of(TizoApiApplication.class).verify();
    }
}
