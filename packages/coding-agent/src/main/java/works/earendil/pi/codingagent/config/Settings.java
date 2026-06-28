package works.earendil.pi.codingagent.config;

import works.earendil.pi.ai.model.ThinkingLevel;
import works.earendil.pi.ai.model.Transport;

import java.util.List;
import java.util.Map;

public record Settings(
        String lastChangelogVersion,
        String defaultProvider,
        String defaultModel,
        ThinkingLevel defaultThinkingLevel,
        Transport transport,
        String steeringMode,
        String followUpMode,
        String theme,
        CompactionSettings compaction,
        RetrySettings retry,
        TerminalSettings terminal,
        ImageSettings images,
        List<String> extensions,
        List<String> skills,
        List<String> prompts,
        List<String> themes,
        List<String> enabledModels,
        String sessionDir,
        String httpProxy,
        Integer httpIdleTimeoutMs,
        Map<String, Object> extra
) {
    public record CompactionSettings(Boolean enabled, Integer reserveTokens, Integer keepRecentTokens) {
    }

    public record RetrySettings(Boolean enabled, Integer maxRetries, Integer baseDelayMs, ProviderRetrySettings provider) {
    }

    public record ProviderRetrySettings(Integer timeoutMs, Integer maxRetries, Integer maxRetryDelayMs) {
    }

    public record TerminalSettings(Boolean showImages, Integer imageWidthCells, Boolean clearOnShrink,
                                   Boolean showTerminalProgress) {
    }

    public record ImageSettings(Boolean autoResize, Boolean blockImages) {
    }
}
