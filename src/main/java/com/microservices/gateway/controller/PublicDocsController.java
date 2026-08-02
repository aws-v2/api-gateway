package com.microservices.gateway.controller;

import java.util.Map;

import com.microservices.gateway.enums.DocType;
import com.microservices.gateway.filter.JwtAuthenticationFilter;
import com.microservices.gateway.service.DocsService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/gateway")
public class PublicDocsController {

    private final DocsService docsService;

 
    public PublicDocsController(DocsService docsService) {
        this.docsService = docsService;
    }

@GetMapping("/health")
public ResponseEntity<?> healthHandler(){
    return ResponseEntity.ok(Map.of("ping","pong"));
}

    @GetMapping("/docs")
    public ResponseEntity<?> getManifest() {

        return ResponseEntity.ok(
                Map.of("data", docsService.getManifest(DocType.PUBLIC)));
    }

    @GetMapping("/docs/{slug}")
    public ResponseEntity<?> getDoc(@PathVariable String slug) {
        return ResponseEntity.ok(
                Map.of("data", docsService.getDoc(DocType.PUBLIC, slug)));
    }
}