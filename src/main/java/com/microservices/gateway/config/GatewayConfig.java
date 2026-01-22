package com.microservices.gateway.config;

import com.microservices.gateway.filter.AdminRoleFilter;
import com.microservices.gateway.filter.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

        @Autowired
        private JwtAuthenticationFilter jwtAuthenticationFilter;

        @Autowired
        private AdminRoleFilter adminRoleFilter;

        @Value("${api.version}")
        private String apiVersion;

        @Bean
        public RouteLocator routes(RouteLocatorBuilder builder) {

                System.out.println(apiVersion + "------------------=");

                return builder.routes()

                                // =================================================
                                // AUTH SERVICE
                                // =================================================

                                // Public auth endpoints
                                .route("auth-public", r -> r
                                                .path(
                                                                apiVersion + "/auth/register",
                                                                apiVersion + "/auth/login",
                                                                apiVersion + "/auth/mfa/verify",
                                                                apiVersion + "/auth/forgot-password",
                                                                apiVersion + "/auth/reset-password",
                                                                apiVersion + "/auth/verify-email",
                                                                apiVersion + "/auth/resend-verification")
                                                .and()
                                                .method("POST", "GET")
                                                .filters(f -> f.stripPrefix(0))
                                                .uri("lb://auth-service"))

                                // Protected auth endpoints (JWT required)
                                .route("auth-protected", r -> r
                                                .path(
                                                                apiVersion + "/auth/mfa/enable",
                                                                apiVersion + "/auth/mfa/disable",
                                                                apiVersion + "/auth/logout",
                                                                apiVersion + "/auth/me",
                                                                apiVersion + "/auth/api-keys/**")
                                                .filters(f -> f
                                                                .stripPrefix(0)
                                                                .filter(jwtAuthenticationFilter))
                                                .uri("lb://auth-service"))

                                // =================================================
                                // BUCKET SERVICE
                                // =================================================

                                // Bucket APIs (JWT required)

                                .route("bucket-service", r -> r
                                                .path(
                                                                "/api/v1/buckets/**",
                                                                "/api/v1/files/**",
                                                                "/api/v1/presign/**",
                                                                "/api/v1/batch/**",
                                                                "/api/v1/prefix/**",
                                                                "/api/v1/analytics/**",
                                                                "/api/v1/search/**",
                                                                "/api/v1/multipart/**",
                                                                "/api/v1/webhooks/**")
                                                .filters(f -> f
                                                                .stripPrefix(2)
                                                                .filter(jwtAuthenticationFilter))
                                                .uri("lb://S3-SERVICE") // Changed to uppercase
                                )
                                // =================================================
                                // ADMIN SERVICE
                                // =================================================

                                .route("admin-reports", r -> r
                                                .path("/api/admin-reports/**")
                                                .filters(f -> f
                                                                .rewritePath(
                                                                                "/api/admin-reports/(?<segment>.*)",
                                                                                "/api/reports/${segment}")
                                                                .filter(jwtAuthenticationFilter)
                                                                .filter(adminRoleFilter))
                                                .uri("lb://admin-service"))

                                // =================================================
                                // FARGATe SERVICE
                                // =================================================

                                .route("fargate-service", r -> r
                                                .path("/api/v1/fargate/**")
                                                .filters(f -> f
                                                                .rewritePath(
                                                                                "/api/v1/fargate/(?<segment>.*)",
                                                                                "/api/v1/fargate/${segment}")
                                                                .filter(jwtAuthenticationFilter)
                                                                .filter(adminRoleFilter))
                                                .uri("lb://fargate-service"))

                                // =================================================
                                // LAMBDA SERVICE
                                // =================================================

                                .route("lambda-service", r -> r
                                                .path("/api/v1/lambda/**")
                                                .filters(f -> f
                                                                .rewritePath(
                                                                                "/api/v1/lambda/(?<segment>.*)",
                                                                                "/api/v1/lambda/${segment}")
                                                                .filter(jwtAuthenticationFilter)
                                                                .filter(adminRoleFilter))
                                                .uri("lb://lambda-service"))

                                .build();
        }
}
