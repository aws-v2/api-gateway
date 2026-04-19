package com.microservices.gateway.filter;

import com.microservices.gateway.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class JwtAuthenticationFilter implements GatewayFilter {

    @Autowired
    private JwtUtil jwtUtil;
@Override
public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    ServerHttpRequest request = exchange.getRequest();
    String path = request.getURI().getPath();

    // ── Public paths — skip auth entirely ────────────────────────────────────
    if (isPublicDocsPath(path)) {
        return chain.filter(exchange);
    }

    // Skip if already authenticated by Global Filter (API Key)
    if (request.getHeaders().containsKey("X-User-Id")) {
        return chain.filter(exchange);
    }

    String authHeader = request.getHeaders().getFirst("Authorization");
    String token = null;

    if (authHeader != null && authHeader.startsWith("Bearer ")) {
        token = authHeader.substring(7);
    } else {
        token = request.getQueryParams().getFirst("token");
    }

    if (token == null || token.isEmpty()) {
        log.warn("Auth failed: MISSING_TOKEN for path: {}", path);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    if (!jwtUtil.isTokenValid(token)) {
        log.warn("Auth failed: INVALID_TOKEN for path: {}", path);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    String email = jwtUtil.extractUsername(token);
    String role = jwtUtil.extractRole(token);
    String userId = jwtUtil.extractUserId(token);

    ServerHttpRequest modifiedRequest = request.mutate()
            .header("X-User-Email", email)
            .header("X-User-Role", role)
            .header("X-User-Id", userId)
            .build();

    return chain.filter(exchange.mutate().request(modifiedRequest).build());
}

// /docs and /docs/{slug} — yes
// /internal/docs         — no, must be protected   
private boolean isPublicDocsPath(String path) {
    return path.matches(".*/docs$") || path.matches(".*/docs/[^/]+$");
}
}
