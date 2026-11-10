error id: file://<WORKSPACE>/src/main/java/com/microservices/gateway/filter/JwtAuthenticationFilter.java:org/springframework/cloud/gateway/filter/GatewayFilter#
file://<WORKSPACE>/src/main/java/com/microservices/gateway/filter/JwtAuthenticationFilter.java
empty definition using pc, found symbol in pc: org/springframework/cloud/gateway/filter/GatewayFilter#
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 253
uri: file://<WORKSPACE>/src/main/java/com/microservices/gateway/filter/JwtAuthenticationFilter.java
text:
```scala
package com.microservices.gateway.filter;

import com.microservices.gateway.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter@@;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Component
@Slf4j
public class JwtAuthenticationFilter implements GatewayFilter {

	@Autowired
	private JwtUtil jwtUtil;

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		ServerHttpRequest request = exchange.getRequest();
		String path = request.getURI().getPath();

        requestId = UUID.randomUUID()

		if (request.getMethod() == HttpMethod.OPTIONS) {
			return chain.filter(exchange);
		}

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
			return handleApiKey(exchange, chain, apiKey, path,requestId);
		}

		// ── SSE / EventSource fallback — token in query param ────────────────
		String queryToken = request.getQueryParams().getFirst("token");
		log.info("SSE token check — path: {}, token present: {}", path, queryToken != null);

		if (queryToken != null && !queryToken.isBlank()) {
			return handleBearerToken(exchange, chain, queryToken, path, requestId);
		}

		// ── Nothing provided — reject ─────────────────────────────────────────
		log.warn("Auth failed: MISSING_CREDENT****IALS for path: {}", path);
		exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
		return exchange.getResponse().setComplete();
	}

	// ── Bearer JWT branch ─────────────────────────────────────────────────────

	private Mono<Void> handleBearerToken(ServerWebExchange exchange, GatewayFilterChain chain, String token,
			String path, String requestId) {
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

		String email = jwtUtil.extractUsername(token);
		String role = jwtUtil.extractRole(token);
		String userId = jwtUtil.extractUserId(token);

		log.debug("Auth OK (JWT): userId={} path={}", userId, path);

		ServerHttpRequest modified = exchange.getRequest().mutate()
				.header("X-User-Role", role)
                .header("X-User-Id", userId)
                .header("X-Request-Id", requestId)
                .header("X-Auth-Method", "jwt") // lets
				.build();

		return chain.filter(exchange.mutate().request(modified).build());
	}

	// ── HMAC API Key branch ───────────────────────────────────────────────────
	//
	// Expected X-Api-Key format:
	// base64url({"userId":"...","iat":...}).<HMAC-SHA256 signature>
	//
	// JwtUtil.isApiKeyValid(apiKey) — verifies the HMAC signature
	// JwtUtil.extractUserIdFromApiKey() — decodes the payload and returns userId

	private Mono<Void> handleApiKey(ServerWebExchange exchange, GatewayFilterChain chain, String apiKey, String path,String requestId) {
		if (!jwtUtil.isApiKeyValid(apiKey)) {
			log.warn("Auth failed: INVALID_API_KEY for path: {} and this key {}", path, apiKey);
			exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
			return exchange.getResponse().setComplete();
		}

		String userId = jwtUtil.extractUserIdFromApiKey(apiKey);
		String role = jwtUtil.extractRoleFromApiKey(apiKey);


		if (userId == null || userId.isBlank()) {
			log.warn("Auth failed: API_KEY_MISSING_USER_ID for path: {}", path);
			exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
			return exchange.getResponse().setComplete();
		}

		log.debug("Auth OK (API key): userId={} path={}", userId, path);

		ServerHttpRequest modified = exchange.getRequest().mutate()
                .header("X-User-Id", userId)
				.header("X-User-Role", role)
                .header("X-Request-Id", requestId)
				.header("X-Auth-Method", "api-key").build();

		return chain.filter(exchange.mutate().request(modified).build());
	}

	// ── Helpers ───────────────────────────────────────────────────────────────

	// /docs and /docs/{slug} → public
	// /internal/docs → protected
	private boolean isPublicDocsPath(String path) {
		return path.matches(".*/docs$") || path.matches(".*/docs/[^/]+$");
	}
}
```


#### Short summary: 

empty definition using pc, found symbol in pc: org/springframework/cloud/gateway/filter/GatewayFilter#