package works.earendil.pi.tui.text;

public final class WordNavigation {
    private WordNavigation() {
    }

    public record EditResult(String text, int newCursor, String deletedText) {
    }

    public static int nextWord(String text, int cursor) {
        if (text == null || cursor >= text.length()) {
            return text == null ? 0 : text.length();
        }
        int pos = Math.max(0, cursor);
        while (pos < text.length() && !isWordChar(text.charAt(pos))) {
            pos++;
        }
        while (pos < text.length() && isWordChar(text.charAt(pos))) {
            pos++;
        }
        return pos;
    }

    public static int previousWord(String text, int cursor) {
        if (text == null || cursor <= 0) {
            return 0;
        }
        int pos = Math.min(cursor, text.length());
        while (pos > 0 && !isWordChar(text.charAt(pos - 1))) {
            pos--;
        }
        while (pos > 0 && isWordChar(text.charAt(pos - 1))) {
            pos--;
        }
        return pos;
    }

    public static EditResult deleteWordForward(String text, int cursor) {
        if (text == null || cursor >= text.length()) {
            return new EditResult(text == null ? "" : text, cursor, "");
        }
        int c = Math.max(0, cursor);
        int next = nextWord(text, c);
        String deleted = text.substring(c, next);
        String remaining = text.substring(0, c) + text.substring(next);
        return new EditResult(remaining, c, deleted);
    }

    public static EditResult deleteWordBackward(String text, int cursor) {
        if (text == null || cursor <= 0) {
            return new EditResult(text == null ? "" : text, 0, "");
        }
        int c = Math.min(cursor, text.length());
        int prev = previousWord(text, c);
        String deleted = text.substring(prev, c);
        String remaining = text.substring(0, prev) + text.substring(c);
        return new EditResult(remaining, prev, deleted);
    }

    private static boolean isWordChar(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_' || ch == '-';
    }
}
