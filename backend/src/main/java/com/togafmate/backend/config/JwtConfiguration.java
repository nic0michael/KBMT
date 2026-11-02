package com.togafmate.backend.config;

import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

/** Configures JWT signing and validation using a supplied Base64 secret. */
@Configuration
public class JwtConfiguration {
    public static final String ACCESS_TOKEN_COOKIE_NAME = "TOGAFMATE_ACCESS_TOKEN";

    /** Creates the signing key from the JWT_SECRET environment property. */
    @Bean
    public SecretKey jwtSecretKey(@Value("${app.jwt.secret}") String encodedSecret) {
        if (encodedSecret == null || encodedSecret.isBlank()) {
            throw new IllegalArgumentException("JWT_SECRET must be configured with a Base64-encoded secret");
        }
        byte[] secret = Base64.getDecoder().decode(encodedSecret);
        if (secret.length < 32) {
            throw new IllegalArgumentException("JWT_SECRET must be Base64-encoded and contain at least 32 bytes");
        }
        return new SecretKeySpec(secret, "HmacSHA256");
    }

    /** Creates the encoder used to issue HS256 tokens. */
    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    /** Creates the decoder used to validate HS256 tokens. */
    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSecretKey).macAlgorithm(MacAlgorithm.HS256).build();
    }
}
