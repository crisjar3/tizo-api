package com.tizo.ecommerce.shared.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tizo.ecommerce.support.PostgresIntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(GlobalWebInfrastructureIT.EndpointConfiguration.class)
@TestPropertySource(properties = {
        "tizo.rate-limit.enabled=true",
        "tizo.rate-limit.requests-per-minute=1",
        "tizo.rate-limit.burst-capacity=0",
        "tizo.rate-limit.cache-maximum-size=10"
})
class RateLimitIT extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void returnsStandardQuotaHeadersAndRetryAdvice() throws Exception {
        mvc.perform(get("/test/ok").with(request -> {
                    request.setRemoteAddr("198.51.100.7");
                    return request;
                }))
                .andExpect(status().isOk())
                .andExpect(header().string("RateLimit-Limit", "1"))
                .andExpect(header().string("RateLimit-Remaining", "0"));

        mvc.perform(get("/test/ok").with(request -> {
                    request.setRemoteAddr("198.51.100.7");
                    return request;
                }))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().exists("RateLimit-Reset"))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.error.retryable").value(true));

        org.assertj.core.api.Assertions.assertThat(meterRegistry
                        .get("tizo.rate.limit.requests")
                        .tag("result", "allowed")
                        .counter()
                        .count())
                .isGreaterThanOrEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(meterRegistry
                        .get("tizo.rate.limit.requests")
                        .tag("result", "rejected")
                        .counter()
                        .count())
                .isGreaterThanOrEqualTo(1);
    }
}
