package com.commonplace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PdfWorksheetStorageTest {

    @TempDir Path temporaryDirectory;

    @Test
    void copiesAndResolvesValidatedPdfInsideManagedStore() throws Exception {
        Path source = temporaryDirectory.resolve("worksheet.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(source.toFile());
        }

        PdfWorksheetStorage storage = new PdfWorksheetStorage();
        String storedPath = storage.copyIntoPdfStore(source);
        Path resolved = storage.resolvePdfPath(storedPath).orElseThrow();

        try {
            assertTrue(storedPath.startsWith("pdfs/"));
            assertTrue(Files.isRegularFile(resolved));
            assertEquals(Files.size(source), Files.size(resolved));
            assertFalse(storage.resolvePdfPath("../outside.pdf").isPresent());
        } finally {
            storage.deleteStoredPdf(storedPath);
        }
    }
}
