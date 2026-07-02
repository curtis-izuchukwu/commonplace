package com.pararepilot.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;

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

    public void saveTodayWorksheetId(long worksheetId) throws SQLException {
        String sql = """
                INSERT INTO daily_recommendations
                    (user_id, recommendation_date, worksheet_id, updated_at)
                VALUES
                    (?, ?, ?, ?)
                ON CONFLICT(user_id) DO UPDATE SET
                    recommendation_date = excluded.recommendation_date,
                    worksheet_id = excluded.worksheet_id,
                    updated_at = excluded.updated_at;
                """;

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, AccountSession.currentUserId());
            stmt.setString(2, DateUtils.toDatabaseDate(LocalDate.now()));
            stmt.setLong(3, worksheetId);
            stmt.setString(4, DateUtils.toDatabaseDateTime(DateUtils.now()));
            stmt.executeUpdate();
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
