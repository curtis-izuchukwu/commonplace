package com.commonplace.service;

import com.commonplace.repository.MistakeRepository;

import java.sql.SQLException;
import java.util.List;

public class MistakeBankService {

    private final MistakeRepository mistakeRepository;
    private final GamificationService gamificationService;

    public MistakeBankService() {
        this(new MistakeRepository(), new GamificationService());
    }

    public MistakeBankService(MistakeRepository mistakeRepository) {
        this(mistakeRepository, new GamificationService());
    }

    public MistakeBankService(
            MistakeRepository mistakeRepository, GamificationService gamificationService) {
        this.mistakeRepository = mistakeRepository;
        this.gamificationService = gamificationService;
    }

    public int createMistakesFromAttempt(long attemptId) throws SQLException {
        return mistakeRepository.createFromAttempt(attemptId);
    }

    public List<MistakeRepository.MistakeDisplayItem> getAllMistakes() throws SQLException {
        return mistakeRepository.findDisplayItems();
    }

    public List<MistakeRepository.MistakeDisplayItem> getMistakesForTopic(long topicId)
            throws SQLException {
        return mistakeRepository.findDisplayItemsByTopicId(topicId);
    }

    public int countUnresolvedMistakes() throws SQLException {
        return mistakeRepository.countUnresolved();
    }

    public int countUnresolvedMistakesForWorksheet(long worksheetId) throws SQLException {
        return mistakeRepository.countUnresolvedByWorksheetId(worksheetId);
    }

    public int countUnresolvedMistakesForTopic(long topicId) throws SQLException {
        return mistakeRepository.countUnresolvedByTopicId(topicId);
    }

    public void setResolved(long mistakeId, boolean resolved) throws SQLException {
        mistakeRepository.markResolved(mistakeId, resolved);
    }

    public void markRevisited(long mistakeId) throws SQLException {
        mistakeRepository.incrementRevisitCount(mistakeId);
    }

    public com.commonplace.service.GamificationResult review(
            long mistakeId, String answer, boolean success, boolean assisted) throws SQLException {
        if (answer == null || answer.isBlank())
            throw new IllegalArgumentException("Write a recall answer before reviewing.");
        return com.commonplace.repository.DatabaseManager.transaction(
                () -> {
                    var item =
                            mistakeRepository.findDisplayItems().stream()
                                    .filter(m -> m.id() == mistakeId)
                                    .findFirst()
                                    .orElseThrow(
                                            () ->
                                                    new IllegalArgumentException(
                                                            "Mistake is not available in this"
                                                                    + " account."));
                    try (var c = com.commonplace.repository.DatabaseManager.connect();
                            var s =
                                    c.prepareStatement(
                                            "INSERT INTO"
                                                + " mistake_reviews(mistake_id,reviewed_at,answer,success,assisted)"
                                                + " VALUES(?,?,?,?,?)")) {
                        s.setLong(1, mistakeId);
                        s.setString(
                                2,
                                com.commonplace.util.DateUtils.toDatabaseDateTime(
                                        java.time.LocalDateTime.now()));
                        s.setString(3, answer.trim());
                        s.setBoolean(4, success);
                        s.setBoolean(5, assisted);
                        s.executeUpdate();
                    }
                    mistakeRepository.incrementRevisitCount(mistakeId);
                    // A hint-assisted review is useful practice, but it must neither count as
                    // independent recall nor reopen a mistake that was already resolved.
                    if (!assisted) {
                        mistakeRepository.markResolved(mistakeId, success);
                    }
                    new LearningService().refresh(item.topicId());
                    return gamificationService.awardMistakeRecall(
                            item.topicId(),
                            LearningModel.fingerprint(item.questionPrompt()),
                            success,
                            assisted);
                });
    }
}
