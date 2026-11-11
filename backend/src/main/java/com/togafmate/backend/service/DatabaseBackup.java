package com.togafmate.backend.service;

/** Describes a database backup available for recovery. */
public record DatabaseBackup(String fileName, long sizeBytes, long createdAtEpochMillis) {
}
