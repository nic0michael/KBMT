package com.togafmate.backend.service;

/** Indicates that a requested user identifier is already in use. */
public class UserAlreadyExistsException extends RuntimeException {
    /** Creates the exception with a user-facing explanation. */
    public UserAlreadyExistsException(String message) {
        super(message);
    }
}
