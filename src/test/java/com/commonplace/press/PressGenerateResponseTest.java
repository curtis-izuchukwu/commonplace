package com.commonplace.press;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PressGenerateResponseTest {

    @Test
    void parsesPressMarkingPointArraysAndMetadata() {
        var response = PressGenerateResponse.fromJson("""
                {"metadata":{"service":"Press API","mode":"ai","cache":"hit","questionCount":1},
                 "questions":[{"id":1,"type":"long-answer","question":"Explain recursion.",
                 "answer":"A function calls itself until its base case.",
                 "marks":8,"markScheme":["Self-call (3 marks)","Base case (5 marks)"]}]}
                """);
        var draft = response.questions().getFirst().toQuestionDraft();
        assertEquals("press,long-answer", draft.tags());
        assertEquals(8, draft.maxMarks());
        assertEquals("- Self-call (3 marks)\n- Base case (5 marks)\n\nAnswer: A function calls itself until its base case.",
                draft.markScheme());
        assertEquals("hit", response.metadata().get("cache"));
        assertEquals("1", response.metadata().get("questionCount"));
    }

    @Test
    void doesNotDropShortAnswersOrInventMissingMarkSchemes() {
        var response = PressGenerateResponse.fromJson("""
                {"questions":[{"question":"Choose one.","answer":"A","markScheme":["Award one mark."]},
                 {"question":"Unfinished question."}]}
                """);
        assertTrue(response.questions().getFirst().toQuestionDraft().markScheme().contains("Answer: A"));
        assertEquals("", response.questions().get(1).toQuestionDraft().markScheme());
    }

    @Test
    void skipsEmptyAliasesAndPreservesUnicodeAndMultilinePoints() {
        var response = PressGenerateResponse.fromJson("""
                {"questions":[{"question":null,"prompt":"Solve x² = 4.","markScheme":[],
                 "mark_scheme":["x = ±2", "Check both roots.\\nSubstitute into x²."],"answer":"±2"}]}
                """);
        assertEquals("Solve x² = 4.", response.questions().getFirst().prompt());
        assertTrue(response.questions().getFirst().markScheme().contains("Check both roots.\nSubstitute"));
    }

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

        PressGenerateResponse response = PressGenerateResponse.fromJson(json);

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

        PressGenerateResponse response = PressGenerateResponse.fromJson(json);

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

        PressGenerateResponse response = PressGenerateResponse.fromJson(json);

        assertEquals(1, response.questions().size());
        assertEquals("Explain Big O notation.", response.questions().get(0).prompt());
    }
}
