package com.togafmate.backend.config;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Updates the PostgreSQL permission constraint to accept the current permission values. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PermissionConstraintMigration implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(PermissionConstraintMigration.class);
    private static final String DROP_CONSTRAINT_SQL = "ALTER TABLE app_user_permissions "
            + "DROP CONSTRAINT IF EXISTS app_user_permissions_permission_check";
    private static final String ADD_CONSTRAINT_SQL = "ALTER TABLE app_user_permissions "
            + "ADD CONSTRAINT app_user_permissions_permission_check CHECK "
            + "(permission IN ('USER_ACCESS', 'ADMINISTRATOR_USER_ACCESS', 'ADMINISTRATOR_ACCESS', 'NO_ACCESS'))";

    private final DataSource dataSource;

    /** Creates the migration with the configured application database. */
    public PermissionConstraintMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Updates the permission constraint before administrator bootstrap writes permission values. */
    @Override
    public void run(ApplicationArguments args) {
        try (Connection connection = dataSource.getConnection()) {
            if (!"PostgreSQL".equals(connection.getMetaData().getDatabaseProductName())) {
                return;
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute(DROP_CONSTRAINT_SQL);
                statement.execute(ADD_CONSTRAINT_SQL);
            }
            LOGGER.info("Updated PostgreSQL user permission constraint");
        } catch (SQLException exception) {
            LOGGER.error("Failed to update PostgreSQL user permission constraint", exception);
            throw new IllegalStateException("Could not update the PostgreSQL user permission constraint", exception);
        }
    }
}
