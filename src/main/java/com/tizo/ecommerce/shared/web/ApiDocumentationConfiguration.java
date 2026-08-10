package com.tizo.ecommerce.shared.web;

import com.scalar.maven.webmvc.ScalarWebMvcController;
import com.scalar.maven.webmvc.SpringBootScalarProperties;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(SpringBootScalarProperties.class)
public class ApiDocumentationConfiguration implements WebMvcConfigurer {

    @Bean
    ScalarWebMvcController scalarWebMvcController() {
        return new ScalarWebMvcController();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/openapi/**")
                .addResourceLocations("classpath:/static/openapi/")
                .setCacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic());
    }
}
