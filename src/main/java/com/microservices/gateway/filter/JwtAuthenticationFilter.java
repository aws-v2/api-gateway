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

        // ── Public paths — skip auth entirely ────────────────────────────────
        if (isPublicDocsPath(path)) {
            return chain.filter(exchange);
        }

        // ── Already authenticated upstream (e.g. Global API Key filter) ──────
        if (request.getHeaders().containsKey("X-User-Id")) {
            return chain.filter(exchange);
        }

        // ── Try Bearer JWT first ──────────────────────────────────────────────
        String authHeader = request.getHeaders().getFirst("Authorization");


        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return handleBearerToken(exchange, chain, authHeader.substring(7), path);
        }

        // ── No Bearer — fall back to HMAC API key ────────────────────────────
        String apiKey = request.getHeaders().getFirst("X-Api-Key");

        if (apiKey != null && !apiKey.isBlank()) {
            return handleApiKey(exchange, chain, apiKey, path);
        }

        // ── Nothing provided — reject ─────────────────────────────────────────
        log.warn("Auth failed: MISSING_CREDENT****IALS for path: {}", path);    
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    // ── Bearer JWT branch ─────────────────────────────────────────────────────

    private Mono<Void> handleBearerToken(ServerWebExchange exchange, GatewayFilterChain chain,
                                          String token, String path) {
        // Also accept token from query param as fallback within this branch
        if (token == null || token.isBlank()) {
            token = exchange.getRequest().getQueryParams().getFirst("token");
        }

        if (token == null || token.isBlank()) {
            log.warn("Auth failed: MISSING_TOKEN for path: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        if (!jwtUtil.isTokenValid(token)) {
            log.warn("Auth failed: INVALID_JWT for path: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String email  = jwtUtil.extractUsername(token);
        String role   = jwtUtil.extractRole(token);
        String userId = jwtUtil.extractUserId(token);

        log.debug("Auth OK (JWT): userId={} path={}", userId, path);

        ServerHttpRequest modified = exchange.getRequest().mutate()
                .header("X-User-Email", email)
                .header("X-User-Role",  role)
                .header("X-User-Id",    userId)
                .header("X-Auth-Method", "jwt")   // lets downstream know which method was used
                .build();

        return chain.filter(exchange.mutate().request(modified).build());
    }

    // ── HMAC API Key branch ───────────────────────────────────────────────────
    //
    // Expected X-Api-Key format:
    //   base64url({"userId":"...","iat":...}).<HMAC-SHA256 signature>
    //
    // JwtUtil.isApiKeyValid(apiKey)      — verifies the HMAC signature
    // JwtUtil.extractUserIdFromApiKey()  — decodes the payload and returns userId
    //
    // See JwtUtil additions below this file.

    private Mono<Void> handleApiKey(ServerWebExchange exchange, GatewayFilterChain chain,
                                     String apiKey, String path) {
        if (!jwtUtil.isApiKeyValid(apiKey)) {
            log.warn("Auth failed: INVALID_API_KEY for path: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String userId = jwtUtil.extractUserIdFromApiKey(apiKey);

        if (userId == null || userId.isBlank()) {
            log.warn("Auth failed: API_KEY_MISSING_USER_ID for path: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        log.debug("Auth OK (API key): userId={} path={}", userId, path);

        ServerHttpRequest modified = exchange.getRequest().mutate()
                .header("X-User-Id",     userId)
                .header("X-User-Role",   "api-key")  // downstream can scope permissions by this
                .header("X-Auth-Method", "api-key")
                .build();

        return chain.filter(exchange.mutate().request(modified).build());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    // /docs and /docs/{slug}  → public
    // /internal/docs          → protected
    private boolean isPublicDocsPath(String path) {
        return path.matches(".*/docs$") || path.matches(".*/docs/[^/]+$");
    }
}