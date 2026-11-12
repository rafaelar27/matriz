package com.matriz;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Setup check: the application context starts, connects to the database configured in the environment,
 * and Flyway has applied the schema. Read-only: it changes no data.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DatabaseSetupTest {

    private static final List<String> TABLES = List.of(
            "account", "workspace", "member", "invitation", "project", "project_member",
            "task", "comment", "notification", "feedback", "personal_data_request");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void databaseAnswersQueries() {
        assertThat(jdbc.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
    }

    @Test
    void allMigrationsSucceeded() {
        Integer failed = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = FALSE", Integer.class);
        Integer applied = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = TRUE", Integer.class);
        assertThat(failed).isZero();
        assertThat(applied).isPositive();
    }

    @Test
    void schemaHasEveryEntityFromTheDataModel() {
        List<String> existing = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = current_schema()",
                String.class);
        assertThat(existing).containsAll(TABLES);
    }
}
