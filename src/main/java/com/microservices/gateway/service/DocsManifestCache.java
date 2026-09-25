package com.microservices.gateway.service;

import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentHashMap;
import com.microservices.gateway.dto.DocManifest;

@Component
public class DocsManifestCache {

    private final ConcurrentHashMap<String, DocManifest> cache = new ConcurrentHashMap<>();

    public void put(String serviceName, DocManifest manifest) {

        cache.put(serviceName, manifest);

    }

    public DocManifest get(String serviceName) {
        return cache.get(serviceName);
    }

    public ConcurrentHashMap<String, DocManifest> snapshot() {
        return new ConcurrentHashMap<>(cache);
    }
}





