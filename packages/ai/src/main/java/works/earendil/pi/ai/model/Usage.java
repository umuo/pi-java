package works.earendil.pi.ai.model;

public record Usage(
        int inputTokens,
        int outputTokens,
        int cacheCreationInputTokens,
        int cacheReadInputTokens,
        int reasoningTokens
) {
    public int totalInputTokens() {
        return inputTokens + cacheCreationInputTokens + cacheReadInputTokens;
    }

    public int totalTokens() {
        // Reasoning tokens are a subset of output tokens in provider usage payloads.
        return totalInputTokens() + outputTokens;
    }
}
