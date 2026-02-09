package com.microservices.gateway.service;

import io.nats.client.Connection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NatsServiceTest {

    @Mock
    private Connection natsConnection;

    @InjectMocks
    private NatsService natsService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(natsService, "env", "test");
        ReflectionTestUtils.setField(natsService, "natsConnection", natsConnection);
    }

    @Test
    void publish_sendsMessageToCorrectSubject() throws Exception {
        String domain = "auth";
        String action = "failure";
        Map<String, String> payload = Map.of("key", "value");
        String expectedSubject = "test.gateway.v1.auth.failure";

        when(natsConnection.getStatus()).thenReturn(Connection.Status.CONNECTED);

        natsService.publish(domain, action, payload);

        verify(natsConnection, times(1)).publish(eq(expectedSubject), any(byte[].class));
    }

    @Test
    void publish_doesNotSendMessage_whenDisconnected() throws Exception {
        when(natsConnection.getStatus()).thenReturn(Connection.Status.DISCONNECTED);

        natsService.publish("domain", "action", "payload");

        verify(natsConnection, never()).publish(anyString(), any(byte[].class));
    }
}