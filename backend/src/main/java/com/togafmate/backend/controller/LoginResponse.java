package com.togafmate.backend.controller;

import java.util.List;

import com.togafmate.backend.entity.UserPermission;

/** Contains a user's identity, permissions, and issued access token. */
public record LoginResponse(String userID, List<UserPermission> permissions, String accessToken,
        String tokenType, long expiresIn) {
}
