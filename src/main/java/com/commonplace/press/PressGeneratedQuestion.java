package com.commonplace.press;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import com.commonplace.repository.QuestionRepository;

public record PressGeneratedQuestion(
        String prompt,
        String answer,
        String markScheme,
        int maxMarks,
        String format,
        List<String> options,
        Map<String, String> metadata
) {

    public PressGeneratedQuestion {
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
                format.isBlank() ? "press" : "press," + format,
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

        if (!answer.isBlank() && !markScheme.equalsIgnoreCase(answer)) {
            joiner.add("Answer: " + answer);
        }

        return joiner.toString();
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
