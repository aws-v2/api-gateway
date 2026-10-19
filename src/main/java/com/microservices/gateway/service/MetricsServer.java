package com.microservices.gateway.service;


import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Executors;

/** Serves /metrics on its own port, protected by an API key (X-Api-Key). */
@Component
public class MetricsServer {

    private static final Logger log = LoggerFactory.getLogger(MetricsServer.class);

    private final PrometheusMeterRegistry registry;
    private final int port;
    private final byte[] wantHash;   // null when no key is configured
    private HttpServer server;

    public MetricsServer(PrometheusMeterRegistry registry,
                         @Value("${metrics.server.port}") int port,
                         @Value("${metrics.server.api-key:}") String apiKey) {
        this.registry = registry;
        this.port = port;
        this.wantHash = apiKey.isBlank() ? null : sha256(apiKey);
    }

    @PostConstruct
    void start() throws IOException {
        if (wantHash == null) {
            // never expose metrics open because an env var was missing
            log.error("metrics server NOT started: metrics.server.api-key is empty");
            return;
        }
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/metrics", this::handle);
        server.setExecutor(Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "metrics-http");
            t.setDaemon(true);
            return t;
        }));
        server.start();
        log.info("metrics server started on port {}", port);
    }

    @PreDestroy
    void stop() {
        if (server != null) server.stop(0);
    }

    private void handle(HttpExchange ex) throws IOException {
        try (ex) {
            if (!"GET".equals(ex.getRequestMethod())) {
                ex.sendResponseHeaders(405, -1);
                return;
            }
            String key = ex.getRequestHeaders().getFirst("X-Api-Key");
            // hash both sides so lengths match, then compare in constant time
            if (key == null || !MessageDigest.isEqual(sha256(key), wantHash)) {
                ex.sendResponseHeaders(401, -1);
                return;
            }
            byte[] body = registry.scrape().getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
        }
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
