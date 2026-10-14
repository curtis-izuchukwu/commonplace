package com.commonplace.model;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** UI-independent immutable document; span text never includes paragraph separators. */
public record NoteDocument(List<Paragraph> paragraphs) {
    public static final int FORMAT_VERSION = 1;
    public static final int BOLD = 1, ITALIC = 2, UNDERLINE = 4, CODE = 8;
    public static final int ALL_MARKS = BOLD | ITALIC | UNDERLINE | CODE;

    public enum Block { NORMAL, HEADING_1, HEADING_2, BULLET, NUMBERED }

    public record Span(String text, int marks) {
        public Span {
            Objects.requireNonNull(text);
            if (text.contains("\n") || text.contains("\r") || (marks & ~ALL_MARKS) != 0) {
                throw new IllegalArgumentException("Invalid note span.");
            }
        }
    }

    public record Paragraph(Block block, List<Span> spans) {
        public Paragraph {
            Objects.requireNonNull(block);
            spans = List.copyOf(spans);
            if (spans.isEmpty()) spans = List.of(new Span("", 0));
        }
        public String text() { return spans.stream().map(Span::text).collect(Collectors.joining()); }
    }

    public NoteDocument {
        paragraphs = List.copyOf(paragraphs);
        if (paragraphs.isEmpty()) paragraphs = List.of(new Paragraph(Block.NORMAL, List.of()));
    }

    public static NoteDocument empty() { return new NoteDocument(List.of()); }
    public static NoteDocument plain(String text) {
        return new NoteDocument(java.util.Arrays.stream(text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1))
                .map(line -> new Paragraph(Block.NORMAL, List.of(new Span(line, 0)))).toList());
    }
    public String plainText() { return paragraphs.stream().map(Paragraph::text).collect(Collectors.joining("\n")); }
    // Whitespace (including deliberate line breaks) is content and must round-trip.
    public boolean isEmpty() { return plainText().isEmpty(); }
}
