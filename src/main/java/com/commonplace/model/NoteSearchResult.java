package com.commonplace.model;

public record NoteSearchResult(NotebookSection section, String plainText, int matchOffset) {
    public String snippet() {
        int start = Math.max(0, matchOffset - 30);
        int end = Math.min(plainText.length(), Math.max(start, matchOffset) + 100);
        return (start > 0 ? "…" : "") + plainText.substring(start, end).replace('\n', ' ')
                + (end < plainText.length() ? "…" : "");
    }
}
