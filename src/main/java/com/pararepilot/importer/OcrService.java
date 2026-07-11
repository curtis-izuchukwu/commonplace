package com.pararepilot.importer;

import java.nio.file.Path;

public interface OcrService {

    boolean isAvailable();

    OcrResult extractTextFromImage(Path imagePath);
}
