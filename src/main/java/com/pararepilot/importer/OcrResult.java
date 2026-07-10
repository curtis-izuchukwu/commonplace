package com.pararepilot.importer;

import java.util.List;

public record OcrResult(
        String text,
        List<ImportIssue> issues
) {
    public OcrResult {
        text = text == null ? "" : text;
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
