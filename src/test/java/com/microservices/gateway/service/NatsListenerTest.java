package com.microservices.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NatsListenerTest {

    @Mock
    private NatsService natsService;

    @Mock
    private Connection natsConnection;

    @Mock
    private Dispatcher dispatcher;

    @Mock
    private RedisService redisService;

    @InjectMocks
    private NatsListener natsListener;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(natsListener, "env", "test");
        when(natsService.getConnection()).thenReturn(natsConnection);
        when(natsConnection.createDispatcher(any())).thenReturn(dispatcher);
    }

    @Test
    void setupListeners_subscribesToExpectedSubjects() {
        natsListener.setupListeners();

        verify(dispatcher, times(1)).subscribe(eq("test.auth.v1.token.blacklisted"),
                any(io.nats.client.MessageHandler.class));
        verify(dispatcher, times(1)).subscribe(eq("test.auth.v1.apikey.created"),
                any(io.nats.client.MessageHandler.class));
        verify(dispatcher, times(1)).subscribe(eq("test.auth.v1.apikey.revoked"),
                any(io.nats.client.MessageHandler.class));
    }
}