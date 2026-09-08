package com.data4circ.portal.features.connectors.config;

import com.data4circ.portal.features.connectors.enums.ConnectorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.datasource.DataSourceUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Ensures the connectors.type constraint includes every ConnectorType enum value.
 * Prevents onboarding failures whenever new connector types are added.
 *
 * <p>Uses CHECK constraints for both PostgreSQL and H2 to maintain Hibernate compatibility.
 * PostgreSQL native ENUMs don't work well with Hibernate's @Enumerated(EnumType.STRING).
 */
@Component
public class ConnectorSchemaConstraintManager {

    private static final Logger logger = LoggerFactory.getLogger(ConnectorSchemaConstraintManager.class);

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public ConnectorSchemaConstraintManager(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void refreshConnectorTypeConstraint() {
        logger.info("=== Starting ConnectorSchemaConstraintManager ===");

        String databaseType = getDatabaseType();
        logger.info("Detected database type: {}", databaseType);

        if (databaseType == null || (!databaseType.equals("h2") && !databaseType.equals("postgresql"))) {
            logger.warn("Database type '{}' not supported for automatic constraint refresh. Skipping.", databaseType);
            return;
        }

        if (!tableExists("connectors")) {
            logger.warn("Connectors table does not exist yet. Skipping constraint refresh.");
            return;
        }

        logger.info("Connectors table exists. Proceeding with constraint update for {} database...", databaseType);

        try {
            logger.info("Updating CHECK constraint for {} database...", databaseType);
            ensureCheckConstraint(databaseType);
            logger.info("=== ConnectorSchemaConstraintManager completed successfully ===");
        } catch (Exception ex) {
            logger.error("=== CRITICAL: Failed to refresh connector type constraint ===", ex);
            logger.error("Error details: {}", ex.getMessage());
            logger.error("This may cause connector creation to fail!");
            throw new RuntimeException("Failed to update database constraints. Cannot proceed safely.", ex);
        }
    }

    /**
     * Ensure CHECK constraint exists with all current ConnectorType values.
     * Works for both PostgreSQL and H2.
     */
    private void ensureCheckConstraint(String databaseType) {
        List<String> currentValues = Arrays.stream(ConnectorType.values())
            .map(Enum::name)
            .toList();

        logger.info("Current ConnectorType values: {}", currentValues);
        String allowedValues = currentValues.stream()
            .map(v -> "'" + v + "'")
            .collect(Collectors.joining(", "));

        // First, handle any existing ENUM type from previous versions (PostgreSQL only)
        if ("postgresql".equals(databaseType)) {
            revertFromEnumToVarchar();
        }

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
     * Revert PostgreSQL column from ENUM back to VARCHAR if needed.
     * This handles cleanup from previous versions that used ENUM types.
     */
    private void revertFromEnumToVarchar() {
        try {
            // Check if ENUM type exists
            Integer enumCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_type WHERE typname = 'connector_type_enum'",
                Integer.class
            );

            if (enumCount != null && enumCount > 0) {
                logger.info("Found existing connector_type_enum ENUM type. Converting back to VARCHAR for Hibernate compatibility...");

                // Convert column back to VARCHAR
                jdbcTemplate.execute(
                    "ALTER TABLE connectors ALTER COLUMN type TYPE VARCHAR(255) USING type::text"
                );

                // Drop the ENUM type
                jdbcTemplate.execute("DROP TYPE IF EXISTS connector_type_enum CASCADE");

                logger.info("Successfully reverted from ENUM to VARCHAR");
            }
        } catch (Exception e) {
            logger.debug("No ENUM type cleanup needed: {}", e.getMessage());
        }
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
                    WHERE cls.relname = 'connectors'
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
                    WHERE tc.TABLE_NAME = 'CONNECTORS'
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
            jdbcTemplate.execute("ALTER TABLE connectors DROP CONSTRAINT IF EXISTS " + constraintName);
        } else {
            jdbcTemplate.execute("ALTER TABLE CONNECTORS DROP CONSTRAINT " + constraintName);
        }
    }

    /**
     * Create CHECK constraint with allowed values (database-specific)
     */
    private void createCheckConstraint(String allowedValues, String databaseType) {
        if ("postgresql".equals(databaseType)) {
            jdbcTemplate.execute(
                "ALTER TABLE connectors ADD CONSTRAINT ck_connector_type CHECK (type IN (" + allowedValues + "))"
            );
        } else {
            // H2
            jdbcTemplate.execute(
                "ALTER TABLE CONNECTORS ADD CONSTRAINT CK_CONNECTOR_TYPE CHECK (TYPE IN (" + allowedValues + "))"
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

    private String buildAllowedValuesExpression() {
        return Arrays.stream(ConnectorType.values())
            .map(type -> "'" + type.name() + "'")
            .collect(Collectors.joining(","));
    }
}
