package com.commonplace.flightdeck;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record FlightDeckGenerateResponse(
        String title,
        String description,
        List<FlightDeckGeneratedQuestion> questions,
        Map<String, String> metadata
) {

    public FlightDeckGenerateResponse {
        title = title == null ? "" : title.trim();
        description = description == null ? "" : description.trim();
        questions = questions == null ? List.of() : List.copyOf(questions);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static FlightDeckGenerateResponse fromJson(String json) {
        Object parsed = FlightDeckJson.parse(json);

        if (parsed instanceof List<?> list) {
            return new FlightDeckGenerateResponse("", "", parseQuestions(list), Map.of());
        }

        Map<String, Object> root = objectOrEmpty(parsed);
        Map<String, Object> worksheet = firstObject(root, "worksheet", "data", "result", "response");
        Map<String, Object> titleSource = worksheet.isEmpty() ? root : worksheet;

        String title = firstString(titleSource, "title", "worksheetTitle", "name");
        String description = firstString(titleSource, "description", "summary", "overview");
        List<Object> questionValues = findQuestionValues(root);
        Map<String, String> metadata = extractMetadata(root, worksheet);

        return new FlightDeckGenerateResponse(
                title,
                description,
                parseQuestions(questionValues),
                metadata
        );
    }

    private static List<FlightDeckGeneratedQuestion> parseQuestions(List<?> questionValues) {
        List<FlightDeckGeneratedQuestion> questions = new ArrayList<>();

        for (Object value : questionValues) {
            FlightDeckGeneratedQuestion question = parseQuestion(value);

            if (question != null && !question.prompt().isBlank()) {
                questions.add(question);
            }
        }

        return questions;
    }

    private static FlightDeckGeneratedQuestion parseQuestion(Object value) {
        if (value instanceof String prompt) {
            return new FlightDeckGeneratedQuestion(prompt, "", "", 1, "", List.of(), Map.of());
        }

        Map<String, Object> question = objectOrEmpty(value);

        if (question.isEmpty()) {
            return null;
        }

        String prompt = firstString(
                question,
                "question",
                "prompt",
                "text",
                "questionText",
                "body",
                "stem"
        );

        String answer = firstString(
                question,
                "answer",
                "expectedAnswer",
                "correctAnswer",
                "modelAnswer",
                "solution"
        );

        String markScheme = firstString(
                question,
                "markScheme",
                "mark_scheme",
                "markingScheme",
                "markingGuidance",
                "marking_guide",
                "explanation",
                "rationale"
        );

        int maxMarks = firstInt(question, 1, "marks", "maxMarks", "max_marks", "points", "mark");
        String format = firstString(question, "format", "type", "questionType");
        List<String> options = firstStringList(question, "options", "choices", "answers");
        Map<String, String> metadata = extractMetadata(question, Map.of());

        return new FlightDeckGeneratedQuestion(
                prompt,
                answer,
                markScheme,
                maxMarks,
                format,
                options,
                metadata
        );
    }

    private static List<Object> findQuestionValues(Map<String, Object> root) {
        Object direct = firstValue(root, "questions", "items");

        if (direct instanceof List<?> list) {
            return new ArrayList<>(list);
        }

        for (String key : List.of("worksheet", "data", "result", "response")) {
            Object nested = root.get(key);

            if (nested instanceof List<?> nestedList) {
                return new ArrayList<>(nestedList);
            }

            if (nested instanceof Map<?, ?> nestedMap) {
                List<Object> questions = findQuestionValues(castMap(nestedMap));

                if (!questions.isEmpty()) {
                    return questions;
                }
            }
        }

        return List.of();
    }

    private static Map<String, String> extractMetadata(
            Map<String, Object> primary,
            Map<String, Object> secondary
    ) {
        Map<String, String> metadata = new LinkedHashMap<>();
        appendMetadata(metadata, objectOrEmpty(primary.get("metadata")));
        appendMetadata(metadata, objectOrEmpty(primary.get("meta")));
        appendMetadata(metadata, objectOrEmpty(secondary.get("metadata")));
        appendMetadata(metadata, objectOrEmpty(secondary.get("meta")));
        return metadata;
    }

    private static void appendMetadata(Map<String, String> target, Map<String, Object> source) {
        for (var entry : source.entrySet()) {
            Object value = entry.getValue();

            if (value == null || value instanceof Map<?, ?> || value instanceof List<?>) {
                continue;
            }

            target.put(entry.getKey(), String.valueOf(value));
        }
    }

    private static String firstString(Map<String, Object> source, String... keys) {
        Object value = firstValue(source, keys);

        if (value == null) {
            return "";
        }

        if (value instanceof String text) {
            return text.trim();
        }

        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }

        return "";
    }

    private static int firstInt(Map<String, Object> source, int fallback, String... keys) {
        Object value = firstValue(source, keys);

        if (value instanceof Number number) {
            return Math.max(1, number.intValue());
        }

        if (value instanceof String text) {
            try {
                return Math.max(1, Integer.parseInt(text.trim()));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }

        return fallback;
    }

    private static List<String> firstStringList(Map<String, Object> source, String... keys) {
        Object value = firstValue(source, keys);

        if (value instanceof List<?> list) {
            List<String> strings = new ArrayList<>();

            for (Object item : list) {
                String text = optionText(item);

                if (!text.isBlank()) {
                    strings.add(text);
                }
            }

            return strings;
        }

        if (value instanceof Map<?, ?> map) {
            List<String> strings = new ArrayList<>();

            for (Object item : map.values()) {
                String text = optionText(item);

                if (!text.isBlank()) {
                    strings.add(text);
                }
            }

            return strings;
        }

        return List.of();
    }

    private static String optionText(Object value) {
        if (value instanceof String text) {
            return text.trim();
        }

        Map<String, Object> option = objectOrEmpty(value);

        return firstString(option, "text", "label", "value", "answer");
    }

    private static Object firstValue(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            if (source.containsKey(key)) {
                return source.get(key);
            }
        }

        return null;
    }

    private static Map<String, Object> firstObject(Map<String, Object> source, String... keys) {
        Object value = firstValue(source, keys);
        return objectOrEmpty(value);
    }

    private static Map<String, Object> objectOrEmpty(Object value) {
        if (value instanceof Map<?, ?> map) {
            return castMap(map);
        }

        return Map.of();
    }

    private static Map<String, Object> castMap(Map<?, ?> map) {
        Map<String, Object> cast = new LinkedHashMap<>();

        for (var entry : map.entrySet()) {
            if (entry.getKey() instanceof String key) {
                cast.put(key, entry.getValue());
            }
        }

        return cast;
    }
}
