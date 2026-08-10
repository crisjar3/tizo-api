package com.tizo.ecommerce.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tizo.ecommerce.demo.adapter.DemoResetController;
import com.tizo.ecommerce.demo.adapter.DemoScenarioInterceptor;
import com.tizo.ecommerce.shared.web.RequestIdentityResolver;
import com.tizo.ecommerce.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "tizo.demo.enabled=true",
        "tizo.demo.tools-enabled=true",
        "spring.flyway.locations=classpath:db/migration,classpath:db/local"
})
@ActiveProfiles(profiles = {"test", "production"}, inheritProfiles = false)
@AutoConfigureMockMvc
class RestrictedProductionDemoIdentityIT extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private RequestIdentityResolver identityResolver;

    @Test
    void implicitCustomerWorksWhileProductionProfileBlocksDestructiveDemoTools() throws Exception {
        assertThat(identityResolver.customerId()).isEqualTo("customer-001");
        assertThat(context.getBeansOfType(DemoResetController.class)).isEmpty();
        assertThat(context.getBeansOfType(DemoScenarioInterceptor.class)).isEmpty();

        mvc.perform(get("/api/me/orders"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/mock/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenario\":\"normal\"}"))
                .andExpect(status().isNotFound());
    }
}
