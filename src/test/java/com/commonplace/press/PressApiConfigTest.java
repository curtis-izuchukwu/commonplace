package com.commonplace.press;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PressApiConfigTest {
    @Test
    void targetsPressAndNormalizesTrailingSlashes() {
        assertEquals("https://press-api.izuchukwucur.workers.dev/generate",
                new PressApiConfig(PressApiConfig.DEFAULT_BASE_URL).generateUri().toString());
        assertEquals("http://localhost:8080/api/generate",
                new PressApiConfig(" http://localhost:8080/api/// ").generateUri().toString());
    }

    @Test
    void systemPropertyOverridesDefault() {
        String previous = System.getProperty(PressApiConfig.BASE_URL_PROPERTY);
        try {
            System.setProperty(PressApiConfig.BASE_URL_PROPERTY, "https://example.test/press");
            assertEquals("https://example.test/press/generate",
                    PressApiConfig.fromEnvironment().generateUri().toString());
        } finally {
            if (previous == null) System.clearProperty(PressApiConfig.BASE_URL_PROPERTY);
            else System.setProperty(PressApiConfig.BASE_URL_PROPERTY, previous);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "example.com", "ftp://example.com", "https://",
            "https://user:password@example.com", "https://example.com?q=1", "https://example.com/#fragment"})
    void rejectsInvalidBaseUrls(String value) {
        assertThrows(IllegalArgumentException.class, () -> new PressApiConfig(value));
    }
}
