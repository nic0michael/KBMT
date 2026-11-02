package com.togafmate.backend.controller;

import java.util.List;

import com.togafmate.backend.entity.UserPermission;

/** Represents user identity and permissions without exposing its password. */
public record UserResponse(String userID, List<UserPermission> permissions) {
}
