package com.microservices.gateway.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NatsListener {

    private final NatsService natsService;

    @Value("${spring.profiles.active:dev}")
    private String env;

    @PostConstruct
    public void setupListeners() {
        if (natsService.getConnection() == null) {
            log.warn("NATS connection not available, listener setup skipped");
            return;
        }

        // Add any other NATS listeners that don't depend on Redis here
        // Currently all listeners were Redis dependent
    }
}
