package com.usic.uniFex.web;

import java.time.Instant;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "time", Instant.now().toString());
    }

    @GetMapping("/api/version")
    public Map<String, Object> version() {
        return Map.of(
                "name", "Super Feria – Guía Interactiva",
                "version", "0.0.1",
                "commit", "dev");
    }
}
