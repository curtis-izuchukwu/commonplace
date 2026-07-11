package com.pararepilot.importer;

public record PdfTextLine(
        int pageNumber,
        String text,
        double yPosition
) {
}
