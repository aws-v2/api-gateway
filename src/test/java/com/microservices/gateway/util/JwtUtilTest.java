package com.microservices.gateway.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private final JwtUtil jwtUtil = new JwtUtil();
    private final String secret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtUtil, "secret", secret);
        ReflectionTestUtils.setField(jwtUtil, "jwtExpiration", 3600000L);
    }

    @Test
    void generateToken_createsValidToken() {
        String username = "test@user.com";
        UUID userId = UUID.randomUUID();

        String token = jwtUtil.generateToken(username, userId);

        assertNotNull(token);
        assertTrue(jwtUtil.isTokenValid(token));
        assertEquals(username, jwtUtil.extractUsername(token));
        assertEquals(userId.toString(), jwtUtil.extractUserId(token));
    }

    @Test
    void getTokenHash_isConsistent() {
        String token = "some-random-token";
        String hash1 = jwtUtil.getTokenHash(token);
        String hash2 = jwtUtil.getTokenHash(token);

        assertEquals(hash1, hash2);
        assertNotNull(hash1);
    }
}
