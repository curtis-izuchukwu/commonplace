package com.commonplace.repository;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Additive learning migration. Existing attempts and rewards are never replayed or discarded. */
final class LearningSchema {

    private static final int CURRENT_VERSION = 2;

    static void migrate(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS learning_schema (version INTEGER PRIMARY KEY)");
            try (ResultSet result =
                    statement.executeQuery(
                            "SELECT 1 FROM learning_schema WHERE version = " + CURRENT_VERSION)) {
                if (result.next()) {
                    return;
                }
            }
        }

        boolean autoCommit = connection.getAutoCommit();
        if (autoCommit) {
            connection.setAutoCommit(false);
        }

        try (Statement statement = connection.createStatement()) {
            addColumn(connection, statement, "questions", "assessed_difficulty", "TEXT");
            addColumn(connection, statement, "answers", "assisted", "INTEGER NOT NULL DEFAULT 0");
            addColumn(
                    connection,
                    statement,
                    "answers",
                    "active_seconds",
                    "INTEGER NOT NULL DEFAULT 0");
            addColumn(
                    connection,
                    statement,
                    "answers",
                    "evidence_mode",
                    "TEXT NOT NULL DEFAULT 'LEGACY'");
            addColumn(connection, statement, "answers", "initial_answer", "TEXT");
            addColumn(connection, statement, "worksheets", "study_scope", "TEXT");
            addColumn(connection, statement, "worksheets", "generation_subject", "TEXT");

            statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS xp_events (
                        id INTEGER PRIMARY KEY,
                        user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                        event_key TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        amount INTEGER NOT NULL CHECK(amount >= 0),
                        earned_at TEXT NOT NULL,
                        UNIQUE(user_id, event_key)
                    );
                    """);
            statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS mistake_reviews (
                        id INTEGER PRIMARY KEY,
                        mistake_id INTEGER NOT NULL REFERENCES mistake_bank(id) ON DELETE CASCADE,
                        reviewed_at TEXT NOT NULL,
                        answer TEXT NOT NULL,
                        success INTEGER NOT NULL,
                        assisted INTEGER NOT NULL DEFAULT 0
                    );
                    """);
            statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS recommendation_actions (
                        id INTEGER PRIMARY KEY,
                        user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                        action_key TEXT NOT NULL,
                        study_date TEXT NOT NULL,
                        selected_at TEXT NOT NULL
                    );
                    """);
            statement.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS learning_answers_attempt "
                            + "ON answers(attempt_id, question_id)");
            statement.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS learning_attempts_worksheet "
                            + "ON worksheet_attempts(worksheet_id, completed_at)");
            statement.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS learning_recommendation_actions "
                            + "ON recommendation_actions(user_id, study_date, selected_at)");
            statement.executeUpdate(
                    "INSERT OR IGNORE INTO learning_schema(version) VALUES("
                            + CURRENT_VERSION
                            + ")");

            if (autoCommit) {
                connection.commit();
            }
        } catch (SQLException exception) {
            if (autoCommit) {
                connection.rollback();
            }
            throw exception;
        } finally {
            if (autoCommit) {
                connection.setAutoCommit(true);
            }
        }
    }

    private static void addColumn(
            Connection connection,
            Statement statement,
            String table,
            String column,
            String definition)
            throws SQLException {
        try (Statement query = connection.createStatement();
                ResultSet columns = query.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (columns.next()) {
                if (column.equalsIgnoreCase(columns.getString("name"))) {
                    return;
                }
            }
        }
        statement.executeUpdate(
                "ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
    }
}
