package works.earendil.pi.codingagent.core.export;

import com.fasterxml.jackson.databind.JsonNode;
import works.earendil.pi.common.json.JsonCodec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HtmlExporter {
    private static final Pattern SKILL_WRAPPER = Pattern.compile(
            "\\A<skill\\s+name=\"([^\"]*)\"\\s+location=\"([^\"]*)\">\\R?([\\s\\S]*?)\\R?</skill>(?:\\R\\R?([\\s\\S]*))?\\z");

    private HtmlExporter() {}

    public static void exportToFile(Path sessionJsonlPath, Path outputHtmlPath) throws IOException {
        if (!Files.exists(sessionJsonlPath)) {
            throw new IllegalArgumentException("Session file not found: " + sessionJsonlPath);
        }

        List<String> lines = Files.readAllLines(sessionJsonlPath, StandardCharsets.UTF_8);
        StringBuilder messagesHtml = new StringBuilder();
        String sessionHeader = "Session Export - " + sessionJsonlPath.getFileName();

        for (String line : lines) {
            if (line == null || line.isBlank()) continue;
            try {
                JsonNode node = JsonCodec.parse(line);
                String type = node.path("type").asText("");
                String timestampStr = formatTimestamp(node.get("timestamp"));

                if ("message".equals(type) && node.has("message")) {
                    JsonNode msgNode = node.get("message");
                    String role = msgNode.path("role").asText("unknown");
                    StringBuilder contentHtml = new StringBuilder();

                    appendMessageContent(contentHtml, role, msgNode.get("content"));

                    String roleClass = "user".equalsIgnoreCase(role) ? "role-user" : "role-assistant";
                    messagesHtml.append("<div class='message ").append(roleClass).append("'>")
                            .append("<div class='msg-header'><span class='role-badge'>")
                            .append(escapeHtml(role.toUpperCase())).append("</span>")
                            .append(sourceBadge(msgNode.path("source").asText("")))
                            .append("<span class='timestamp'>").append(timestampStr).append("</span></div>")
                            .append("<div class='msg-body'>").append(contentHtml).append("</div>")
                            .append("</div>\n");
                } else if ("custom".equals(type) || "custom_message".equals(type)) {
                    messagesHtml.append(renderCustomEntry(node, timestampStr));
                }
            } catch (Exception ignored) {
            }
        }

        String html = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>%s</title>
                    <style>
                        :root {
                            --bg: #0f172a;
                            --card-bg: #1e293b;
                            --user-bg: #1e3a8a;
                            --assistant-bg: #1e293b;
                            --text: #f8fafc;
                            --text-dim: #94a3b8;
                            --border: #334155;
                            --accent: #38bdf8;
                        }
                        body {
                            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                            background-color: var(--bg);
                            color: var(--text);
                            margin: 0;
                            padding: 2rem;
                            line-height: 1.6;
                        }
                        .container {
                            max-width: 900px;
                            margin: 0 auto;
                        }
                        h1 {
                            color: var(--accent);
                            border-bottom: 2px solid var(--border);
                            padding-bottom: 0.5rem;
                            margin-bottom: 2rem;
                        }
                        .message {
                            background-color: var(--card-bg);
                            border: 1px solid var(--border);
                            border-radius: 8px;
                            margin-bottom: 1.5rem;
                            padding: 1.25rem;
                            box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
                        }
                        .role-user {
                            border-left: 4px solid var(--accent);
                        }
                        .role-assistant {
                            border-left: 4px solid #10b981;
                        }
                        .msg-header {
                            display: flex;
                            justify-content: space-between;
                            align-items: center;
                            margin-bottom: 0.75rem;
                            font-size: 0.85rem;
                        }
                        .role-badge {
                            font-weight: bold;
                            padding: 0.2rem 0.6rem;
                            border-radius: 4px;
                            background-color: rgba(255, 255, 255, 0.1);
                        }
                        .timestamp {
                            color: var(--text-dim);
                        }
                        .source-badge {
                            color: var(--text-dim);
                            margin-left: 0.5rem;
                            font-size: 0.8rem;
                        }
                        .msg-body {
                            white-space: pre-wrap;
                            word-wrap: break-word;
                        }
                        .content-text {
                            margin-bottom: 0.5rem;
                        }
                        .content-image {
                            margin: 0.75rem 0;
                        }
                        .content-image img {
                            display: block;
                            max-width: 100%%;
                            height: auto;
                            border: 1px solid var(--border);
                            border-radius: 6px;
                            background-color: rgba(255, 255, 255, 0.04);
                        }
                        .image-caption {
                            color: var(--text-dim);
                            font-size: 0.85rem;
                            margin-top: 0.35rem;
                        }
                        .skill-invocation {
                            border-left: 3px solid #f59e0b;
                        }
                        .custom-entry {
                            border-left: 3px solid #a78bfa;
                        }
                        details {
                            background-color: rgba(0, 0, 0, 0.2);
                            border-radius: 6px;
                            padding: 0.5rem 0.75rem;
                            margin: 0.5rem 0;
                        }
                        summary {
                            cursor: pointer;
                            color: var(--accent);
                            font-weight: 500;
                        }
                        pre {
                            background-color: rgba(0, 0, 0, 0.4);
                            padding: 0.75rem;
                            border-radius: 4px;
                            overflow-x: auto;
                            margin-top: 0.5rem;
                        }
                        code {
                            font-family: Menlo, Monaco, Consolas, "Courier New", monospace;
                            font-size: 0.9rem;
                        }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <h1>%s</h1>
                        <div class="messages">
                            %s
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(escapeHtml(sessionHeader), escapeHtml(sessionHeader), messagesHtml.toString());

        if (outputHtmlPath.getParent() != null) {
            Files.createDirectories(outputHtmlPath.getParent());
        }
        Files.writeString(outputHtmlPath, html, StandardCharsets.UTF_8);
    }

    private static void appendMessageContent(StringBuilder contentHtml, String role, JsonNode contentNode) {
        if (contentNode == null) {
            return;
        }
        if (contentNode.isArray()) {
            for (JsonNode c : contentNode) {
                String cType = c.path("type").asText();
                if ("text".equals(cType) || c.has("text")) {
                    appendTextContent(contentHtml, role, c.path("text").asText());
                } else if ("image".equals(cType) || c.has("data") || c.has("url") || c.has("mimeType")) {
                    appendImageContent(contentHtml, c);
                } else if ("tool_use".equals(cType) || "toolCall".equals(cType)) {
                    String name = c.path("name").asText();
                    String input = c.has("input") ? c.path("input").toString() : c.path("displayContent").toString();
                    contentHtml.append("<details class='tool-use'><summary>Tool Use: <strong>")
                            .append(escapeHtml(name)).append("</strong></summary>")
                            .append("<pre><code>").append(escapeHtml(input)).append("</code></pre></details>");
                } else if ("tool_result".equals(cType)) {
                    String text = c.path("content").asText("");
                    if (text.isEmpty() && c.has("content") && c.get("content").isArray()) {
                        StringBuilder sub = new StringBuilder();
                        for (JsonNode subC : c.get("content")) {
                            sub.append(subC.path("text").asText("")).append("\n");
                        }
                        text = sub.toString();
                    }
                    contentHtml.append("<details class='tool-result'><summary>Tool Result</summary>")
                            .append("<pre><code>").append(escapeHtml(text)).append("</code></pre></details>");
                } else {
                    contentHtml.append("<details class='content-block'><summary>Content Block: <strong>")
                            .append(escapeHtml(cType.isBlank() ? "unknown" : cType)).append("</strong></summary>")
                            .append("<pre><code>").append(escapeHtml(c.toString())).append("</code></pre></details>");
                }
            }
        } else if (contentNode.isTextual()) {
            appendTextContent(contentHtml, role, contentNode.asText());
        } else {
            contentHtml.append("<pre><code>").append(escapeHtml(contentNode.toString())).append("</code></pre>");
        }
    }

    private static void appendTextContent(StringBuilder contentHtml, String role, String text) {
        SkillBlock skillBlock = "user".equalsIgnoreCase(role) ? parseSkillBlock(text) : null;
        if (skillBlock != null) {
            contentHtml.append("<details class='skill-invocation' open><summary>Skill: <strong>")
                    .append(escapeHtml(skillBlock.name())).append("</strong></summary>")
                    .append("<div class='skill-location'>").append(escapeHtml(skillBlock.location())).append("</div>")
                    .append("<pre><code>").append(escapeHtml(skillBlock.content())).append("</code></pre></details>");
            if (!skillBlock.userMessage().isBlank()) {
                contentHtml.append("<div class='content-text user-message'>")
                        .append(escapeHtml(skillBlock.userMessage())).append("</div>");
            }
            return;
        }
        contentHtml.append("<div class='content-text'>").append(escapeHtml(text)).append("</div>");
    }

    private static void appendImageContent(StringBuilder contentHtml, JsonNode imageNode) {
        String mimeType = safeImageMime(imageNode.path("mimeType").asText("image/png"));
        String data = imageNode.path("data").asText("");
        String url = imageNode.path("url").asText("");
        if (!data.isBlank()) {
            if (mimeType == null) {
                appendImagePlaceholder(contentHtml, "unsupported inline image mime type",
                        imageNode.path("mimeType").asText(""));
                return;
            }
            contentHtml.append("<figure class='content-image'><img alt='Image attachment' src=\"data:")
                    .append(escapeHtml(mimeType)).append(";base64,").append(escapeHtml(data)).append("\"/>")
                    .append("<figcaption class='image-caption'>")
                    .append(escapeHtml(mimeType)).append(" inline image</figcaption></figure>");
            return;
        }
        if (!url.isBlank()) {
            String safeUrl = safeImageUrl(url);
            if (safeUrl == null) {
                appendImagePlaceholder(contentHtml, "unsupported image URL scheme", url);
                return;
            }
            contentHtml.append("<figure class='content-image'><img alt='Image attachment' src=\"")
                    .append(escapeHtml(safeUrl)).append("\"/>");
            if (mimeType != null) {
                contentHtml.append("<figcaption class='image-caption'>").append(escapeHtml(mimeType))
                        .append(" image</figcaption>");
            }
            contentHtml.append("</figure>");
            return;
        }
        appendImagePlaceholder(contentHtml, "image content has no data or URL", "");
    }

    private static void appendImagePlaceholder(StringBuilder contentHtml, String reason, String value) {
        contentHtml.append("<details class='content-image image-placeholder'><summary>Image omitted: ")
                .append(escapeHtml(reason)).append("</summary>");
        if (value != null && !value.isBlank()) {
            contentHtml.append("<pre><code>").append(escapeHtml(value)).append("</code></pre>");
        }
        contentHtml.append("</details>");
    }

    private static String safeImageMime(String mimeType) {
        String normalized = mimeType == null || mimeType.isBlank()
                ? "image/png"
                : mimeType.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "image/png", "image/jpeg", "image/jpg", "image/gif", "image/webp", "image/bmp" -> normalized;
            default -> null;
        };
    }

    private static String safeImageUrl(String url) {
        if (url == null) {
            return null;
        }
        String trimmed = url.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://") ? trimmed : null;
    }

    private static SkillBlock parseSkillBlock(String text) {
        if (text == null || !text.startsWith("<skill ")) {
            return null;
        }
        Matcher matcher = SKILL_WRAPPER.matcher(text);
        if (!matcher.matches()) {
            return null;
        }
        return new SkillBlock(unescapeXml(matcher.group(1)), unescapeXml(matcher.group(2)),
                matcher.group(3) == null ? "" : matcher.group(3),
                matcher.group(4) == null ? "" : matcher.group(4));
    }

    private static String renderCustomEntry(JsonNode node, String timestampStr) {
        String type = node.path("type").asText("custom");
        String customType = node.path("customType").asText(type);
        JsonNode payload = "custom_message".equals(type) ? node.get("content") : node.get("data");
        if (payload == null || payload.isNull()) {
            payload = node;
        }
        return "<div class='message custom-entry'>"
                + "<div class='msg-header'><span class='role-badge'>" + escapeHtml(type.toUpperCase()) + "</span>"
                + sourceBadge(node)
                + "<span class='timestamp'>" + timestampStr + "</span></div>"
                + "<div class='msg-body'><details open><summary>Custom Entry: <strong>"
                + escapeHtml(customType) + "</strong></summary><pre><code>"
                + escapeHtml(payload.toString()) + "</code></pre></details></div></div>\n";
    }

    private static String sourceBadge(JsonNode node) {
        return sourceBadge(node.path("source").asText(""));
    }

    private static String sourceBadge(String sourceValue) {
        String source = sourceValue == null ? "" : sourceValue.trim();
        if (source.isEmpty()) {
            return "";
        }
        return "<span class='source-badge'>source=" + escapeHtml(source) + "</span>";
    }

    private static String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String unescapeXml(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&");
    }

    private static String formatTimestamp(JsonNode timestamp) {
        if (timestamp == null || timestamp.isNull()) {
            return "";
        }
        try {
            Instant instant = timestamp.isNumber()
                    ? Instant.ofEpochMilli(timestamp.asLong())
                    : Instant.parse(timestamp.asText());
            return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault())
                    .format(instant);
        } catch (Exception ignored) {
            return "";
        }
    }

    private record SkillBlock(String name, String location, String content, String userMessage) {
    }
}
