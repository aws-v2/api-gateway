package com.microservices.gateway.filter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import java.util.UUID;

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

			"/api/v1/auth/health","/api/v1/gateway/health","/api/v1/s3/health", "/api/v1/ec2/health", "/api/v1/rds/health", "/api/v1/lambda/health",
			"/api/v1/sagemaker/health", "/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/verify",
			"/api/v1/auth/mfa/verify", "/api/v1/auth/verify-email", "/api/v1/auth/resend-verification",
			"/api/v1/auth/reset-password", "/api/v1/auth/forgot-password", "/api/v1/auth/docs", "/api/v1/auth/docs/**",
			"/api/v1/ec2/docs", "/api/v1/ec2/docs/**", "/api/v1/lambda/docs", "/api/v1/lambda/docs/**",
			"/api/v1/rds/docs", "/api/v1/rds/docs/**", "/api/v1/identity/docs", "/api/v1/identity/docs/**",
			"/api/v1/gamelift/docs", "/api/v1/gamelift/docs/**", "/api/v1/fargate/docs", "/api/v1/fargate/docs/**",
			"/api/v1/gateway/docs", "/api/v1/gateway/docs/**", "/api/v1/sagemaker/docs",
			"/api/v1/sagemaker/docs/**", "/api/v1/network/docs", "/api/v1/network/docs/**", "/api/v1/metrics/docs",
			"/api/v1/metrics/docs/**", "/api/v1/s3/docs", "/api/v1/s3/docs/**", "/api/v1/config/docs",
			"/api/v1/config/docs/**", "/api/v1/gateway/docs", "/api/v1/gateway/docs/**", "/api/v1/billing/docs",
			"/api/v1/billing/docs/**");

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		String path = exchange.getRequest().getURI().getPath();
		String requestId = UUID.randomUUID().toString();
		String userId = "x";
		String authMethod = "None";
		String role;

		// 1. Public endpoints — no auth
		// TODO: this is glue code,it should be removed
		// the issue is, since the docs comefrom the backend, and each backend has the
		// auth
		// middleware that need several haedersuserId, requestId, role, authMethod,
		// but since docs endpoints can be reached with a authentication, the middleware
		// in each microservice blocks the reques,
		// so we do somehting dangerous

		// TODO:
		// a cool idea, we could create a scheduler born to solve this problem,
		// instead of the frontend sending docs requests for each service,
		// we could have a poll here that polls each service and stores the docs
		// manifest in redis, the frontend be making one docs/manifest call and getting
		// allthe
		// manifest for allavailable services,
		// the pollis done onfirst run and then updaetdevery hour
		if (isPublicEndpoint(path)) {

			ServerHttpRequest mutatedRequest = exchange.getRequest().mutate().header("X-Auth-Method", "None")
					.header("X-User-Role", "USER").header("X-User-Id", "xx")
					.header("X-Request-Id", "public-req-" + requestId)

					.build();

			return chain.filter(exchange.mutate().request(mutatedRequest).build());
		}
		String credential = null;

		// 2. Try Authorization: Bearer <token>
		String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

		if (authHeader != null && authHeader.startsWith("Bearer ")) {
			credential = authHeader.substring(7).trim();
			authMethod = "jwt";
		}
// TODO: number 4 is above number 3 because the query authenticatorisnt working
// properly,for now we put the apikey 
// 4. Fallback to X-Api-Key
		if (credential == null || credential.isBlank()) {
			String apiKey = exchange.getRequest().getHeaders().getFirst("X-Api-Key");

			if (apiKey != null && !apiKey.isBlank()) {
				credential = apiKey.trim();
				authMethod = "api-key";
			}
		}

		// 3. Fallback to ?token=
		if (credential == null || credential.isBlank()) {
			String queryToken = exchange.getRequest().getQueryParams().getFirst("token");
			if (queryToken != null && !queryToken.isBlank()) {
				credential = queryToken.trim();
				authMethod = "query";
			}
		}

		

		// 5. No credentials found
		if (credential == null || credential.isBlank()) {
			return failAuth(exchange, path, "MISSING_CREDENTIALS");
		}

		// 6. Validate based on auth method

		switch (authMethod) {

		case "jwt":
		case "query":

			if (!jwtUtil.isTokenValid(credential)) {
				return failAuth(exchange, path, "JWT_SIGNATURE");
			}

			userId = jwtUtil.extractUserId(credential);
			role = jwtUtil.extractRole(credential);

			System.out.println("====> uwthMethod: " + userId + " Url: " + role);


			break;

		case "api-key":

			// Replace with your actual validation logic
			if (!jwtUtil.isApiKeyValid(credential)) {
				return failAuth(exchange, path, "INVALID_API_KEY");
			}

			userId = jwtUtil.extractUserIdFromApiKey(credential);
			role = "00000000-0000-0000-0000-000000000000".equals(userId) ? "SYSTEM" : jwtUtil.getUserRole(credential);
			break;

		default:
			return failAuth(exchange, path, "UNKNOWN_AUTH_METHOD");
		}

		log.info("[auth] userId={} role={} method={} path={}", userId, role, authMethod, path);

		// 7. Forward auth context downstream
		ServerHttpRequest mutatedRequest = exchange.getRequest().mutate().header("X-Auth-Method", authMethod)
				.header("X-Authorization", credential).header("X-User-Id", userId != null ? userId : "")
				.header("X-User-Role", role != null ? role : "").header("X-Request-Id", requestId)

				.build();

		return chain.filter(

				exchange.mutate().request(mutatedRequest).build()

		);
	}

	private Mono<Void> failAuth(ServerWebExchange exchange, String path, String type) {
		String clientIp = "unknown";
		if (exchange.getRequest().getRemoteAddress() != null
				&& exchange.getRequest().getRemoteAddress().getAddress() != null) {
			clientIp = exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
		}

		log.warn("Auth failed: {} | ,,path: {} | ip: {}", type, path, clientIp);

		natsService.publish("auth", "failure", java.util.Map.of("type", type, "path", path, "ip", clientIp, "timestamp",
				java.time.LocalDateTime.now().toString()));

		exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
		return exchange.getResponse().setComplete();
	}

	private boolean isPublicEndpoint(String path) {
		return PUBLIC_ENDPOINTS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
	}

	@Override
	public int getOrder() {
		return -1;
	}
}