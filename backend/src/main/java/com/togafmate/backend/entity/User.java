package com.togafmate.backend.entity;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

/** Stores a user's encoded password and access permissions. */
@Entity
@Table(name = "app_users")
public class User {
    @Id
    @Column(name = "user_id", nullable = false, length = 255)
    private String userID;

    @Column(nullable = false, length = 255)
    private String password;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "app_user_permissions", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "permission", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private List<UserPermission> permissions = new ArrayList<>();

    protected User() {
    }

    /** Creates a user with no access by default. */
    public User(String userID, String encodedPassword) {
        this(userID, encodedPassword, List.of(UserPermission.NO_ACCESS));
    }

    /** Creates a user with the supplied encoded password and permissions. */
    public User(String userID, String encodedPassword, List<UserPermission> permissions) {
        this.userID = userID;
        this.password = encodedPassword;
        this.permissions = new ArrayList<>(permissions);
    }

    /** Returns the user identifier. */
    public String getUserID() {
        return userID;
    }

    /** Returns the encoded password. */
    public String getPassword() {
        return password;
    }

    /** Replaces the stored encoded password. */
    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    /** Returns the user's current permissions. */
    public List<UserPermission> getPermissions() {
        return List.copyOf(permissions);
    }

    /** Adds a permission without duplicating it. */
    public void addPermission(UserPermission permission) {
        if (permission == UserPermission.NO_ACCESS) {
            permissions.clear();
        } else {
            permissions.remove(UserPermission.NO_ACCESS);
        }
        if (!permissions.contains(permission)) {
            permissions.add(permission);
        }
    }

    /** Removes a permission and restores no-access when no permissions remain. */
    public void removePermission(UserPermission permission) {
        permissions.remove(permission);
        if (permissions.isEmpty()) {
            permissions.add(UserPermission.NO_ACCESS);
        }
    }
}
