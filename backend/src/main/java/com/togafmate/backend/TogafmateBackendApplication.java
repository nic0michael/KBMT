package com.togafmate.backend;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Starts the TOGAFmate backend application. */
@SpringBootApplication
@OpenAPIDefinition(info = @Info(title = "TOGAFmate Backend API", version = "v1"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class TogafmateBackendApplication {
    /** Runs the backend application. */
    public static void main(String[] args) {
        SpringApplication.run(TogafmateBackendApplication.class, args);
    }
}
