package works.earendil.pi.ai.provider;

import works.earendil.pi.ai.model.CacheRetention;
import works.earendil.pi.ai.model.Model;
import works.earendil.pi.ai.model.Transport;

import java.time.Duration;
import java.util.Map;

public record StreamOptions(
        Double temperature,
        Integer maxTokens,
        String apiKey,
        Transport transport,
        CacheRetention cacheRetention,
        String sessionId,
        Map<String, String> headers,
        Duration timeout,
        Integer maxRetries,
        Map<String, String> env,
        Map<String, Object> metadata,
        ProviderHooks providerHooks
) {
    public StreamOptions(Double temperature, Integer maxTokens, String apiKey, Transport transport,
                         CacheRetention cacheRetention, String sessionId, Map<String, String> headers,
                         Duration timeout, Integer maxRetries, Map<String, String> env,
                         Map<String, Object> metadata) {
        this(temperature, maxTokens, apiKey, transport, cacheRetention, sessionId, headers, timeout, maxRetries,
                env, metadata, null);
    }

    public static StreamOptions defaults() {
        return new StreamOptions(null, null, null, Transport.AUTO, CacheRetention.SHORT,
                null, Map.of(), Duration.ofMinutes(10), 2, Map.of(), Map.of(), null);
    }

    public record ProviderHooks(ProviderPayloadHook beforeRequest, ProviderResponseHook afterResponse) {
    }

    @FunctionalInterface
    public interface ProviderPayloadHook {
        Object beforeRequest(Object payload, Model model) throws Exception;
    }

    @FunctionalInterface
    public interface ProviderResponseHook {
        void afterResponse(int status, Map<String, String> headers, Model model) throws Exception;
    }
}
