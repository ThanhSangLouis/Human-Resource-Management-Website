package org.example.hrmsystem.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    @GetMapping("/")
    public Map<String, String> root() {
        return publicStatus();
    }

    @GetMapping("/api/public/status")
    public Map<String, String> home() {
        return publicStatus();
    }

    private Map<String, String> publicStatus() {
        return Map.of(
                "app", "HRM API",
                "status", "running",
                "loginEndpoint", "/api/auth/login"
        );
    }
}
