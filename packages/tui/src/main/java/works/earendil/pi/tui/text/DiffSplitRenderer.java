package works.earendil.pi.tui.text;

import works.earendil.pi.common.text.EastAsianWidth;

import java.util.ArrayList;
import java.util.List;

public final class DiffSplitRenderer {
    private DiffSplitRenderer() {
    }

    public static List<SplitLine> render(String unifiedDiff, int width) {
        if (unifiedDiff == null || unifiedDiff.isEmpty()) {
            return List.of();
        }
        int columnWidth = Math.max(1, (width - 3) / 2);
        List<DiffLine> parsed = parse(unifiedDiff);
        List<SplitLine> output = new ArrayList<>();
        for (int i = 0; i < parsed.size(); ) {
            DiffLine line = parsed.get(i);
            if (line.type() == LineType.REMOVED) {
                List<String> removed = new ArrayList<>();
                while (i < parsed.size() && parsed.get(i).type() == LineType.REMOVED) {
                    removed.add(parsed.get(i).text());
                    i++;
                }
                List<String> added = new ArrayList<>();
                while (i < parsed.size() && parsed.get(i).type() == LineType.ADDED) {
                    added.add(parsed.get(i).text());
                    i++;
                }
                int rows = Math.max(removed.size(), added.size());
                for (int row = 0; row < rows; row++) {
                    String left = row < removed.size() ? removed.get(row) : "";
                    String right = row < added.size() ? added.get(row) : "";
                    output.add(split(left, right, LineType.CHANGED, columnWidth));
                }
                continue;
            }
            if (line.type() == LineType.ADDED) {
                output.add(split("", line.text(), LineType.ADDED, columnWidth));
            } else if (line.type() == LineType.CONTEXT) {
                output.add(split(line.text(), line.text(), LineType.CONTEXT, columnWidth));
            } else {
                output.add(split(line.text(), line.text(), line.type(), columnWidth));
            }
            i++;
        }
        return List.copyOf(output);
    }

    public static List<DiffLine> parse(String unifiedDiff) {
        List<DiffLine> lines = new ArrayList<>();
        for (String raw : unifiedDiff.split("\\R", -1)) {
            if (raw.startsWith("+++") || raw.startsWith("---")) {
                lines.add(new DiffLine(raw, LineType.FILE_HEADER));
            } else if (raw.startsWith("@@")) {
                lines.add(new DiffLine(raw, LineType.HUNK));
            } else if (raw.startsWith("+")) {
                lines.add(new DiffLine(raw.substring(1), LineType.ADDED));
            } else if (raw.startsWith("-")) {
                lines.add(new DiffLine(raw.substring(1), LineType.REMOVED));
            } else if (raw.startsWith(" ")) {
                lines.add(new DiffLine(raw.substring(1), LineType.CONTEXT));
            } else {
                lines.add(new DiffLine(raw, LineType.CONTEXT));
            }
        }
        return List.copyOf(lines);
    }

    private static SplitLine split(String left, String right, LineType type, int columnWidth) {
        return new SplitLine(fit(left, columnWidth), fit(right, columnWidth), type);
    }

    private static String fit(String text, int width) {
        if (EastAsianWidth.visibleWidth(text) <= width) {
            return text;
        }
        if (width <= 1) {
            return EastAsianWidth.truncateToWidth(text, width);
        }
        return EastAsianWidth.truncateToWidth(text, width - 1) + "…";
    }

    public enum LineType {
        FILE_HEADER,
        HUNK,
        CONTEXT,
        REMOVED,
        ADDED,
        CHANGED
    }

    public record DiffLine(String text, LineType type) {
    }

    public record SplitLine(String left, String right, LineType type) {
    }
}
