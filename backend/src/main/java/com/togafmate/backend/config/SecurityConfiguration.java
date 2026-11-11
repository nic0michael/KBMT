package com.togafmate.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import jakarta.servlet.http.Cookie;

/** Configures authentication and permission checks for backend endpoints. */
@Configuration
public class SecurityConfiguration {
    /** Builds the password encoder used when saving and checking passwords. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Maps permission names carried by access tokens to Spring authorities. */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("authorities");
        authoritiesConverter.setAuthorityPrefix("");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }

    /** Accepts bearer headers and the short-lived login cookie used by Swagger UI. */
    @Bean
    public BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver headerResolver = new DefaultBearerTokenResolver();
        return request -> {
            String headerToken = headerResolver.resolve(request);
            if (headerToken != null) {
                return headerToken;
            }
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if (JwtConfiguration.ACCESS_TOKEN_COOKIE_NAME.equals(cookie.getName())) {
                        return cookie.getValue();
                    }
                }
            }
            return null;
        };
    }

    /** Applies public, authenticated, and administrator-only access rules. */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .bearerTokenResolver(bearerTokenResolver())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/health", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/users/register", "/api/users/logon", "/api/users/login",
                                "/api/users/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/users").hasAuthority("ADMINISTRATOR_USER_ACCESS")
                        .requestMatchers(HttpMethod.PUT, "/api/users/*")
                        .hasAuthority("ADMINISTRATOR_USER_ACCESS")
                        .requestMatchers(HttpMethod.PUT, "/api/users/*/permissions")
                        .hasAuthority("ADMINISTRATOR_USER_ACCESS")
                        .requestMatchers(HttpMethod.DELETE, "/api/users/*/permissions")
                        .hasAuthority("ADMINISTRATOR_USER_ACCESS")
                        .requestMatchers("/api/database/backups", "/api/database/backups/**")
                        .hasAuthority("ADMINISTRATOR_USER_ACCESS")
                        .requestMatchers(HttpMethod.GET, "/api/knowledge-bases")
                        .hasAnyAuthority("USER_ACCESS", "ADMINISTRATOR_USER_ACCESS")
                        .requestMatchers(HttpMethod.GET, "/api/knowledge-bases/*/pages/**")
                        .hasAnyAuthority("USER_ACCESS", "ADMINISTRATOR_USER_ACCESS")
                        .requestMatchers(HttpMethod.GET, "/api/knowledge-bases/*/files")
                        .hasAnyAuthority("USER_ACCESS", "ADMINISTRATOR_USER_ACCESS")
                        .requestMatchers(HttpMethod.GET, "/api/knowledge-bases/*/files/content")
                        .hasAnyAuthority("USER_ACCESS", "ADMINISTRATOR_USER_ACCESS")
                        .requestMatchers(HttpMethod.POST, "/api/knowledge-bases/*/pages")
                        .hasAuthority("ADMINISTRATOR_USER_ACCESS")
                        .requestMatchers(HttpMethod.POST, "/api/knowledge-bases/*/files")
                        .hasAuthority("ADMINISTRATOR_USER_ACCESS")
                        .requestMatchers(HttpMethod.POST, "/api/knowledge-bases/import")
                        .hasAuthority("ADMINISTRATOR_USER_ACCESS")
                        .requestMatchers(HttpMethod.GET, "/api/knowledge-bases/*/export")
                        .hasAuthority("ADMINISTRATOR_USER_ACCESS")
                        .requestMatchers(HttpMethod.GET, "/api/knowledge-bases/management")
                        .hasAuthority("ADMINISTRATOR_USER_ACCESS")
                        .requestMatchers(HttpMethod.POST, "/api/knowledge-bases")
                        .hasAuthority("ADMINISTRATOR_USER_ACCESS")
                        .requestMatchers(HttpMethod.PUT, "/api/knowledge-bases/*")
                        .hasAuthority("ADMINISTRATOR_USER_ACCESS")
                        .requestMatchers(HttpMethod.DELETE, "/api/knowledge-bases/*")
                        .hasAuthority("ADMINISTRATOR_USER_ACCESS")
                        .anyRequest().denyAll());
        return http.build();
    }
}
