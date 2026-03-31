package com.microservices.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class NatsService {

    @Value("${nats.url:nats://localhost:4222}")
    private String natsUrl;

    @Value("${nats.username:}")
    private String natsUsername;

    @Value("${nats.password:}")
    private String natsPassword;

    @Value("${spring.profiles.active:dev}")
    private String env;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Connection natsConnection;

    @PostConstruct
    public void init() {
        try {
            Options.Builder builder = new Options.Builder()
                    .server(natsUrl)
                    .connectionName("api-gateway")
                    .maxReconnects(-1)
                    .reconnectWait(Duration.ofSeconds(2));

            if (natsUsername != null && !natsUsername.isEmpty()) {
                builder.userInfo(natsUsername, natsPassword);
            }

            natsConnection = Nats.connect(builder.build());
            log.info("Connected to NATS at {} with environment prefix: {}", natsUrl, env);
        } catch (IOException | InterruptedException e) {
            log.error("Failed to connect to NATS: {}", e.getMessage());
        }
    }

    /**
     * Publishes an event using the standardized scheme:
     * <env>.gateway.v1.<domain>.<action>
     */
    public void publish(String domain, String action, Object payload) {
        if (natsConnection == null || natsConnection.getStatus() != Connection.Status.CONNECTED) {
            log.warn("NATS not connected, skipping publish to domain: {}", domain);
            return;
        }

        String subject = String.format("%s.gateway.v1.%s.%s", env, domain, action);

        try {
            String json = objectMapper.writeValueAsString(payload);
            natsConnection.publish(subject, json.getBytes());
            log.debug("Published NATS event to subject: {}", subject);
        } catch (Exception e) {
            log.error("Failed to publish to subject {}: {}", subject, e.getMessage());
        }
    }

    public Connection getConnection() {
        return natsConnection;
    }
}
