package com.pararepilot.service;

import com.pararepilot.model.Topic;
import com.pararepilot.model.Worksheet;

public record WorksheetRecommendation(
        Worksheet worksheet,
        Topic topic,
        int priorityScore,
        String explanation
) {
}