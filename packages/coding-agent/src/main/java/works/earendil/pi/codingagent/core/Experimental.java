package works.earendil.pi.codingagent.core;

public final class Experimental {
    public static final String ENV_PI_EXPERIMENTAL = "PI_EXPERIMENTAL";

    private Experimental() {
    }

    public static boolean areExperimentalFeaturesEnabled() {
        return "1".equals(System.getenv(ENV_PI_EXPERIMENTAL));
    }

    public static boolean areExperimentalFeaturesEnabled(java.util.Map<String, String> environment) {
        return "1".equals(environment.get(ENV_PI_EXPERIMENTAL));
    }
}
