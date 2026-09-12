package com.commonplace.model;

public record Question(
        long id,
        long worksheetId,
        String prompt,
        String markScheme,
        int maxMarks,
        int questionOrder,
        String tags,
        String imagePath
) {
}
