package com.commonplace.model;

import java.time.LocalDateTime;

public record NoteRecord(NoteKey key, String content, int formatVersion,
                         LocalDateTime createdAt, LocalDateTime updatedAt) {}
