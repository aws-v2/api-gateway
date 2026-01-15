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

    public void saveApiKey(String accessKeyId) {
        // Persist API Keys indefinitely or until revocation
        redisTemplate.opsForValue().set(APIKEY_PREFIX + accessKeyId, "valid").subscribe();
    }

    public void revokeApiKey(String accessKeyId) {
        redisTemplate.delete(APIKEY_PREFIX + accessKeyId).subscribe();
    }
}
