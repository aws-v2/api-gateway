package com.microservices.gateway.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.SecretKey;

@Component
@Slf4j
public class JwtUtil {

    @Value("${jwt.secret:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}")
    private String secret;

    @Value("${jwt.expiration:86400000}")
    private long jwtExpiration;

    private static final String HMAC_ALGO = "HmacSHA256";

    // ── Internal record ───────────────────────────────────────────────────────────

    private record ApiKeyPayload(String userId, String keyId, String role) {}

    // ── JWT ───────────────────────────────────────────────────────────────────────

    public String generateToken(String username, UUID userId) {
        java.util.Map<String, Object> claims = new java.util.HashMap<>();
        claims.put("userId", userId.toString());
        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(new java.util.Date(System.currentTimeMillis()))
                .expiration(new java.util.Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSignInKey())
                .compact();
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    public String extractUserId(String token) {
        return extractAllClaims(token).get("userId", String.class);
    }

    public java.util.Date extractExpiration(String token) {
        return extractAllClaims(token).getExpiration();
    }

    public boolean isTokenValid(String token) {
        try {
            extractAllClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String getTokenHash(String token) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return org.apache.commons.codec.binary.Hex.encodeHexString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Error hashing token", e);
        }
    }

    // ── API Key generation ────────────────────────────────────────────────────────

    /**
     * Generates an API key with format: ak_<base64(userId:keyId:role)>.<hmac>
     */
    public String generateApiKey(String userId, String role) {
        try {
            String raw     = userId + ":" + UUID.randomUUID() + ":" + role;
            String encoded = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
            return "ak_" + encoded + "." + hmacSign(encoded);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate API key", e);
        }
    }

    // ── API Key validation & extraction ──────────────────────────────────────────

    public boolean isApiKeyValid(String apiKey) {
        debugSecret();
        return parseApiKey(apiKey) != null;
    }

    public String getUserId(String apiKey) {
        debugSecret();
        ApiKeyPayload p = parseApiKey(apiKey);
        return p != null ? p.userId() : "unknown";
    }

    public String getUserRole(String apiKey) {
        debugSecret();
        ApiKeyPayload p = parseApiKey(apiKey);
        return p != null ? p.role() : "unknown";
    }

    /** Skips signature revalidation — only call after isApiKeyValid() returns true. */
    public String extractUserIdFromApiKey(String apiKey) {
        ApiKeyPayload p = parseApiKey(apiKey);
        return p != null ? p.userId() : null;
    }

    // ── Private helpers ───────────────────────────────────────────────────────────

    /**
     * Parses, validates HMAC signature, and decodes the API key payload.
     * Returns null on any failure.
     */
    private ApiKeyPayload parseApiKey(String apiKey) {
        if (apiKey == null || !apiKey.contains("ak_")) return null;
        try {
            int dotIdx = apiKey.lastIndexOf('.');
            if (dotIdx < 0) return null;

            String encodedPayload    = apiKey.substring(0, dotIdx).replace("ak_", "");
            String incomingSignature = apiKey.substring(dotIdx + 1);

            log.info("[API-KEY] encodedPayload={} incomingSignature={}", encodedPayload, incomingSignature);

            boolean signatureValid = MessageDigest.isEqual(
                    incomingSignature.getBytes(StandardCharsets.UTF_8),
                    hmacSign(encodedPayload).getBytes(StandardCharsets.UTF_8));

            if (!signatureValid) {
                log.warn("[API-KEY] Invalid signature for key={}", apiKey);
                return null;
            }

            String decoded = new String(Base64.getUrlDecoder().decode(encodedPayload), StandardCharsets.UTF_8);
            String[] parts = decoded.split(":");

            if (parts.length < 2) {
                log.warn("[API-KEY] Invalid payload format decoded={}", decoded);
                return null;
            }

            // backward compat: old keys are userId:keyId (2 parts), new keys are userId:keyId:role
            String role = parts.length >= 3 ? parts[2] : "user";

            log.info("[API-KEY] Parsed userId={} keyId={} role={}", parts[0], parts[1], role);
            return new ApiKeyPayload(parts[0], parts[1], role);

        } catch (Exception e) {
            log.error("[API-KEY] Parse error: {}", e.getMessage());
            return null;
        }
    }

    private String hmacSign(String data) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGO);
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    private SecretKey getSignInKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    @PostConstruct
    public void debugSecret() {
        log.info("[SECRET] class={} length={} first8='{}' last8='{}'",
                getClass().getSimpleName(), secret.length(),
                secret.substring(0, Math.min(8, secret.length())),
                secret.substring(Math.max(0, secret.length() - 8)));
    }
}