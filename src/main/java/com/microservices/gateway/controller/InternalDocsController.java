package com.microservices.gateway.controller;

import java.util.Map;

import com.microservices.gateway.enums.DocType;
import com.microservices.gateway.service.DocsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/gateway/internal/docs")
public class InternalDocsController {

    private final DocsService docsService;

    public InternalDocsController(DocsService docsService) {
        this.docsService = docsService;
    }

    @GetMapping
    public ResponseEntity<?> getManifest() {
        return ResponseEntity.ok(
            Map.of("data", docsService.getManifest(DocType.INTERNAL))
        );
    }

    @GetMapping("/{slug}")
    public ResponseEntity<?> getDoc(@PathVariable String slug) {
        return ResponseEntity.ok(
            Map.of("data", docsService.getDoc(DocType.INTERNAL, slug))
        );
    }
}