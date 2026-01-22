package com.microservices.gateway.filter;

import com.microservices.gateway.service.RedisService;
import lombok.extern.slf4j.Slf4j;
import com.microservices.gateway.util.JwtUtil;
import org.springframework.util.AntPathMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@Slf4j
public class AuthenticationFilter implements GlobalFilter, Ordered {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private RedisService redisService;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    // List of public endpoints that don't require auth
    private static final List<String> PUBLIC_ENDPOINTS = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/verify",
            "/api/v1/auth/mfa/verify",
            "/api/v1/auth/verify-email",
            "/api/v1/auth/resend-verification",
            "/api/v1/auth/reset-password",
            "/api/v1/auth/forgot-password",
            "/auth/login",
            "/auth/register",
            "/auth/verify",
            "/auth/mfa/verify",
            "/auth/verify-email",
            "/auth/resend-verification",
            "/auth/reset-password",
            "/auth/forgot-password");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 1. Allow public endpoints
        log.info("Processing request for path: {}", path);
        if (isPublicEndpoint(path)) {
            log.info("Path {} is public, bypassing auth", path);
            return chain.filter(exchange);
        }

        // 2. Check for API Key (X-API-Key)
        if (exchange.getRequest().getHeaders().containsKey("X-API-Key")) {
            String apiKey = exchange.getRequest().getHeaders().getFirst("X-API-Key");
            // Validate API Key via Redis
            return redisService.isValidApiKey(apiKey)
                    .flatMap(isValid -> {
                        if (isValid) {
                            // If valid, mutation (e.g. adding user ID) could happen here if we tracked it
                            // in Redis
                            return chain.filter(exchange);
                        } else {
                            // Invalid API Key
                            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                            return exchange.getResponse().setComplete();
                        }
                    });
        }

        // 3. Check for JWT (Authorization: Bearer ...)
        if (exchange.getRequest().getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);

                // 3a. Validate Signature (Stateless)
                if (!jwtUtil.isTokenValid(token)) {
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED); // Invalid Signature
                    return exchange.getResponse().setComplete();
                }

                // 3b. Check Blacklist (Stateful)
                String tokenHash = jwtUtil.getTokenHash(token);
                return redisService.isBlacklisted(tokenHash)
                        .flatMap(isBlacklisted -> {
                            if (isBlacklisted) {
                                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED); // Token is Revoked
                                return exchange.getResponse().setComplete();
                            } else {
                                // Valid Token: Extract Claims & Forward
                                String userId = jwtUtil.extractUserId(token);
                                ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                                        .header("X-User-Id", userId)
                                        .build();
                                return chain.filter(exchange.mutate().request(mutatedRequest).build());
                            }
                        });
            }
        }

        // 4. No Auth Header found
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    private boolean isPublicEndpoint(String path) {
        return PUBLIC_ENDPOINTS.stream()
                .anyMatch(publicPath -> pathMatcher.match(publicPath, path));
    }

    @Override
    public int getOrder() {
        return -1; // Execute early in the chain
    }
}
