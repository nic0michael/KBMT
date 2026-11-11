package com.togafmate.backend.service;

/** Indicates that the selected database backup file is not present. */
public class DatabaseBackupNotFoundException extends RuntimeException {
    /** Creates the exception with a user-safe message. */
    public DatabaseBackupNotFoundException(String message) {
        super(message);
    }
}
