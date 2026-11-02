package com.togafmate.backend.config;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.togafmate.backend.entity.User;
import com.togafmate.backend.entity.UserPermission;
import com.togafmate.backend.repository.UserRepository;

/** Provisions the configured administrator from runtime environment settings. */
@Component
public class BootstrapAdministrator implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(BootstrapAdministrator.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String userID;
    private final String password;

    /** Creates the optional bootstrap administrator runner. */
    public BootstrapAdministrator(UserRepository userRepository, PasswordEncoder passwordEncoder,
            @Value("${ADMIN_USER:${APP_BOOTSTRAP_ADMIN_USER_ID:}}") String userID,
            @Value("${ADMIN_USER_PASSWORD:${APP_BOOTSTRAP_ADMIN_PASSWORD:}}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userID = userID;
        this.password = password;
    }

    /** Creates or reconciles the configured administrator when both environment values are configured. */
    @Override
    public void run(ApplicationArguments args) {
        if (userID.isBlank() && password.isBlank()) {
            return;
        }
        if (userID.isBlank() || password.isBlank()) {
            throw new IllegalStateException("Both bootstrap administrator settings must be configured");
        }
        Optional<User> configuredUser = userRepository.findById(userID);
        if (configuredUser.isEmpty()) {
            userRepository.save(new User(userID, passwordEncoder.encode(password),
                    List.of(UserPermission.USER_ACCESS, UserPermission.ADMINISTRATOR_USER_ACCESS)));
            LOGGER.info("Bootstrapped administrator account");
            return;
        }

        User user = configuredUser.get();
        boolean updated = false;
        if (!user.getPermissions().contains(UserPermission.USER_ACCESS)) {
            user.addPermission(UserPermission.USER_ACCESS);
            updated = true;
        }
        if (user.getPermissions().contains(UserPermission.ADMINISTRATOR_ACCESS)) {
            user.removePermission(UserPermission.ADMINISTRATOR_ACCESS);
            updated = true;
        }
        if (!user.getPermissions().contains(UserPermission.ADMINISTRATOR_USER_ACCESS)) {
            user.addPermission(UserPermission.ADMINISTRATOR_USER_ACCESS);
            updated = true;
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            user.changePassword(passwordEncoder.encode(password));
            updated = true;
        }
        if (updated) {
            userRepository.save(user);
            LOGGER.info("Reconciled configured administrator account");
        }
    }
}
