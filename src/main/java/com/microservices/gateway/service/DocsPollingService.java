package com.microservices.gateway.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import com.microservices.gateway.dto.DocManifest;
import com.microservices.gateway.dto.ApiEnvelope;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DocsPollingService {

    @Autowired
    private DiscoveryClient discoveryClient;

    @Autowired
    private DocsManifestCache cache;

    private final WebClient webClient = WebClient.builder().build();

    private static final String SYSTEM_USER_ID = "00000000-0000-0000-0000-000000000000";

    // Short, environment-agnostic keys mapped to their docs path. A
    // registered serviceId (e.g. "ec2-service", "ec2-service-staging",
    // "ec2-service-prod") is matched by checking whether it *contains*
    // one of these keys, rather than requiring an exact match — since
    // the same service registers under a different literal name per
    // environment.
    private static final Map<String, String> DOCS_PATHS = Map.ofEntries(
            Map.entry("ec2", "/api/v1/ec2/docs"),
            Map.entry("s3", "/api/v1/s3/docs"),
    Map.entry("lambda", "/api/v1/lambda/docs"),

            Map.entry("rds", "/api/v1/rds/docs"),
    Map.entry("llm", "/api/v1/llm/docs"),
    Map.entry("api", "/api/v1/gateway/docs")
    // ... one entry per service that exposes docs
    );

    @PostConstruct
    public void pollOnStartup() {
        log.info("[DocsPolling] running eager poll on startup");
        pollAll();
    }

    @Scheduled(fixedRateString = "${docs.poll-interval-ms:3600}")
    public void scheduledPoll() {
        log.info("[DocsPolling] running scheduled poll");
        pollAll();
    }

    private void pollAll() {
        List<String> serviceIds = discoveryClient.getServices();
        for (String serviceId : serviceIds) {
            String lowerServiceId = serviceId.toLowerCase();

            Optional<String> matchedKey = DOCS_PATHS.keySet().stream()
                    .filter(lowerServiceId::contains)
                    .findFirst();

            if (matchedKey.isEmpty()) {
                continue;
            }

            List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);
            if (!instances.isEmpty()) {
                pollOne(instances.get(0), matchedKey.get());
            }
        }
    }

    private void pollOne(ServiceInstance instance, String docsKey) {
        String serviceId = instance.getServiceId().toLowerCase();
        String docsPath = DOCS_PATHS.get(docsKey);
        String url = instance.getUri().toString() + docsPath + "?internal=true";

        webClient.get()
                .uri(url)
                .header("X-Auth-Method", "system")
                .header("X-User-Role", "SYSTEM")
                .header("X-User-Id", SYSTEM_USER_ID)
                .header("X-Authorization", SYSTEM_USER_ID)
                .header("X-Request-Id", "docs-poll-" + UUID.randomUUID())
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<ApiEnvelope<DocManifest>>() {
                })
                .timeout(Duration.ofSeconds(5))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        envelope -> {
                            DocManifest manifest = envelope.getData();
                            cache.put(docsKey, manifest);
                            log.info("[DocsPolling] updated manifest cache for {} (matched key: {})", serviceId,
                                    docsKey);
                        },
                        error -> {
                            log.warn("[DocsPolling] failed to poll {} at {}: {}", serviceId, url, error.getMessage());
                        });
    }

}