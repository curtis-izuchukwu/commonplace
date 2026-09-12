package com.commonplace.importer;

import java.nio.file.Path;
import java.util.List;

public record ImportedWorksheetDraft(
        String suggestedTitle,
        Long moduleId,
        Long topicId,
        List<ImportedQuestionDraft> questions,
        List<ExtractedPdfImage> unattachedImages,
        List<ImportIssue> issues,
        Path sourcePdfPath
) {
    public ImportedWorksheetDraft {
        questions = questions == null ? List.of() : List.copyOf(questions);
        unattachedImages = unattachedImages == null ? List.of() : List.copyOf(unattachedImages);
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
