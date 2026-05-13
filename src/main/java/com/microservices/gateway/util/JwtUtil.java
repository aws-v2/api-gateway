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
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.SecretKey;

@Component
@Slf4j
public class JwtUtil {

    @Value("${jwt.secret:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}")
    private String secret;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String HMAC_ALGO = "HmacSHA256";

 
    @Value("${jwt.expiration:86400000}") // Default 24h
    private long jwtExpiration;

    public String generateToken(String username, java.util.UUID userId) {
        java.util.Map<String, Object> claims = new java.util.HashMap<>();
        claims.put("userId", userId.toString());
        return createToken(claims, username);
    }

    private String createToken(java.util.Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new java.util.Date(System.currentTimeMillis()))
                .expiration(new java.util.Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSignInKey())
                .compact();
    }

    public Claims extractAllClaims(String token) {
        return Jwts
                .parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public java.util.Date extractExpiration(String token) {
        return extractAllClaims(token).getExpiration();
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

    public boolean isTokenValid(String token) {
        try {
            extractAllClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String getTokenHash(String token) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return org.apache.commons.codec.binary.Hex.encodeHexString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Error hashing token", e);
        }
    }

    public String generateApiKey(String userId) {
        try {
            String payload = MAPPER.writeValueAsString(Map.of(
                    "userId", userId,
                    "iat", Instant.now().getEpochSecond()));

            String encodedPayload = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

            String signature = hmacSign(encodedPayload);

            return encodedPayload + "." + signature;

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate API key", e);
        }
    }
@PostConstruct
public void debugSecret() {
    log.info("[SECRET CHECK] class={} length={} first8='{}' last8='{}'",
        getClass().getSimpleName(),
        secret.length(),
        secret.substring(0, Math.min(8, secret.length())),
        secret.substring(Math.max(0, secret.length() - 8))
    );
}
    /**
     * Validate the HMAC signature of the incoming API key.
     * Returns false if the format is wrong or the signature doesn't match.
     */
    public boolean isApiKeyValid(String apiKey) {
debugSecret();
        log.info("Validating API key with this api keys {}", apiKey);

        if (apiKey == null || !apiKey.contains("ak_")) {
            return false;
        }
        log.info("Validating API key with this api keys1");

        try {

            // split payload.signature
            int dotIdx = apiKey.lastIndexOf('.');

            String encodedPayload = apiKey.substring(0, dotIdx).replace("ak_", "");
            String incomingSignature = apiKey.substring(dotIdx + 1);
            log.info("this is the encoded payload {}", encodedPayload);
            log.info("this is the incoming signature {}", incomingSignature);

            // recompute signature
            String expectedSignature = hmacSign(encodedPayload);

            // constant-time signature comparison
            boolean signatureValid = MessageDigest.isEqual(
                    incomingSignature.getBytes(StandardCharsets.UTF_8),
                    expectedSignature.getBytes(StandardCharsets.UTF_8));
            log.info("Validating API key with this api keys2");

            if (!signatureValid) {
                log.warn("Invalid API key signature {}", apiKey);
                return false;
            }
            log.info("Validating API key with this api keys3");

            // decode payload
            String decodedPayload = new String(
                    Base64.getUrlDecoder().decode(encodedPayload),
                    StandardCharsets.UTF_8);

            // expected format: userId:keyId
            String[] parts = decodedPayload.split(":");

            if (parts.length != 2) {
                log.warn("Invalid API key payload format");
                return false;
            }

            String userId = parts[0];
            String keyId = parts[1];

            log.info("API key valid for userId={}, keyId={}", userId, keyId);

            return true;

        } catch (Exception e) {

            log.error("API key validation failed: {}", e.getMessage());

            return false;
        }
    }

    /**
     * Decode the payload and return the userId.
     * Only call this after isApiKeyValid() returns true.
     */
    public String extractUserIdFromApiKey(String apiKey) {
        try {
            int dotIdx = apiKey.lastIndexOf('.');
            String encodedPayload = apiKey.substring(0, dotIdx).replace("ak_", "");

            String decodedPayload = new String(
                    Base64.getUrlDecoder().decode(encodedPayload),
                    StandardCharsets.UTF_8);

            String[] parts = decodedPayload.split(":");
            if (parts.length == 2) {
                return parts[0];
            }
            return null;
        } catch (Exception e) {
            log.error("API key validation failed: {}", e.getMessage());
            return null;
        }
    }

    // ── Private helper — signs the payload with HMAC-SHA256 ──────────────────────

    private String hmacSign(String data) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGO);
        SecretKeySpec keySpec = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                HMAC_ALGO);
        mac.init(keySpec);
        byte[] sig = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
    }

}
