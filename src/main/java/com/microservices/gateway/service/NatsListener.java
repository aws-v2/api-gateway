package com.microservices.gateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.microservices.gateway.util.JwtUtil;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
@Slf4j
public class NatsListener {

    private final Connection natsConnection;
    private final RedisService redisService;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void setupListeners() {
        Dispatcher dispatcher = natsConnection.createDispatcher((msg) -> {
        });

        // 1. Token Blacklisted
        dispatcher.subscribe("auth.token.blacklisted", (msg) -> {
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

        // 2. API Key Created
        dispatcher.subscribe("auth.apikey.created", (msg) -> {
            try {
                String json = new String(msg.getData(), StandardCharsets.UTF_8);
                JsonNode node = objectMapper.readTree(json);
                if (node.has("accessKeyId")) {
                    String accessKeyId = node.get("accessKeyId").asText();
                    redisService.saveApiKey(accessKeyId);
                    log.info("Cached new API Key: {}", accessKeyId);
                }
            } catch (Exception e) {
                log.error("Error processing apikey created event", e);
            }
        });

        // 3. API Key Revoked
        dispatcher.subscribe("auth.apikey.revoked", (msg) -> {
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
