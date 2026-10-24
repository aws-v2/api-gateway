package com.microservices.gateway.service;


import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class AuthMetrics {

    private final MeterRegistry registry;

    public AuthMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(String method, String result, String reason) {
        registry.counter("gateway.auth.requests",
                "method", method,     // jwt | api-key | query | public | docs | none
                "result", result,     // success | failure
                "reason", reason)     // none | MISSING_CREDENTIALS | JWT_SIGNATURE | INVALID_API_KEY ...
                .increment();
    }
}