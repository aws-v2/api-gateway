package com.microservices.gateway.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisServiceTest {

    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    @InjectMocks
    private RedisService redisService;

    @BeforeEach
    void setUp() {
    }

    @Test
    void isBlacklisted_returnsTrue_whenKeyExists() {
        String tokenHash = "testHash";
        when(redisTemplate.hasKey("blacklist:" + tokenHash)).thenReturn(Mono.just(true));

        StepVerifier.create(redisService.isBlacklisted(tokenHash))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void blacklistToken_callsSetOnRedis() {
        String tokenHash = "testHash";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.set(eq("blacklist:" + tokenHash), eq("true"), any(Duration.class)))
                .thenReturn(Mono.just(true));

        redisService.blacklistToken(tokenHash);

        verify(valueOperations, times(1)).set(eq("blacklist:" + tokenHash), eq("true"), any(Duration.class));
    }

    @Test
    void isValidApiKey_returnsTrue_whenKeyExists() {
        String accessKeyId = "testKey";
        when(redisTemplate.hasKey("apikey:" + accessKeyId)).thenReturn(Mono.just(true));

        StepVerifier.create(redisService.isValidApiKey(accessKeyId))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void saveApiKey_callsSetOnRedis() {
        String accessKeyId = "testKey";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.set(eq("apikey:" + accessKeyId), eq("valid")))
                .thenReturn(Mono.just(true));

        redisService.saveApiKey(accessKeyId);

        verify(valueOperations, times(1)).set(eq("apikey:" + accessKeyId), eq("valid"));
    }

    @Test
    void revokeApiKey_callsDeleteOnRedis() {
        String accessKeyId = "testKey";
        when(redisTemplate.delete("apikey:" + accessKeyId)).thenReturn(Mono.just(1L));

        redisService.revokeApiKey(accessKeyId);

        verify(redisTemplate, times(1)).delete("apikey:" + accessKeyId);
    }
}