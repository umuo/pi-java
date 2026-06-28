package works.earendil.pi.codingagent.core;

import works.earendil.pi.ai.model.Model;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

public final class ProviderAttribution {
    private static final String OPENROUTER_HOST = "openrouter.ai";
    private static final String NVIDIA_NIM_HOST = "integrate.api.nvidia.com";
    private static final String CLOUDFLARE_API_HOST = "api.cloudflare.com";
    private static final String CLOUDFLARE_AI_GATEWAY_HOST = "gateway.ai.cloudflare.com";
    private static final String OPENCODE_HOST = "opencode.ai";
    private static final String VERCEL_GATEWAY_HOST = "ai-gateway.vercel.sh";

    private ProviderAttribution() {
    }

    @SafeVarargs
    public static Map<String, String> mergeProviderAttributionHeaders(
            Model model,
            BooleanSupplier installTelemetryEnabled,
            String sessionId,
            Map<String, String>... headerSources) {
        Map<String, String> merged = new LinkedHashMap<>();
        Map<String, String> sessionHeaders = getSessionHeaders(model, sessionId);
        if (sessionHeaders != null) {
            merged.putAll(sessionHeaders);
        }
        Map<String, String> attributionHeaders = getDefaultAttributionHeaders(model, installTelemetryEnabled);
        if (attributionHeaders != null) {
            merged.putAll(attributionHeaders);
        }
        for (Map<String, String> headers : headerSources) {
            if (headers != null) {
                merged.putAll(headers);
            }
        }
        return merged.isEmpty() ? null : Map.copyOf(merged);
    }

    private static Map<String, String> getDefaultAttributionHeaders(Model model, BooleanSupplier installTelemetryEnabled) {
        if (!installTelemetryEnabled.getAsBoolean()) {
            return null;
        }
        if (isOpenRouterModel(model)) {
            return Map.of(
                    "HTTP-Referer", "https://pi.dev",
                    "X-OpenRouter-Title", "pi",
                    "X-OpenRouter-Categories", "cli-agent");
        }
        if (isNvidiaNimModel(model)) {
            return Map.of("X-BILLING-INVOKE-ORIGIN", "Pi");
        }
        if (isCloudflareModel(model)) {
            return Map.of("User-Agent", "pi-coding-agent");
        }
        if (isVercelGatewayModel(model)) {
            return Map.of(
                    "http-referer", "https://pi.dev",
                    "x-title", "pi");
        }
        return null;
    }

    private static Map<String, String> getSessionHeaders(Model model, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        if (!model.provider().equals("opencode")
                && !model.provider().equals("opencode-go")
                && !matchesHost(baseUrl(model), OPENCODE_HOST)) {
            return null;
        }
        return Map.of("x-opencode-session", sessionId, "x-opencode-client", "pi");
    }

    private static boolean isOpenRouterModel(Model model) {
        return model.provider().equals("openrouter") || baseUrl(model).contains(OPENROUTER_HOST);
    }

    private static boolean isNvidiaNimModel(Model model) {
        return model.provider().equals("nvidia") || matchesHost(baseUrl(model), NVIDIA_NIM_HOST);
    }

    private static boolean isCloudflareModel(Model model) {
        return model.provider().equals("cloudflare-workers-ai")
                || model.provider().equals("cloudflare-ai-gateway")
                || matchesHost(baseUrl(model), CLOUDFLARE_API_HOST)
                || matchesHost(baseUrl(model), CLOUDFLARE_AI_GATEWAY_HOST);
    }

    private static boolean isVercelGatewayModel(Model model) {
        return model.provider().equals("vercel-ai-gateway") || matchesHost(baseUrl(model), VERCEL_GATEWAY_HOST);
    }

    private static boolean matchesHost(String baseUrl, String expectedHost) {
        try {
            return URI.create(baseUrl).getHost().equals(expectedHost);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static String baseUrl(Model model) {
        Object value = model.options() == null ? null : model.options().get("baseUrl");
        return value == null ? "" : value.toString();
    }
}
