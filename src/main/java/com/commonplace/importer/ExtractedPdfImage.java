package com.commonplace.importer;

public record ExtractedPdfImage(
        String imagePath,
        int pageNumber,
        int imageIndexOnPage,
        double yPosition,
        String suggestedAltText
) {
    public ExtractedPdfImage(
            String imagePath,
            int pageNumber,
            int imageIndexOnPage,
            String suggestedAltText
    ) {
        this(imagePath, pageNumber, imageIndexOnPage, Double.NaN, suggestedAltText);
    }
}
