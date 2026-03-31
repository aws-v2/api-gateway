package com.microservices.gateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Dispatcher;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
@Slf4j
public class NatsListener {

    private final NatsService natsService;
    private final RedisService redisService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${spring.profiles.active:dev}")
    private String env;

    @PostConstruct
    public void setupListeners() {
        if (natsService.getConnection() == null) {
            log.warn("NATS connection not available, listener setup skipped");
            return;
        }

        Dispatcher dispatcher = natsService.getConnection().createDispatcher((msg) -> {
        });

        // 1. Token Blacklisted - Standardized subject: <env>.auth.v1.token.blacklisted
        dispatcher.subscribe(String.format("%s.auth.v1.token.blacklisted", env), (msg) -> {
            try {
                String json = new String(msg.getData(), StandardCharsets.UTF_8);
                JsonNode node = objectMapper.readTree(json);
                if (node.has("tokenHash")) {
                    String tokenHash = node.get("tokenHash").asText();
                    redisService.blacklistToken(tokenHash);
                    log.info("Blacklisted token hash: {}", tokenHash);
                }
            } catch (Exception e) {
                log.error("Error processing blacklist event", e);
            }
        });

        // 2. API Key Created - Standardized subject: <env>.auth.v1.apikey.created
        dispatcher.subscribe(String.format("%s.auth.v1.apikey.created", env), (msg) -> {
            try {
                String json = new String(msg.getData(), StandardCharsets.UTF_8);
                JsonNode node = objectMapper.readTree(json);
                if (node.has("accessKeyId")) {
                    String accessKeyId = node.get("accessKeyId").asText();
                    String userId = node.has("userId") ? node.get("userId").asText() : "unknown";
                    String secretKeyHash = node.has("secretKeyHash") ? node.get("secretKeyHash").asText() : "";

                    redisService.saveApiKey(accessKeyId, userId, secretKeyHash);
                    log.info("Cached new API Key: {} for user: {}", accessKeyId, userId);
                }

            } catch (Exception e) {
                log.error("Error processing apikey created event", e);
            }
        });

        // 3. API Key Revoked - Standardized subject: <env>.auth.v1.apikey.revoked
        dispatcher.subscribe(String.format("%s.auth.v1.apikey.revoked", env), (msg) -> {
            try {
                String json = new String(msg.getData(), StandardCharsets.UTF_8);
                JsonNode node = objectMapper.readTree(json);
                if (node.has("accessKeyId")) {
                    String accessKeyId = node.get("accessKeyId").asText();
                    redisService.revokeApiKey(accessKeyId);
                    log.info("Revoked API Key: {}", accessKeyId);
                }
            } catch (Exception e) {
                log.error("Error processing apikey revocation event", e);
            }
        });
    }
}
