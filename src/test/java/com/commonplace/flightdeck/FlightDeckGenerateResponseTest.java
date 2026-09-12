package com.commonplace.flightdeck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FlightDeckGenerateResponseTest {

    @Test
    void parsesWorksheetWrappedResponse() {
        String json = """
                {
                  "worksheet": {
                    "title": "Binary Search Practice",
                    "description": "Generated revision worksheet",
                    "questions": [
                      {
                        "question": "What condition ends binary search?",
                        "answer": "The target is found or the search bounds cross.",
                        "markScheme": "Award one mark for found and one mark for exhausted bounds.",
                        "marks": 2,
                        "type": "short-answer"
                      },
                      {
                        "prompt": "Which list can binary search use?",
                        "correctAnswer": "Sorted",
                        "options": ["Random", "Sorted", "Circular"],
                        "maxMarks": 1,
                        "format": "multiple-choice"
                      }
                    ]
                  }
                }
                """;

        FlightDeckGenerateResponse response = FlightDeckGenerateResponse.fromJson(json);

        assertEquals("Binary Search Practice", response.title());
        assertEquals("Generated revision worksheet", response.description());
        assertEquals(2, response.questions().size());
        assertEquals(2, response.questions().get(0).maxMarks());
        assertTrue(response.questions().get(1).toQuestionDraft().prompt().contains("Options:"));
        assertTrue(response.questions().get(1).toQuestionDraft().markScheme().contains("Answer: Sorted"));
    }

    @Test
    void parsesDirectQuestionArrayResponse() {
        String json = """
                [
                  {
                    "text": "Define recursion.",
                    "solution": "A function calling itself.",
                    "points": 3
                  }
                ]
                """;

        FlightDeckGenerateResponse response = FlightDeckGenerateResponse.fromJson(json);

        assertEquals(1, response.questions().size());
        assertEquals("Define recursion.", response.questions().get(0).prompt());
        assertEquals(3, response.questions().get(0).maxMarks());
        assertTrue(response.questions().get(0).toQuestionDraft().markScheme().contains("Answer: A function"));
    }

    @Test
    void skipsQuestionObjectsWithoutPrompt() {
        String json = """
                {
                  "data": {
                    "questions": [
                      {"marks": 2},
                      {"questionText": "Explain Big O notation.", "mark_scheme": "Mentions growth rate."}
                    ]
                  }
                }
                """;

        FlightDeckGenerateResponse response = FlightDeckGenerateResponse.fromJson(json);

        assertEquals(1, response.questions().size());
        assertEquals("Explain Big O notation.", response.questions().get(0).prompt());
    }
}
