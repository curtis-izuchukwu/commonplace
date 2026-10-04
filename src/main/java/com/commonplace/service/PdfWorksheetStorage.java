package com.commonplace.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.apache.pdfbox.pdmodel.PDDocument;

import com.commonplace.repository.DatabaseManager;

public class PdfWorksheetStorage {

    private static final String PDF_DIRECTORY_NAME = "pdfs";

    public String copyIntoPdfStore(Path sourcePath) throws IOException {
        validatePdf(sourcePath);

        Path pdfDirectory = pdfDirectory();
        Files.createDirectories(pdfDirectory);

        String fileName = UUID.randomUUID() + ".pdf";
        Path destination = pdfDirectory.resolve(fileName);
        Files.copy(sourcePath, destination);

        return PDF_DIRECTORY_NAME + "/" + fileName;
    }

    public Optional<Path> resolvePdfPath(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return Optional.empty();
        }

        Path relativePath;
        try {
            relativePath = Path.of(storedPath.replace("\\", "/"));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }

        if (relativePath.isAbsolute()) {
            return Optional.empty();
        }

        Path pdfDirectory = pdfDirectory().toAbsolutePath().normalize();
        Path resolved = Path.of(DatabaseManager.DB_DIR).resolve(relativePath).toAbsolutePath().normalize();
        return resolved.startsWith(pdfDirectory) ? Optional.of(resolved) : Optional.empty();
    }

    public void deleteStoredPdf(String storedPath) {
        resolvePdfPath(storedPath).ifPresent(path -> {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // The worksheet record is already gone; an orphan is safer than deleting elsewhere.
            }
        });
    }

    private void validatePdf(Path sourcePath) throws IOException {
        if (sourcePath == null || !Files.isRegularFile(sourcePath)) {
            throw new IOException("Selected PDF file does not exist.");
        }

        String fileName = sourcePath.getFileName() == null
                ? ""
                : sourcePath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!fileName.endsWith(".pdf")) {
            throw new IOException("Choose a PDF file.");
        }

        try (PDDocument document = PDDocument.load(sourcePath.toFile())) {
            if (document.getNumberOfPages() < 1) {
                throw new IOException("The selected PDF has no pages.");
            }
        }
    }

    private static Path pdfDirectory() {
        return Path.of(DatabaseManager.DB_DIR).resolve(PDF_DIRECTORY_NAME);
    }
}
