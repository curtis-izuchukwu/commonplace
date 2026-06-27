package com.pararepilot.repository;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;

class DatabaseManagerTest {

    @Test
    void connectCreatesDatabaseAndTables() throws Exception {
        try (Connection conn = DatabaseManager.connect()) {
            assertNotNull(conn);

            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery("""
                    SELECT name
                    FROM sqlite_master
                    WHERE type = 'table'
                    AND name = 'modules';
                """);

                assertEquals("modules", rs.getString("name"));
            }
        }
    }
}