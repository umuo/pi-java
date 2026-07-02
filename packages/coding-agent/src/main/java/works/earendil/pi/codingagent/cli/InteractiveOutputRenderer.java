package works.earendil.pi.codingagent.cli;

import com.fasterxml.jackson.databind.JsonNode;
import works.earendil.pi.ai.model.Content;
import works.earendil.pi.ai.model.Message;
import works.earendil.pi.codingagent.core.AgentSession;
import works.earendil.pi.codingagent.resources.SkillLoader;
import works.earendil.pi.codingagent.tools.EditDiff;
import works.earendil.pi.codingagent.tools.PathUtils;
import works.earendil.pi.common.json.JsonCodec;
import works.earendil.pi.common.text.Ansi;
import works.earendil.pi.common.text.EastAsianWidth;
import works.earendil.pi.orchestrator.service.OrchestratorLogTailer;
import works.earendil.pi.orchestrator.service.OrchestratorSupervisor;
import works.earendil.pi.tui.component.CollapsibleToolPanel;
import works.earendil.pi.tui.component.Diff;
import works.earendil.pi.tui.component.Markdown;
import works.earendil.pi.tui.component.SplitDiffPanel;
import works.earendil.pi.tui.component.Surface;

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

    static void renderOrchestratorEvent(PrintStream out, OrchestratorSupervisor.RpcEvent event, int width) {
        if (event == null) {
            return;
        }
        List<String> rows = new ArrayList<>();
        rows.add("seq: " + event.sequence());
        rows.add("instance: " + displayValue(event.instanceId()));
        rows.add("request: " + displayValue(event.requestId()));
        rows.add("at: " + displayValue(event.receivedAt()));
        rows.add("payload:");
        for (String line : lines(event.rawJson())) {
            rows.add("  " + line);
        }
        renderPanel(out, "Orchestrator event", rows, width);
    }

    static void renderOrchestratorLogLine(PrintStream out, OrchestratorLogTailer.LogLine line, int width) {
        if (line == null) {
            return;
        }
        List<String> rows = new ArrayList<>();
        rows.add("instance: " + displayValue(line.instanceId()));
        rows.add("at: " + displayValue(line.receivedAt()));
        rows.add("path: " + displayPath(line.path()));
        rows.add("line: " + displayValue(line.line()));
        renderPanel(out, "Orchestrator stderr", rows, width);
    }

    static void renderSkillTriggerDiagnostic(PrintStream out,
                                             AgentSession.AgentSessionEvent.SkillTriggerDiagnostic diagnostic,
                                             int width) {
        if (diagnostic == null || diagnostic.matches().isEmpty()) {
            return;
        }
        List<String> rows = new ArrayList<>();
        for (SkillLoader.SkillTriggerMatch match : diagnostic.matches()) {
            rows.add("skill: " + displayValue(match.skillName())
                    + " | model: " + (match.modelVisible() ? "visible" : "manual")
                    + " | reasons: " + displayReasons(match.reasons()));
            rows.add("path: " + displayPath(match.skillPath()));
        }
        renderPanel(out, "Skill trigger diagnostic", rows, width);
    }

    static void renderSkillDiagnosticInspect(PrintStream out, JsonNode snapshot, int width) {
        if (snapshot == null) {
            return;
        }
        List<String> rows = new ArrayList<>();
        JsonNode selectedSource = snapshot.path("selectedSource");
        if (!selectedSource.isMissingNode() && !selectedSource.isEmpty()) {
            rows.add("source: " + selectedSource.path("title").asText(""));
            String subtitle = selectedSource.path("subtitle").asText("");
            if (!subtitle.isBlank()) {
                rows.add("details: " + subtitle);
            }
        } else {
            JsonNode source = snapshot.path("source");
            String sessionFile = source.path("sessionFile").asText("current");
            String branch = source.path("branch").asText("");
            rows.add("source: " + displayPath(Path.of(sessionFile)) + (branch.isBlank() ? "" : " | branch: " + branch));
        }

        JsonNode summary = snapshot.path("summary");
        rows.add("summary: entries=" + summary.path("entries").asInt(0)
                + " | matches=" + summary.path("matches").asInt(0)
                + " | visible=" + summary.path("visible").asInt(0)
                + " | manualOnly=" + summary.path("manualOnly").asInt(0));

        JsonNode reasonDrillDown = summary.path("reasonDrillDown");
        if (reasonDrillDown.isArray() && !reasonDrillDown.isEmpty()) {
            rows.add("reason drill-down:");
            for (JsonNode reasonNode : reasonDrillDown) {
                rows.add("  * " + reasonNode.path("reason").asText("")
                        + " (" + reasonNode.path("matches").asInt(0) + " matches | visible: "
                        + reasonNode.path("visible").asInt(0) + " | manual: "
                        + reasonNode.path("manualOnly").asInt(0) + ")");
                for (JsonNode skillNode : reasonNode.path("skills")) {
                    rows.add("    - " + skillNode.path("skill").asText("")
                            + ": " + skillNode.path("matches").asInt(0) + " matches | visible: "
                            + skillNode.path("visible").asInt(0) + " | manual: "
                            + skillNode.path("manualOnly").asInt(0));
                }
            }
        } else {
            rows.add("reason drill-down: no matches");
        }

        JsonNode entries = snapshot.path("entries");
        if (entries.isArray() && !entries.isEmpty()) {
            rows.add("recent entries:");
            int count = 0;
            for (JsonNode entryNode : entries) {
                if (++count > 3) {
                    break;
                }
                rows.add("  " + count + ". at: " + entryNode.path("capturedAt").asText(""));
                for (JsonNode matchNode : entryNode.path("matches")) {
                    List<String> reasons = new ArrayList<>();
                    matchNode.path("reasons").forEach(r -> reasons.add(r.asText("")));
                    rows.add("     - " + matchNode.path("skill").asText("")
                            + " | model: " + (matchNode.path("modelVisible").asBoolean(false) ? "visible" : "manual")
                            + " | reasons: " + displayReasons(reasons));
                }
            }
        }

        renderPanel(out, "Skill diagnostic inspect", rows, width);
    }

    static void renderSkillRecommendation(PrintStream out, SkillLoader.SkillRecommendationResult res, int width) {
        if (res == null) {
            return;
        }
        List<String> rows = new ArrayList<>();
        rows.add("query: " + (res.query().isBlank() ? "<all>" : res.query()) + " | total matched: " + res.totalMatched() + " | reason-filtered: " + res.filteredByReason());
        if (res.items().isEmpty()) {
            rows.add("recommendations: none");
        } else {
            rows.add("recommendations:");
            int count = 0;
            for (SkillLoader.SkillRecommendationItem item : res.items()) {
                rows.add("  " + (++count) + ". " + item.skillName() + " (score: " + item.score() + " | visible: " + item.modelVisible() + ")");
                if (!item.description().isBlank()) {
                    rows.add("     desc: " + item.description());
                }
                if (!item.matchedReasons().isEmpty()) {
                    rows.add("     reasons: " + displayReasons(item.matchedReasons()));
                }
                if (!item.matchedKeywords().isEmpty()) {
                    rows.add("     keywords: " + String.join(", ", item.matchedKeywords()));
                }
            }
        }
        renderPanel(out, "Skill search & recommendation", rows, width);
    }

    static void renderSplitDiff(PrintStream out, String leftTitle, String rightTitle,
                                String leftContent, String rightContent, int width, int height) {
        int safeWidth = Math.max(30, width);
        int safeHeight = Math.max(3, height);
        SplitDiffPanel diffPanel = new SplitDiffPanel(leftTitle, rightTitle, leftContent, rightContent);
        Surface surf = new Surface(safeWidth, safeHeight);
        diffPanel.render(surf);
        for (String line : surf.frame().split("\r?\n", -1)) {
            out.println(line);
        }
    }

    static void renderCollapsibleToolOutput(PrintStream out, String toolName, String toolCallId,
                                            String output, boolean collapsed, int maxLines, int width) {
        int safeWidth = Math.max(30, width);
        int safeHeight = Math.max(2, maxLines + 2);
        CollapsibleToolPanel toolPanel = new CollapsibleToolPanel(toolName, toolCallId, output, collapsed, maxLines);
        Surface surf = new Surface(safeWidth, safeHeight);
        toolPanel.render(surf);
        for (String line : surf.frame().split("\r?\n", -1)) {
            out.println(line);
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

    private static void renderPanel(PrintStream out, String title, List<String> rows, int width) {
        int safeWidth = Math.max(40, width);
        out.println(panelBorder(title, safeWidth));
        for (String row : rows) {
            out.println(panelRow(row, safeWidth));
        }
        out.println(panelBorder("", safeWidth));
    }

    private static String panelBorder(String title, int width) {
        String label = title == null || title.isBlank() ? "" : " " + safe(title) + " ";
        int innerWidth = width - 2;
        if (EastAsianWidth.visibleWidth(label) > innerWidth) {
            label = EastAsianWidth.truncateToWidth(label, innerWidth);
        }
        int filler = Math.max(0, innerWidth - EastAsianWidth.visibleWidth(label));
        return "+" + label + "-".repeat(filler) + "+";
    }

    private static String panelRow(String value, int width) {
        int contentWidth = width - 4;
        String text = EastAsianWidth.truncateToWidth(safe(value), contentWidth);
        int padding = Math.max(0, contentWidth - EastAsianWidth.visibleWidth(text));
        return "| " + text + " ".repeat(padding) + " |";
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

    private static String displayValue(Object value) {
        return value == null ? "-" : safe(String.valueOf(value));
    }

    private static String displayPath(Path path) {
        if (path == null) {
            return "-";
        }
        Path fileName = path.getFileName();
        String fullPath = safe(path.toString());
        if (fileName == null || fullPath.equals(fileName.toString())) {
            return fullPath;
        }
        return safe(fileName.toString()) + " (" + fullPath + ")";
    }

    private static String displayReasons(List<String> reasons) {
        if (reasons == null || reasons.isEmpty()) {
            return "-";
        }
        return safe(String.join(", ", reasons));
    }

    private record EditInput(String path, List<EditDiff.Edit> edits) {
    }
}
