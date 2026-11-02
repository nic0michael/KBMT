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

/** Adds and backfills the active flag for existing Knowledge Base records. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class KnowledgeBaseActiveFlagMigration implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(KnowledgeBaseActiveFlagMigration.class);
    private static final String[] MIGRATION_SQL = {
            "ALTER TABLE knowledge_bases ADD COLUMN IF NOT EXISTS knowledge_base_is_active BOOLEAN",
            "UPDATE knowledge_bases SET knowledge_base_is_active = TRUE WHERE knowledge_base_is_active IS NULL",
            "ALTER TABLE knowledge_bases ALTER COLUMN knowledge_base_is_active SET DEFAULT TRUE",
            "ALTER TABLE knowledge_bases ALTER COLUMN knowledge_base_is_active SET NOT NULL"
    };

    private final DataSource dataSource;

    /** Creates the migration with the configured application database. */
    public KnowledgeBaseActiveFlagMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Applies the PostgreSQL migration and defaults existing rows to active. */
    @Override
    public void run(ApplicationArguments args) {
        try (Connection connection = dataSource.getConnection()) {
            if (!"PostgreSQL".equals(connection.getMetaData().getDatabaseProductName())) {
                return;
            }
            try (Statement statement = connection.createStatement()) {
                for (String sql : MIGRATION_SQL) {
                    statement.execute(sql);
                }
            }
            LOGGER.info("Updated PostgreSQL Knowledge Base active flag schema");
        } catch (SQLException exception) {
            LOGGER.error("Failed to update PostgreSQL Knowledge Base active flag schema", exception);
            throw new IllegalStateException("Could not update the PostgreSQL Knowledge Base active flag schema",
                    exception);
        }
    }
}
