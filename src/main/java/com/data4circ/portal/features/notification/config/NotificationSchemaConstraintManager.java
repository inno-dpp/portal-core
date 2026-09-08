package com.data4circ.portal.features.notification.config;

import com.data4circ.portal.features.notification.entity.NotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.jdbc.datasource.DataSourceUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Ensures the notifications.type CHECK constraint includes every {@link NotificationType} enum
 * value. Mirrors {@code ConnectorSchemaConstraintManager} for the connectors table: with
 * {@code ddl-auto=update}, Hibernate creates a CHECK constraint from the enum values present the
 * first time the table is created, but never widens it when new enum constants are added later.
 * Without this, inserting a notification of a newly-added type fails with a
 * {@code notifications_type_check} constraint violation that gets silently swallowed by the
 * best-effort try/catch blocks around notification sends (the notification is simply never
 * created, with no user-visible error).
 *
 * <p>Uses CHECK constraints for both PostgreSQL and H2 to maintain Hibernate compatibility.
 */
@Component
public class NotificationSchemaConstraintManager {

    private static final Logger logger = LoggerFactory.getLogger(NotificationSchemaConstraintManager.class);

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public NotificationSchemaConstraintManager(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void refreshNotificationTypeConstraint() {
        logger.info("=== Starting NotificationSchemaConstraintManager ===");

        String databaseType = getDatabaseType();
        logger.info("Detected database type: {}", databaseType);

        if (databaseType == null || (!databaseType.equals("h2") && !databaseType.equals("postgresql"))) {
            logger.warn("Database type '{}' not supported for automatic constraint refresh. Skipping.", databaseType);
            return;
        }

        if (!tableExists("notifications")) {
            logger.warn("Notifications table does not exist yet. Skipping constraint refresh.");
            return;
        }

        logger.info("Notifications table exists. Proceeding with constraint update for {} database...", databaseType);

        try {
            logger.info("Updating CHECK constraint for {} database...", databaseType);
            ensureCheckConstraint(databaseType);
            logger.info("=== NotificationSchemaConstraintManager completed successfully ===");
        } catch (Exception ex) {
            logger.error("=== CRITICAL: Failed to refresh notification type constraint ===", ex);
            logger.error("Error details: {}", ex.getMessage());
            logger.error("This may cause notification creation to fail!");
            throw new RuntimeException("Failed to update database constraints. Cannot proceed safely.", ex);
        }
    }

    /**
     * Ensure CHECK constraint exists with all current NotificationType values.
     * Works for both PostgreSQL and H2.
     */
    private void ensureCheckConstraint(String databaseType) {
        List<String> currentValues = Arrays.stream(NotificationType.values())
            .map(Enum::name)
            .toList();

        logger.info("Current NotificationType values: {}", currentValues);
        String allowedValues = currentValues.stream()
            .map(v -> "'" + v + "'")
            .collect(Collectors.joining(", "));

        // Find and drop existing CHECK constraints
        List<String> constraintNames = findTypeCheckConstraints(databaseType);
        logger.info("Found {} existing type constraint(s): {}", constraintNames.size(), constraintNames);

        for (String constraintName : constraintNames) {
            dropConstraint(constraintName, databaseType);
            logger.info("Dropped constraint: {}", constraintName);
        }

        // Create new CHECK constraint with all current values
        createCheckConstraint(allowedValues, databaseType);
        logger.info("CHECK constraint created with {} values: {}", currentValues.size(), currentValues);
    }

    /**
     * Find CHECK constraints on type column (database-specific queries)
     */
    private List<String> findTypeCheckConstraints(String databaseType) {
        if ("postgresql".equals(databaseType)) {
            return jdbcTemplate.queryForList(
                """
                    SELECT con.conname
                    FROM pg_constraint con
                    JOIN pg_class cls ON con.conrelid = cls.oid
                    WHERE cls.relname = 'notifications'
                      AND con.contype = 'c'
                      AND pg_get_constraintdef(con.oid) ILIKE '%type%'
                """,
                String.class
            );
        } else {
            // H2 - Query using TABLE_CONSTRAINTS joined with CHECK_CONSTRAINTS
            return jdbcTemplate.queryForList(
                """
                    SELECT tc.CONSTRAINT_NAME
                    FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
                    JOIN INFORMATION_SCHEMA.CHECK_CONSTRAINTS cc
                        ON tc.CONSTRAINT_NAME = cc.CONSTRAINT_NAME
                    WHERE tc.TABLE_NAME = 'NOTIFICATIONS'
                      AND tc.CONSTRAINT_TYPE = 'CHECK'
                      AND cc.CHECK_CLAUSE LIKE '%TYPE%'
                """,
                String.class
            );
        }
    }

    /**
     * Drop CHECK constraint (database-specific)
     */
    private void dropConstraint(String constraintName, String databaseType) {
        if ("postgresql".equals(databaseType)) {
            jdbcTemplate.execute("ALTER TABLE notifications DROP CONSTRAINT IF EXISTS " + constraintName);
        } else {
            jdbcTemplate.execute("ALTER TABLE NOTIFICATIONS DROP CONSTRAINT " + constraintName);
        }
    }

    /**
     * Create CHECK constraint with allowed values (database-specific)
     */
    private void createCheckConstraint(String allowedValues, String databaseType) {
        if ("postgresql".equals(databaseType)) {
            jdbcTemplate.execute(
                "ALTER TABLE notifications ADD CONSTRAINT ck_notification_type CHECK (type IN (" + allowedValues + "))"
            );
        } else {
            // H2
            jdbcTemplate.execute(
                "ALTER TABLE NOTIFICATIONS ADD CONSTRAINT CK_NOTIFICATION_TYPE CHECK (TYPE IN (" + allowedValues + "))"
            );
        }
    }

    private String getDatabaseType() {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            if (metaData == null) {
                return null;
            }
            String productName = metaData.getDatabaseProductName();
            if (productName == null) {
                return null;
            }
            return productName.toLowerCase().contains("postgresql") ? "postgresql"
                 : productName.toLowerCase().contains("h2") ? "h2"
                 : productName.toLowerCase();
        } catch (SQLException e) {
            logger.debug("Unable to determine database product name", e);
            return null;
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private boolean tableExists(String tableName) {
        try {
            // PostgreSQL stores table names in lowercase, H2 in uppercase
            String databaseType = getDatabaseType();
            String searchName = "postgresql".equals(databaseType)
                ? tableName.toLowerCase()
                : tableName.toUpperCase();

            Integer count = jdbcTemplate.queryForObject(
                """
                    SELECT COUNT(*)
                    FROM INFORMATION_SCHEMA.TABLES
                    WHERE LOWER(TABLE_NAME) = LOWER(?)
                """,
                Integer.class,
                searchName
            );
            boolean exists = count != null && count > 0;
            logger.debug("Table '{}' exists: {}", tableName, exists);
            return exists;
        } catch (Exception e) {
            logger.warn("Failed to check if table '{}' exists: {}", tableName, e.getMessage());
            return false;
        }
    }
}
