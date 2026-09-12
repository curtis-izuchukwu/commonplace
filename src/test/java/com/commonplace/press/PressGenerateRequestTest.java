package com.commonplace.press;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.commonplace.model.DifficultyLevel;

class PressGenerateRequestTest {

    @Test
    void validatesRequiredFieldsAndCount() {
        for (int count : new int[] {-1, 0, 11}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new PressGenerateRequest("Maths", "Algebra", null, count, null));
        }
        assertThrows(IllegalArgumentException.class,
                () -> new PressGenerateRequest(" ", "Algebra", null, 1, null));
        assertThrows(IllegalArgumentException.class,
                () -> new PressGenerateRequest("Maths", null, null, 1, null));
    }

    @Test
    void validatesSubjectAndTopicLengthsAgainstPressContract() {
        assertThrows(IllegalArgumentException.class,
                () -> new PressGenerateRequest("s".repeat(81), "Topic", null, 3, null));
        assertThrows(IllegalArgumentException.class,
                () -> new PressGenerateRequest("Subject", "t".repeat(121), null, 3, null));
        var boundary = new PressGenerateRequest(" " + "s".repeat(80) + " ",
                " " + "t".repeat(120) + " ", null, 3, null);
        assertEquals(80, boundary.subject().length());
        assertEquals(120, boundary.topic().length());
    }

    @Test
    void defaultsAndTrimsInputAndListsOnlySupportedFormats() {
        var request = new PressGenerateRequest(" Maths ", " Algebra ", null, 10, null);
        assertEquals("Maths", request.subject());
        assertEquals("Algebra", request.topic());
        assertEquals(DifficultyLevel.MEDIUM, request.difficulty());
        assertEquals(PressQuestionFormat.SHORT_ANSWER, request.format());
        assertEquals(java.util.List.of("short-answer", "long-answer"),
                java.util.Arrays.stream(PressQuestionFormat.values()).map(PressQuestionFormat::apiValue).toList());
    }

    @Test
    void serializesExpectedPressPayload() {
        PressGenerateRequest request = new PressGenerateRequest(
                "Computer \"Science\"",
                "Binary\nSearch",
                DifficultyLevel.HARD,
                5,
                PressQuestionFormat.LONG_ANSWER
        );

        String json = request.toJson();

        assertTrue(json.contains("\"subject\":\"Computer \\\"Science\\\"\""));
        assertTrue(json.contains("\"topic\":\"Binary\\nSearch\""));
        assertTrue(json.contains("\"difficulty\":\"hard\""));
        assertTrue(json.contains("\"questionCount\":5"));
        assertTrue(json.contains("\"format\":\"long-answer\""));
    }
}
