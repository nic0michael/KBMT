package com.togafmate.backend.controller;

import java.time.Instant;
import java.util.UUID;

/** Describes a Markdown page without including its content. */
public record KnowledgeBasePageResponse(UUID id, String title, Instant createdAt) { }
