package works.earendil.pi.codingagent.core;

import java.nio.file.Path;

public final class AuthGuidance {
    private static final String UNKNOWN_PROVIDER = "unknown";

    private AuthGuidance() {
    }

    public static String getProviderLoginHelp(Path docsPath) {
        return String.join("\n",
                "Use /login to log into a provider via OAuth or API key. See:",
                "  " + docsPath.resolve("providers.md"),
                "  " + docsPath.resolve("models.md"));
    }

    public static String formatNoModelsAvailableMessage(Path docsPath) {
        return "No models available. " + getProviderLoginHelp(docsPath);
    }

    public static String formatNoModelSelectedMessage(Path docsPath) {
        return "No model selected.\n\n" + getProviderLoginHelp(docsPath) + "\n\nThen use /model to select a model.";
    }

    public static String formatNoApiKeyFoundMessage(String provider, Path docsPath) {
        String providerDisplay = UNKNOWN_PROVIDER.equals(provider) ? "the selected model" : provider;
        return "No API key found for " + providerDisplay + ".\n\n" + getProviderLoginHelp(docsPath);
    }
}
