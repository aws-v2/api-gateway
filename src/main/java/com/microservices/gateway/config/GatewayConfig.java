package com.microservices.gateway.config;

import com.microservices.gateway.filter.AdminRoleFilter;
import com.microservices.gateway.filter.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class GatewayConfig {

        @Autowired
        private JwtAuthenticationFilter jwtAuthenticationFilter;

        @Autowired
        private AdminRoleFilter adminRoleFilter;

        @Value("${api.version}")
        private String apiVersion;

        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

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
                                                                apiVersion + "/auth/payment/**",
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
                                                                "/api/v1/s3/buckets/**",
                                                                "/api/v1/s3/files/**",
                                                                "/api/v1/s3/presign/**",
                                                                "/api/v1/s3/batch/**",
                                                                "/api/v1/s3/prefix/**",
                                                                "/api/v1/s3/analytics/**",
                                                                "/api/v1/s3/docs/**",
                                                                "/api/v1/s3/search/**",
                                                                "/api/v1/s3/multipart/**",
                                                                "/api/v1/s3/webhooks/**",
                                                                "/api/v1/s3/security/**",
                                                                "/api/v1/s3/health/**")
                                                .filters(f -> f
                                                                .stripPrefix(0)
                                                                .filter(jwtAuthenticationFilter))
                                                .uri("lb://s3-service"))
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
                                                .path(
                                                                "/api/v1/lambda/**",
                                                                "/api/v1/lambda/health/**")
                                                .filters(f -> f
                                                                .stripPrefix(0)
                                                                .filter(jwtAuthenticationFilter)
                                                                .filter(adminRoleFilter))
                                                .uri("lb://lambda-service"))

                                // =================================================
                                // EC2 SERVICE
                                // =================================================

                                .route("ec2-service", r -> r
                                                .path(
                                                                "/api/v1/ec2/instances/**",
                                                                "/api/v1/ec2/snapshots/**",
                                                                "/api/v1/ec2/templates/**",
                                                                "/api/v1/ec2/ssh-keys/**",
                                                                "/api/v1/ec2/ip/**",
                                                                "/api/v1/ec2/security-groups/**",
                                                                "/api/v1/volumes/**")
                                                .filters(f -> f
                                                                .stripPrefix(0)
                                                                .filter(jwtAuthenticationFilter))
                                                .uri("lb://ec2-service"))

                                .build();
        }
}
