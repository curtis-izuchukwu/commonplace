package com.commonplace.repository;

import com.commonplace.model.*;
import com.commonplace.service.AccountSession;
import com.commonplace.util.DateUtils;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Account identity is captured once, so queued work cannot follow a later sign-in. */
public class NoteRepository {
    private final long userId;

    public NoteRepository() throws SQLException { this(AccountSession.currentUserId()); }
    public NoteRepository(long userId) {
        if (userId <= 0) throw new IllegalArgumentException("A note repository needs an account.");
        this.userId = userId;
    }

    public Optional<NoteRecord> find(NoteKey key) throws SQLException {
        try (Connection conn = DatabaseManager.connect()) {
            requireOwned(conn, key);
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT * FROM " + table(key) + " WHERE " + column(key) + " = ?")) {
                stmt.setLong(1, id(key));
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next() ? Optional.of(new NoteRecord(key, rs.getString("content"),
                            rs.getInt("format_version"), DateUtils.fromDatabaseDateTime(rs.getString("created_at")),
                            DateUtils.fromDatabaseDateTime(rs.getString("updated_at")))) : Optional.empty();
                }
            }
        }
    }

    public void save(NoteKey key, String content, String plainText, int version) throws SQLException {
        DatabaseManager.transaction(() -> {
            try (Connection conn = DatabaseManager.connect()) {
                requireOwned(conn, key);
                // Truly empty documents need no row. Deleting text removes an existing row too.
                if (plainText.isEmpty()) {
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "DELETE FROM " + table(key) + " WHERE " + column(key) + " = ?")) {
                        stmt.setLong(1, id(key));
                        stmt.executeUpdate();
                    }
                } else {
                    String sql = """
                            INSERT INTO %s (%s, content, plain_text, format_version, created_at, updated_at)
                            VALUES (?, ?, ?, ?, ?, ?)
                            ON CONFLICT(%s) DO UPDATE SET content = excluded.content,
                                plain_text = excluded.plain_text, format_version = excluded.format_version,
                                updated_at = excluded.updated_at
                            """.formatted(table(key), column(key), column(key));
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        String now = DateUtils.toDatabaseDateTime(DateUtils.now());
                        stmt.setLong(1, id(key));
                        stmt.setString(2, content);
                        stmt.setString(3, plainText);
                        stmt.setInt(4, version);
                        stmt.setString(5, now);
                        stmt.setString(6, now);
                        stmt.executeUpdate();
                    }
                }
            }
            return null;
        });
    }

    /** One joined query, including Overview sections for modules with no topics. */
    public List<NotebookSection> sections() throws SQLException {
        List<NotebookSection> sections = new ArrayList<>();
        try (Connection conn = DatabaseManager.connect(); PreparedStatement stmt = conn.prepareStatement("""
                SELECT m.id AS module_id, m.name AS module_name, t.id AS topic_id, t.name AS topic_name
                FROM modules m LEFT JOIN topics t ON t.module_id = m.id
                WHERE m.user_id = ? ORDER BY m.name COLLATE NOCASE, m.id, t.created_at, t.id
                """)) {
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                long previous = -1;
                while (rs.next()) {
                    long moduleId = rs.getLong("module_id");
                    String name = rs.getString("module_name");
                    if (previous != moduleId) {
                        sections.add(new NotebookSection(NoteKey.overview(moduleId), name, "Overview"));
                        previous = moduleId;
                    }
                    long topicId = rs.getLong("topic_id");
                    if (!rs.wasNull()) sections.add(new NotebookSection(NoteKey.topic(moduleId, topicId),
                            name, rs.getString("topic_name")));
                }
            }
        }
        return sections;
    }

    public List<NoteSearchResult> searchableNotes() throws SQLException {
        List<NoteSearchResult> notes = new ArrayList<>();
        try (Connection conn = DatabaseManager.connect(); PreparedStatement stmt = conn.prepareStatement("""
                SELECT m.id AS module_id, m.name AS module_name, NULL AS topic_id,
                       'Overview' AS title, n.plain_text
                FROM module_notes n JOIN modules m ON m.id = n.module_id WHERE m.user_id = ?
                UNION ALL
                SELECT m.id, m.name, t.id, t.name, n.plain_text
                FROM topic_notes n JOIN topics t ON t.id = n.topic_id
                JOIN modules m ON m.id = t.module_id WHERE m.user_id = ?
                """)) {
            stmt.setLong(1, userId);
            stmt.setLong(2, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long topicId = rs.getLong("topic_id");
                    Long topic = rs.wasNull() ? null : topicId;
                    var section = new NotebookSection(new NoteKey(rs.getLong("module_id"), topic),
                            rs.getString("module_name"), rs.getString("title"));
                    notes.add(new NoteSearchResult(section, rs.getString("plain_text"), -1));
                }
            }
        }
        return notes;
    }

    private void requireOwned(Connection conn, NoteKey key) throws SQLException {
        String sql = key.isOverview() ? "SELECT 1 FROM modules WHERE id = ? AND user_id = ?"
                : """
                SELECT 1 FROM topics t JOIN modules m ON m.id = t.module_id
                WHERE m.id = ? AND m.user_id = ? AND t.id = ?
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, key.moduleId());
            stmt.setLong(2, userId);
            if (!key.isOverview()) stmt.setLong(3, key.topicId());
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) throw new SQLException("This note's module or topic is unavailable for this account.");
            }
        }
    }

    private static String table(NoteKey key) { return key.isOverview() ? "module_notes" : "topic_notes"; }
    private static String column(NoteKey key) { return key.isOverview() ? "module_id" : "topic_id"; }
    private static long id(NoteKey key) { return key.isOverview() ? key.moduleId() : key.topicId(); }
}
