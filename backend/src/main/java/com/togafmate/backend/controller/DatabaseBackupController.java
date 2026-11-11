package com.togafmate.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.togafmate.backend.service.DatabaseBackup;
import com.togafmate.backend.service.DatabaseBackupService;
import com.togafmate.backend.service.DatabaseBackupNotFoundException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

/** Exposes administrator-only PostgreSQL backup operations. */
@RestController
@RequestMapping("/api/database/backups")
public class DatabaseBackupController {
    private final DatabaseBackupService backupService;

    /** Creates the controller with its backup service. */
    public DatabaseBackupController(DatabaseBackupService backupService) {
        this.backupService = backupService;
    }

    /** Lists available PostgreSQL backups. */
    @GetMapping
    @Operation(summary = "List database backups", description = "Lists custom-format PostgreSQL backups for this database.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "Backup list returned")
    @ApiResponse(responseCode = "403", description = "Administrator permission is required")
    @ApiResponse(responseCode = "500", description = "Backup directory is unavailable")
    public List<DatabaseBackup> listBackups() {
        try {
            return backupService.listBackups();
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not list database backups", exception);
        }
    }

    /** Creates a custom-format PostgreSQL backup. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create database backup", description = "Creates a custom-format dump named "
            + "kbmt_<database>_yyyyddMMHHmmss.dump.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "201", description = "Database backup created")
    @ApiResponse(responseCode = "403", description = "Administrator permission is required")
    @ApiResponse(responseCode = "500", description = "Backup operation failed")
    public DatabaseBackup createBackup() {
        try {
            return backupService.createBackup();
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not create database backup", exception);
        }
    }

    /** Creates a safety backup and restores the selected dump. */
    @PostMapping("/{fileName}/restore")
    @Operation(summary = "Restore database backup", description = "Creates a safety backup before restoring the selected dump.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "Database backup restored")
    @ApiResponse(responseCode = "400", description = "Invalid backup filename")
    @ApiResponse(responseCode = "404", description = "Backup file not found")
    @ApiResponse(responseCode = "403", description = "Administrator permission is required")
    @ApiResponse(responseCode = "500", description = "Restore operation failed")
    public DatabaseBackup restoreBackup(@PathVariable String fileName) {
        try {
            return backupService.restore(fileName);
        } catch (DatabaseBackupNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not restore database backup", exception);
        }
    }
}
