package com.commonplace.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record StudyModule(
        long id,
        String name,
        String description,
        LocalDate examDate,
        ImportanceLevel importance,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}