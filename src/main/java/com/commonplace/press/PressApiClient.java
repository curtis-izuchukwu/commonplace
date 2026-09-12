package com.commonplace.press;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;

public class PressApiClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(90);

    private final HttpClient httpClient;
    private final PressApiConfig config;
    private final Duration requestTimeout;

    public PressApiClient() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                PressApiConfig.fromEnvironment());
    }

    public PressApiClient(HttpClient httpClient, PressApiConfig config) {
        this(httpClient, config, REQUEST_TIMEOUT);
    }

    PressApiClient(HttpClient httpClient, PressApiConfig config, Duration requestTimeout) {
        this.httpClient = httpClient;
        this.config = config;
        this.requestTimeout = requestTimeout;
    }

    public PressGenerateResponse generate(PressGenerateRequest request)
            throws PressApiException {

        HttpRequest httpRequest = HttpRequest.newBuilder(config.generateUri())
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(request.toJson()))
                .build();

        HttpResponse<String> response;

        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PressApiException("Press generation was interrupted.", e);
        } catch (HttpTimeoutException e) {
            throw new PressApiException(
                    "Press took too long to respond. Try fewer questions or try again shortly.", e);
        } catch (IOException e) {
            throw new PressApiException(
                    "Press is unavailable. Check your internet connection and try again.",
                    e
            );
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new PressApiException(errorMessage(response.statusCode(), response.body()));
        }

        try {
            PressGenerateResponse generated = PressGenerateResponse.fromJson(response.body());
            if (generated.questions().isEmpty()) {
                throw new PressApiException(
                        "Press did not return any usable questions. Try again or create questions manually.");
            }
            return generated;
        } catch (RuntimeException e) {
            throw new PressApiException("Press returned a response Commonplace could not read.", e);
        }
    }

    private static String errorMessage(int status, String body) {
        String guidance = switch (status) {
            case 400, 422 -> "Check the subject, topic, count, and format.";
            case 401, 403 -> "The service refused access. Check the Press API configuration.";
            case 404 -> "Check the Press API base URL.";
            case 429 -> "The request limit was reached. Wait a little before trying again.";
            default -> "Try again shortly. Manual worksheet creation is still available.";
        };
        String detail = "";
        try {
            if (PressJson.parse(body) instanceof Map<?, ?> error) {
                Object message = error.get("message");
                if (!(message instanceof String)) {
                    message = error.get("error");
                    if (message instanceof Map<?, ?> nested) {
                        message = nested.get("message");
                    }
                }
                if (message instanceof String text && !text.isBlank()) {
                    detail = text.replaceAll("\\s+", " ").trim();
                    // Keep proxy/server diagnostics from overwhelming the worksheet form.
                    detail = detail.substring(0, Math.min(detail.length(), 300)) + " ";
                }
            }
        } catch (RuntimeException ignored) {
            // A gateway may return HTML instead of the API's JSON error envelope.
        }
        return "Press returned HTTP " + status + ". " + detail + guidance;
    }
}
