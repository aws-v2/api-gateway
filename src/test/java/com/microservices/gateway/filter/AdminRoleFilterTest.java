package com.microservices.gateway.filter;

import com.microservices.gateway.util.JwtUtil;
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
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminRoleFilterTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private GatewayFilterChain chain;

    @InjectMocks
    private AdminRoleFilter adminRoleFilter;

    @Test
    void filter_allowsAdmin() {
        String token = "admin-token";
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/admin/reports")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(jwtUtil.isTokenValid(token)).thenReturn(true);
        when(jwtUtil.extractUsername(token)).thenReturn("admin@test.com");
        when(jwtUtil.extractRole(token)).thenReturn("ADMIN");
        when(jwtUtil.extractUserId(token)).thenReturn("admin123");
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(adminRoleFilter.filter(exchange, chain))
                .verifyComplete();

        verify(chain, times(1)).filter(any());
    }

    @Test
    void filter_rejectsNonAdmin() {
        String token = "user-token";
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/admin/reports")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(jwtUtil.isTokenValid(token)).thenReturn(true);
        when(jwtUtil.extractRole(token)).thenReturn("USER");

        StepVerifier.create(adminRoleFilter.filter(exchange, chain))
                .verifyComplete();

        assert exchange.getResponse().getStatusCode() == HttpStatus.FORBIDDEN;
    }
}