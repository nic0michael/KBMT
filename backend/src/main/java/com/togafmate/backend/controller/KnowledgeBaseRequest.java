package com.togafmate.backend.controller;

import io.swagger.v3.oas.annotations.media.Schema;

/** Represents metadata submitted when registering a Knowledge Base. */
public record KnowledgeBaseRequest(String title, String folderNameVersion, String version, String author,
        String emailAddress, String tableOfContents, String description, String contentType,
        @Schema(description = "Whether the Knowledge Base is active and visible to users", requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean isActive) {
}
