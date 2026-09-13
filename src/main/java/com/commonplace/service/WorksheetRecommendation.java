package com.commonplace.service;

import com.commonplace.model.Topic;
import com.commonplace.model.Worksheet;

public record WorksheetRecommendation(
        Worksheet worksheet, Topic topic, int priorityScore, String explanation) {

    public String key() {
        return "worksheet:" + worksheet.id();
    }
}
