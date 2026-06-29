package works.earendil.pi.codingagent.cli;

import works.earendil.pi.ai.model.Content;
import works.earendil.pi.ai.model.Message;
import works.earendil.pi.common.text.Ansi;
import works.earendil.pi.tui.component.Diff;
import works.earendil.pi.tui.component.Markdown;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class InteractiveOutputRenderer {
    private static final int PREVIEW_LINES = 20;

    private InteractiveOutputRenderer() {
    }

    static void renderAssistantText(PrintStream out, String text, int width) {
        renderText(out, text, width, false);
    }

    static void renderToolResult(PrintStream out, Message.ToolResult result, int width) {
        if (result == null) {
            return;
        }
        String title = result.error()
                ? "Tool failed: " + safe(result.toolName())
                : "Tool finished: " + safe(result.toolName());
        renderText(out, "**" + title + "**", width, false);
        String diff = diffFromDetails(result.details());
        if (diff != null && !diff.isBlank()) {
            renderDiffPreview(out, diff, width);
            return;
        }
        String text = textFromContent(result.content());
        if (!text.isBlank()) {
            if (looksLikeUnifiedDiff(text)) {
                renderDiffPreview(out, text, width);
            } else {
                renderCollapsedText(out, text, width);
            }
        }
    }

    static String textFromContent(List<Content> content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (Content block : content) {
            if (block instanceof Content.Text t) {
                text.append(t.text());
            }
        }
        return text.toString();
    }

    static boolean looksLikeUnifiedDiff(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        boolean hasHunk = false;
        boolean hasChange = false;
        for (String line : text.split("\\R")) {
            if (line.startsWith("@@")) {
                hasHunk = true;
            } else if (line.startsWith("+") && !line.startsWith("+++")) {
                hasChange = true;
            } else if (line.startsWith("-") && !line.startsWith("---")) {
                hasChange = true;
            }
        }
        return hasHunk && hasChange;
    }

    private static void renderText(PrintStream out, String text, int width, boolean forceDiff) {
        if (text == null || text.isBlank()) {
            return;
        }
        int safeWidth = Math.max(20, width);
        List<String> lines = forceDiff
                ? new Diff(text).renderLines(safeWidth)
                : new Markdown(text.strip(), 1, 0, null).renderLines(safeWidth);
        for (String line : lines) {
            out.println(line);
        }
    }

    private static void renderDiffPreview(PrintStream out, String diff, int width) {
        String preview = firstLines(diff, PREVIEW_LINES);
        renderText(out, preview, width, true);
        int hidden = lineCount(diff) - lineCount(preview);
        if (hidden > 0) {
            renderText(out, "... " + hidden + " more diff lines hidden in collapsed preview", width, false);
        }
    }

    private static void renderCollapsedText(PrintStream out, String text, int width) {
        String preview = tailLines(text, PREVIEW_LINES);
        renderText(out, preview, width, false);
        int hidden = lineCount(text) - lineCount(preview);
        if (hidden > 0) {
            renderText(out, "... " + hidden + " earlier output lines hidden in collapsed preview", width, false);
        }
    }

    private static String diffFromDetails(Object details) {
        if (details instanceof Map<?, ?> map) {
            Object value = map.get("diff");
            return value instanceof String diff ? diff : null;
        }
        return null;
    }

    private static String firstLines(String text, int maxLines) {
        List<String> lines = lines(text);
        return String.join("\n", lines.subList(0, Math.min(maxLines, lines.size())));
    }

    private static String tailLines(String text, int maxLines) {
        List<String> lines = lines(text);
        int from = Math.max(0, lines.size() - maxLines);
        return String.join("\n", lines.subList(from, lines.size()));
    }

    private static int lineCount(String text) {
        return lines(text).size();
    }

    private static List<String> lines(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        String[] parts = text.split("\\R", -1);
        List<String> lines = new ArrayList<>(List.of(parts));
        if (!lines.isEmpty() && lines.getLast().isEmpty()) {
            lines.removeLast();
        }
        return lines;
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : Ansi.strip(value);
    }
}
