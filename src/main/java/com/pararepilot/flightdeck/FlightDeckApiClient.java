package com.pararepilot.flightdeck;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class FlightDeckApiClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(25);

    private final HttpClient httpClient;
    private final FlightDeckApiConfig config;

    public FlightDeckApiClient() {
        this(HttpClient.newHttpClient(), FlightDeckApiConfig.fromEnvironment());
    }

    public FlightDeckApiClient(HttpClient httpClient, FlightDeckApiConfig config) {
        this.httpClient = httpClient;
        this.config = config;
    }

    public FlightDeckGenerateResponse generate(FlightDeckGenerateRequest request)
            throws FlightDeckApiException {

        HttpRequest httpRequest = HttpRequest.newBuilder(config.generateUri())
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(request.toJson()))
                .build();

        HttpResponse<String> response;

        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FlightDeckApiException("FlightDeck generation was interrupted.", e);
        } catch (IOException e) {
            throw new FlightDeckApiException(
                    "FlightDeck is unavailable. Check your internet connection and try again.",
                    e
            );
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new FlightDeckApiException(
                    "FlightDeck returned HTTP " + response.statusCode() + ". Try again later."
            );
        }

        try {
            return FlightDeckGenerateResponse.fromJson(response.body());
        } catch (RuntimeException e) {
            e.printStackTrace();
            throw new FlightDeckApiException("FlightDeck returned a response PararePilot could not read.", e);
        }
    }
}
