package com.tizo.ecommerce.demo.adapter;

import com.tizo.ecommerce.demo.application.DemoResetService;
import com.tizo.ecommerce.demo.domain.DemoScenario;
import com.tizo.ecommerce.generated.api.DemoApi;
import com.tizo.ecommerce.generated.model.ResetMockRequest;
import com.tizo.ecommerce.generated.model.ResetMockResponse;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!production")
@ConditionalOnProperty(
        name = {"tizo.demo.enabled", "tizo.demo.tools-enabled"},
        havingValue = "true")
public class DemoResetController implements DemoApi {

    private final DemoResetService resetService;

    public DemoResetController(DemoResetService resetService) {
        this.resetService = resetService;
    }

    @Override
    public ResponseEntity<ResetMockResponse> resetDemo(ResetMockRequest request) {
        DemoResetService.ResetResult result = resetService.reset(DemoScenario.from(request.getScenario()));
        return ResponseEntity.ok(new ResetMockResponse(
                OffsetDateTime.ofInstant(result.resetAt(), ZoneOffset.UTC),
                result.schemaVersion(),
                result.scenario().toContract()));
    }
}
