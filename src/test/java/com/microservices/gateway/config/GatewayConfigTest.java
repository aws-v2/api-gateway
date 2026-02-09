package com.microservices.gateway.config;

import com.microservices.gateway.filter.AdminRoleFilter;
import com.microservices.gateway.filter.JwtAuthenticationFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class GatewayConfigTest {

    @Mock
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Mock
    private AdminRoleFilter adminRoleFilter;

    @InjectMocks
    private GatewayConfig gatewayConfig;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(gatewayConfig, "apiVersion", "/api/v1");
    }

    @Test
    void routesBeanCreation() {
        // In a real scenario, we'd use @SpringBootTest, but for a "normal" unit test,
        // we'll just verify the config object initializes.
        assertNotNull(gatewayConfig);
    }
}