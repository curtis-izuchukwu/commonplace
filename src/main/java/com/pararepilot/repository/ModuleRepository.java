package com.pararepilot.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.pararepilot.model.ImportanceLevel;
import com.pararepilot.model.StudyModule;
import com.pararepilot.util.DateUtils;

public class ModuleRepository {

    public StudyModule create(String name, String description, String examDate, ImportanceLevel importance)
            throws SQLException {

        String sql = """
                INSERT INTO modules
                    (name, description, exam_date, importance, created_at, updated_at)
                VALUES
                    (?, ?, ?, ?, ?, ?);
                """;

        LocalDateTime now = DateUtils.now();

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, name.trim());
            stmt.setString(2, blankToNull(description));
            stmt.setString(3, blankToNull(examDate));
            stmt.setString(4, importance.name());
            stmt.setString(5, DateUtils.toDatabaseDateTime(now));
            stmt.setString(6, DateUtils.toDatabaseDateTime(now));

            stmt.executeUpdate();

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return findById(keys.getLong(1))
                            .orElseThrow(() -> new SQLException("Created module could not be reloaded."));
                }
            }

            throw new SQLException("Creating module failed; no ID returned.");
        }
    }

    public List<StudyModule> findAll() throws SQLException {
        String sql = """
                SELECT id, name, description, exam_date, importance, created_at, updated_at
                FROM modules
                ORDER BY created_at DESC;
                """;

        List<StudyModule> modules = new ArrayList<>();

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                modules.add(mapRow(rs));
            }
        }

        return modules;
    }

    public Optional<StudyModule> findById(long id) throws SQLException {
        String sql = """
                SELECT id, name, description, exam_date, importance, created_at, updated_at
                FROM modules
                WHERE id = ?;
                """;

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }

        return Optional.empty();
    }

    public void update(StudyModule module) throws SQLException {
        String sql = """
                UPDATE modules
                SET name = ?,
                    description = ?,
                    exam_date = ?,
                    importance = ?,
                    updated_at = ?
                WHERE id = ?;
                """;

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, module.name().trim());
            stmt.setString(2, blankToNull(module.description()));
            stmt.setString(3, DateUtils.toDatabaseDate(module.examDate()));
            stmt.setString(4, module.importance().name());
            stmt.setString(5, DateUtils.toDatabaseDateTime(DateUtils.now()));
            stmt.setLong(6, module.id());

            stmt.executeUpdate();
        }
    }

    public void deleteById(long id) throws SQLException {
        String sql = "DELETE FROM modules WHERE id = ?;";

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);
            stmt.executeUpdate();
        }
    }

    private StudyModule mapRow(ResultSet rs) throws SQLException {
        return new StudyModule(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("description"),
                DateUtils.fromDatabaseDate(rs.getString("exam_date")),
                ImportanceLevel.valueOf(rs.getString("importance")),
                DateUtils.fromDatabaseDateTime(rs.getString("created_at")),
                DateUtils.fromDatabaseDateTime(rs.getString("updated_at"))
        );
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}