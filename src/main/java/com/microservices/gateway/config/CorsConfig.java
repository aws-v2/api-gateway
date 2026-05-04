package com.microservices.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;
@Configuration
public class CorsConfig {

    @Value("${spring.profiles.active}")
    private String profile;

    @Bean
    public CorsWebFilter corsWebFilter() {

        CorsConfiguration config = new CorsConfiguration();

        // ✅ 1. Origins (no duplicates, no overwrite)
        if ("dev".equals(profile)) {
            config.setAllowedOrigins(List.of(
                    "http://localhost:5173",
                    "http://localhost:8001",
                    "http://localhost:5174"
            ));
        } else if ("staging".equals(profile)) {
            config.setAllowedOrigins(List.of(
                    "http://localhost:5173",
                    "http://localhost:8001",
                    "http://localhost:5174",
                    "http://139.144.169.155:5173",
                    "http://139.144.169.155:5174",
                    "http://100.66.1.98:5173",
                    "http://100.66.1.98:5174"
            ));
        } else {
            config.setAllowedOrigins(List.of(
                    "http://139.144.169.155:5173",
                    "http://139.144.169.155:5174"
            ));
        }

        // ✅ 2. Methods
        config.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"
        ));

        // ✅ 3. IMPORTANT: explicit headers (NOT "*")
        config.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "X-Request-Source"
        ));

        // optional but safe
        config.setExposedHeaders(List.of(
                "Authorization",
                "Content-Type"
        ));

        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsWebFilter(source);
    }
}