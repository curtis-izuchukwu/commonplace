package com.commonplace.flightdeck;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.commonplace.model.DifficultyLevel;

class FlightDeckGenerateRequestTest {

    @Test
    void serializesExpectedFlightDeckPayload() {
        FlightDeckGenerateRequest request = new FlightDeckGenerateRequest(
                "Computer \"Science\"",
                "Binary\nSearch",
                DifficultyLevel.HARD,
                5,
                FlightDeckQuestionFormat.MIXED
        );

        String json = request.toJson();

        assertTrue(json.contains("\"subject\":\"Computer \\\"Science\\\"\""));
        assertTrue(json.contains("\"topic\":\"Binary\\nSearch\""));
        assertTrue(json.contains("\"difficulty\":\"hard\""));
        assertTrue(json.contains("\"questionCount\":5"));
        assertTrue(json.contains("\"format\":\"mixed\""));
    }
}
