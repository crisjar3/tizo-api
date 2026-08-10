package com.tizo.ecommerce.shared.web;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tizo.ecommerce.shared.error.DomainException;
import com.tizo.ecommerce.shared.observability.CorrelationIdFilter;
import com.tizo.ecommerce.support.PostgresIntegrationTest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@Import(GlobalWebInfrastructureIT.EndpointConfiguration.class)
class GlobalWebInfrastructureIT extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void preservesValidCorrelationIdOnSuccessfulResponse() throws Exception {
        mvc.perform(get("/test/ok").header(CorrelationIdFilter.HEADER, "test-correlation-001"))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdFilter.HEADER, "test-correlation-001"));
    }

    @Test
    void generatesCorrelationIdAndRfc9457AngularEnvelopeForDomainFailure() throws Exception {
        mvc.perform(get("/test/domain"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(CorrelationIdFilter.HEADER, matchesPattern("[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.code").value("TEST_INVALID"))
                .andExpect(jsonPath("$.error.category").value("VALIDATION"))
                .andExpect(jsonPath("$.error.fieldErrors", empty()))
                .andExpect(jsonPath("$.error.correlationId").isString());
    }

    @Test
    void reportsMalformedJsonAsBadRequestWithoutInternalDetails() throws Exception {
        mvc.perform(post("/test/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{broken"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.detail").value("La estructura o los tipos de la solicitud no son válidos."));
    }

    @Test
    void reportsBeanValidationAsUnprocessableContent() throws Exception {
        mvc.perform(post("/test/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("name"));
    }

    @TestConfiguration
    static class EndpointConfiguration {

        @Bean
        TestEndpoint testEndpoint() {
            return new TestEndpoint();
        }
    }

    @RestController
    static class TestEndpoint {

        @GetMapping("/test/ok")
        Map<String, String> ok() {
            return Map.of("status", "ok");
        }

        @GetMapping("/test/domain")
        void domainFailure() {
            throw DomainException.validation("TEST_INVALID", "La operación de prueba no es válida.");
        }

        @PostMapping("/test/validated")
        Map<String, String> validated(@Valid @RequestBody TestBody body) {
            return Map.of("name", body.name());
        }
    }

    record TestBody(@NotBlank String name) {
    }
}
