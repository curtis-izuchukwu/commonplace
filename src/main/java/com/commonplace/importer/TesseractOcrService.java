package com.commonplace.importer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class TesseractOcrService implements OcrService {

    private static final long OCR_TIMEOUT_SECONDS = 30;

    @Override
    public boolean isAvailable() {
        try {
            Process process = new ProcessBuilder("tesseract", "--version")
                    .redirectErrorStream(true)
                    .start();

            return process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public OcrResult extractTextFromImage(Path imagePath) {
        if (imagePath == null || !Files.isRegularFile(imagePath)) {
            return new OcrResult(
                    "",
                    List.of(new ImportIssue(ImportIssueSeverity.WARNING, "OCR image file was unavailable."))
            );
        }

        if (!isAvailable()) {
            return new NoOpOcrService().extractTextFromImage(imagePath);
        }

        try {
            Process process = new ProcessBuilder(
                    "tesseract",
                    imagePath.toString(),
                    "stdout"
            )
                    .redirectErrorStream(true)
                    .start();

            boolean completed = process.waitFor(OCR_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            if (!completed) {
                process.destroyForcibly();
                return new OcrResult(
                        "",
                        List.of(new ImportIssue(ImportIssueSeverity.WARNING, "Local OCR timed out."))
                );
            }

            if (process.exitValue() != 0) {
                return new OcrResult(
                        "",
                        List.of(new ImportIssue(
                                ImportIssueSeverity.WARNING,
                                "Local OCR could not read the image: " + output.strip()
                        ))
                );
            }

            return new OcrResult(output.strip(), List.of());

        } catch (IOException e) {
            return new OcrResult(
                    "",
                    List.of(new ImportIssue(
                            ImportIssueSeverity.WARNING,
                            "Local OCR failed: " + e.getMessage()
                    ))
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new OcrResult(
                    "",
                    List.of(new ImportIssue(
                            ImportIssueSeverity.WARNING,
                            "Local OCR failed: " + e.getMessage()
                    ))
            );
        }
    }
}
