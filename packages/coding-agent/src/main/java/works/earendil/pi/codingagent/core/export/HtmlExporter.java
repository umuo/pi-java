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

public final class HtmlExporter {

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

                    JsonNode contentNode = msgNode.get("content");
                    if (contentNode != null && contentNode.isArray()) {
                        for (JsonNode c : contentNode) {
                            String cType = c.path("type").asText();
                            if ("text".equals(cType) || c.has("text")) {
                                String text = c.path("text").asText();
                                contentHtml.append("<div class='content-text'>").append(escapeHtml(text)).append("</div>");
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
                            }
                        }
                    } else if (contentNode != null && contentNode.isTextual()) {
                        contentHtml.append("<div class='content-text'>").append(escapeHtml(contentNode.asText())).append("</div>");
                    }

                    String roleClass = "user".equalsIgnoreCase(role) ? "role-user" : "role-assistant";
                    messagesHtml.append("<div class='message ").append(roleClass).append("'>")
                            .append("<div class='msg-header'><span class='role-badge'>").append(role.toUpperCase()).append("</span>")
                            .append("<span class='timestamp'>").append(timestampStr).append("</span></div>")
                            .append("<div class='msg-body'>").append(contentHtml).append("</div>")
                            .append("</div>\n");
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
                        .msg-body {
                            white-space: pre-wrap;
                            word-wrap: break-word;
                        }
                        .content-text {
                            margin-bottom: 0.5rem;
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

    private static String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
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
}
