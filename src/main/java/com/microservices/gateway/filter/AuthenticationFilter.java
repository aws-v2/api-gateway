package com.microservices.gateway.filter;

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
import com.microservices.gateway.util.*;

@Component
@Slf4j
public class AuthenticationFilter implements GlobalFilter, Ordered {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private com.microservices.gateway.service.NatsService natsService;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private static final List<String> PUBLIC_ENDPOINTS = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/verify",
            "/api/v1/auth/mfa/verify",
            "/api/v1/auth/verify-email",
            "/api/v1/auth/resend-verification",
            "/api/v1/auth/reset-password",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/docs",          "/api/v1/auth/docs/**",
            "/api/v1/ec2/docs",           "/api/v1/ec2/docs/**",
            "/api/v1/lambda/docs",        "/api/v1/lambda/docs/**",
            "/api/v1/rds/docs",           "/api/v1/rds/docs/**",
            "/api/v1/identity/docs",      "/api/v1/identity/docs/**",
            "/api/v1/gamelift/docs",      "/api/v1/gamelift/docs/**",
            "/api/v1/fargate/docs",       "/api/v1/fargate/docs/**",
            "/api/v1/api-gateway/docs",   "/api/v1/api-gateway/docs/**",
            "/api/v1/sagemaker/docs",     "/api/v1/sagemaker/docs/**",
            "/api/v1/network/docs",       "/api/v1/network/docs/**",
            "/api/v1/metrics/docs",       "/api/v1/metrics/docs/**",
            "/api/v1/s3/docs",            "/api/v1/s3/docs/**",
            "/api/v1/config/docs",        "/api/v1/config/docs/**",
            "/api/v1/gateway/docs",       "/api/v1/gateway/docs/**",
            "/api/v1/billing/docs",       "/api/v1/billing/docs/**"
    );


@Override
public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String path = exchange.getRequest().getURI().getPath();

    // 1. Public endpoints — no auth
    if (isPublicEndpoint(path)) {
        return chain.filter(exchange);
    }

    String credential = null;
    String authMethod = null;

    // 2. Try Authorization: Bearer <token>
    String authHeader = exchange.getRequest()
            .getHeaders()
            .getFirst(HttpHeaders.AUTHORIZATION);

    if (authHeader != null && authHeader.startsWith("Bearer ")) {
        credential = authHeader.substring(7).trim();
        authMethod = "jwt";
    }

    // 3. Fallback to ?token=
    if (credential == null || credential.isBlank()) {
        String queryToken = exchange.getRequest()
                .getQueryParams()
                .getFirst("token");

        if (queryToken != null && !queryToken.isBlank()) {
            credential = queryToken.trim();
            authMethod = "query";
        }
    }

    // 4. Fallback to X-Api-Key
    if (credential == null || credential.isBlank()) {
        String apiKey = exchange.getRequest()
                .getHeaders()
                .getFirst("X-Api-Key");

        if (apiKey != null && !apiKey.isBlank()) {
            credential = apiKey.trim();
            authMethod = "api-key";
        }
    }

    // 5. No credentials found
    if (credential == null || credential.isBlank()) {
        return failAuth(exchange, path, "MISSING_CREDENTIALS");
    }

    String userId = null;
    String role   = null;

    // 6. Validate based on auth method
    switch (authMethod) {

        case "jwt":
        case "query":

            if (!jwtUtil.isTokenValid(credential)) {
                return failAuth(exchange, path, "JWT_SIGNATURE");
            }

            userId = jwtUtil.extractUserId(credential);
            role   = jwtUtil.extractRole(credential);

            break;

        case "api-key":

   
            // Replace with your actual validation logic
            if (!jwtUtil.isApiKeyValid(credential)) {
                return failAuth(exchange, path, "INVALID_API_KEY");
            }

            // Optional machine identity
            userId = "agent";
            role   = "SYSTEM";

            break;

        default:
            return failAuth(exchange, path, "UNKNOWN_AUTH_METHOD");
    }

    log.info(
            "[auth] userId={} role={} method={} path={}",
            userId,
            role,
            authMethod,
            path
    );

    // 7. Forward auth context downstream
    ServerHttpRequest mutatedRequest = exchange.getRequest()
            .mutate()
            .header("X-Auth-Method", authMethod)
            .header("X-User-Id", userId != null ? userId : "")
            .header("X-User-Role", role != null ? role : "")
            .build();

    return chain.filter(
            exchange.mutate()
                    .request(mutatedRequest)
                    .build()
    );
}


    private Mono<Void> failAuth(ServerWebExchange exchange, String path, String type) {
        String clientIp = "unknown";
        if (exchange.getRequest().getRemoteAddress() != null
                && exchange.getRequest().getRemoteAddress().getAddress() != null) {
            clientIp = exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }

        log.warn("Auth failed: {} | ,,path: {} | ip: {}", type, path, clientIp);

        natsService.publish("auth", "failure", java.util.Map.of(
                "type",      type,
                "path",      path,
                "ip",        clientIp,
                "timestamp", java.time.LocalDateTime.now().toString()));

        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    private boolean isPublicEndpoint(String path) {
        return PUBLIC_ENDPOINTS.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    @Override
    public int getOrder() {
        return -1;
    }
}