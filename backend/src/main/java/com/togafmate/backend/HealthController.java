package com.togafmate.backend;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes a database-independent backend health endpoint. */
@RestController
@RequestMapping("/api")
public class HealthController {
    private static final Logger LOGGER = LoggerFactory.getLogger(HealthController.class);

    /** Returns the current process health status. */
    @GetMapping("/health")
    public Map<String, String> getHealth() {
        Map<String, String> health = Map.of("status", "UP");
        LOGGER.info("Backend health check completed with status {}", health.get("status"));
        return health;
    }
}
