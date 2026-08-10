package com.tizo.ecommerce.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tizo.ecommerce.demo.adapter.DemoResetController;
import com.tizo.ecommerce.demo.adapter.DemoScenarioInterceptor;
import com.tizo.ecommerce.shared.error.DomainException;
import com.tizo.ecommerce.shared.web.RequestIdentityResolver;
import com.tizo.ecommerce.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "tizo.demo.enabled=false")
@AutoConfigureMockMvc
class ProductionSafetyIT extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private RequestIdentityResolver identityResolver;

    @Test
    void demoEndpointFaultInjectionAndImplicitIdentityAreAbsent() throws Exception {
        assertThat(context.getBeansOfType(DemoResetController.class)).isEmpty();
        assertThat(context.getBeansOfType(DemoScenarioInterceptor.class)).isEmpty();

        mvc.perform(post("/api/mock/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenario\":\"normal\"}"))
                .andExpect(status().isNotFound());

        assertThatThrownBy(identityResolver::customerId)
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).code())
                .isEqualTo("AUTHENTICATION_REQUIRED");
    }
}
