package com.togafmate.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Value;
import java.time.Duration;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import com.togafmate.backend.service.UserService;
import com.togafmate.backend.service.UserAlreadyExistsException;
import com.togafmate.backend.service.JwtTokenService;
import com.togafmate.backend.config.JwtConfiguration;

/** Exposes user registration, logon, listing, and permission management endpoints. */
@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;
    private final JwtTokenService jwtTokenService;
    private final boolean jwtCookieSecure;

    /** Creates the user controller with its application service. */
    public UserController(UserService userService, JwtTokenService jwtTokenService,
            @Value("${app.jwt.cookie-secure:true}") boolean jwtCookieSecure) {
        this.userService = userService;
        this.jwtTokenService = jwtTokenService;
        this.jwtCookieSecure = jwtCookieSecure;
    }

    /** Lists registered users and their permissions. */
    @GetMapping
    @SecurityRequirement(name = "bearerAuth")
    public List<UserResponse> getUsers() {
        return userService.findAll();
    }

    /** Updates the identifier and optional password of the identified user. */
    @PutMapping("/{userID}")
    @SecurityRequirement(name = "bearerAuth")
    public UserResponse updateUser(@PathVariable String userID, @RequestBody UserUpdateRequest request) {
        if (request == null || request.userID() == null || request.userID().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User ID is required");
        }
        try {
            return userService.updateUser(userID, request.userID(), request.password());
        } catch (UserAlreadyExistsException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        }
    }

    /** Registers a new user with no access by default. */
    @PostMapping("/register")
    @ApiResponse(responseCode = "201", description = "User registered successfully")
    public ResponseEntity<UserResponse> register(@RequestBody UserCredentialsRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Credentials are required");
        }
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(userService.register(request.userID(), request.password()));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    /** Verifies a user's credentials without returning the password. */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody UserCredentialsRequest request) {
        return authenticate(request);
    }

    /** Verifies credentials using the legacy logon path. */
    @PostMapping("/logon")
    public ResponseEntity<LoginResponse> logon(@RequestBody UserCredentialsRequest request) {
        return authenticate(request);
    }

    /** Clears the browser's login cookie. */
    @PostMapping("/logout")
    @ApiResponse(responseCode = "204", description = "Logout successful")
    public ResponseEntity<Void> logout() {
        ResponseCookie cookie = accessTokenCookie("", Duration.ZERO);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie.toString()).build();
    }

    private ResponseEntity<LoginResponse> authenticate(UserCredentialsRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Credentials are required");
        }
        UserResponse user = userService.logon(request.userID(), request.password())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        LoginResponse response = jwtTokenService.issueToken(user);
        ResponseCookie cookie = accessTokenCookie(response.accessToken(), Duration.ofSeconds(response.expiresIn()));
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(response);
    }

    private ResponseCookie accessTokenCookie(String value, Duration maxAge) {
        return ResponseCookie.from(JwtConfiguration.ACCESS_TOKEN_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(jwtCookieSecure)
                .sameSite("Strict")
                .path("/api")
                .maxAge(maxAge)
                .build();
    }

    /** Adds a permission to the identified user. */
    @PutMapping("/{userID}/permissions")
    @SecurityRequirement(name = "bearerAuth")
    public UserResponse addPermission(@PathVariable String userID, @RequestBody PermissionRequest request) {
        validatePermissionRequest(request);
        return updateExistingUser(() -> userService.addPermission(userID, request.permission()));
    }

    /** Removes a permission from the identified user. */
    @DeleteMapping("/{userID}/permissions")
    @SecurityRequirement(name = "bearerAuth")
    public UserResponse removePermission(@PathVariable String userID, @RequestBody PermissionRequest request) {
        validatePermissionRequest(request);
        return updateExistingUser(() -> userService.removePermission(userID, request.permission()));
    }

    private void validatePermissionRequest(PermissionRequest request) {
        if (request == null || request.permission() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Permission is required");
        }
    }

    private UserResponse updateExistingUser(java.util.function.Supplier<UserResponse> operation) {
        try {
            return operation.get();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        }
    }
}
