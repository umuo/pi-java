package works.earendil.pi.codingagent.cli;

import com.fasterxml.jackson.databind.JsonNode;
import works.earendil.pi.ai.model.Content;
import works.earendil.pi.ai.model.Message;
import works.earendil.pi.codingagent.tools.EditDiff;
import works.earendil.pi.codingagent.tools.PathUtils;
import works.earendil.pi.common.json.JsonCodec;
import works.earendil.pi.common.text.Ansi;
import works.earendil.pi.tui.component.Diff;
import works.earendil.pi.tui.component.Markdown;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
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

    static void renderToolStart(PrintStream out, String toolName, Object args, Path cwd, int width) {
        String safeToolName = safe(toolName);
        renderText(out, "**Tool started:** `" + safeToolName + "`", width, false);
        String summary;
        try {
            summary = toolStartSummary(safeToolName, args);
        } catch (Exception e) {
            summary = "args preview unavailable: " + e.getMessage();
        }
        if (!summary.isBlank()) {
            renderText(out, summary, width, false);
        }
        if ("edit".equals(safeToolName) && cwd != null) {
            renderEditPreview(out, args, cwd, width);
        } else if ("write".equals(safeToolName) && cwd != null) {
            renderWritePreview(out, args, cwd, width);
        }
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

    private static String toolStartSummary(String toolName, Object args) {
        return switch (toolName) {
            case "edit" -> {
                String path = textArg(args, "path", "file_path");
                int edits = editCount(args);
                yield "path: `" + safeInline(path) + "`\nedits: " + edits;
            }
            case "write" -> "path: `" + safeInline(textArg(args, "path")) + "`\ncontent chars: "
                    + textArg(args, "content").length();
            case "read", "ls" -> "path: `" + safeInline(textArg(args, "path")) + "`";
            case "grep" -> "pattern: `" + safeInline(textArg(args, "pattern", "regex")) + "`\npath: `"
                    + safeInline(textArg(args, "path")) + "`";
            case "find" -> "pattern: `" + safeInline(textArg(args, "pattern", "glob")) + "`";
            case "bash" -> "command: `" + safeInline(textArg(args, "command")) + "`";
            default -> fallbackArgsSummary(args);
        };
    }

    private static void renderEditPreview(PrintStream out, Object args, Path cwd, int width) {
        try {
            EditInput input = editInput(args);
            Path path = PathUtils.resolveInside(cwd, input.path());
            String oldContent = Files.readString(path);
            EditDiff.Applied applied = EditDiff.apply(oldContent, input.edits());
            String diff = EditDiff.unifiedPatch(input.path(), oldContent, applied.content(), 3);
            if (!diff.isBlank()) {
                renderDiffPreview(out, diff, width);
            }
        } catch (Exception e) {
            renderText(out, "edit preview unavailable: " + e.getMessage(), width, false);
        }
    }

    private static void renderWritePreview(PrintStream out, Object args, Path cwd, int width) {
        try {
            String rawPath = textArg(args, "path", "file_path");
            if (rawPath.isBlank()) {
                throw new IllegalArgumentException("path is required");
            }
            String newContent = textArg(args, "content");
            if (!hasArg(args, "content")) {
                throw new IllegalArgumentException("content is required");
            }
            Path path = PathUtils.resolveInside(cwd, rawPath);
            if (Files.exists(path) && !booleanArg(args, "overwrite", true)) {
                throw new IllegalStateException("File already exists: " + rawPath);
            }
            String oldContent = Files.exists(path) ? Files.readString(path) : "";
            String diff = EditDiff.unifiedPatch(rawPath, oldContent, newContent, 3);
            if (!diff.isBlank()) {
                renderDiffPreview(out, diff, width);
            }
        } catch (Exception e) {
            renderText(out, "write preview unavailable: " + e.getMessage(), width, false);
        }
    }

    private static EditInput editInput(Object args) {
        String path = textArg(args, "path", "file_path");
        if (path.isBlank()) {
            throw new IllegalArgumentException("path is required");
        }
        List<EditDiff.Edit> edits = editArgs(args);
        if (edits.isEmpty()) {
            throw new IllegalArgumentException("edits must contain at least one replacement");
        }
        return new EditInput(path, edits);
    }

    private static List<EditDiff.Edit> editArgs(Object args) {
        List<EditDiff.Edit> edits = new ArrayList<>();
        Object rawEdits = valueArg(args, "edits");
        if (rawEdits instanceof String text && !text.isBlank()) {
            rawEdits = JsonCodec.parse(text);
        }
        if (rawEdits instanceof JsonNode node && node.isArray()) {
            for (JsonNode item : node) {
                edits.add(new EditDiff.Edit(item.path("oldText").asText(), item.path("newText").asText()));
            }
        } else if (rawEdits instanceof List<?> list) {
            for (Object item : list) {
                String oldText = textArg(item, "oldText");
                String newText = textArg(item, "newText");
                if (!oldText.isBlank() || !newText.isBlank()) {
                    edits.add(new EditDiff.Edit(oldText, newText));
                }
            }
        }
        if (hasArg(args, "oldText") || hasArg(args, "newText")) {
            edits.add(new EditDiff.Edit(textArg(args, "oldText"), textArg(args, "newText")));
        }
        return List.copyOf(edits);
    }

    private static int editCount(Object args) {
        List<EditDiff.Edit> edits = editArgs(args);
        return edits.size();
    }

    private static String fallbackArgsSummary(Object args) {
        if (args == null) {
            return "";
        }
        try {
            String json = args instanceof JsonNode node
                    ? JsonCodec.mapper().writerWithDefaultPrettyPrinter().writeValueAsString(node)
                    : JsonCodec.mapper().writerWithDefaultPrettyPrinter().writeValueAsString(args);
            return "```json\n" + firstLines(json, 12) + "\n```";
        } catch (Exception e) {
            return "args: `" + safeInline(String.valueOf(args)) + "`";
        }
    }

    private static String textArg(Object args, String... names) {
        Object value = null;
        for (String name : names) {
            value = valueArg(args, name);
            if (value != null) {
                break;
            }
        }
        if (value == null || value instanceof JsonNode node && node.isNull()) {
            return "";
        }
        if (value instanceof JsonNode node) {
            return node.isTextual() ? node.asText() : node.toString();
        }
        return value.toString();
    }

    private static boolean hasArg(Object args, String name) {
        return valueArg(args, name) != null;
    }

    private static boolean booleanArg(Object args, String name, boolean fallback) {
        Object value = valueArg(args, name);
        if (value == null) {
            return fallback;
        }
        if (value instanceof JsonNode node) {
            if (node.isBoolean()) {
                return node.asBoolean();
            }
            if (node.isTextual()) {
                return Boolean.parseBoolean(node.asText());
            }
        }
        return Boolean.parseBoolean(value.toString());
    }

    private static Object valueArg(Object args, String name) {
        if (args instanceof JsonNode node) {
            JsonNode value = node.get(name);
            return value == null || value.isNull() ? null : value;
        }
        if (args instanceof Map<?, ?> map) {
            return map.get(name);
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

    private static String safeInline(String value) {
        return safe(value).replace("`", "\\`").replace("\n", "\\n");
    }

    private record EditInput(String path, List<EditDiff.Edit> edits) {
    }
}
