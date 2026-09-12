package com.commonplace.flightdeck;

import java.net.URI;

public final class FlightDeckApiConfig {

    public static final String BASE_URL_PROPERTY = "commonplace.flightdeck.baseUrl";
    public static final String BASE_URL_ENV = "COMMONPLACE_FLIGHTDECK_API_BASE_URL";
    public static final String DEFAULT_BASE_URL = "https://flightdeck-api.izuchukwucur.workers.dev";

    private final URI baseUri;

    public FlightDeckApiConfig(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("FlightDeck API base URL cannot be empty.");
        }

        this.baseUri = URI.create(stripTrailingSlash(baseUrl.trim()));
    }

    public static FlightDeckApiConfig fromEnvironment() {
        String propertyValue = System.getProperty(BASE_URL_PROPERTY);

        if (propertyValue != null && !propertyValue.isBlank()) {
            return new FlightDeckApiConfig(propertyValue);
        }

        String envValue = System.getenv(BASE_URL_ENV);

        if (envValue != null && !envValue.isBlank()) {
            return new FlightDeckApiConfig(envValue);
        }

        return new FlightDeckApiConfig(DEFAULT_BASE_URL);
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
