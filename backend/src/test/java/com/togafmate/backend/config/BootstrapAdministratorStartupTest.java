package com.togafmate.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;

import com.togafmate.backend.entity.UserPermission;
import com.togafmate.backend.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class BootstrapAdministratorStartupTest {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${ADMIN_USER}")
    private String configuredAdminUser;

    @Value("${ADMIN_USER_PASSWORD}")
    private String configuredAdminPassword;

    @Test
    void should_persistConfiguredAdministratorWithEncodedPassword_whenBackendStarts() {
        var administrator = userRepository.findById(configuredAdminUser).orElseThrow();

        assertThat(administrator.getPermissions()).contains(UserPermission.USER_ACCESS, UserPermission.ADMINISTRATOR_USER_ACCESS);
        assertThat(administrator.getPassword()).isNotEqualTo(configuredAdminPassword);
        assertThat(passwordEncoder.matches(configuredAdminPassword, administrator.getPassword())).isTrue();
    }
}
