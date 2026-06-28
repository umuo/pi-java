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
        return totalInputTokens() + outputTokens + reasoningTokens;
    }
}
