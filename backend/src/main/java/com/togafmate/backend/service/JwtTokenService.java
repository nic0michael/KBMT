package com.togafmate.backend.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.togafmate.backend.controller.LoginResponse;
import com.togafmate.backend.controller.UserResponse;

/** Issues short-lived JWT access tokens after successful credential validation. */
@Service
public class JwtTokenService {
    private static final long TOKEN_VALIDITY_MINUTES = 15;
    private static final Logger LOGGER = LoggerFactory.getLogger(JwtTokenService.class);
    private final JwtEncoder jwtEncoder;

    /** Creates the token service. */
    public JwtTokenService(JwtEncoder jwtEncoder) {
        this.jwtEncoder = jwtEncoder;
    }

    /** Creates an access token for an authenticated user. */
    public LoginResponse issueToken(UserResponse user) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(TOKEN_VALIDITY_MINUTES, ChronoUnit.MINUTES);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("togafmate")
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(user.userID())
                .claim("authorities", user.permissions().stream().map(Enum::name).toList())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        LOGGER.info("Issued access token for user {} with expiry at {}", user.userID(), expiresAt);
        return new LoginResponse(user.userID(), List.copyOf(user.permissions()), token, "Bearer",
                TOKEN_VALIDITY_MINUTES * 60);
    }
}
