package com.togafmate.backend.controller;

/** Holds stored bytes and the media type for a Knowledge Base resource response. */
public record KnowledgeBaseFileContent(String contentType, byte[] bytes) { }
