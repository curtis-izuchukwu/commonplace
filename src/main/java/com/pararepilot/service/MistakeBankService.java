package com.pararepilot.service;

import java.sql.SQLException;
import java.util.List;

import com.pararepilot.repository.MistakeRepository;

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
            MistakeRepository mistakeRepository,
            GamificationService gamificationService
    ) {
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
    gamificationService.awardMistakeReview();
    }
}