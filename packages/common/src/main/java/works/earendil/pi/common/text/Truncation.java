package works.earendil.pi.common.text;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Truncation {
    public static final int DEFAULT_MAX_LINES = 2000;
    public static final int DEFAULT_MAX_BYTES = 50 * 1024;
    public static final int GREP_MAX_LINE_LENGTH = 500;

    private Truncation() {
    }

    public enum Limit {
        LINES,
        BYTES
    }

    public record Options(int maxLines, int maxBytes) {
        public Options {
            if (maxLines < 0) {
                throw new IllegalArgumentException("maxLines must be non-negative");
            }
            if (maxBytes < 0) {
                throw new IllegalArgumentException("maxBytes must be non-negative");
            }
        }

        public static Options defaults() {
            return new Options(DEFAULT_MAX_LINES, DEFAULT_MAX_BYTES);
        }
    }

    public record LineResult(String text, boolean wasTruncated) {
    }

    public record Result(
            String content,
            boolean truncated,
            Limit truncatedBy,
            int totalLines,
            int totalBytes,
            int outputLines,
            int outputBytes,
            boolean lastLinePartial,
            boolean firstLineExceedsLimit,
            int maxLines,
            int maxBytes
    ) {
    }

    public static String formatSize(int bytes) {
        if (bytes < 1024) {
            return bytes + "B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1fKB", bytes / 1024.0);
        }
        return String.format("%.1fMB", bytes / (1024.0 * 1024.0));
    }

    public static Result truncateHead(String content) {
        return truncateHead(content, Options.defaults());
    }

    public static Result truncateHead(String content, Options options) {
        options = options == null ? Options.defaults() : options;
        List<String> lines = splitLinesForCounting(content);
        int totalBytes = bytes(content);
        if (lines.size() <= options.maxLines && totalBytes <= options.maxBytes) {
            return unchanged(content, lines.size(), totalBytes, options);
        }
        if (!lines.isEmpty() && bytes(lines.getFirst()) > options.maxBytes) {
            return new Result("", true, Limit.BYTES, lines.size(), totalBytes, 0, 0,
                    false, true, options.maxLines, options.maxBytes);
        }
        List<String> out = new ArrayList<>();
        int outBytes = 0;
        Limit limit = Limit.LINES;
        for (int i = 0; i < lines.size() && i < options.maxLines; i++) {
            String line = lines.get(i);
            int lineBytes = bytes(line) + (i > 0 ? 1 : 0);
            if (outBytes + lineBytes > options.maxBytes) {
                limit = Limit.BYTES;
                break;
            }
            out.add(line);
            outBytes += lineBytes;
        }
        String output = String.join("\n", out);
        return new Result(output, true, limit, lines.size(), totalBytes, out.size(), bytes(output),
                false, false, options.maxLines, options.maxBytes);
    }

    public static Result truncateTail(String content) {
        return truncateTail(content, Options.defaults());
    }

    public static Result truncateTail(String content, Options options) {
        options = options == null ? Options.defaults() : options;
        List<String> lines = splitLinesForCounting(content);
        int totalBytes = bytes(content);
        if (lines.size() <= options.maxLines && totalBytes <= options.maxBytes) {
            return unchanged(content, lines.size(), totalBytes, options);
        }
        List<String> out = new ArrayList<>();
        int outBytes = 0;
        Limit limit = Limit.LINES;
        boolean partial = false;
        for (int i = lines.size() - 1; i >= 0 && out.size() < options.maxLines; i--) {
            String line = lines.get(i);
            int lineBytes = bytes(line) + (!out.isEmpty() ? 1 : 0);
            if (outBytes + lineBytes > options.maxBytes) {
                limit = Limit.BYTES;
                if (out.isEmpty()) {
                    String truncatedLine = tailBytes(line, options.maxBytes);
                    out.addFirst(truncatedLine);
                    outBytes = bytes(truncatedLine);
                    partial = true;
                }
                break;
            }
            out.addFirst(line);
            outBytes += lineBytes;
        }
        String output = String.join("\n", out);
        return new Result(output, true, limit, lines.size(), totalBytes, out.size(), bytes(output),
                partial, false, options.maxLines, options.maxBytes);
    }

    public static LineResult truncateLine(String line) {
        return truncateLine(line, GREP_MAX_LINE_LENGTH);
    }

    public static LineResult truncateLine(String line, int maxChars) {
        if (maxChars < 0) {
            throw new IllegalArgumentException("maxChars must be non-negative");
        }
        if (line.length() <= maxChars) {
            return new LineResult(line, false);
        }
        return new LineResult(line.substring(0, maxChars) + "... [truncated]", true);
    }

    private static Result unchanged(String content, int lines, int totalBytes, Options options) {
        return new Result(content, false, null, lines, totalBytes, lines, totalBytes,
                false, false, options.maxLines, options.maxBytes);
    }

    private static List<String> splitLinesForCounting(String content) {
        if (content.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>(Arrays.asList(content.split("\n", -1)));
        if (content.endsWith("\n")) {
            lines.removeLast();
        }
        return lines;
    }

    private static int bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8).length;
    }

    private static String tailBytes(String text, int maxBytes) {
        StringBuilder out = new StringBuilder();
        int used = 0;
        for (int i = text.length(); i > 0; ) {
            int cp = text.codePointBefore(i);
            String s = new String(Character.toChars(cp));
            int b = bytes(s);
            if (used + b > maxBytes) {
                break;
            }
            out.insert(0, s);
            used += b;
            i -= Character.charCount(cp);
        }
        return out.toString();
    }
}
