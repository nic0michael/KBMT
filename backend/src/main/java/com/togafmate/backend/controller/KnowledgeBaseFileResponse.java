package com.togafmate.backend.controller;

import java.util.UUID;

/** Describes a stored Knowledge Base resource without returning its bytes. */
public record KnowledgeBaseFileResponse(UUID id, String path, String contentType, long size) { }
