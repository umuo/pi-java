package works.earendil.pi.common.text;

public final class EastAsianWidth {
    private EastAsianWidth() {
    }

    public static int visibleWidth(String text) {
        int width = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '\n' || cp == '\r') {
                continue;
            }
            if (isCombining(cp)) {
                continue;
            }
            width += isWide(cp) ? 2 : 1;
        }
        return width;
    }

    public static String truncateToWidth(String text, int maxWidth) {
        StringBuilder out = new StringBuilder();
        int width = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            int charCount = Character.charCount(cp);
            int next = isCombining(cp) ? 0 : (isWide(cp) ? 2 : 1);
            if (width + next > maxWidth) {
                break;
            }
            out.appendCodePoint(cp);
            width += next;
            i += charCount;
        }
        return out.toString();
    }

    private static boolean isCombining(int cp) {
        int type = Character.getType(cp);
        return type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK;
    }

    private static boolean isWide(int cp) {
        return (cp >= 0x1100 && cp <= 0x115F)
                || (cp >= 0x2329 && cp <= 0x232A)
                || (cp >= 0x2E80 && cp <= 0xA4CF)
                || (cp >= 0xAC00 && cp <= 0xD7A3)
                || (cp >= 0xF900 && cp <= 0xFAFF)
                || (cp >= 0xFE10 && cp <= 0xFE19)
                || (cp >= 0xFE30 && cp <= 0xFE6F)
                || (cp >= 0xFF00 && cp <= 0xFF60)
                || (cp >= 0xFFE0 && cp <= 0xFFE6)
                || (cp >= 0x1F300 && cp <= 0x1FAFF);
    }
}
