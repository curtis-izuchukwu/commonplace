package com.commonplace.importer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

public class PdfTextExtractionService {

    private static final int MIN_USEFUL_TEXT_LENGTH = 30;

    public PdfTextExtractionResult extractText(Path pdfPath) {
        List<ImportIssue> issues = new ArrayList<>();

        if (pdfPath == null || !Files.isRegularFile(pdfPath)) {
            return new PdfTextExtractionResult(
                    "",
                    List.of(),
                    0,
                    List.of(new ImportIssue(ImportIssueSeverity.ERROR, "PDF file was not found."))
            );
        }

        try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
            if (document.isEncrypted()) {
                return new PdfTextExtractionResult(
                        "",
                        List.of(),
                        document.getNumberOfPages(),
                        List.of(new ImportIssue(
                                ImportIssueSeverity.ERROR,
                                "This PDF is encrypted and cannot be imported."
                        ))
                );
            }

            List<String> pageTexts = new ArrayList<>();
            List<PdfTextLine> lines = new ArrayList<>();

            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                PositionedTextStripper stripper = new PositionedTextStripper();
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                stripper.setSortByPosition(true);

                String pageText = normalizeLineEndings(stripper.getText(document));
                pageTexts.add(pageText);
                lines.addAll(stripper.lines());
            }

            String text = normalizeLineEndings(String.join("\n\n", pageTexts)).strip();

            if (text.length() < MIN_USEFUL_TEXT_LENGTH) {
                issues.add(new ImportIssue(
                        ImportIssueSeverity.WARNING,
                        "This PDF contains little or no selectable text."
                ));
            }

            return new PdfTextExtractionResult(text, pageTexts, lines, document.getNumberOfPages(), issues);

        } catch (IOException e) {
            return new PdfTextExtractionResult(
                    "",
                    List.of(),
                    0,
                    List.of(new ImportIssue(
                            ImportIssueSeverity.ERROR,
                            "The PDF could not be read: " + e.getMessage()
                    ))
            );
        }
    }

    private String normalizeLineEndings(String value) {
        return value == null ? "" : value.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static final class PositionedTextStripper extends PDFTextStripper {

        private final List<PdfTextLine> lines = new ArrayList<>();

        private PositionedTextStripper() throws IOException {
            super();
        }

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) {
            String normalizedText = text == null ? "" : text.strip();

            if (normalizedText.isBlank() || textPositions == null || textPositions.isEmpty()) {
                return;
            }

            double yPosition = textPositions.stream()
                    .mapToDouble(TextPosition::getYDirAdj)
                    .min()
                    .orElse(Double.NaN);

            lines.add(new PdfTextLine(getCurrentPageNo(), normalizedText, yPosition));
        }

        private List<PdfTextLine> lines() {
            return Collections.unmodifiableList(lines);
        }
    }
}
