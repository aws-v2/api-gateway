
package com.microservices.gateway.controller;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.microservices.gateway.dto.DocCategory;
import com.microservices.gateway.dto.DocManifest;
import com.microservices.gateway.dto.DocResponse;
import com.microservices.gateway.service.DocsService;
import com.microservices.gateway.service.DocsManifestCache;
import com.microservices.gateway.util.JwtUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

@RestController
@RequestMapping("/api/v1/gateway")
public class PublicDocsController {
    @Autowired
    private JwtUtil jwtUtil;

    private final DocsService docsService;

    public PublicDocsController(DocsService docsService) {
        this.docsService = docsService;
    }

    @Autowired
    private DocsManifestCache docsManifestCache;

    @GetMapping("/health")
    public ResponseEntity<?> healthHandler() {
        return ResponseEntity.ok(Map.of("ping", "pong"));
    }

    private static final Set<String> ELEVATED_ROLES = Set.of("ADMIN", "SYSTEM", "STAFF");

    @GetMapping("/docs/all-manifest")
    public ResponseEntity<?> getAllDocsManifests(ServerWebExchange exchange) {
        String role = resolveRole(exchange);
        System.out.println("The role is:" + role);
        boolean includeInternal = role != null && ELEVATED_ROLES.contains(role.toUpperCase());

        ConcurrentHashMap<String, DocManifest> cached = docsManifestCache.snapshot();
        Map<String, DocManifest> response = new ConcurrentHashMap<>();

        for (Map.Entry<String, DocManifest> entry : cached.entrySet()) {
            DocManifest source = entry.getValue();

            System.out.println("the source: " + source.toString());

            DocManifest filtered = new DocManifest();
            filtered.setService(source.getService());
            filtered.setApiVersion(source.getApiVersion());
            filtered.setScope(source.getScope());
            filtered.setPublicDocs(source.getPublicDocs());
            filtered.setInternal(includeInternal ? source.getInternal() : List.of());

            response.put(entry.getKey(), filtered);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * This controller is resolved directly by Spring's annotated-controller
     * dispatch, bypassing the Gateway's route/filter chain entirely (Spring's
     * RequestMappingHandlerMapping takes priority over Gateway's
     * RoutePredicateHandlerMapping for any path with a matching @GetMapping).
     * That means AuthenticationFilter never runs for this endpoint, so we
     * resolve the caller's role here directly, degrading to anonymous on any
     * failure — docs should never hard-fail on a bad/missing credential.
     */
    private String resolveRole(ServerWebExchange exchange) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            try {
                if (jwtUtil.isTokenValid(token)) {
                    return jwtUtil.extractRole(token);
                }
            } catch (Exception e) {
                // fall through to anonymous
            }
            return "USER";
        }

        String apiKey = exchange.getRequest().getHeaders().getFirst("X-Api-Key");
        if (apiKey != null && !apiKey.isBlank()) {
            try {
                if (jwtUtil.isApiKeyValid(apiKey)) {
                    String userId = jwtUtil.extractUserIdFromApiKey(apiKey);
                    return "00000000-0000-0000-0000-000000000000".equals(userId) ? "SYSTEM"
                            : jwtUtil.getUserRole(apiKey);
                }
            } catch (Exception e) {
                // fall through to anonymous
            }
            return "USER";
        }

        String queryToken = exchange.getRequest().getQueryParams().getFirst("token");
        if (queryToken != null && !queryToken.isBlank()) {
            try {
                if (jwtUtil.isTokenValid(queryToken)) {
                    return jwtUtil.extractRole(queryToken);
                }
            } catch (Exception e) {
                // fall through to anonymous
            }
        }

        return "USER";
    }

    @GetMapping("/docs")
    public ResponseEntity<?> getManifest(
            @RequestHeader(value = "Authorization", required = false) Optional<String> token) {
        String role = "USER";

        if (token.isPresent() && !token.get().isBlank()) {
            String raw = token.get().trim();
            if (raw.regionMatches(true, 0, "Bearer ", 0, 7)) {
                raw = raw.substring(7).trim();
            }
            role = jwtUtil.extractRole(raw);
        }

        try {
            if ("USER".equals(role)) {
                DocManifest publicManifest = docsService.getManifest(false);

                return ResponseEntity.ok(Map.of("data", Map.of(
                        "service", nullToEmpty(publicManifest.getService()),
                        "apiVersion", nullToEmpty(publicManifest.getApiVersion()),
                        "scope", "public",
                        "internal", List.<DocCategory>of(),
                        "public", publicManifest.getPublicDocs())));
            }

            // Administrative/internal roles get both manifests
            DocManifest publicManifest = docsService.getManifest(false);
            DocManifest internalManifest = docsService.getManifest(true);

            return ResponseEntity.ok(Map.of("data", Map.of(
                    "service", chooseString(publicManifest, internalManifest),
                    "apiVersion", chooseVersion(publicManifest, internalManifest),
                    "scope", "internal",
                    "internal", safeCategories(internalManifest),
                    "public", safeCategories(publicManifest))));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "failed to get docs manifest : " + e.getMessage()));
        }
    }

    @GetMapping("/docs/{slug}")
    public ResponseEntity<?> getDoc(@PathVariable String slug,
            @RequestHeader(value = "Authorization", required = false) Optional<String> token) {
        String role = "USER";

        if (token.isPresent() && !token.get().isBlank()) {
            String raw = token.get().trim();
            if (raw.regionMatches(true, 0, "Bearer ", 0, 7)) {
                raw = raw.substring(7).trim();
            }
            role = jwtUtil.extractRole(raw);
        }

        try {
            if ("USER".equals(role)) {
                DocResponse doc = docsService.getDoc(slug, false);
                return ResponseEntity.ok(Map.of("data", doc));
            }

            // Try internal doc first, fall back to public doc if not found
            DocResponse doc;
            try {
                doc = docsService.getDoc(slug, true);
            } catch (Exception e) {
                doc = docsService.getDoc(slug, false);
            }

            return ResponseEntity.ok(Map.of("data", doc));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "document not found"));
        }
    }

    // =========================
    // HELPERS — mirror the Go handler's chooseString/chooseVersion/safeCategories
    // =========================

    private List<DocCategory> safeCategories(DocManifest m) {
        if (m == null) {
            return List.of();
        }
        if (m.getPublicDocs() != null && !m.getPublicDocs().isEmpty()) {
            return m.getPublicDocs();
        }
        if (m.getInternal() != null && !m.getInternal().isEmpty()) {
            return m.getInternal();
        }
        return List.of();
    }

    private String chooseString(DocManifest a, DocManifest b) {
        if (a != null && a.getService() != null && !a.getService().isEmpty()) {
            return a.getService();
        }
        return b != null ? nullToEmpty(b.getService()) : "";
    }

    private String chooseVersion(DocManifest a, DocManifest b) {
        if (a != null && a.getApiVersion() != null && !a.getApiVersion().isEmpty()) {
            return a.getApiVersion();
        }
        return b != null ? nullToEmpty(b.getApiVersion()) : "";
    }

    private String nullToEmpty(String s) {
        return s != null ? s : "";
    }
}
