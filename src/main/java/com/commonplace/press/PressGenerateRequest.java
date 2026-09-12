package com.commonplace.press;

import java.util.Locale;

import com.commonplace.model.DifficultyLevel;

public record PressGenerateRequest(
        String subject,
        String topic,
        DifficultyLevel difficulty,
        int questionCount,
        PressQuestionFormat format
) {

    public PressGenerateRequest {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Subject is required for Press generation.");
        }

        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("Topic is required for Press generation.");
        }

        if (questionCount < 1 || questionCount > 10) {
            throw new IllegalArgumentException("Press question count must be between 1 and 10.");
        }

        subject = subject.trim();
        topic = topic.trim();
        if (subject.length() > 80) {
            throw new IllegalArgumentException("Press subject must be 80 characters or fewer.");
        }
        if (topic.length() > 120) {
            throw new IllegalArgumentException("Press topic must be 120 characters or fewer.");
        }
        difficulty = difficulty == null ? DifficultyLevel.MEDIUM : difficulty;
        format = format == null ? PressQuestionFormat.SHORT_ANSWER : format;
    }

    public String toJson() {
        return """
                {"subject":"%s","topic":"%s","difficulty":"%s","questionCount":%d,"format":"%s"}
                """.formatted(
                escapeJson(subject),
                escapeJson(topic),
                difficulty.name().toLowerCase(Locale.ROOT),
                questionCount,
                escapeJson(format.apiValue())
        ).trim();
    }

    private static String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder();

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);

            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20) {
                        escaped.append("\\u%04x".formatted((int) c));
                    } else {
                        escaped.append(c);
                    }
                }
            }
        }

        return escaped.toString();
    }
}
