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

        // 2. Extract token — Bearer header first, then ?token= query param (WebSocket fallback)
        String token       = null;
        String authMethod  = "jwt";

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        } else {
            token = exchange.getRequest().getQueryParams().getFirst("token");
        }

        if (token == null) {
            return failAuth(exchange, path, "MISSING_CREDENTIALS");
        }

        // 3. Validate
        if (!jwtUtil.isTokenValid(token)) {
            return failAuth(exchange, path, "JWT_SIGNATURE");
        }

        // 4. Extract claims
        String userId = jwtUtil.extractUserId(token);
        String role   = jwtUtil.extractRole(token);

        log.info("[auth] userId={} role={} method={} path={}", userId, role, authMethod, path);

        // 5. Forward user context downstream
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header("X-User-Id",     userId)
                .header("X-User-Role",   role)
                .header("X-Auth-Method", authMethod)
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private Mono<Void> failAuth(ServerWebExchange exchange, String path, String type) {
        String clientIp = "unknown";
        if (exchange.getRequest().getRemoteAddress() != null
                && exchange.getRequest().getRemoteAddress().getAddress() != null) {
            clientIp = exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }

        log.warn("Auth failed: {} | path: {} | ip: {}", type, path, clientIp);

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