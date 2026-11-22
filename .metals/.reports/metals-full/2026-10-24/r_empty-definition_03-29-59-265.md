error id: file://<WORKSPACE>/src/main/java/com/microservices/gateway/service/DocsManifestCache.java:org/springframework/stereotype/Component#
file://<WORKSPACE>/src/main/java/com/microservices/gateway/service/DocsManifestCache.java
empty definition using pc, found symbol in pc: org/springframework/stereotype/Component#
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 84
uri: file://<WORKSPACE>/src/main/java/com/microservices/gateway/service/DocsManifestCache.java
text:
```scala
package com.microservices.gateway.service;

import org.springframework.stereotype.Co@@mponent;
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






```


#### Short summary: 

empty definition using pc, found symbol in pc: org/springframework/stereotype/Component#