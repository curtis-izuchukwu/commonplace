package com.pararepilot.repository;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;


public class DatabaseManager {

    public static final String DB_DIR = System.getProperty("user.home") + File.separator + ".pararepilot";
    public static final String DB_PATH = DB_DIR + File.separator + "appdata.db";
    public static final String CONNECTION_URL = "jdbc:sqlite:" + DB_PATH;

    public static Connection connect() {

        try {
            //ensure the directory exists
            File directory = new File(DB_DIR);
            if(!directory.exists()) {
                directory.mkdirs();
            }

            Connection conn = DriverManager.getConnection(CONNECTION_URL);

            //initialise tables
            initialiseTables(conn);
            
            return conn;
        } catch(SQLException e) {
            System.err.println("Database connection failed: " + e.getMessage());
            return null;
        }
    }
    
    private static void initialiseTables(Connection conn) {
        String[] connectionQueries = {
            "CREATE TABLE IF NOT EXISTS MODULES ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT NOT NULL,"
                + "description TEXT,"
                + "exam_date TEXT,"
                + "importance TEXT NOT NULL DEFAULT 'MEDIUM',"
                + "created_at TEXT NOT NULL,"
                + "updated_at TEXT NOT NULL"
                + ");",

                "CREATE TABLE IF NOT EXISTS topics ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "module_id INTEGER NOT NULL,"
                + "name TEXT NOT NULL,"
                + "description TEXT,"
                + "importance TEXT NOT NULL DEFAULT 'MEDIUM',"
                + "confidence TEXT NOT NULL DEFAULT 'MEDIUM',"
                + "mastery_score REAL NOT NULL DEFAULT 0,"
                + "created_at TEXT NOT NULL,"
                + "updated_at TEXT NOT NULL,"
                + "FOREIGN KEY (module_id) REFERENCES modules(id) ON DELETE CASCADE"
                + ");",

            "CREATE TABLE IF NOT EXISTS worksheets ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "topic_id INTEGER NOT NULL,"
                + "title TEXT NOT NULL,"
                + "description TEXT,"
                + "difficulty TEXT NOT NULL DEFAULT 'MEDIUM',"
                + "importance TEXT NOT NULL DEFAULT 'MEDIUM',"
                + "source TEXT NOT NULL DEFAULT 'manual',"
                + "created_at TEXT NOT NULL,"
                + "last_attempted_at TEXT,"
                + "times_attempted INTEGER NOT NULL DEFAULT 0,"
                + "latest_score_percent REAL,"
                + "average_score_percent REAL,"
                + "failure_streak INTEGER NOT NULL DEFAULT 0,"
                + "FOREIGN KEY (topic_id) REFERENCES topics(id) ON DELETE CASCADE"
                + ");",

            "CREATE TABLE IF NOT EXISTS questions ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "worksheet_id INTEGER NOT NULL,"
                + "prompt TEXT NOT NULL,"
                + "mark_scheme TEXT NOT NULL,"
                + "max_marks INTEGER NOT NULL,"
                + "question_order INTEGER NOT NULL,"
                + "tags TEXT,"
                + "FOREIGN KEY (worksheet_id) REFERENCES worksheets(id) ON DELETE CASCADE"
                + ");",

            "CREATE TABLE IF NOT EXISTS worksheet_attempts ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "worksheet_id INTEGER NOT NULL,"
                + "started_at TEXT NOT NULL,"
                + "completed_at TEXT NOT NULL,"
                + "score INTEGER NOT NULL,"
                + "max_score INTEGER NOT NULL,"
                + "score_percent REAL NOT NULL,"
                + "confidence_after TEXT NOT NULL,"
                + "main_weakness TEXT,"
                + "next_action TEXT,"
                + "reflection_notes TEXT,"
                + "FOREIGN KEY (worksheet_id) REFERENCES worksheets(id) ON DELETE CASCADE"
                + ");",

            "CREATE TABLE IF NOT EXISTS answers ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "attempt_id INTEGER NOT NULL,"
                + "question_id INTEGER NOT NULL,"
                + "user_answer TEXT NOT NULL,"
                + "awarded_marks INTEGER NOT NULL,"
                + "max_marks INTEGER NOT NULL,"
                + "marked_as_mistake INTEGER NOT NULL DEFAULT 0,"
                + "mistake_note TEXT,"
                + "FOREIGN KEY (attempt_id) REFERENCES worksheet_attempts(id) ON DELETE CASCADE,"
                + "FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE"
                + ");",

            "CREATE TABLE IF NOT EXISTS mistake_bank ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "topic_id INTEGER NOT NULL,"
                + "worksheet_id INTEGER NOT NULL,"
                + "question_id INTEGER NOT NULL,"
                + "attempt_id INTEGER NOT NULL,"
                + "user_answer TEXT NOT NULL,"
                + "mark_scheme TEXT NOT NULL,"
                + "mistake_note TEXT,"
                + "created_at TEXT NOT NULL,"
                + "resolved INTEGER NOT NULL DEFAULT 0,"
                + "times_revisited INTEGER NOT NULL DEFAULT 0,"
                + "FOREIGN KEY (topic_id) REFERENCES topics(id) ON DELETE CASCADE,"
                + "FOREIGN KEY (worksheet_id) REFERENCES worksheets(id) ON DELETE CASCADE,"
                + "FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE,"
                + "FOREIGN KEY (attempt_id) REFERENCES worksheet_attempts(id) ON DELETE CASCADE"
                + ");",

            "CREATE TABLE IF NOT EXISTS user_stats ("
                + "id INTEGER PRIMARY KEY CHECK (id = 1),"
                + "xp INTEGER NOT NULL DEFAULT 0,"
                + "streak_count INTEGER NOT NULL DEFAULT 0,"
                + "last_completion_date TEXT,"
                + "worksheet_interval_days INTEGER NOT NULL DEFAULT 1"
                + ");"
        };

        //execute using try with resources
        try (Statement stmt = conn.createStatement()) {
                for (String sql : connectionQueries) {
                    stmt.executeUpdate(sql);
                }
                
                //seed default row for user_stats if empty
                stmt.executeUpdate("INSERT OR IGNORE INTO user_stats (id, xp, streak_count) VALUES (1, 0, 0);");

            } catch (SQLException e) {
                System.err.println("Failed to build or verify database tables: " + e.getMessage());
            }
    }

    // TEMPORARY TEST METHOD
    public static void main(String[] args) {
        System.out.println("Starting database test...");
        System.out.println("Expected DB Location: " + DB_PATH);

        // Call the connect method, which triggers directory & table creation
        try (Connection conn = DatabaseManager.connect()) {
            
            if (conn != null && !conn.isClosed()) {
                System.out.println("🎉 SUCCESS! Connected to SQLite and initialised all tables.");
            } else {
                System.err.println("❌ FAILURE: Connection object was null or closed.");
            }
            
        } catch (SQLException e) {
            System.err.println("❌ FAILURE: An error occurred during verification.");
            e.printStackTrace();
        }
    }

    
}
