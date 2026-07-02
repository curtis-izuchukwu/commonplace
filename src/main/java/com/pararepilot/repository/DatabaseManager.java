package com.pararepilot.repository;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseManager {

    public static final String DB_DIR =
            System.getProperty("user.home") + File.separator + ".pararepilot";

    public static final String DB_PATH =
            DB_DIR + File.separator + "appdata.db";

    public static final String CONNECTION_URL =
            "jdbc:sqlite:" + DB_PATH;

    private DatabaseManager() {
        // Utility class
    }

    public static Connection connect() throws SQLException {
        ensureDatabaseDirectoryExists();

        Connection conn = DriverManager.getConnection(CONNECTION_URL);
        enableForeignKeys(conn);
        initialiseTables(conn);

        return conn;
    }

    private static void ensureDatabaseDirectoryExists() {
        File directory = new File(DB_DIR);

        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException(
                    "Failed to create database directory: " + DB_DIR
            );
        }
    }

    private static void enableForeignKeys(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
        }
    }

    public static void initialiseTables(Connection conn) throws SQLException {
        String[] schemaQueries = {
                """
                CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT NOT NULL UNIQUE COLLATE NOCASE,
                    password_hash TEXT NOT NULL,
                    password_salt TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                );
                """,

                """
                CREATE TABLE IF NOT EXISTS modules (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER,
                    name TEXT NOT NULL,
                    description TEXT,
                    exam_date TEXT,
                    importance TEXT NOT NULL DEFAULT 'MEDIUM',
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                );
                """,

                """
                CREATE TABLE IF NOT EXISTS topics (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    module_id INTEGER NOT NULL,
                    name TEXT NOT NULL,
                    description TEXT,
                    importance TEXT NOT NULL DEFAULT 'MEDIUM',
                    confidence TEXT NOT NULL DEFAULT 'MEDIUM',
                    mastery_score REAL NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    FOREIGN KEY (module_id) REFERENCES modules(id) ON DELETE CASCADE
                );
                """,

                """
                CREATE TABLE IF NOT EXISTS worksheets (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    topic_id INTEGER NOT NULL,
                    title TEXT NOT NULL,
                    description TEXT,
                    difficulty TEXT NOT NULL DEFAULT 'MEDIUM',
                    importance TEXT NOT NULL DEFAULT 'MEDIUM',
                    source TEXT NOT NULL DEFAULT 'manual',
                    created_at TEXT NOT NULL,
                    last_attempted_at TEXT,
                    times_attempted INTEGER NOT NULL DEFAULT 0,
                    latest_score_percent REAL,
                    average_score_percent REAL,
                    failure_streak INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY (topic_id) REFERENCES topics(id) ON DELETE CASCADE
                );
                """,

                """
                CREATE TABLE IF NOT EXISTS questions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    worksheet_id INTEGER NOT NULL,
                    prompt TEXT NOT NULL,
                    mark_scheme TEXT NOT NULL,
                    max_marks INTEGER NOT NULL,
                    question_order INTEGER NOT NULL,
                    tags TEXT,
                    FOREIGN KEY (worksheet_id) REFERENCES worksheets(id) ON DELETE CASCADE
                );
                """,

                """
                CREATE TABLE IF NOT EXISTS worksheet_attempts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    worksheet_id INTEGER NOT NULL,
                    started_at TEXT NOT NULL,
                    completed_at TEXT NOT NULL,
                    score INTEGER NOT NULL,
                    max_score INTEGER NOT NULL,
                    score_percent REAL NOT NULL,
                    confidence_after TEXT NOT NULL,
                    main_weakness TEXT,
                    next_action TEXT,
                    reflection_notes TEXT,
                    xp_awarded_at TEXT,
                    FOREIGN KEY (worksheet_id) REFERENCES worksheets(id) ON DELETE CASCADE
                );
                """,

                """
                CREATE TABLE IF NOT EXISTS answers (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    attempt_id INTEGER NOT NULL,
                    question_id INTEGER NOT NULL,
                    user_answer TEXT NOT NULL,
                    awarded_marks INTEGER NOT NULL,
                    max_marks INTEGER NOT NULL,
                    marked_as_mistake INTEGER NOT NULL DEFAULT 0,
                    mistake_note TEXT,
                    FOREIGN KEY (attempt_id) REFERENCES worksheet_attempts(id) ON DELETE CASCADE,
                    FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE
                );
                """,

                """
                CREATE TABLE IF NOT EXISTS mistake_bank (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    topic_id INTEGER NOT NULL,
                    worksheet_id INTEGER NOT NULL,
                    question_id INTEGER NOT NULL,
                    attempt_id INTEGER NOT NULL,
                    user_answer TEXT NOT NULL,
                    mark_scheme TEXT NOT NULL,
                    mistake_note TEXT,
                    created_at TEXT NOT NULL,
                    resolved INTEGER NOT NULL DEFAULT 0,
                    times_revisited INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY (topic_id) REFERENCES topics(id) ON DELETE CASCADE,
                    FOREIGN KEY (worksheet_id) REFERENCES worksheets(id) ON DELETE CASCADE,
                    FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE,
                    FOREIGN KEY (attempt_id) REFERENCES worksheet_attempts(id) ON DELETE CASCADE
                );
                """,

                """
                CREATE TABLE IF NOT EXISTS user_stats (
                    user_id INTEGER PRIMARY KEY,
                    xp INTEGER NOT NULL DEFAULT 0,
                    streak_count INTEGER NOT NULL DEFAULT 0,
                    last_completion_date TEXT,
                    worksheet_interval_days INTEGER NOT NULL DEFAULT 1
                );
                """,

                """
                CREATE TABLE IF NOT EXISTS user_settings (
                    user_id INTEGER PRIMARY KEY,
                    theme TEXT NOT NULL DEFAULT 'DARK',
                    accent_color TEXT NOT NULL DEFAULT 'CYAN',
                    reduce_motion INTEGER NOT NULL DEFAULT 0,
                    compact_layout INTEGER NOT NULL DEFAULT 0,
                    font_size TEXT NOT NULL DEFAULT 'DEFAULT',
                    daily_worksheet_goal INTEGER NOT NULL DEFAULT 1,
                    daily_reminder_time TEXT NOT NULL DEFAULT '18:00',
                    preferred_min_difficulty TEXT NOT NULL DEFAULT 'EASY',
                    preferred_max_difficulty TEXT NOT NULL DEFAULT 'HARD',
                    recommendation_focus TEXT NOT NULL DEFAULT 'BALANCED',
                    include_resolved_mistakes_in_recommendations INTEGER NOT NULL DEFAULT 0,
                    daily_reminder_enabled INTEGER NOT NULL DEFAULT 0,
                    exam_reminder_enabled INTEGER NOT NULL DEFAULT 1,
                    mistake_reminder_enabled INTEGER NOT NULL DEFAULT 1,
                    streak_reminder_enabled INTEGER NOT NULL DEFAULT 1,
                    quiet_hours_enabled INTEGER NOT NULL DEFAULT 0,
                    quiet_hours_start TEXT NOT NULL DEFAULT '22:00',
                    quiet_hours_end TEXT NOT NULL DEFAULT '07:00',
                    show_xp_and_rank INTEGER NOT NULL DEFAULT 1,
                    streak_tracking_enabled INTEGER NOT NULL DEFAULT 1,
                    completion_celebrations_enabled INTEGER NOT NULL DEFAULT 1,
                    default_module_priority TEXT NOT NULL DEFAULT 'MEDIUM',
                    archive_completed_modules INTEGER NOT NULL DEFAULT 0,
                    higher_contrast INTEGER NOT NULL DEFAULT 0,
                    larger_controls INTEGER NOT NULL DEFAULT 0,
                    keyboard_hints_enabled INTEGER NOT NULL DEFAULT 0,
                    screen_reader_labels_enabled INTEGER NOT NULL DEFAULT 1,
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                );
                """,

                """
                CREATE TABLE IF NOT EXISTS remembered_session (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    user_id INTEGER NOT NULL,
                    updated_at TEXT NOT NULL,
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                );
                """,

                """
                CREATE TABLE IF NOT EXISTS daily_recommendations (
                    user_id INTEGER PRIMARY KEY,
                    recommendation_date TEXT NOT NULL,
                    worksheet_id INTEGER NOT NULL,
                    updated_at TEXT NOT NULL,
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                    FOREIGN KEY (worksheet_id) REFERENCES worksheets(id) ON DELETE CASCADE
                );
                """
        };

        try (Statement stmt = conn.createStatement()) {
            for (String sql : schemaQueries) {
                stmt.executeUpdate(sql);
            }

            migrateExistingSchema(conn);
        }
    }

    private static void migrateExistingSchema(Connection conn) throws SQLException {
        addColumnIfMissing(conn, "modules", "user_id", "INTEGER");
        addColumnIfMissing(conn, "worksheet_attempts", "xp_awarded_at", "TEXT");
        addColumnIfMissing(conn, "user_settings", "compact_layout", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(conn, "user_settings", "font_size", "TEXT NOT NULL DEFAULT 'DEFAULT'");
        addColumnIfMissing(conn, "user_settings", "preferred_min_difficulty", "TEXT NOT NULL DEFAULT 'EASY'");
        addColumnIfMissing(conn, "user_settings", "preferred_max_difficulty", "TEXT NOT NULL DEFAULT 'HARD'");
        addColumnIfMissing(conn, "user_settings", "recommendation_focus", "TEXT NOT NULL DEFAULT 'BALANCED'");
        addColumnIfMissing(conn, "user_settings", "include_resolved_mistakes_in_recommendations", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(conn, "user_settings", "quiet_hours_enabled", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(conn, "user_settings", "quiet_hours_start", "TEXT NOT NULL DEFAULT '22:00'");
        addColumnIfMissing(conn, "user_settings", "quiet_hours_end", "TEXT NOT NULL DEFAULT '07:00'");
        addColumnIfMissing(conn, "user_settings", "show_xp_and_rank", "INTEGER NOT NULL DEFAULT 1");
        addColumnIfMissing(conn, "user_settings", "streak_tracking_enabled", "INTEGER NOT NULL DEFAULT 1");
        addColumnIfMissing(conn, "user_settings", "completion_celebrations_enabled", "INTEGER NOT NULL DEFAULT 1");
        addColumnIfMissing(conn, "user_settings", "default_module_priority", "TEXT NOT NULL DEFAULT 'MEDIUM'");
        addColumnIfMissing(conn, "user_settings", "archive_completed_modules", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(conn, "user_settings", "higher_contrast", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(conn, "user_settings", "larger_controls", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(conn, "user_settings", "keyboard_hints_enabled", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(conn, "user_settings", "screen_reader_labels_enabled", "INTEGER NOT NULL DEFAULT 1");
        migrateUserStatsTableIfNeeded(conn);
    }

    private static void addColumnIfMissing(
            Connection conn,
            String tableName,
            String columnName,
            String definition
    ) throws SQLException {

        if (columnExists(conn, tableName, columnName)) {
            return;
        }

        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                    "ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition + ";"
            );
        }
    }

    private static void migrateUserStatsTableIfNeeded(Connection conn) throws SQLException {
        if (columnExists(conn, "user_stats", "user_id")) {
            return;
        }

        int xp = 0;
        int streakCount = 0;
        String lastCompletionDate = null;
        int worksheetIntervalDays = 1;

        if (tableExists(conn, "user_stats")) {
            String sql = """
                    SELECT xp, streak_count, last_completion_date, worksheet_interval_days
                    FROM user_stats
                    WHERE id = 1;
                    """;

            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                if (rs.next()) {
                    xp = rs.getInt("xp");
                    streakCount = rs.getInt("streak_count");
                    lastCompletionDate = rs.getString("last_completion_date");
                    worksheetIntervalDays = rs.getInt("worksheet_interval_days");
                }
            }
        }

        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("ALTER TABLE user_stats RENAME TO user_stats_legacy;");
            stmt.executeUpdate("""
                    CREATE TABLE user_stats (
                        user_id INTEGER PRIMARY KEY,
                        xp INTEGER NOT NULL DEFAULT 0,
                        streak_count INTEGER NOT NULL DEFAULT 0,
                        last_completion_date TEXT,
                        worksheet_interval_days INTEGER NOT NULL DEFAULT 1
                    );
                    """);
        }

        String insertLegacySql = """
                INSERT INTO user_stats
                    (user_id, xp, streak_count, last_completion_date, worksheet_interval_days)
                VALUES
                    (0, ?, ?, ?, ?);
                """;

        try (PreparedStatement stmt = conn.prepareStatement(insertLegacySql)) {
            stmt.setInt(1, xp);
            stmt.setInt(2, streakCount);
            stmt.setString(3, lastCompletionDate);
            stmt.setInt(4, worksheetIntervalDays);
            stmt.executeUpdate();
        }

        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DROP TABLE user_stats_legacy;");
        }
    }

    private static boolean tableExists(Connection conn, String tableName) throws SQLException {
        String sql = """
                SELECT 1
                FROM sqlite_master
                WHERE type = 'table'
                  AND name = ?;
                """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, tableName);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean columnExists(
            Connection conn,
            String tableName,
            String columnName
    ) throws SQLException {

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + tableName + ");")) {

            while (rs.next()) {
                if (columnName.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }

        return false;
    }
}
