package com.microservices.gateway.config;

import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.Duration;

@Configuration
public class NatsConfig {

    @Value("${nats.url:nats://localhost:4222}")
    private String natsUrl;

    @Value("${nats.username:}")
    private String natsUsername;

    @Value("${nats.password:}")
    private String natsPassword;

    @Bean
    public Connection natsConnection() throws IOException, InterruptedException {
        Options.Builder builder = new Options.Builder()
                .server(natsUrl)
                .connectionName("api-gateway")
                .maxReconnects(-1)
                .reconnectWait(Duration.ofSeconds(2));

        if (natsUsername != null && !natsUsername.isEmpty()) {
            builder.userInfo(natsUsername, natsPassword);
        }

        return Nats.connect(builder.build());
    }
}
