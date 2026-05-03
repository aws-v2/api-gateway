package com.microservices.gateway.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
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
public class JwtUtil {

    @Value("${jwt.secret:367566B5970}")
    private String secret;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String HMAC_ALGO = "HmacSHA256";

    @Value("${api.key.secret}") // add api.key.secret=<strong-random> to application.yml
    private String apiKeySecret;

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

    /**
     * Validate the HMAC signature of the incoming API key.
     * Returns false if the format is wrong or the signature doesn't match.
     */
    public boolean isApiKeyValid(String apiKey) {
        if (apiKey == null || !apiKey.contains("."))
            return false;

        try {
            int dotIdx = apiKey.lastIndexOf('.');
            String payload = apiKey.substring(0, dotIdx);
            String incomingSig = apiKey.substring(dotIdx + 1);
            String expectedSig = hmacSign(payload);

            // Constant-time comparison — avoids timing attacks
            return MessageDigest.isEqual(
                    incomingSig.getBytes(StandardCharsets.UTF_8),
                    expectedSig.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
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
            String encodedPayload = apiKey.substring(0, dotIdx);

            byte[] decoded = Base64.getUrlDecoder().decode(encodedPayload);
            Map<?, ?> payload = MAPPER.readValue(decoded, Map.class);

            return (String) payload.get("userId");

        } catch (Exception e) {
            return null;
        }
    }

    // ── Private helper — signs the payload with HMAC-SHA256 ──────────────────────

    private String hmacSign(String data) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGO);
        SecretKeySpec keySpec = new SecretKeySpec(
                apiKeySecret.getBytes(StandardCharsets.UTF_8),
                HMAC_ALGO);
        mac.init(keySpec);
        byte[] sig = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
    }

}
