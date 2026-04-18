package com.microservices.gateway.service;

import io.nats.client.Connection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NatsListenerTest {

    @Mock
    private NatsService natsService;

    @Mock
    private Connection natsConnection;

    @InjectMocks
    private NatsListener natsListener;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(natsListener, "env", "test");
        when(natsService.getConnection()).thenReturn(natsConnection);
    }

    @Test
    void setupListeners_checksConnection() {
        natsListener.setupListeners();
        verify(natsService, times(1)).getConnection();
    }
}