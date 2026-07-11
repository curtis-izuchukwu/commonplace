package com.pararepilot.importer;

import java.nio.file.Path;
import java.util.List;

public class NoOpOcrService implements OcrService {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public OcrResult extractTextFromImage(Path imagePath) {
        return new OcrResult(
                "",
                List.of(new ImportIssue(
                        ImportIssueSeverity.WARNING,
                        "Local OCR is not configured, so scanned image text could not be extracted."
                ))
        );
    }
}
