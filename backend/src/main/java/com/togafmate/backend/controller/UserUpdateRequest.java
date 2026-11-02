package com.togafmate.backend.controller;

import io.swagger.v3.oas.annotations.media.Schema;

/** Represents editable user account details. */
public record UserUpdateRequest(
        @Schema(description = "New user identifier", requiredMode = Schema.RequiredMode.REQUIRED) String userID,
        @Schema(description = "Optional replacement password; omit to keep the current password") String password) {
}
