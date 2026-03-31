package com.microservices.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.reactive.CorsWebFilter;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class CorsConfigTest {

    private final CorsConfig corsConfig = new CorsConfig();

    @Test
    void corsWebFilterBeanCreation() {
        CorsWebFilter filter = corsConfig.corsWebFilter();
        assertNotNull(filter, "CorsWebFilter bean should be created!");
    }
}