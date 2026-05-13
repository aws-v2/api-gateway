package com.microservices.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class CorsConfig {

    @Value("${spring.profiles.active}")
    private String profile;

    // comma separated ips
    // example:
    // app.tailscale.ips=100.66.1.98,100.70.2.11
    @Value("#{'${app.tailscale.ips:}'.split(',')}")
    private List<String> tailscaleIps;

    @Bean
    public CorsWebFilter corsWebFilter() {

        CorsConfiguration config = new CorsConfiguration();

        List<String> origins = new ArrayList<>();

        // =========================
        // DEV
        // =========================
        if ("dev".equals(profile)) {
            origins.addAll(List.of(
                    "http://localhost:5173",
                    "http://localhost:8001",
                    "http://localhost:5174",
                    "https://frontend-server-staging.onrender.com"  // <-- add this

            ));
        }

        // =========================
        // STAGING
        // =========================
      // =========================
// STAGING
// =========================
else if ("staging".equals(profile)) {
    origins.addAll(List.of(
            "http://localhost:5173",
            "http://localhost:8001",
            "http://localhost:5174",
            "http://139.144.169.155:5173",
            "http://139.144.169.155:5174",
            "https://frontend-server-staging.onrender.com"  // <-- add this
    ));
}

        // =========================
        // PROD
        // =========================
        else {
            origins.addAll(List.of(
                    "http://139.144.169.155:5173",
                    "http://139.144.169.155:5174"
            ));
        }

        // =========================
        // TAILSCALE IPS (ALL PROFILES)
        // =========================
        for (String ip : tailscaleIps) {

            ip = ip.trim();

            if (ip.isBlank()) {
                continue;
            }

            origins.add("http://" + ip + ":5173");
            origins.add("http://" + ip + ":5174");

            // optional https
            origins.add("https://" + ip + ":5173");
            origins.add("https://" + ip + ":5174");
        }

        config.setAllowedOrigins(origins);

        // METHODS
        config.setAllowedMethods(List.of(
                "GET",
                "POST",
                "PUT",
                "DELETE",
                "OPTIONS",
                "PATCH"
        ));

        // HEADERS
        config.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "X-Request-Source"
        ));

        config.setExposedHeaders(List.of(
                "Authorization",
                "Content-Type"
        ));

        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", config);

        return new CorsWebFilter(source);
    }
}