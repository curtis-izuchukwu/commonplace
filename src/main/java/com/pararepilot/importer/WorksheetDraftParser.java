package com.pararepilot.importer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WorksheetDraftParser {

    private static final int MIN_PROMPT_SIGNAL_LENGTH = 3;
    private static final Pattern ANSWER_HEADING_PATTERN = Pattern.compile(
            "(?im)^\\s*(answers?|mark\\s*scheme|solutions?)\\s*:?\\s*$"
    );
    private static final Pattern QUESTION_START_PATTERN = Pattern.compile(
            "(?im)(?:^|\\n)\\s*(?:"
                    + "(?:questions?|questlon|q)\\s*[#:.\\-]?\\s*(\\d{1,3})\\s*[.)\\]:\\-]?"
                    + "|\\(?\\s*(\\d{1,3})\\s*\\)?\\s*[.)\\]:\\-]"
                    + "|(\\d{1,3})\\s+(?!(?:marks?|pts?|points?)\\b)(?=\\S)"
                    + ")\\s*"
                    + "|\\b(?:questions?|questlon|q)\\s*[#:.\\-]?\\s*(\\d{1,3})\\s*[.)\\]:\\-]?\\s+"
    );
    private static final Pattern ORPHAN_QUESTION_MARKER_LINE_PATTERN = Pattern.compile(
            "(?im)^\\s*(?:"
                    + "(?:questions?|questlon|q)\\s*[#:.\\-]?\\s*\\d{1,3}\\s*[.)\\]:\\-]?"
                    + "|\\(?\\s*\\d{1,3}\\s*\\)?\\s*[.)\\]:\\-]?"
                    + ")\\s*$"
    );
    private static final Pattern MARKS_PATTERN = Pattern.compile(
            "(?i)(?:[\\[(]\\s*(\\d{1,2})\\s*(?:marks?|pts?|points?)?\\s*[\\])])|\\b(\\d{1,2})\\s*marks?\\b"
    );
    private static final Pattern FIGURE_REFERENCE_PATTERN = Pattern.compile(
            "(?i)\\b(?:fig(?:ure)?\\.?|diagram)\\s*(\\d{1,3})\\b"
    );

    public ImportedWorksheetDraft parse(
            PdfTextExtractionResult textResult,
            PdfImageExtractionResult imageResult,
            Path sourcePdfPath,
            Long moduleId,
            Long topicId
    ) {

        List<ImportIssue> issues = new ArrayList<>();

        if (textResult != null) {
            issues.addAll(textResult.issues());
        }

        if (imageResult != null) {
            issues.addAll(imageResult.issues());
        }

        String text = textResult == null ? "" : normalize(textResult.text());
        List<String> pageTexts = textResult == null ? List.of() : textResult.pageTexts();
        List<PdfTextLine> textLines = textResult == null ? List.of() : textResult.lines();
        List<ExtractedPdfImage> images = imageResult == null ? List.of() : imageResult.images();

        String suggestedTitle = titleFrom(sourcePdfPath, text);

        if (text.isBlank()) {
            issues.add(new ImportIssue(
                    ImportIssueSeverity.WARNING,
                    "No selectable question text was found. You can still create questions manually from this draft."
            ));

            return new ImportedWorksheetDraft(
                    suggestedTitle,
                    moduleId,
                    topicId,
                    List.of(new ImportedQuestionDraft(
                            1,
                            "",
                            "",
                            1,
                            1,
                            List.of(),
                            List.of(new ImportIssue(
                                    ImportIssueSeverity.WARNING,
                                    "Question text could not be detected automatically."
                            ))
                    )),
                    images,
                    issues,
                    sourcePdfPath
            );
        }

        TextSections sections = splitAnswers(text);
        List<QuestionMatch> matches = findQuestionMatches(sections.questionText());
        List<QuestionPosition> questionPositions = findQuestionPositions(textLines);
        List<FigureCaption> figureCaptions = findFigureCaptions(textLines);
        List<ImportedQuestionDraft> questions = new ArrayList<>();

        if (matches.isEmpty()) {
            issues.add(new ImportIssue(
                    ImportIssueSeverity.WARNING,
                    "No numbered questions were detected. The extracted text was placed into one editable draft question."
            ));

            int pageNumber = pageFor(sections.questionText(), pageTexts);
            double yPosition = firstPositionOnPage(questionPositions, pageNumber);

            questions.add(new ImportedQuestionDraft(
                    1,
                    sections.questionText().strip(),
                    "",
                    detectMarks(sections.questionText()),
                    pageNumber,
                    yPosition,
                    List.of(),
                    List.of(new ImportIssue(
                            ImportIssueSeverity.WARNING,
                            "Please split this draft into separate questions if needed."
                    ))
            ));

        } else {
            String leadingText = sections.questionText()
                    .substring(0, matches.get(0).start())
                    .strip();

            for (int i = 0; i < matches.size(); i++) {
                QuestionMatch current = matches.get(i);
                int end = i + 1 < matches.size()
                        ? matches.get(i + 1).start()
                        : sections.questionText().length();

                String questionBlock = sections.questionText()
                        .substring(current.start(), end)
                        .strip();
                int questionNumber = current.questionNumber() == 0
                        ? i + 1
                        : current.questionNumber();
                String prompt = cleanOrphanedQuestionMarkers(cleanQuestionPrefix(questionBlock)).strip();

                if (i == 0
                        && !leadingText.isBlank()
                        && hasUsefulText(leadingText)
                        && !isOrphanQuestionMarker(leadingText)) {
                    prompt = leadingText + "\n\n" + prompt;
                }

                if (!hasUsefulText(prompt)) {
                    issues.add(new ImportIssue(
                            ImportIssueSeverity.WARNING,
                            "Skipped an empty OCR question marker for question " + questionNumber + "."
                    ));
                    continue;
                }

                int fallbackPage = pageFor(prompt, pageTexts);
                QuestionPosition position = positionFor(questionPositions, questionNumber, i, fallbackPage);

                questions.add(new ImportedQuestionDraft(
                        questionNumber,
                        prompt,
                        markSchemeFor(questionNumber, sections.answerText()),
                        detectMarks(questionBlock),
                        position == null ? fallbackPage : position.pageNumber(),
                        position == null ? Double.NaN : position.yPosition(),
                        List.of(),
                        List.of()
                ));
            }

            if (questions.isEmpty()) {
                issues.add(new ImportIssue(
                        ImportIssueSeverity.WARNING,
                        "Question markers were detected, but none had usable text. The extracted text was placed into one editable draft question."
                ));
                questions.add(new ImportedQuestionDraft(
                        1,
                        sections.questionText().strip(),
                        "",
                        detectMarks(sections.questionText()),
                        pageFor(sections.questionText(), pageTexts),
                        firstPositionOnPage(questionPositions, pageFor(sections.questionText(), pageTexts)),
                        List.of(),
                        List.of(new ImportIssue(
                                ImportIssueSeverity.WARNING,
                                "Please split this draft into separate questions if needed."
                        ))
                ));
            }
        }

        ImageAttachmentResult attachmentResult = attachImages(questions, images, figureCaptions);

        if (!attachmentResult.unattachedImages().isEmpty()) {
            issues.add(new ImportIssue(
                    ImportIssueSeverity.WARNING,
                    attachmentResult.unattachedImages().size()
                            + " image(s) could not be confidently attached to a question."
            ));
        }

        return new ImportedWorksheetDraft(
                suggestedTitle,
                moduleId,
                topicId,
                attachmentResult.questions(),
                attachmentResult.unattachedImages(),
                issues,
                sourcePdfPath
        );
    }

    private String titleFrom(Path sourcePdfPath, String text) {
        if (sourcePdfPath != null && sourcePdfPath.getFileName() != null) {
            String fileName = sourcePdfPath.getFileName().toString();
            int extension = fileName.lastIndexOf('.');

            if (extension > 0) {
                return fileName.substring(0, extension);
            }

            return fileName;
        }

        return text.lines()
                .map(String::strip)
                .filter(line -> !line.isBlank())
                .filter(line -> line.length() <= 120)
                .findFirst()
                .orElse("Imported worksheet");
    }

    private TextSections splitAnswers(String text) {
        Matcher matcher = ANSWER_HEADING_PATTERN.matcher(text);

        if (!matcher.find()) {
            return new TextSections(text, "");
        }

        return new TextSections(
                text.substring(0, matcher.start()).strip(),
                text.substring(matcher.end()).strip()
        );
    }

    private List<QuestionMatch> findQuestionMatches(String text) {
        List<QuestionMatch> rawMatches = new ArrayList<>();
        Matcher matcher = QUESTION_START_PATTERN.matcher(text);

        while (matcher.find()) {
            int number = parseInt(
                    firstNonBlank(
                            matcher.group(1),
                            matcher.group(2),
                            matcher.group(3),
                            matcher.group(4)
                    ),
                    0
            );
            rawMatches.add(new QuestionMatch(matcher.start(), matcher.end(), number));
        }

        return filterQuestionMatches(text, rawMatches);
    }

    private List<QuestionMatch> filterQuestionMatches(String text, List<QuestionMatch> rawMatches) {
        List<QuestionMatch> matches = new ArrayList<>();

        for (int i = 0; i < rawMatches.size(); i++) {
            QuestionMatch match = rawMatches.get(i);
            int nextStart = i + 1 < rawMatches.size()
                    ? rawMatches.get(i + 1).start()
                    : text.length();
            String promptCandidate = text.substring(match.prefixEnd(), nextStart).strip();

            if (isPlausibleQuestionStart(match, promptCandidate)) {
                matches.add(match);
            }
        }

        return matches;
    }

    private boolean isPlausibleQuestionStart(QuestionMatch match, String promptCandidate) {
        if (match.questionNumber() <= 0 || promptCandidate == null || promptCandidate.isBlank()) {
            return false;
        }

        String normalizedPrompt = normalize(promptCandidate);

        if (!hasUsefulText(normalizedPrompt)) {
            return false;
        }

        String lowerPrompt = normalizedPrompt.toLowerCase(Locale.ROOT);
        return !lowerPrompt.matches("^(marks?|pts?|points?|answers?|solutions?|mark\\s+scheme)\\b.*");
    }

    private boolean hasUsefulText(String value) {
        if (value == null) {
            return false;
        }

        long signalCharacters = value.chars()
                .filter(Character::isLetterOrDigit)
                .limit(MIN_PROMPT_SIGNAL_LENGTH)
                .count();

        return signalCharacters >= MIN_PROMPT_SIGNAL_LENGTH || value.contains("?");
    }

    private List<QuestionPosition> findQuestionPositions(List<PdfTextLine> lines) {
        List<QuestionPosition> positions = new ArrayList<>();

        if (lines == null || lines.isEmpty()) {
            return positions;
        }

        for (PdfTextLine line : lines) {
            Matcher matcher = QUESTION_START_PATTERN.matcher(line.text());

            if (!matcher.find()) {
                continue;
            }

            int number = parseInt(
                    firstNonBlank(
                            matcher.group(1),
                            matcher.group(2),
                            matcher.group(3),
                            matcher.group(4)
                    ),
                    0
            );

            if (number > 0) {
                positions.add(new QuestionPosition(number, line.pageNumber(), line.yPosition()));
            }
        }

        return positions;
    }

    private List<FigureCaption> findFigureCaptions(List<PdfTextLine> lines) {
        List<FigureCaption> captions = new ArrayList<>();

        if (lines == null || lines.isEmpty()) {
            return captions;
        }

        for (PdfTextLine line : lines) {
            Matcher matcher = FIGURE_REFERENCE_PATTERN.matcher(line.text());

            while (matcher.find()) {
                int figureNumber = parseInt(matcher.group(1), 0);

                if (figureNumber > 0) {
                    captions.add(new FigureCaption(figureNumber, line.pageNumber(), line.yPosition()));
                }
            }
        }

        return captions;
    }

    private QuestionPosition positionFor(
            List<QuestionPosition> positions,
            int questionNumber,
            int occurrenceIndex,
            int fallbackPage
    ) {

        List<QuestionPosition> matches = positions.stream()
                .filter(position -> position.questionNumber() == questionNumber)
                .toList();

        if (!matches.isEmpty()) {
            return matches.get(0);
        }

        List<QuestionPosition> onFallbackPage = positions.stream()
                .filter(position -> position.pageNumber() == fallbackPage)
                .toList();

        if (occurrenceIndex >= 0 && occurrenceIndex < onFallbackPage.size()) {
            return onFallbackPage.get(occurrenceIndex);
        }

        return null;
    }

    private double firstPositionOnPage(List<QuestionPosition> positions, int pageNumber) {
        return positions.stream()
                .filter(position -> position.pageNumber() == pageNumber)
                .mapToDouble(QuestionPosition::yPosition)
                .min()
                .orElse(Double.NaN);
    }

    private String cleanQuestionPrefix(String questionBlock) {
        return QUESTION_START_PATTERN.matcher(questionBlock)
                .replaceFirst("")
                .strip();
    }

    private String cleanOrphanedQuestionMarkers(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        return ORPHAN_QUESTION_MARKER_LINE_PATTERN.matcher(value)
                .replaceAll("")
                .strip();
    }

    private boolean isOrphanQuestionMarker(String value) {
        return value != null
                && ORPHAN_QUESTION_MARKER_LINE_PATTERN.matcher(value.strip()).matches();
    }

    private int detectMarks(String questionBlock) {
        Matcher matcher = MARKS_PATTERN.matcher(questionBlock);

        while (matcher.find()) {
            int marks = parseInt(firstNonBlank(matcher.group(1), matcher.group(2)), 0);

            if (marks > 0) {
                return marks;
            }
        }

        return 1;
    }

    private String markSchemeFor(int questionNumber, String answerText) {
        if (answerText == null || answerText.isBlank()) {
            return "";
        }

        List<QuestionMatch> answerMatches = findQuestionMatches(answerText);

        if (answerMatches.isEmpty()) {
            return answerText.strip();
        }

        for (int i = 0; i < answerMatches.size(); i++) {
            QuestionMatch match = answerMatches.get(i);

            if (match.questionNumber() != questionNumber) {
                continue;
            }

            int end = i + 1 < answerMatches.size()
                    ? answerMatches.get(i + 1).start()
                    : answerText.length();

            return cleanQuestionPrefix(answerText.substring(match.start(), end)).strip();
        }

        return "";
    }

    private int pageFor(String questionText, List<String> pageTexts) {
        String needle = searchablePrefix(questionText);

        if (needle.isBlank()) {
            return 1;
        }

        for (int i = 0; i < pageTexts.size(); i++) {
            String pageText = normalize(pageTexts.get(i)).toLowerCase(Locale.ROOT);

            if (pageText.contains(needle)) {
                return i + 1;
            }
        }

        return 1;
    }

    private String searchablePrefix(String value) {
        String normalized = normalize(value).toLowerCase(Locale.ROOT);

        if (normalized.length() <= 34) {
            return normalized;
        }

        return normalized.substring(0, 34).strip();
    }

    private ImageAttachmentResult attachImages(
            List<ImportedQuestionDraft> questions,
            List<ExtractedPdfImage> images,
            List<FigureCaption> figureCaptions
    ) {

        if (images.isEmpty() || questions.isEmpty()) {
            return new ImageAttachmentResult(questions, images);
        }

        List<ImportedQuestionDraft> sortedQuestions = questions.stream()
                .sorted(Comparator.comparingInt(ImportedQuestionDraft::questionNumber))
                .toList();
        List<ImportedQuestionDraft> updatedQuestions = new ArrayList<>(sortedQuestions);
        List<ExtractedPdfImage> unattachedImages = new ArrayList<>();
        Set<String> attachedImagePaths = new HashSet<>();

        List<ExtractedPdfImage> sortedImages = images.stream()
                .sorted(Comparator.comparingInt(ExtractedPdfImage::pageNumber)
                        .thenComparingDouble(this::imageSortPosition)
                        .thenComparingInt(ExtractedPdfImage::imageIndexOnPage))
                .toList();

        attachFigureReferencedImages(updatedQuestions, sortedImages, figureCaptions, attachedImagePaths);

        for (ExtractedPdfImage image : sortedImages) {
            if (attachedImagePaths.contains(image.imagePath())) {
                continue;
            }

            List<Integer> indexesOnSamePage = new ArrayList<>();

            for (int i = 0; i < updatedQuestions.size(); i++) {
                if (updatedQuestions.get(i).pageNumber() == image.pageNumber()) {
                    indexesOnSamePage.add(i);
                }
            }

            if (indexesOnSamePage.size() == 1) {
                int questionIndex = indexesOnSamePage.get(0);
                ImportedQuestionDraft question = updatedQuestions.get(questionIndex);
                List<String> imagePaths = new ArrayList<>(question.imagePaths());
                imagePaths.add(image.imagePath());
                updatedQuestions.set(questionIndex, copyWithImages(question, imagePaths));
                attachedImagePaths.add(image.imagePath());
            } else if (hasKnownPosition(image)) {
                int questionIndex = nearestPrecedingQuestionIndex(image, indexesOnSamePage, updatedQuestions);

                if (questionIndex >= 0) {
                    ImportedQuestionDraft question = updatedQuestions.get(questionIndex);
                    List<String> imagePaths = new ArrayList<>(question.imagePaths());
                    imagePaths.add(image.imagePath());
                    updatedQuestions.set(questionIndex, copyWithImages(question, imagePaths));
                    attachedImagePaths.add(image.imagePath());
                } else {
                    int fallbackIndex = samePageQuestionByImageOrder(image, sortedImages, indexesOnSamePage);

                    if (fallbackIndex >= 0) {
                        ImportedQuestionDraft question = updatedQuestions.get(fallbackIndex);
                        List<String> imagePaths = new ArrayList<>(question.imagePaths());
                        imagePaths.add(image.imagePath());
                        updatedQuestions.set(fallbackIndex, copyWithImages(question, imagePaths));
                        attachedImagePaths.add(image.imagePath());
                    } else {
                        unattachedImages.add(image);
                    }
                }
            } else {
                int questionIndex = samePageQuestionByImageOrder(image, sortedImages, indexesOnSamePage);

                if (questionIndex >= 0) {
                ImportedQuestionDraft question = updatedQuestions.get(questionIndex);
                List<String> imagePaths = new ArrayList<>(question.imagePaths());
                imagePaths.add(image.imagePath());
                updatedQuestions.set(questionIndex, copyWithImages(question, imagePaths));
                attachedImagePaths.add(image.imagePath());
            } else {
                unattachedImages.add(image);
            }
            }
        }

        return new ImageAttachmentResult(updatedQuestions, unattachedImages);
    }

    private void attachFigureReferencedImages(
            List<ImportedQuestionDraft> questions,
            List<ExtractedPdfImage> sortedImages,
            List<FigureCaption> figureCaptions,
            Set<String> attachedImagePaths
    ) {

        for (int i = 0; i < questions.size(); i++) {
            ImportedQuestionDraft question = questions.get(i);
            List<Integer> figureNumbers = figureNumbersIn(question.questionText());

            if (figureNumbers.isEmpty()) {
                continue;
            }

            List<String> imagePaths = new ArrayList<>(question.imagePaths());

            for (Integer figureNumber : figureNumbers) {
                ExtractedPdfImage image = imageForFigureNumber(
                        figureNumber,
                        question,
                        sortedImages,
                        figureCaptions,
                        attachedImagePaths
                );

                if (image == null || attachedImagePaths.contains(image.imagePath())) {
                    continue;
                }

                imagePaths.add(image.imagePath());
                attachedImagePaths.add(image.imagePath());
            }

            questions.set(i, copyWithImages(question, imagePaths));
        }
    }

    private List<Integer> figureNumbersIn(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        Set<Integer> numbers = new LinkedHashSet<>();
        Matcher matcher = FIGURE_REFERENCE_PATTERN.matcher(text);

        while (matcher.find()) {
            int figureNumber = parseInt(matcher.group(1), 0);

            if (figureNumber > 0) {
                numbers.add(figureNumber);
            }
        }

        return new ArrayList<>(numbers);
    }

    private ExtractedPdfImage imageForFigureNumber(
            int figureNumber,
            ImportedQuestionDraft question,
            List<ExtractedPdfImage> sortedImages,
            List<FigureCaption> figureCaptions,
            Set<String> attachedImagePaths
    ) {

        ExtractedPdfImage captionImage = imageNearFigureCaption(
                figureNumber,
                sortedImages,
                figureCaptions,
                attachedImagePaths
        );

        if (captionImage != null) {
            return captionImage;
        }

        List<ExtractedPdfImage> samePageImages = sortedImages.stream()
                .filter(image -> image.pageNumber() == question.pageNumber())
                .filter(image -> !attachedImagePaths.contains(image.imagePath()))
                .toList();

        if (figureNumber > 0 && figureNumber <= sortedImages.size()) {
            ExtractedPdfImage globalOrdinalImage = sortedImages.get(figureNumber - 1);

            if (!attachedImagePaths.contains(globalOrdinalImage.imagePath())
                    && (globalOrdinalImage.pageNumber() == question.pageNumber()
                            || samePageImages.isEmpty())) {
                return globalOrdinalImage;
            }
        }

        if (figureNumber > 0 && figureNumber <= samePageImages.size()) {
            return samePageImages.get(figureNumber - 1);
        }

        if (samePageImages.size() == 1) {
            return samePageImages.get(0);
        }

        return null;
    }

    private ExtractedPdfImage imageNearFigureCaption(
            int figureNumber,
            List<ExtractedPdfImage> sortedImages,
            List<FigureCaption> figureCaptions,
            Set<String> attachedImagePaths
    ) {

        List<FigureCaption> matchingCaptions = figureCaptions.stream()
                .filter(caption -> caption.figureNumber() == figureNumber)
                .toList();

        ExtractedPdfImage bestImage = null;
        double bestDistance = Double.MAX_VALUE;

        for (FigureCaption caption : matchingCaptions) {
            for (ExtractedPdfImage image : sortedImages) {
                if (attachedImagePaths.contains(image.imagePath())
                        || image.pageNumber() != caption.pageNumber()
                        || !hasKnownPosition(image)) {
                    continue;
                }

                double distance = Math.abs(image.yPosition() - caption.yPosition());

                if (distance < bestDistance) {
                    bestImage = image;
                    bestDistance = distance;
                }
            }
        }

        return bestImage;
    }

    private int nearestPrecedingQuestionIndex(
            ExtractedPdfImage image,
            List<Integer> indexesOnSamePage,
            List<ImportedQuestionDraft> questions
    ) {

        int bestIndex = -1;
        double bestDistance = Double.MAX_VALUE;

        for (Integer questionIndex : indexesOnSamePage) {
            ImportedQuestionDraft question = questions.get(questionIndex);

            if (!hasKnownPosition(question)) {
                continue;
            }

            double distance = image.yPosition() - question.yPosition();

            if (distance >= -4 && distance < bestDistance) {
                bestIndex = questionIndex;
                bestDistance = distance;
            }
        }

        if (bestIndex >= 0) {
            return bestIndex;
        }

        for (Integer questionIndex : indexesOnSamePage) {
            ImportedQuestionDraft question = questions.get(questionIndex);

            if (!hasKnownPosition(question)) {
                continue;
            }

            double distance = Math.abs(image.yPosition() - question.yPosition());

            if (distance < bestDistance) {
                bestIndex = questionIndex;
                bestDistance = distance;
            }
        }

        return bestIndex;
    }

    private int samePageQuestionByImageOrder(
            ExtractedPdfImage image,
            List<ExtractedPdfImage> sortedImages,
            List<Integer> indexesOnSamePage
    ) {

        int imageOrdinalOnPage = 0;

        for (ExtractedPdfImage candidate : sortedImages) {
            if (candidate.pageNumber() != image.pageNumber()) {
                continue;
            }

            if (candidate == image) {
                break;
            }

            imageOrdinalOnPage++;
        }

        int boundedIndex = Math.min(imageOrdinalOnPage, indexesOnSamePage.size() - 1);
        return boundedIndex >= 0 ? indexesOnSamePage.get(boundedIndex) : -1;
    }

    private boolean hasKnownPosition(ImportedQuestionDraft question) {
        return question != null && !Double.isNaN(question.yPosition());
    }

    private boolean hasKnownPosition(ExtractedPdfImage image) {
        return image != null && !Double.isNaN(image.yPosition());
    }

    private double imageSortPosition(ExtractedPdfImage image) {
        return hasKnownPosition(image) ? image.yPosition() : Double.MAX_VALUE;
    }

    private ImportedQuestionDraft copyWithImages(
            ImportedQuestionDraft question,
            List<String> imagePaths
    ) {

        return new ImportedQuestionDraft(
                question.questionNumber(),
                question.questionText(),
                question.markScheme(),
                question.maxMarks(),
                question.pageNumber(),
                question.yPosition(),
                imagePaths,
                question.issues()
        );
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.replace("\r\n", "\n")
                        .replace('\r', '\n')
                        .replaceAll("[ \\t]+", " ")
                        .strip();
    }

    private int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        return null;
    }

    private record QuestionMatch(int start, int prefixEnd, int questionNumber) {
    }

    private record TextSections(String questionText, String answerText) {
    }

    private record QuestionPosition(int questionNumber, int pageNumber, double yPosition) {
    }

    private record FigureCaption(int figureNumber, int pageNumber, double yPosition) {
    }

    private record ImageAttachmentResult(
            List<ImportedQuestionDraft> questions,
            List<ExtractedPdfImage> unattachedImages
    ) {
    }
}
