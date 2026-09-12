package com.commonplace.press;

import static org.junit.jupiter.api.Assertions.*;

import com.commonplace.model.DifficultyLevel;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Opt-in smoke test: sends synthetic study topics to the deployed API. */
@EnabledIfSystemProperty(named = "commonplace.press.liveTest", matches = "true")
class PressLiveTest {
    @ParameterizedTest
    @EnumSource(PressQuestionFormat.class)
    void generatesUsingDeployedPress(PressQuestionFormat format) throws Exception {
        var client = new PressApiClient(java.net.http.HttpClient.newHttpClient(),
                new PressApiConfig(PressApiConfig.DEFAULT_BASE_URL));
        boolean shortAnswer = format == PressQuestionFormat.SHORT_ANSWER;
        var response = client.generate(new PressGenerateRequest(
                shortAnswer ? "Computer Science" : "Mathematics",
                shortAnswer ? "Binary Search" : "Quadratic equations",
                DifficultyLevel.MEDIUM, 1, format));
        assertEquals(1, response.questions().size());
        assertEquals("Press API", response.metadata().get("service"));
        var question = response.questions().getFirst();
        assertEquals(format.apiValue(), question.format());
        assertFalse(question.prompt().isBlank());
        assertFalse(question.answer().isBlank());
        assertTrue(question.markScheme().startsWith("- "));
        assertTrue(question.maxMarks() > 0);
        assertTrue(question.toQuestionDraft().markScheme().contains(question.answer()));
    }
}
