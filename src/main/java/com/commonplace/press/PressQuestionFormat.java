package com.commonplace.press;

public enum PressQuestionFormat {
    SHORT_ANSWER("short-answer", "Short answer"),
    LONG_ANSWER("long-answer", "Long answer");

    private final String apiValue;
    private final String label;

    PressQuestionFormat(String apiValue, String label) {
        this.apiValue = apiValue;
        this.label = label;
    }

    public String apiValue() {
        return apiValue;
    }

    @Override
    public String toString() {
        return label;
    }
}
