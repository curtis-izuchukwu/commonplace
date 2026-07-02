package com.pararepilot.service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.pararepilot.model.Topic;
import com.pararepilot.model.Worksheet;
import com.pararepilot.repository.TopicRepository;
import com.pararepilot.repository.WorksheetRepository;
import com.pararepilot.util.WeightedRandomPicker;

public class WorksheetSelectionService {

    private final WorksheetRepository worksheetRepository;
    private final TopicRepository topicRepository;
    private final PriorityScoreService priorityScoreService;
    private final WeightedRandomPicker<WorksheetRecommendation> picker;

    public WorksheetSelectionService() {
        this(
                new WorksheetRepository(),
                new TopicRepository(),
                new PriorityScoreService(),
                new WeightedRandomPicker<>()
        );
    }

    public WorksheetSelectionService(
            WorksheetRepository worksheetRepository,
            TopicRepository topicRepository,
            PriorityScoreService priorityScoreService,
            WeightedRandomPicker<WorksheetRecommendation> picker
    ) {
        this.worksheetRepository = worksheetRepository;
        this.topicRepository = topicRepository;
        this.priorityScoreService = priorityScoreService;
        this.picker = picker;
    }

    public Optional<WorksheetRecommendation> recommendWorksheet() throws SQLException {
        List<WorksheetRecommendation> recommendations = buildRecommendations();

        if (recommendations.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(
                picker.pick(recommendations, WorksheetRecommendation::priorityScore)
        );
    }

    public List<WorksheetRecommendation> previewPriorities() throws SQLException {
        return buildRecommendations().stream()
                .sorted((first, second) -> Integer.compare(
                        second.priorityScore(),
                        first.priorityScore()
                ))
                .toList();
    }

    private List<WorksheetRecommendation> buildRecommendations() throws SQLException {
        List<Worksheet> worksheets = worksheetRepository.findAll();
        List<WorksheetRecommendation> recommendations = new ArrayList<>();

        for (Worksheet worksheet : worksheets) {
            Optional<Topic> topic = topicRepository.findById(worksheet.topicId());

            if (topic.isEmpty()) {
                continue;
            }

            /*
             * Mistake bank is the next later branch.
             * For now, unresolved mistake count is treated as 0.
             * Once MistakeRepository exists, plug the real count in here.
             */
            int unresolvedMistakeCount = 0;

            int priorityScore = priorityScoreService.calculatePriority(
                    worksheet,
                    topic.get(),
                    unresolvedMistakeCount
            );

            String explanation = priorityScoreService.explainPriority(
                    worksheet,
                    topic.get(),
                    unresolvedMistakeCount
            );

            recommendations.add(new WorksheetRecommendation(
                    worksheet,
                    topic.get(),
                    priorityScore,
                    explanation
            ));
        }

        return recommendations;
    }
}