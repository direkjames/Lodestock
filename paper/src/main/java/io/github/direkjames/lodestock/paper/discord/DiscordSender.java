package io.github.direkjames.lodestock.paper.discord;

import io.github.direkjames.lodestock.core.discord.DiscordPayload;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sends messages to a webhook, one at a time on its own thread, so the server never waits for Discord.
 * It obeys Discord's rate limits (waits and tries again) and drops messages if too many pile up.
 */
public final class DiscordSender {
    public record Result(boolean ok, String error) {
        public static final Result OK = new Result(true, null);

        public static Result failed(String error) {
            return new Result(false, error);
        }
    }

    private static final int MAX_QUEUED = 50;
    private static final int MAX_TRIES = 3;
    private static final double MAX_WAIT_SECONDS = 30;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Lodestock-Discord");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicInteger queued = new AtomicInteger();

    CompletableFuture<Result> send(String url, String json) {
        if (queued.incrementAndGet() > MAX_QUEUED) {
            queued.decrementAndGet();
            return CompletableFuture.completedFuture(Result.failed("too many messages waiting, this one was dropped"));
        }
        CompletableFuture<Result> future = new CompletableFuture<>();
        try {
            worker.execute(() -> {
                try {
                    future.complete(post(url, json));
                } catch (Throwable t) { // the caller must always get an answer
                    future.complete(Result.failed("unexpected error: " + t.getClass().getSimpleName()));
                } finally {
                    queued.decrementAndGet();
                }
            });
        } catch (RuntimeException e) { // already shut down
            queued.decrementAndGet();
            future.complete(Result.failed("the plugin is shutting down"));
        }
        return future;
    }

    private Result post(String url, String json) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("User-Agent", "Lodestock (https://github.com/direkjames/Lodestock)")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        try {
            for (int attempt = 1; attempt <= MAX_TRIES; attempt++) {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                int code = response.statusCode();
                if (code >= 200 && code < 300) {
                    // Discord says when the bucket is empty: wait it out so the next message isn't refused.
                    if ("0".equals(response.headers().firstValue("x-ratelimit-remaining").orElse(""))) {
                        double reset = parse(response.headers().firstValue("x-ratelimit-reset-after").orElse("1"), 1);
                        sleep(Math.min(reset, 10));
                    }
                    return Result.OK;
                }
                if (code == 429 && attempt < MAX_TRIES) {
                    sleep(Math.min(DiscordPayload.retryAfterSeconds(response.body(), 2), MAX_WAIT_SECONDS));
                    continue;
                }
                return Result.failed(describe(code));
            }
            return Result.failed("Discord kept refusing (rate limit)");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.failed("interrupted");
        } catch (Exception e) {
            // The address is a secret, so it must never end up in a log or chat message.
            String message = String.valueOf(e.getMessage()).replace(url, "<webhook>");
            return Result.failed(e.getClass().getSimpleName() + ": " + message);
        }
    }

    private static String describe(int code) {
        return switch (code) {
            case 400 -> "Discord refused the message (HTTP 400)";
            case 401, 403 -> "Discord refused the webhook (HTTP " + code + "), check the address";
            case 404 -> "the webhook does not exist any more (HTTP 404), make a new one";
            case 429 -> "Discord rate limit (HTTP 429)";
            default -> "Discord answered HTTP " + code;
        };
    }

    private static double parse(String text, double fallback) {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static void sleep(double seconds) throws InterruptedException {
        if (seconds > 0) Thread.sleep((long) (seconds * 1000));
    }

    /** Gives messages already waiting a moment to go out, then stops. */
    void shutdown() {
        worker.shutdown();
        try {
            if (!worker.awaitTermination(3, TimeUnit.SECONDS)) worker.shutdownNow();
        } catch (InterruptedException e) {
            worker.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
