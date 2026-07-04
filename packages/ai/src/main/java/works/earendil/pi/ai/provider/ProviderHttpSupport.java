package works.earendil.pi.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import works.earendil.pi.ai.model.Model;
import works.earendil.pi.common.json.JsonCodec;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;

final class ProviderHttpSupport {
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration DEFAULT_INITIAL_RETRY_DELAY = Duration.ofMillis(250);
    private static final Duration DEFAULT_MAX_RETRY_DELAY = Duration.ofSeconds(4);
    private static final int DEFAULT_MAX_CONCURRENT_REQUESTS = 4;
    private static final ConcurrentMap<String, Limiter> LIMITERS = new ConcurrentHashMap<>();

    private ProviderHttpSupport() {
    }

    interface Sender {
        HttpResponse<InputStream> send(HttpRequest request) throws IOException, InterruptedException;
    }

    private record Limiter(int maxConcurrentRequests, Semaphore semaphore) {
    }

    record RetryPolicy(int maxRetries, Duration initialDelay, Duration maxDelay, int maxConcurrentRequests) {
        RetryPolicy(int maxRetries, Duration initialDelay, Duration maxDelay) {
            this(maxRetries, initialDelay, maxDelay, DEFAULT_MAX_CONCURRENT_REQUESTS);
        }

        RetryPolicy {
            if (maxRetries < 0) {
                maxRetries = 0;
            }
            initialDelay = initialDelay == null || initialDelay.isNegative() || initialDelay.isZero()
                    ? DEFAULT_INITIAL_RETRY_DELAY
                    : initialDelay;
            maxDelay = maxDelay == null || maxDelay.isNegative() || maxDelay.isZero()
                    ? DEFAULT_MAX_RETRY_DELAY
                    : maxDelay;
            if (maxConcurrentRequests <= 0) {
                maxConcurrentRequests = DEFAULT_MAX_CONCURRENT_REQUESTS;
            }
        }
    }

    static HttpClient client() {
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(DEFAULT_CONNECT_TIMEOUT)
                .build();
    }

    static RetryPolicy retryPolicy(StreamOptions options) {
        return retryPolicy(null, options);
    }

    static RetryPolicy retryPolicy(String providerId, StreamOptions options) {
        int retries = options != null && options.maxRetries() != null ? options.maxRetries() : 2;
        Duration initialDelay = durationMetadata(options, "retryInitialDelayMs").orElse(DEFAULT_INITIAL_RETRY_DELAY);
        Duration maxDelay = durationMetadata(options, "maxRetryDelayMs").orElse(DEFAULT_MAX_RETRY_DELAY);
        int maxConcurrentRequests = intMetadata(options, "maxConcurrentRequests").orElse(DEFAULT_MAX_CONCURRENT_REQUESTS);
        MapLike providerOverride = providerRetryOverride(providerId, options);
        if (providerOverride != null) {
            retries = providerOverride.nonNegativeIntValue("maxRetries").orElse(retries);
            initialDelay = providerOverride.durationValue("retryInitialDelayMs")
                    .or(() -> providerOverride.durationValue("baseDelayMs"))
                    .orElse(initialDelay);
            maxDelay = providerOverride.durationValue("maxRetryDelayMs").orElse(maxDelay);
            maxConcurrentRequests = providerOverride.intValue("maxConcurrentRequests").orElse(maxConcurrentRequests);
        }
        return new RetryPolicy(retries, initialDelay, maxDelay, maxConcurrentRequests);
    }

    static Duration requestTimeout(String providerId, StreamOptions options) {
        Duration timeout = options != null && options.timeout() != null ? options.timeout() : DEFAULT_CONNECT_TIMEOUT;
        MapLike providerOverride = providerRetryOverride(providerId, options);
        return providerOverride == null ? timeout : providerOverride.durationValue("timeoutMs").orElse(timeout);
    }

    static HttpResponse<InputStream> sendWithRetries(String providerId, HttpRequest request, Sender sender,
                                                     RetryPolicy policy) throws IOException, InterruptedException {
        RetryPolicy effectivePolicy = policy == null ? retryPolicy(null) : policy;
        IOException lastIoException = null;
        int attempts = effectivePolicy.maxRetries() + 1;
        for (int attempt = 0; attempt < attempts; attempt++) {
            try {
                HttpResponse<InputStream> response = sendWithRateLimit(providerId, request, sender,
                        effectivePolicy.maxConcurrentRequests());
                if (!isRetryableStatus(response.statusCode()) || attempt == attempts - 1) {
                    return response;
                }
                discardBody(response);
                sleep(retryDelay(attempt, effectivePolicy, response.headers()));
            } catch (IOException e) {
                lastIoException = e;
                if (attempt == attempts - 1) {
                    throw e;
                }
                sleep(retryDelay(attempt, effectivePolicy, null));
            }
        }
        throw lastIoException == null ? new IOException("HTTP request failed without response") : lastIoException;
    }

    static JsonNode applyBeforeProviderRequest(StreamOptions options, Model model, JsonNode payload) throws Exception {
        if (options == null || options.providerHooks() == null || options.providerHooks().beforeRequest() == null) {
            return payload;
        }
        Object result = options.providerHooks().beforeRequest().beforeRequest(payload, model);
        if (result == null) {
            return payload;
        }
        if (result instanceof JsonNode node) {
            return node;
        }
        return JsonCodec.mapper().valueToTree(result);
    }

    static void emitAfterProviderResponse(StreamOptions options, Model model, HttpResponse<?> response) throws Exception {
        if (options == null || options.providerHooks() == null || options.providerHooks().afterResponse() == null
                || response == null) {
            return;
        }
        options.providerHooks().afterResponse().afterResponse(response.statusCode(), flattenHeaders(response.headers()),
                model);
    }

    private static Map<String, String> flattenHeaders(HttpHeaders headers) {
        if (headers == null) {
            return Map.of();
        }
        Map<String, String> flattened = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : headers.map().entrySet()) {
            flattened.put(entry.getKey(), String.join(", ", entry.getValue()));
        }
        return Map.copyOf(flattened);
    }

    static boolean isRetryableStatus(int statusCode) {
        return statusCode == 429 || statusCode == 500 || statusCode == 502 || statusCode == 503 || statusCode == 504;
    }

    static Duration retryDelay(int failedAttempt, RetryPolicy policy, HttpHeaders headers) {
        Duration retryAfter = retryAfter(headers);
        if (retryAfter != null) {
            return retryAfter.compareTo(policy.maxDelay()) > 0 ? policy.maxDelay() : retryAfter;
        }
        long multiplier = 1L << Math.min(failedAttempt, 10);
        Duration delay = policy.initialDelay().multipliedBy(multiplier);
        return delay.compareTo(policy.maxDelay()) > 0 ? policy.maxDelay() : delay;
    }

    private static HttpResponse<InputStream> sendWithRateLimit(String providerId, HttpRequest request, Sender sender,
                                                               int maxConcurrentRequests)
            throws IOException, InterruptedException {
        Limiter limiter = LIMITERS.compute(providerId == null ? "default" : providerId, (ignored, existing) ->
                existing != null && existing.maxConcurrentRequests() == maxConcurrentRequests
                        ? existing
                        : new Limiter(maxConcurrentRequests, new Semaphore(maxConcurrentRequests)));
        limiter.semaphore().acquire();
        try {
            return sender.send(request);
        } finally {
            limiter.semaphore().release();
        }
    }

    private static Optional<Duration> durationMetadata(StreamOptions options, String key) {
        if (options == null || options.metadata() == null || !(options.metadata().get(key) instanceof Number value)) {
            return Optional.empty();
        }
        long millis = value.longValue();
        return millis <= 0 ? Optional.empty() : Optional.of(Duration.ofMillis(millis));
    }

    private static Optional<Integer> intMetadata(StreamOptions options, String key) {
        if (options == null || options.metadata() == null || !(options.metadata().get(key) instanceof Number value)) {
            return Optional.empty();
        }
        int result = value.intValue();
        return result <= 0 ? Optional.empty() : Optional.of(result);
    }

    private static MapLike providerRetryOverride(String providerId, StreamOptions options) {
        if (providerId == null || providerId.isBlank() || options == null || options.metadata() == null) {
            return null;
        }
        Object overrides = options.metadata().get("providerRetryOverrides");
        if (!(overrides instanceof java.util.Map<?, ?> overrideMap)) {
            return null;
        }
        Object providerOverride = overrideMap.get(providerId);
        return providerOverride instanceof java.util.Map<?, ?> providerMap ? new MapLike(providerMap) : null;
    }

    private record MapLike(java.util.Map<?, ?> values) {
        Optional<Integer> intValue(String key) {
            Object value = values.get(key);
            if (!(value instanceof Number number)) {
                return Optional.empty();
            }
            int result = number.intValue();
            return result <= 0 ? Optional.empty() : Optional.of(result);
        }

        Optional<Integer> nonNegativeIntValue(String key) {
            Object value = values.get(key);
            if (!(value instanceof Number number)) {
                return Optional.empty();
            }
            int result = number.intValue();
            return result < 0 ? Optional.empty() : Optional.of(result);
        }

        Optional<Duration> durationValue(String key) {
            return intValue(key).map(Duration::ofMillis);
        }
    }

    private static Duration retryAfter(HttpHeaders headers) {
        if (headers == null) {
            return null;
        }
        Optional<String> header = headers.firstValue("Retry-After");
        if (header.isEmpty()) {
            return null;
        }
        try {
            long seconds = Long.parseLong(header.get().trim());
            return seconds <= 0 ? null : Duration.ofSeconds(seconds);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static void discardBody(HttpResponse<InputStream> response) throws IOException {
        InputStream body = response.body();
        if (body != null) {
            body.readAllBytes();
            body.close();
        }
    }

    private static void sleep(Duration delay) throws InterruptedException {
        if (delay != null && !delay.isZero() && !delay.isNegative()) {
            Thread.sleep(delay.toMillis());
        }
    }
}
