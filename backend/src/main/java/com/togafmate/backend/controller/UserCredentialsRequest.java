package com.togafmate.backend.controller;

/** Represents credentials submitted for registration or logon. */
public record UserCredentialsRequest(String userID, String password) {
}
