package com.commonplace.model;

public record NotebookSection(NoteKey key, String moduleName, String title) {
    @Override public String toString() { return title; }
}
