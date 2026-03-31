package com.microservices.gateway.filter;

import com.microservices.gateway.service.NatsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock private ReactiveRedisTemplate<String, String> redisTemplate;
    @Mock private NatsService natsService;
    @Mock private ReactiveValueOperations<String, String> valueOps;
    @Mock private ServerWebExchange exchange;
    @Mock private ServerHttpRequest request;
    @Mock private ServerHttpResponse response;
    @Mock private GatewayFilterChain chain;

    @InjectMocks
    private RateLimitFilter rateLimitFilter;

    private static final String CLIENT_IP    = "192.168.1.100";
    private static final String RATE_KEY     = "ratelimit:" + CLIENT_IP;
    private static final String UNKNOWN_KEY  = "ratelimit:unknown";

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(exchange.getRequest()).thenReturn(request);
        when(exchange.getResponse()).thenReturn(response);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private void stubRemoteAddress(String ip) throws Exception {
        InetAddress addr = InetAddress.getByName(ip);
        InetSocketAddress socketAddress = new InetSocketAddress(addr, 0);
        when(request.getRemoteAddress()).thenReturn(socketAddress);
    }

    private void stubNullRemoteAddress() {
        when(request.getRemoteAddress()).thenReturn(null);
    }

    private void stubNullAddressInSocket() throws Exception {
        InetSocketAddress socketAddress = mock(InetSocketAddress.class);
        when(socketAddress.getAddress()).thenReturn(null);
        when(request.getRemoteAddress()).thenReturn(socketAddress);
    }

    private void stubRedisCount(String key, long count) {
        when(valueOps.increment(key)).thenReturn(Mono.just(count));
    }

    private void stubExpire(String key) {
        when(redisTemplate.expire(eq(key), any(Duration.class))).thenReturn(Mono.just(true));
    }

    private void stubChainPass() {
        when(chain.filter(exchange)).thenReturn(Mono.empty());
    }

    private void stubResponseComplete() {
        when(response.setComplete()).thenReturn(Mono.empty());
    }





    // ═══════════════════════════════════════════════════════════════════════════
    // filter — rate limit exceeded (count > 500)
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class RateLimitExceeded {

        @Test
        void count501_returns429() throws Exception {
            stubRemoteAddress(CLIENT_IP);
            stubRedisCount(RATE_KEY, 501L);
            stubResponseComplete();

            StepVerifier.create(rateLimitFilter.filter(exchange, chain))
                    .verifyComplete();

            verify(response).setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        }

        @Test
        void exceeded_doesNotDelegateToChain() throws Exception {
            stubRemoteAddress(CLIENT_IP);
            stubRedisCount(RATE_KEY, 600L);
            stubResponseComplete();

            StepVerifier.create(rateLimitFilter.filter(exchange, chain))
                    .verifyComplete();

            verify(chain, never()).filter(any());
        }

        @Test
        void exceeded_callsResponseSetComplete() throws Exception {
            stubRemoteAddress(CLIENT_IP);
            stubRedisCount(RATE_KEY, 501L);
            stubResponseComplete();

            StepVerifier.create(rateLimitFilter.filter(exchange, chain))
                    .verifyComplete();

            verify(response).setComplete();
        }

        @Test
        void exceeded_publishesNatsEvent() throws Exception {
            stubRemoteAddress(CLIENT_IP);
            stubRedisCount(RATE_KEY, 501L);
            stubResponseComplete();

            StepVerifier.create(rateLimitFilter.filter(exchange, chain))
                    .verifyComplete();

            verify(natsService).publish(eq("rate_limit"), eq("exceeded"), any());
        }

        @Test
        void exceeded_natsPayloadContainsIp() throws Exception {
            stubRemoteAddress(CLIENT_IP);
            stubRedisCount(RATE_KEY, 501L);
            stubResponseComplete();

            StepVerifier.create(rateLimitFilter.filter(exchange, chain))
                    .verifyComplete();

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(natsService).publish(eq("rate_limit"), eq("exceeded"), captor.capture());

            Map<String, Object> payload = captor.getValue();
            assertThat(payload).containsKey("ip");
            assertThat(payload.get("ip")).isEqualTo(CLIENT_IP);
        }

        @Test
        void exceeded_natsPayloadContainsCountAndLimit() throws Exception {
            stubRemoteAddress(CLIENT_IP);
            stubRedisCount(RATE_KEY, 999L);
            stubResponseComplete();

            StepVerifier.create(rateLimitFilter.filter(exchange, chain))
                    .verifyComplete();

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(natsService).publish(eq("rate_limit"), eq("exceeded"), captor.capture());

            Map<String, Object> payload = captor.getValue();
            assertThat(payload).containsKey("count");
            assertThat(payload).containsKey("limit");
            assertThat(payload.get("count")).isEqualTo(999L);
            assertThat(payload.get("limit")).isEqualTo(500);
        }

        @Test
        void exceeded_natsPayloadContainsTimestamp() throws Exception {
            stubRemoteAddress(CLIENT_IP);
            stubRedisCount(RATE_KEY, 501L);
            stubResponseComplete();

            StepVerifier.create(rateLimitFilter.filter(exchange, chain))
                    .verifyComplete();

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(natsService).publish(eq("rate_limit"), eq("exceeded"), captor.capture());

            assertThat(captor.getValue()).containsKey("timestamp");
        }

        @Test
        void unknownIp_exceeded_natsPayloadContainsUnknown() {
            stubNullRemoteAddress();
            stubRedisCount(UNKNOWN_KEY, 501L);
            stubResponseComplete();

            StepVerifier.create(rateLimitFilter.filter(exchange, chain))
                    .verifyComplete();

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(natsService).publish(eq("rate_limit"), eq("exceeded"), captor.capture());

            assertThat(captor.getValue().get("ip")).isEqualTo("unknown");
        }


        @Test
        void exactlyOneBeyondBoundary_count501_isExceeded() throws Exception {
            stubRemoteAddress(CLIENT_IP);
            stubRedisCount(RATE_KEY, 501L);
            stubResponseComplete();

            StepVerifier.create(rateLimitFilter.filter(exchange, chain))
                    .verifyComplete();

            verify(response).setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            verifyNoInteractions(chain);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // filter — reactive pipeline completeness
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class ReactivePipeline {



        @Test
        void rateLimitedRequest_completesWithoutError() throws Exception {
            stubRemoteAddress(CLIENT_IP);
            stubRedisCount(RATE_KEY, 999L);
            stubResponseComplete();

            StepVerifier.create(rateLimitFilter.filter(exchange, chain))
                    .verifyComplete();
        }

    }
}