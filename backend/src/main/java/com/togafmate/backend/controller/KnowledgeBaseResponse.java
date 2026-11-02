package com.togafmate.backend.controller;

import java.util.UUID;

/** Represents Knowledge Base metadata returned by the REST API. */
public record KnowledgeBaseResponse(UUID id, String title, String version, String description,
        String contentType, String folderNameVersion, String author, String emailAddress, String tableOfContents,
        Boolean isActive) {
}
