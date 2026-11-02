package com.togafmate.backend.controller;

import com.togafmate.backend.entity.UserPermission;

/** Represents a permission to add to or remove from a user. */
public record PermissionRequest(UserPermission permission) {
}
