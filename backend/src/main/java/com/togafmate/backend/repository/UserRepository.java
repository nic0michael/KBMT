package com.togafmate.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.togafmate.backend.entity.User;

/** Provides persistence operations for user accounts. */
public interface UserRepository extends JpaRepository<User, String> {
    /** Looks up a user for logon using a bound user identifier parameter. */
    @Query("SELECT account FROM User account WHERE account.userID = :userID")
    Optional<User> findForLogon(@Param("userID") String userID);
}
