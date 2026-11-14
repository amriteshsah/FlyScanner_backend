package com.flyscanner.platform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("local")
class LocalDatabaseMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayCreatesTheModuleSchemasInH2() {
        Integer accountSchemaCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME = 'account'",
                Integer.class);

        Integer searchSchemaCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME = 'search'",
                Integer.class);

        assertTrue(accountSchemaCount != null && accountSchemaCount > 0,
                "Flyway should create the account schema in the local H2 database");
        assertTrue(searchSchemaCount != null && searchSchemaCount > 0,
                "Flyway should create the search schema in the local H2 database");
    }
}
