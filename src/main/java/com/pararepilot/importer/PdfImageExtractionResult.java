package com.pararepilot.importer;

import java.util.List;

public record PdfImageExtractionResult(
        List<ExtractedPdfImage> images,
        List<ImportIssue> issues
) {
    public PdfImageExtractionResult {
        images = images == null ? List.of() : List.copyOf(images);
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
