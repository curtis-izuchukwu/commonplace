package com.commonplace.flightdeck;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import com.commonplace.repository.QuestionRepository;

public record FlightDeckGeneratedQuestion(
        String prompt,
        String answer,
        String markScheme,
        int maxMarks,
        String format,
        List<String> options,
        Map<String, String> metadata
) {

    public FlightDeckGeneratedQuestion {
        prompt = blankToEmpty(prompt);
        answer = blankToEmpty(answer);
        markScheme = blankToEmpty(markScheme);
        maxMarks = Math.max(1, maxMarks);
        format = blankToEmpty(format);
        options = options == null ? List.of() : List.copyOf(options);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public QuestionRepository.QuestionDraft toQuestionDraft() {
        return new QuestionRepository.QuestionDraft(
                promptWithOptions(),
                markSchemeWithAnswer(),
                maxMarks,
                format.isBlank() ? "flightdeck" : "flightdeck," + format,
                null
        );
    }

    private String promptWithOptions() {
        if (options.isEmpty()) {
            return prompt;
        }

        List<String> formattedOptions = new ArrayList<>();

        for (int i = 0; i < options.size(); i++) {
            formattedOptions.add((char) ('A' + i) + ". " + options.get(i));
        }

        return prompt + "\n\nOptions:\n" + String.join("\n", formattedOptions);
    }

    private String markSchemeWithAnswer() {
        StringJoiner joiner = new StringJoiner("\n\n");

        if (!markScheme.isBlank()) {
            joiner.add(markScheme);
        }

        if (!answer.isBlank() && !markScheme.toLowerCase().contains(answer.toLowerCase())) {
            joiner.add("Answer: " + answer);
        }

        String value = joiner.toString();
        return value.isBlank() ? "Review generated answer before saving." : value;
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
