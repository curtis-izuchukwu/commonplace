package com.commonplace.importer;

import java.util.List;

public record ImportedQuestionDraft(
        int questionNumber,
        String questionText,
        String markScheme,
        int maxMarks,
        int pageNumber,
        double yPosition,
        List<String> imagePaths,
        List<ImportIssue> issues
) {
    public ImportedQuestionDraft(
            int questionNumber,
            String questionText,
            String markScheme,
            int maxMarks,
            int pageNumber,
            List<String> imagePaths,
            List<ImportIssue> issues
    ) {
        this(
                questionNumber,
                questionText,
                markScheme,
                maxMarks,
                pageNumber,
                Double.NaN,
                imagePaths,
                issues
        );
    }

    public ImportedQuestionDraft {
        imagePaths = imagePaths == null ? List.of() : List.copyOf(imagePaths);
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
