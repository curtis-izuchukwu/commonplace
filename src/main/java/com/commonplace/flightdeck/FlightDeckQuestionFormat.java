package com.commonplace.flightdeck;

public enum FlightDeckQuestionFormat {
    SHORT_ANSWER("short-answer", "Short answer"),
    MULTIPLE_CHOICE("multiple-choice", "Multiple choice"),
    MIXED("mixed", "Mixed");

    private final String apiValue;
    private final String label;

    FlightDeckQuestionFormat(String apiValue, String label) {
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
