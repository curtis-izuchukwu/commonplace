package com.commonplace.press;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.commonplace.model.DifficultyLevel;
import com.commonplace.service.PressWorksheetGenerationService;
import com.sun.net.httpserver.HttpServer;

class PressApiClientTest {
    private HttpServer server;
    private PressApiConfig config;
    private final PressGenerateRequest request = new PressGenerateRequest(
            "Computer Science", "Binary Search", DifficultyLevel.MEDIUM, 1, PressQuestionFormat.SHORT_ANSWER);

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        config = new PressApiConfig("http://127.0.0.1:" + server.getAddress().getPort());
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void postsContractAndKeepsMarkingPointsThroughServiceAndDraftConversion() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        server.createContext("/generate", exchange -> {
            method.set(exchange.getRequestMethod());
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    {"metadata":{"service":"Press API","format":"short-answer","mode":"ai","cache":"miss"},
                     "questions":[{"id":1,"type":"short-answer","question":"What does binary search require?",
                     "answer":"A sorted list.","markScheme":["Identify the list.","State that it must be sorted."],"marks":2}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        var result = new PressWorksheetGenerationService(client()).generate(request);
        var draft = result.questions().getFirst().toQuestionDraft();
        assertEquals("POST", method.get());
        assertEquals("application/json", contentType.get());
        assertEquals(PressJson.parse(request.toJson()), PressJson.parse(body.get()));
        assertEquals(2, draft.maxMarks());
        assertEquals("press,short-answer", draft.tags());
        assertEquals("- Identify the list.\n- State that it must be sorted.\n\nAnswer: A sorted list.", draft.markScheme());
        assertEquals("ai", result.metadata().get("mode"));
    }

    @Test
    void surfacesValidationErrorMessage() {
        respond(400, "{\"message\":\"Unsupported format. Use short-answer or long-answer.\"}");
        var error = assertThrows(PressApiException.class, () -> client().generate(request));
        assertTrue(error.getMessage().contains("Unsupported format"));
        assertTrue(error.getMessage().contains("HTTP 400"));
    }

    @Test
    void explainsRateLimits() {
        respond(429, "{\"error\":{\"message\":\"Too many requests\"}}");
        var error = assertThrows(PressApiException.class, () -> client().generate(request));
        assertTrue(error.getMessage().contains("Too many requests"));
        assertTrue(error.getMessage().contains("Wait"));
    }

    @Test
    void handlesNonJsonServerErrors() {
        respond(503, "<html>Gateway unavailable</html>");
        var error = assertThrows(PressApiException.class, () -> client().generate(request));
        assertTrue(error.getMessage().contains("HTTP 503"));
        assertFalse(error.getMessage().contains("<html>"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"not JSON", "{", "{\"questions\":[]}", "{\"questions\":[{\"marks\":2}]}", "null"})
    void rejectsMalformedOrEmptySuccess(String body) {
        respond(200, body);
        assertThrows(PressApiException.class, () -> client().generate(request));
    }

    @Test
    void explainsTimeoutWithoutRetryingGeneration() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        server.createContext("/generate", exchange -> {
            try { release.await(3, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        try {
            var client = new PressApiClient(HttpClient.newHttpClient(), config, Duration.ofMillis(100));
            var error = assertThrows(PressApiException.class, () -> client.generate(request));
            assertTrue(error.getMessage().contains("too long"));
        } finally {
            release.countDown();
        }
    }

    @Test
    void cancellationInterruptsHttpRequest() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        server.createContext("/generate", exchange -> {
            entered.countDown();
            try { release.await(3, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        AtomicReference<PressApiException> error = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread worker = new Thread(() -> {
            try { client().generate(request); }
            catch (PressApiException e) {
                error.set(e);
                interrupted.set(Thread.currentThread().isInterrupted());
            }
        });
        worker.start();
        try {
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            worker.interrupt();
            worker.join(3000);
            assertFalse(worker.isAlive());
            assertNotNull(error.get());
            assertTrue(interrupted.get());
        } finally {
            worker.interrupt();
            release.countDown();
        }
    }

    private PressApiClient client() {
        return new PressApiClient(HttpClient.newHttpClient(), config);
    }

    private void respond(int status, String body) {
        server.createContext("/generate", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
    }
}
