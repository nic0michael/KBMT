package com.togafmate.backend.config;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionConstraintMigrationTest {
    @Mock private DataSource dataSource;
    @Mock private Connection connection;
    @Mock private DatabaseMetaData metadata;
    @Mock private Statement statement;

    @Test
    void should_updatePermissionConstraint_whenDatabaseIsPostgres() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(connection.createStatement()).thenReturn(statement);

        new PermissionConstraintMigration(dataSource).run(new DefaultApplicationArguments(new String[0]));

        verify(statement).execute("ALTER TABLE app_user_permissions DROP CONSTRAINT IF EXISTS app_user_permissions_permission_check");
        verify(statement).execute("ALTER TABLE app_user_permissions ADD CONSTRAINT app_user_permissions_permission_check CHECK (permission IN ('USER_ACCESS', 'ADMINISTRATOR_USER_ACCESS', 'ADMINISTRATOR_ACCESS', 'NO_ACCESS'))");
    }

    @Test
    void should_skipPermissionConstraintMigration_whenDatabaseIsNotPostgres() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("H2");

        new PermissionConstraintMigration(dataSource).run(new DefaultApplicationArguments(new String[0]));

        verify(connection, never()).createStatement();
    }
}
