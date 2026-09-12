package com.commonplace.importer;

public record PdfTextLine(
        int pageNumber,
        String text,
        double yPosition
) {
}
