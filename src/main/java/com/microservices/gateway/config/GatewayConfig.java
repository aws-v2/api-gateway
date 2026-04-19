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
                                                                apiVersion + "/auth/docs", // ← public, no auth
                                                                apiVersion + "/auth/docs/**", // ← public slugs, no auth
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
                                                                apiVersion + "/auth/internal/docs", // ← protected
                                                                apiVersion + "/auth/internal/docs/**", // ← protected
                                                                                                       // slugs
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
                                // IAM SERVICE
                                // =================================================

                                .route("iam-docs", r -> r
                                                .path("/api/v1/identity/**")

                                                .uri("lb://iam"))

                                // =================================================
                                // CONFIG SERVICE
                                // =================================================

                                // Public — no auth
                                .route("config-docs-public", r -> r
                                                .path(
                                                                "/api/v1/config/docs",
                                                                "/api/v1/config/docs/**")
                                                .and()
                                                .method("GET")
                                                .filters(f -> f.stripPrefix(0))
                                                .uri("lb://config-server"))

                                // Protected — JWT required
                                .route("config-internal-protected", r -> r
                                                .path("/api/v1/config/internal/**",
                                                                "/api/v1/config/internal/docs/**")
                                                .filters(f -> f
                                                                .stripPrefix(0)
                                                                .filter(jwtAuthenticationFilter))
                                                .uri("lb://config-server"))

                                // =================================================
                                // METRICS SERVICE
                                // =================================================

                                .route("metrics-docs", r -> r
                                                .path("/api/v1/metrics/docs/**",
                                                                "/api/v1/metrics/internal/docs/**")

                                                .uri("lb://metrics-service"))
                                // =================================================
                                // Networking docs
                                // =================================================
                                .route("networking-docs", r -> r
                                                .path("/api/v1/network/**",
                                                                "/api/v1/network/docs/**",
                                                                "/api/v1/network/internal/docs/**")

                                                .uri("lb://network-service"))

                                // =================================================
                                // Billing SERVICE
                                // =================================================

                                .route("billing-service", r -> r
                                                .path("/api/v1/billing/**",
                                                                "/api/v1/billing/docs/**",
                                                                "/api/v1/billing/internal/docs/**")

                                                .uri("lb://billing-service"))

                                // =================================================
                                // FARGATe SERVICE
                                // =================================================

                                .route("fargate-service", r -> r
                                                .path("/api/v1/fargate/**",
                                                                "/api/v1/fargate/docs/**",
                                                                "/api/v1/fargate/internal/docs/**")

                                                .uri("lb://fargate-service"))

                                // =================================================
                                // LAMBDA SERVICE
                                // =================================================

                                // Public docs — no auth
                                .route("lambda-docs-public", r -> r
                                                .path(
                                                                apiVersion + "/lambda/docs",
                                                                apiVersion + "/lambda/docs/**")
                                                .filters(f -> f.stripPrefix(0))
                                                .uri("lb://lambda-service"))

                                // Internal docs — JWT required
                                .route("lambda-docs-internal", r -> r
                                                .path(
                                                                apiVersion + "/lambda/internal/docs",
                                                                apiVersion + "/lambda/internal/docs/**")
                                                .filters(f -> f
                                                                .stripPrefix(0)
                                                                .filter(jwtAuthenticationFilter))
                                                .uri("lb://lambda-service"))

                                // Protected lambda endpoints — JWT + admin role required
                                .route("lambda-service", r -> r
                                                .path(
                                                                apiVersion + "/lambda/**",
                                                                apiVersion + "/lambda/scaling-policies/**",
                                                                apiVersion + "/lambda/health/**")
                                                .filters(f -> f
                                                                .stripPrefix(0)
                                                                .filter(jwtAuthenticationFilter)
                                                                .filter(adminRoleFilter))
                                                .uri("lb://lambda-service"))

                                // EC2 public docs - no auth
                                .route("ec2-docs-public", r -> r
                                                .path("/api/v1/ec2/docs/**")
                                                .filters(f -> f.stripPrefix(0))
                                                .uri("lb://ec2-service"))

                                // EC2 internal docs - auth required
                                .route("ec2-docs-internal", r -> r
                                                .path("/api/v1/ec2/internal/docs/**")
                                                .filters(f -> f
                                                                .stripPrefix(0)
                                                                .filter(jwtAuthenticationFilter))
                                                .uri("lb://ec2-service"))

                                // EC2 everything else - auth required
                                .route("ec2-service", r -> r
                                                .path(
                                                                "/api/v1/ec2/instances/**",
                                                                "/api/v1/ec2/scaling-policies/**",
                                                                "/api/v1/compute/docs/**",
                                                                "/api/v1/compute/fleet/**",
                                                                "/api/v1/compute/instances/**",
                                                                "/api/v1/compute/**",
                                                                "/api/v1/ec2/snapshots/**",
                                                                "/api/v1/ec2/templates/**",
                                                                "/api/v1/ec2/vpcs/**",
                                                                "/api/v1/ec2/ssh-keys/**",
                                                                "/api/v1/ec2/ip/**",
                                                                "/api/v1/ec2/security-groups/**",
                                                                "/api/v1/ec2/volumes/**")
                                                .filters(f -> f
                                                                .stripPrefix(0)
                                                                .filter(jwtAuthenticationFilter))
                                                .uri("lb://ec2-service"))
                                // =================================================
                                // AI ORCHESTRATOR SERVICE
                                // =================================================

                                .route("ai-orchestrator", r -> r
                                                .path(
                                                                "/api/v1/jobs/**",
                                                                "/api/v1/jobs")
                                                .filters(f -> f
                                                                .stripPrefix(0)
                                                                .filter(jwtAuthenticationFilter))
                                                .uri("lb://ai-orchestrator"))

                                // =================================================
                                // GAMELIFT SERVICE
                                // =================================================

                                // Public docs — no auth
                                .route("gamelift-docs-public", r -> r
                                                .path(
                                                                apiVersion + "/gamelift/docs",
                                                                apiVersion + "/gamelift/docs/**")
                                                .filters(f -> f.stripPrefix(0))
                                                .uri("lb://gamelift-server"))

                                // Internal docs — JWT required
                                .route("gamelift-docs-internal", r -> r
                                                .path(
                                                                apiVersion + "/gamelift/internal/docs",
                                                                apiVersion + "/gamelift/internal/docs/**")
                                                .filters(f -> f
                                                                .stripPrefix(0)
                                                                .filter(jwtAuthenticationFilter))
                                                .uri("lb://gamelift-server"))

                                // Protected game endpoints — JWT required
                                .route("gamelift-server", r -> r
                                                .path(
                                                                apiVersion + "/gamelift/**",
                                                                apiVersion + "/ws/**",
                                                                apiVersion + "/webrtc/**")
                                                .filters(f -> f
                                                                .filter(jwtAuthenticationFilter))
                                                .uri("lb://gamelift-server"))

                                // =================================================
                                // RDS SERVICE
                                // =================================================
                                .route("rds", r -> r
                                                .path(
                                                                "/api/v1/rds/databases/**",
                                                                "/api/v1/rds/databases",
                                                                "/api/v1/rds/scaling-policies/**",
                                                                "/api/v1/rds/snapshots/**",
                                                                "/api/v1/rds/docs/**",
                                                                "/api/v1/rds/vpcs/**",
                                                                "/api/v1/rds/vpcs",
                                                                "/api/v1/rds/volumes/**",
                                                                "/api/v1/rds/volumes")
                                                .filters(f -> f
                                                                .stripPrefix(0)
                                                                .filter(jwtAuthenticationFilter))
                                                .uri("lb://rds-service"))

                                .build();
        }
}
