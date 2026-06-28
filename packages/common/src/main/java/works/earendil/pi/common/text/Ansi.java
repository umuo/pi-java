package works.earendil.pi.common.text;

import java.util.regex.Pattern;

public final class Ansi {
    private static final Pattern ANSI_PATTERN = Pattern.compile("\\u001B\\[[0-?]*[ -/]*[@-~]");

    private Ansi() {
    }

    public static String strip(String text) {
        return ANSI_PATTERN.matcher(text).replaceAll("");
    }

    public static String sgr(String text, int code) {
        return "\u001B[" + code + "m" + text + "\u001B[0m";
    }

    public static String bold(String text) {
        return sgr(text, 1);
    }

    public static String dim(String text) {
        return sgr(text, 2);
    }
}
