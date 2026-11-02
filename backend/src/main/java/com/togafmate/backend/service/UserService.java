package com.togafmate.backend.service;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.togafmate.backend.controller.UserResponse;
import com.togafmate.backend.entity.User;
import com.togafmate.backend.entity.UserPermission;
import com.togafmate.backend.repository.UserRepository;

/** Provides account registration, logon, listing, and permission management operations. */
@Service
public class UserService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /** Creates the user service with persistence and password encoding dependencies. */
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Registers a user with an encoded password and no access. */
    public UserResponse register(String userID, String password) {
        if (userID == null || userID.isBlank() || password == null || password.isBlank()) {
            LOGGER.warn("User registration rejected because required credentials were missing");
            throw new IllegalArgumentException("User ID and password are required");
        }
        if (userRepository.existsById(userID)) {
            LOGGER.warn("User registration rejected because the identifier is already registered");
            throw new IllegalArgumentException("User ID is already registered");
        }
        User user = userRepository.save(new User(userID, passwordEncoder.encode(password)));
        LOGGER.info("Registered user account");
        return toResponse(user);
    }

    /** Verifies credentials and returns permissions when they match. */
    @Transactional(readOnly = true)
    public Optional<UserResponse> logon(String userID, String password) {
        if (userID == null || password == null) {
            return Optional.empty();
        }
        Optional<UserResponse> response = userRepository.findForLogon(userID)
                .filter(user -> passwordEncoder.matches(password, user.getPassword()))
                .map(this::toResponse);
        response.ifPresent(user -> LOGGER.info("User logon succeeded"));
        return response;
    }

    /** Returns all users without exposing their stored password hashes. */
    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        List<UserResponse> users = userRepository.findAll().stream().map(this::toResponse).toList();
        LOGGER.info("Listed user accounts: count={}", users.size());
        return users;
    }

    /** Updates a user's identifier and optionally their password. */
    @Transactional
    public UserResponse updateUser(String userID, String updatedUserID, String password) {
        if (updatedUserID == null || updatedUserID.isBlank()) {
            throw new IllegalArgumentException("User ID is required");
        }
        User user = findUser(userID);
        if (!updatedUserID.equals(userID) && userRepository.existsById(updatedUserID)) {
            throw new UserAlreadyExistsException("User ID is already registered");
        }

        String encodedPassword = password == null || password.isBlank()
                ? user.getPassword()
                : passwordEncoder.encode(password);
        User updatedUser = new User(updatedUserID, encodedPassword, user.getPermissions());
        if (updatedUserID.equals(userID)) {
            user.changePassword(encodedPassword);
            updatedUser = user;
        } else {
            userRepository.delete(user);
            userRepository.flush();
        }
        UserResponse response = toResponse(userRepository.save(updatedUser));
        LOGGER.info("Updated user account");
        return response;
    }

    /** Adds a permission to an existing user. */
    @Transactional
    public UserResponse addPermission(String userID, UserPermission permission) {
        requirePermission(permission);
        User user = findUser(userID);
        user.addPermission(permission);
        UserResponse response = toResponse(userRepository.save(user));
        LOGGER.info("Updated user permissions");
        return response;
    }

    /** Removes a permission from an existing user. */
    @Transactional
    public UserResponse removePermission(String userID, UserPermission permission) {
        requirePermission(permission);
        User user = findUser(userID);
        user.removePermission(permission);
        UserResponse response = toResponse(userRepository.save(user));
        LOGGER.info("Updated user permissions");
        return response;
    }

    private User findUser(String userID) {
        return userRepository.findById(userID)
                .orElseThrow(() -> {
                    LOGGER.warn("User update rejected because the account was not found");
                    return new IllegalArgumentException("User was not found");
                });
    }

    private void requirePermission(UserPermission permission) {
        if (permission == null) {
            throw new IllegalArgumentException("Permission is required");
        }
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getUserID(), List.copyOf(user.getPermissions()));
    }
}
