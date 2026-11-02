package com.togafmate.backend;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Configures cross-origin access for the Angular frontend. */
@Configuration
public class CorsConfiguration implements WebMvcConfigurer {
    private final String[] allowedOrigins;

    /** Creates the CORS configuration with the configured frontend origins. */
    public CorsConfiguration(@Value("${app.cors.allowed-origins:http://localhost:4200}") String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    /** Registers allowed origins, request methods, and headers for API routes. */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
