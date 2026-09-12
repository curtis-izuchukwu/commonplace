package com.commonplace.press;

import java.net.URI;

public final class PressApiConfig {

    public static final String BASE_URL_PROPERTY = "commonplace.press.baseUrl";
    public static final String BASE_URL_ENV = "COMMONPLACE_PRESS_API_BASE_URL";
    public static final String DEFAULT_BASE_URL = "https://press-api.izuchukwucur.workers.dev";

    private final URI baseUri;

    public PressApiConfig(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Press API base URL cannot be empty.");
        }

        try {
            this.baseUri = URI.create(stripTrailingSlash(baseUrl.trim()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Press API base URL must be a valid HTTP or HTTPS URL.", e);
        }
        if (!("https".equalsIgnoreCase(baseUri.getScheme())
                || "http".equalsIgnoreCase(baseUri.getScheme()))
                || baseUri.getHost() == null || baseUri.getRawQuery() != null
                || baseUri.getRawFragment() != null || baseUri.getRawUserInfo() != null) {
            throw new IllegalArgumentException(
                    "Press API base URL must be an HTTP or HTTPS URL without credentials, a query, or a fragment.");
        }
    }

    public static PressApiConfig fromEnvironment() {
        String propertyValue = System.getProperty(BASE_URL_PROPERTY);

        if (propertyValue != null && !propertyValue.isBlank()) {
            return new PressApiConfig(propertyValue);
        }

        String envValue = System.getenv(BASE_URL_ENV);

        if (envValue != null && !envValue.isBlank()) {
            return new PressApiConfig(envValue);
        }

        return new PressApiConfig(DEFAULT_BASE_URL);
    }

    public URI generateUri() {
        return URI.create(baseUri + "/generate");
    }

    private static String stripTrailingSlash(String value) {
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }

        return value;
    }
}
