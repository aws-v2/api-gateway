error id: file://<WORKSPACE>/src/main/java/com/microservices/gateway/controller/PublicDocsController.java:_empty_/DocsService#getManifest#
file://<WORKSPACE>/src/main/java/com/microservices/gateway/controller/PublicDocsController.java
empty definition using pc, found symbol in pc: _empty_/DocsService#getManifest#
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 661
uri: file://<WORKSPACE>/src/main/java/com/microservices/gateway/controller/PublicDocsController.java
text:
```scala
package com.microservices.gateway.controller;

import java.util.Map;

import com.microservices.gateway.enums.DocType;
import com.microservices.gateway.service.DocsService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/gateway/docs")
public class PublicDocsController {

    private final DocsService docsService;

    public PublicDocsController(DocsService docsService) {
        this.docsService = docsService;
    }



    @GetMapping
    public ResponseEntity<?> getManifest() {
        return ResponseEntity.ok(
                Map.of("data", docsService.getMan@@ifest(DocType.PUBLIC)));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<?> getDoc(@PathVariable String slug) {
        return ResponseEntity.ok(
                Map.of("data", docsService.getDoc(DocType.PUBLIC, slug)));
    }
}
```


#### Short summary: 

empty definition using pc, found symbol in pc: _empty_/DocsService#getManifest#