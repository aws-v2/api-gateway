package com.microservices.gateway.filter;

import com.microservices.gateway.service.NatsService;
import com.microservices.gateway.service.RedisService;
import com.microservices.gateway.util.JwtUtil;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationFilterTest {

        @Mock
        private JwtUtil jwtUtil;

        @Mock
        private RedisService redisService;

        @Mock
        private NatsService natsService;

        @Mock
        private GatewayFilterChain chain;

        @InjectMocks
        private AuthenticationFilter authenticationFilter;

        @Test
        void filter_allowsPublicEndpoints() {
                MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/auth/login").build();
                MockServerWebExchange exchange = MockServerWebExchange.from(request);
                when(chain.filter(exchange)).thenReturn(Mono.empty());

                StepVerifier.create(authenticationFilter.filter(exchange, chain))
                                .verifyComplete();

                verify(chain, times(1)).filter(exchange);
                verifyNoInteractions(jwtUtil, redisService, natsService);
        }

        @Test
        void filter_blocksUnauthorizedRequest() {
                MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/protected").build();
                MockServerWebExchange exchange = MockServerWebExchange.from(request);

                StepVerifier.create(authenticationFilter.filter(exchange, chain))
                                .verifyComplete();

                assert exchange.getResponse().getStatusCode() == HttpStatus.UNAUTHORIZED;
                verify(chain, never()).filter(any());
        }

        @Test
        void filter_allowsValidApiKey() {
                MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/protected")
                                .header("X-API-Key", "valid-key")
                                .build();
                MockServerWebExchange exchange = MockServerWebExchange.from(request);

                when(redisService.isValidApiKey("valid-key")).thenReturn(Mono.just(true));
                when(chain.filter(exchange)).thenReturn(Mono.empty());

                StepVerifier.create(authenticationFilter.filter(exchange, chain))
                                .verifyComplete();

                verify(chain, times(1)).filter(exchange);
        }

        @Test
        void filter_publishesEventOnInvalidApiKey() {
                MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/protected")
                                .header("X-API-Key", "invalid-key")
                                .remoteAddress(new java.net.InetSocketAddress("127.0.0.1", 80))
                                .build();
                MockServerWebExchange exchange = MockServerWebExchange.from(request);

                when(redisService.isValidApiKey("invalid-key")).thenReturn(Mono.just(false));

                StepVerifier.create(authenticationFilter.filter(exchange, chain))
                                .verifyComplete();

                assert exchange.getResponse().getStatusCode() == HttpStatus.UNAUTHORIZED;
                verify(natsService, times(1)).publish(eq("auth"), eq("failure"), any());
        }

        @Test
        void filter_allowsValidJwt() {
                String token = "valid-token";
                MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/protected")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .build();
                MockServerWebExchange exchange = MockServerWebExchange.from(request);

                when(jwtUtil.isTokenValid(token)).thenReturn(true);
                when(jwtUtil.getTokenHash(token)).thenReturn("hash");
                when(redisService.isBlacklisted("hash")).thenReturn(Mono.just(false));
                when(jwtUtil.extractUserId(token)).thenReturn("user123");
                when(chain.filter(any())).thenReturn(Mono.empty());

                StepVerifier.create(authenticationFilter.filter(exchange, chain))
                                .verifyComplete();

                verify(chain, times(1)).filter(any());
        }
}