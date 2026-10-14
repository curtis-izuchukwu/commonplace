package com.commonplace.repository;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** Additive, idempotent migration. Notes inherit ownership through their parent records. */
final class NoteSchema {
    private NoteSchema() {}

    static void migrate(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            for (String table : new String[] {"module_notes", "topic_notes"}) {
                String parent = table.equals("module_notes") ? "module" : "topic";
                String parents = parent.equals("module") ? "modules" : "topics";
                stmt.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS %s (
                            %s_id INTEGER PRIMARY KEY,
                            content TEXT NOT NULL,
                            plain_text TEXT NOT NULL,
                            format_version INTEGER NOT NULL CHECK (format_version > 0),
                            created_at TEXT NOT NULL,
                            updated_at TEXT NOT NULL,
                            FOREIGN KEY (%s_id) REFERENCES %s(id) ON DELETE CASCADE
                        );
                        """.formatted(table, parent, parent, parents));
            }
        }
    }
}
