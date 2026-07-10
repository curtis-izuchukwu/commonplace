package com.pararepilot.importer;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class WorksheetDraftParserTest {

    private final WorksheetDraftParser parser = new WorksheetDraftParser();

    @Test
    void numberedQuestionsSplitCorrectlyAndMarksAreDetected() {
        String text = """
                1. Define a stack. [2 marks]
                2. Explain how a queue differs from a stack. (3 marks)
                """;

        ImportedWorksheetDraft draft = parse(text);

        assertEquals(2, draft.questions().size());
        assertEquals("Define a stack. [2 marks]", draft.questions().get(0).questionText());
        assertEquals(2, draft.questions().get(0).maxMarks());
        assertEquals(3, draft.questions().get(1).maxMarks());
    }

    @Test
    void qStyleQuestionsAndMarkSchemesSplitCorrectly() {
        String text = """
                Q1. What is encapsulation?
                Q2. What is inheritance?

                Answers
                Q1. Grouping data and behaviour inside an object.
                Q2. Reusing and extending behaviour from a parent class.
                """;

        ImportedWorksheetDraft draft = parse(text);

        assertEquals(2, draft.questions().size());
        assertEquals(
                "Grouping data and behaviour inside an object.",
                draft.questions().get(0).markScheme()
        );
        assertEquals(
                "Reusing and extending behaviour from a parent class.",
                draft.questions().get(1).markScheme()
        );
    }

    @Test
    void ocrQuestionLabelsWithoutSpacingStillSplitCorrectly() {
        String text = """
                Question1 Define binary search. Question2 Explain why binary search needs sorted input.
                Question3 State the worst case time complexity.
                """;

        ImportedWorksheetDraft draft = parse(text);

        assertEquals(3, draft.questions().size());
        assertEquals("Define binary search.", draft.questions().get(0).questionText());
        assertEquals("Explain why binary search needs sorted input.", draft.questions().get(1).questionText());
        assertEquals("State the worst case time complexity.", draft.questions().get(2).questionText());
    }

    @Test
    void plainNumberedOcrLinesStillSplitCorrectly() {
        String text = """
                1 Define a stack
                2 Explain how a queue differs from a stack
                3 State one use of a queue
                """;

        ImportedWorksheetDraft draft = parse(text);

        assertEquals(3, draft.questions().size());
        assertEquals("Define a stack", draft.questions().get(0).questionText());
        assertEquals("Explain how a queue differs from a stack", draft.questions().get(1).questionText());
        assertEquals("State one use of a queue", draft.questions().get(2).questionText());
    }

    @Test
    void emptyOcrQuestionMarkersDoNotCreateEmptyQuestions() {
        String text = """
                Question 1
                Question 2 Define a queue.
                Question 3
                Question 4 Explain FIFO ordering.
                """;

        ImportedWorksheetDraft draft = parse(text);

        assertEquals(2, draft.questions().size());
        assertEquals("Define a queue.", draft.questions().get(0).questionText());
        assertEquals("Explain FIFO ordering.", draft.questions().get(1).questionText());
    }

    @Test
    void leadingContextIsKeptWithTheFirstDetectedQuestion() {
        String text = """
                Use the following graph for all questions.

                1) What is the shortest path from A to C?
                2) Explain Dijkstra's algorithm.
                """;

        ImportedWorksheetDraft draft = parse(text);

        assertEquals(2, draft.questions().size());
        assertTrue(draft.questions().get(0).questionText().startsWith("Use the following graph"));
        assertTrue(draft.questions().get(0).questionText().contains("What is the shortest path"));
    }

    @Test
    void missingMarkSchemesAreAllowedInDraft() {
        ImportedWorksheetDraft draft = parse("""
                1. What is a binary search tree?
                2. Why does in-order traversal produce sorted values?
                """);

        assertEquals(2, draft.questions().size());
        assertEquals("", draft.questions().get(0).markScheme());
        assertEquals("", draft.questions().get(1).markScheme());
    }

    @Test
    void emptyExtractedTextCreatesEditableDraftWithWarning() {
        ImportedWorksheetDraft draft = parse("");

        assertEquals(1, draft.questions().size());
        assertEquals("", draft.questions().get(0).questionText());
        assertTrue(hasSeverity(draft.issues(), ImportIssueSeverity.WARNING));
    }

    @Test
    void lowConfidenceParseStillReturnsDraft() {
        ImportedWorksheetDraft draft = parse("Explain sorting algorithms in your own words.");

        assertEquals(1, draft.questions().size());
        assertEquals("Explain sorting algorithms in your own words.", draft.questions().get(0).questionText());
        assertTrue(hasSeverity(draft.issues(), ImportIssueSeverity.WARNING));
    }

    @Test
    void noOpOcrReportsUnavailableWarning() {
        NoOpOcrService ocrService = new NoOpOcrService();

        OcrResult result = ocrService.extractTextFromImage(Path.of("missing.png"));

        assertFalse(ocrService.isAvailable());
        assertEquals("", result.text());
        assertTrue(hasSeverity(result.issues(), ImportIssueSeverity.WARNING));
    }

    @Test
    void unpositionedImagesFallbackToQuestionOrderOnTheSamePage() {
        String text = """
                1. Question one?
                2. Question two?
                """;

        ImportedWorksheetDraft draft = parse(
                text,
                List.of(new ExtractedPdfImage("images/diagram.png", 1, 1, "Diagram"))
        );

        assertEquals(2, draft.questions().size());
        assertEquals(0, draft.unattachedImages().size());
        assertEquals(List.of("images/diagram.png"), draft.questions().get(0).imagePaths());
    }

    @Test
    void imagesWithoutAnyQuestionOnThePageRemainUnattached() {
        PdfTextExtractionResult textResult = new PdfTextExtractionResult(
                "1. Page one question?",
                List.of("1. Page one question?", "Reference diagram"),
                2,
                List.of()
        );
        PdfImageExtractionResult imageResult = new PdfImageExtractionResult(
                List.of(new ExtractedPdfImage("images/reference.png", 2, 1, "Reference diagram")),
                List.of()
        );

        ImportedWorksheetDraft draft = parser.parse(
                textResult,
                imageResult,
                Path.of("worksheet.pdf"),
                1L,
                2L
        );

        assertEquals(1, draft.unattachedImages().size());
        assertEquals("images/reference.png", draft.unattachedImages().get(0).imagePath());
    }

    @Test
    void imageOnPageWithOneQuestionAttachesToThatQuestion() {
        PdfTextExtractionResult textResult = new PdfTextExtractionResult(
                "1. Page one question?\n\n2. Page two question?",
                List.of("1. Page one question?", "2. Page two question?"),
                2,
                List.of()
        );
        PdfImageExtractionResult imageResult = new PdfImageExtractionResult(
                List.of(new ExtractedPdfImage("images/page-two.png", 2, 1, "Page two diagram")),
                List.of()
        );

        ImportedWorksheetDraft draft = parser.parse(
                textResult,
                imageResult,
                Path.of("worksheet.pdf"),
                1L,
                2L
        );

        assertEquals(0, draft.unattachedImages().size());
        assertEquals(List.of("images/page-two.png"), draft.questions().get(1).imagePaths());
    }

    @Test
    void positionedImageAttachesToNearestPrecedingQuestionOnSamePage() {
        PdfTextExtractionResult textResult = new PdfTextExtractionResult(
                "1. First question?\n2. Second question?",
                List.of("1. First question?\n2. Second question?"),
                List.of(
                        new PdfTextLine(1, "1. First question?", 100),
                        new PdfTextLine(1, "2. Second question?", 300)
                ),
                1,
                List.of()
        );
        PdfImageExtractionResult imageResult = new PdfImageExtractionResult(
                List.of(new ExtractedPdfImage("images/second-diagram.png", 1, 1, 340, "Second diagram")),
                List.of()
        );

        ImportedWorksheetDraft draft = parser.parse(
                textResult,
                imageResult,
                Path.of("worksheet.pdf"),
                1L,
                2L
        );

        assertEquals(0, draft.unattachedImages().size());
        assertEquals(List.of("images/second-diagram.png"), draft.questions().get(1).imagePaths());
    }

    @Test
    void questionReferencingFigureNumberGetsMatchingImageByFigureOrder() {
        PdfTextExtractionResult textResult = new PdfTextExtractionResult(
                "1. Explain Figure 1.\n2. Explain Figure 2.",
                List.of("1. Explain Figure 1.\n2. Explain Figure 2."),
                1,
                List.of()
        );
        PdfImageExtractionResult imageResult = new PdfImageExtractionResult(
                List.of(
                        new ExtractedPdfImage("images/figure-one.png", 1, 1, 120, "Figure one"),
                        new ExtractedPdfImage("images/figure-two.png", 1, 2, 360, "Figure two")
                ),
                List.of()
        );

        ImportedWorksheetDraft draft = parser.parse(
                textResult,
                imageResult,
                Path.of("worksheet.pdf"),
                1L,
                2L
        );

        assertEquals(List.of("images/figure-one.png"), draft.questions().get(0).imagePaths());
        assertEquals(List.of("images/figure-two.png"), draft.questions().get(1).imagePaths());
    }

    @Test
    void questionReferencingFigureNumberUsesCaptionPositionWhenAvailable() {
        PdfTextExtractionResult textResult = new PdfTextExtractionResult(
                "1. Explain Figure 1.\n2. Explain Figure 2.",
                List.of("1. Explain Figure 1.\n2. Explain Figure 2."),
                List.of(
                        new PdfTextLine(1, "Figure 1", 140),
                        new PdfTextLine(1, "Figure 2", 420)
                ),
                1,
                List.of()
        );
        PdfImageExtractionResult imageResult = new PdfImageExtractionResult(
                List.of(
                        new ExtractedPdfImage("images/figure-two.png", 1, 1, 400, "Figure two"),
                        new ExtractedPdfImage("images/figure-one.png", 1, 2, 160, "Figure one")
                ),
                List.of()
        );

        ImportedWorksheetDraft draft = parser.parse(
                textResult,
                imageResult,
                Path.of("worksheet.pdf"),
                1L,
                2L
        );

        assertEquals(List.of("images/figure-one.png"), draft.questions().get(0).imagePaths());
        assertEquals(List.of("images/figure-two.png"), draft.questions().get(1).imagePaths());
    }

    private ImportedWorksheetDraft parse(String text) {
        return parse(text, List.of());
    }

    private ImportedWorksheetDraft parse(String text, List<ExtractedPdfImage> images) {
        return parser.parse(
                new PdfTextExtractionResult(text, List.of(text), 1, List.of()),
                new PdfImageExtractionResult(images, List.of()),
                Path.of("worksheet.pdf"),
                1L,
                2L
        );
    }

    private boolean hasSeverity(List<ImportIssue> issues, ImportIssueSeverity severity) {
        return issues.stream().anyMatch(issue -> issue.severity() == severity);
    }
}
