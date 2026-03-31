package com.microservices.gateway.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RedisService {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    private static final String BLACKLIST_PREFIX = "blacklist:";
    private static final String APIKEY_PREFIX = "apikey:";

    // Blacklist Operations
    public Mono<Boolean> isBlacklisted(String tokenHash) {
        return redisTemplate.hasKey(BLACKLIST_PREFIX + tokenHash);
    }

    public void blacklistToken(String tokenHash) {
        // Default blacklist for 24 hours (or parse event expiry)
        redisTemplate.opsForValue().set(BLACKLIST_PREFIX + tokenHash, "true", Duration.ofHours(24)).subscribe();
    }

    // API Key Operations
    public Mono<Boolean> isValidApiKey(String accessKeyId) {
        return redisTemplate.hasKey(APIKEY_PREFIX + accessKeyId);
    }

    public void saveApiKey(String accessKeyId, String userId, String secretKeyHash) {
        // Store as JSON for full validation
        String data = String.format("{\"userId\":\"%s\",\"secretKeyHash\":\"%s\"}", userId, secretKeyHash);
        redisTemplate.opsForValue().set(APIKEY_PREFIX + accessKeyId, data).subscribe();
    }

    public Mono<String> getApiKeyData(String accessKeyId) {
        return redisTemplate.opsForValue().get(APIKEY_PREFIX + accessKeyId);
    }

    public void revokeApiKey(String accessKeyId) {
        redisTemplate.delete(APIKEY_PREFIX + accessKeyId).subscribe();
    }
}
