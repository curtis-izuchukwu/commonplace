package com.pararepilot.importer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.pararepilot.service.QuestionImageStorage;

public class PdfImportService {

    private final PdfTextExtractionService textExtractionService;
    private final PdfImageExtractionService imageExtractionService;
    private final OcrService ocrService;
    private final WorksheetDraftParser parser;

    public PdfImportService() {
        this(
                new PdfTextExtractionService(),
                new PdfImageExtractionService(),
                new LocalOcrService(),
                new WorksheetDraftParser()
        );
    }

    public PdfImportService(
            PdfTextExtractionService textExtractionService,
            PdfImageExtractionService imageExtractionService,
            OcrService ocrService,
            WorksheetDraftParser parser
    ) {
        this.textExtractionService = textExtractionService;
        this.imageExtractionService = imageExtractionService;
        this.ocrService = ocrService;
        this.parser = parser;
    }

    public ImportedWorksheetDraft importPdf(Path pdfPath, Long moduleId, Long topicId) {
        List<ImportIssue> importIssues = validatePdf(pdfPath);

        if (!importIssues.isEmpty()) {
            return new ImportedWorksheetDraft(
                    fallbackTitle(pdfPath),
                    moduleId,
                    topicId,
                    List.of(new ImportedQuestionDraft(
                            1,
                            "",
                            "",
                            1,
                            1,
                            List.of(),
                            importIssues
                    )),
                    List.of(),
                    importIssues,
                    pdfPath
            );
        }

        PdfTextExtractionResult textResult = textExtractionService.extractText(pdfPath);
        boolean lowText = textResult.text().length() < 30;
        PdfImageExtractionResult imageResult = imageExtractionService.extractImages(pdfPath, lowText);

        if (lowText) {
            PdfImageExtractionResult ocrImageResult = imageExtractionService.renderPagesForOcr(pdfPath);
            textResult = attemptLocalOcr(textResult, ocrImageResult);
        }

        return parser.parse(textResult, imageResult, pdfPath, moduleId, topicId);
    }

    private PdfTextExtractionResult attemptLocalOcr(
            PdfTextExtractionResult textResult,
            PdfImageExtractionResult imageResult
    ) {

        List<ImportIssue> issues = new ArrayList<>(textResult.issues());
        issues.addAll(imageResult.issues());

        if (!ocrService.isAvailable()) {
            issues.add(new ImportIssue(
                    ImportIssueSeverity.WARNING,
                    "This PDF does not contain selectable text. No local OCR engine is available, so PararePilot could not extract questions automatically."
            ));
            return new PdfTextExtractionResult(
                    textResult.text(),
                    textResult.pageTexts(),
                    textResult.lines(),
                    textResult.pageCount(),
                    issues
            );
        }

        int pageCount = Math.max(
                textResult.pageCount(),
                imageResult.images().stream()
                        .mapToInt(ExtractedPdfImage::pageNumber)
                        .max()
                        .orElse(0)
        );
        List<String> ocrPageTexts = new ArrayList<>();

        for (int pageNumber = 1; pageNumber <= pageCount; pageNumber++) {
            List<ExtractedPdfImage> pageImages = imagesForPage(imageResult.images(), pageNumber);
            List<String> pageTextParts = new ArrayList<>();

            for (ExtractedPdfImage image : pageImages) {
                QuestionImageStorage.resolveImagePath(image.imagePath()).ifPresentOrElse(path -> {
                    OcrResult result = ocrService.extractTextFromImage(path);

                    if (!result.text().isBlank()) {
                        pageTextParts.add(result.text());
                    }

                    issues.addAll(result.issues());
                }, () -> issues.add(new ImportIssue(
                        ImportIssueSeverity.WARNING,
                        "An OCR page image could not be found."
                )));
            }

            ocrPageTexts.add(String.join("\n\n", pageTextParts).strip());
        }

        String ocrText = String.join("\n\n", ocrPageTexts).strip();

        if (ocrText.isBlank()) {
            issues.add(new ImportIssue(
                    ImportIssueSeverity.WARNING,
                    "Local OCR did not detect worksheet text. Please review the draft manually."
            ));
            return new PdfTextExtractionResult(
                    textResult.text(),
                    textResult.pageTexts(),
                    textResult.lines(),
                    textResult.pageCount(),
                    issues
            );
        }

        issues.add(new ImportIssue(
                ImportIssueSeverity.INFO,
                "Question text was extracted using local OCR."
        ));
        return new PdfTextExtractionResult(ocrText, ocrPageTexts, pageCount, issues);
    }

    private List<ExtractedPdfImage> imagesForPage(List<ExtractedPdfImage> images, int pageNumber) {
        return images.stream()
                .filter(image -> image.pageNumber() == pageNumber)
                .sorted(Comparator.comparingInt(ExtractedPdfImage::imageIndexOnPage))
                .toList();
    }

    private List<ImportIssue> validatePdf(Path pdfPath) {
        if (pdfPath == null || !Files.isRegularFile(pdfPath)) {
            return List.of(new ImportIssue(ImportIssueSeverity.ERROR, "Choose a PDF file to import."));
        }

        String fileName = pdfPath.getFileName() == null
                ? ""
                : pdfPath.getFileName().toString().toLowerCase(Locale.ROOT);

        if (!fileName.endsWith(".pdf")) {
            return List.of(new ImportIssue(ImportIssueSeverity.ERROR, "The selected file must be a PDF."));
        }

        return List.of();
    }

    private String fallbackTitle(Path pdfPath) {
        if (pdfPath == null || pdfPath.getFileName() == null) {
            return "Imported worksheet";
        }

        String fileName = pdfPath.getFileName().toString();
        int extensionStart = fileName.lastIndexOf('.');
        return extensionStart > 0 ? fileName.substring(0, extensionStart) : fileName;
    }
}
