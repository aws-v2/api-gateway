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

        @Value("${sping.profiles.active}")
        private String profile;

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();

        if (profile.equals("dev")) {
            config.setAllowedOrigins(List.of(
                    "http://localhost:5173",
                    "http://localhost:5174"));
        } else if (profile.equals("staging")) {
            config.setAllowedOrigins(List.of(
                    "http://localhost:5173",
                    "http://localhost:5174",
                    "http://139.144.169.155:5173",
                    "http://139.144.169.155:5174"));
        } else if (profile.equals("prod")) {
            config.setAllowedOrigins(List.of(
                    "http://139.144.169.155:5173",
                    "http://139.144.169.155:5174"));
        }

        // ✅ FIX: remove "/login" — origins must NOT include paths
        config.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "http://localhost:5174",
                "http://139.144.169.155:5173",
                "http://139.144.169.155:5174"

        ));

        config.setAllowedMethods(List.of(
                "GET",
                "POST",
                "PUT",
                "DELETE",
                "OPTIONS",
                "PATCH"
        ));

        config.setAllowedHeaders(List.of("*"));

        // ⚠️ safer than exposing everything unless you really need it
        config.setExposedHeaders(List.of(
                "Authorization",
                "Content-Type"
        ));

        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        // ✅ Apply to ALL gateway routes
        source.registerCorsConfiguration("/**", config);

        return new CorsWebFilter(source);
    }
}