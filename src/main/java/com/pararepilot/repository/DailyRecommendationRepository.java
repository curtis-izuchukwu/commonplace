package com.pararepilot.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import com.pararepilot.service.AccountSession;
import com.pararepilot.util.DateUtils;

public class DailyRecommendationRepository {

    public Optional<Long> findTodayWorksheetId() throws SQLException {
        String sql = """
                SELECT recommendation_date, worksheet_id
                FROM daily_recommendations
                WHERE user_id = ?;
                """;

        LocalDate today = LocalDate.now();
        Long worksheetId = null;
        boolean staleRecommendation = false;

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, AccountSession.currentUserId());

            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }

                LocalDate recommendationDate = DateUtils.fromDatabaseDate(rs.getString("recommendation_date"));
                worksheetId = rs.getLong("worksheet_id");

                if (recommendationDate == null || !recommendationDate.isEqual(today)) {
                    staleRecommendation = true;
                }
            }
        }

        if (worksheetId == null) {
            return Optional.empty();
        }

        if (staleRecommendation) {
            clear();
            return Optional.empty();
        }

        return Optional.of(worksheetId);
    }

    public Set<Long> findTodayWorksheetIds() throws SQLException {
        String sql = """
                SELECT worksheet_id
                FROM daily_recommendation_history
                WHERE user_id = ?
                  AND recommendation_date = ?;
                """;

        Set<Long> worksheetIds = new HashSet<>();

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, AccountSession.currentUserId());
            stmt.setString(2, DateUtils.toDatabaseDate(LocalDate.now()));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    worksheetIds.add(rs.getLong("worksheet_id"));
                }
            }
        }

        return worksheetIds;
    }

    public void saveTodayWorksheetId(long worksheetId) throws SQLException {
        String activeSql = """
                INSERT INTO daily_recommendations
                    (user_id, recommendation_date, worksheet_id, updated_at)
                VALUES
                    (?, ?, ?, ?)
                ON CONFLICT(user_id) DO UPDATE SET
                    recommendation_date = excluded.recommendation_date,
                    worksheet_id = excluded.worksheet_id,
                    updated_at = excluded.updated_at;
                """;

        String historySql = """
                INSERT OR IGNORE INTO daily_recommendation_history
                    (user_id, recommendation_date, worksheet_id, created_at)
                VALUES
                    (?, ?, ?, ?);
                """;

        long userId = AccountSession.currentUserId();
        String today = DateUtils.toDatabaseDate(LocalDate.now());
        String now = DateUtils.toDatabaseDateTime(DateUtils.now());

        try (Connection conn = DatabaseManager.connect()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try (PreparedStatement activeStmt = conn.prepareStatement(activeSql);
                 PreparedStatement historyStmt = conn.prepareStatement(historySql)) {

                activeStmt.setLong(1, userId);
                activeStmt.setString(2, today);
                activeStmt.setLong(3, worksheetId);
                activeStmt.setString(4, now);
                activeStmt.executeUpdate();

                historyStmt.setLong(1, userId);
                historyStmt.setString(2, today);
                historyStmt.setLong(3, worksheetId);
                historyStmt.setString(4, now);
                historyStmt.executeUpdate();

                conn.commit();

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        }
    }

    public void clear() throws SQLException {
        String sql = """
                DELETE FROM daily_recommendations
                WHERE user_id = ?;
                """;

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, AccountSession.currentUserId());
            stmt.executeUpdate();
        }
    }
}
