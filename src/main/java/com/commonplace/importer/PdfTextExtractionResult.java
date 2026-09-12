package com.commonplace.importer;

import java.util.List;

public record PdfTextExtractionResult(
        String text,
        List<String> pageTexts,
        List<PdfTextLine> lines,
        int pageCount,
        List<ImportIssue> issues
) {
    public PdfTextExtractionResult(
            String text,
            List<String> pageTexts,
            int pageCount,
            List<ImportIssue> issues
    ) {
        this(text, pageTexts, List.of(), pageCount, issues);
    }

    public PdfTextExtractionResult {
        text = text == null ? "" : text;
        pageTexts = pageTexts == null ? List.of() : List.copyOf(pageTexts);
        lines = lines == null ? List.of() : List.copyOf(lines);
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
