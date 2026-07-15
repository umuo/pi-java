package works.earendil.pi.agent.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import works.earendil.pi.common.json.JsonCodec;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class SessionEntryCodec {
    private SessionEntryCodec() {
    }

    public static String encode(SessionEntry entry) {
        ObjectNode node = JsonCodec.mapper().createObjectNode();
        node.put("type", entry.type());
        node.put("id", entry.id());
        if (entry.parentId() == null) {
            node.putNull("parentId");
        } else {
            node.put("parentId", entry.parentId());
        }
        node.put("timestamp", entry.timestamp().toString());
        switch (entry) {
            case SessionEntry.MessageEntry e -> node.set("message", e.message());
            case SessionEntry.ThinkingLevelChangeEntry e -> node.put("thinkingLevel", e.thinkingLevel());
            case SessionEntry.ModelChangeEntry e -> {
                node.put("provider", e.provider());
                node.put("modelId", e.modelId());
            }
            case SessionEntry.ActiveToolsChangeEntry e -> node.set("activeToolNames", JsonCodec.mapper().valueToTree(e.activeToolNames()));
            case SessionEntry.CompactionEntry e -> {
                node.put("summary", e.summary());
                node.put("firstKeptEntryId", e.firstKeptEntryId());
                node.put("tokensBefore", e.tokensBefore());
                node.set("details", e.details());
                node.put("fromHook", e.fromHook());
            }
            case SessionEntry.BranchSummaryEntry e -> {
                node.put("summary", e.summary());
                node.put("fromId", e.fromId());
                node.set("details", e.details());
                node.put("fromHook", e.fromHook());
            }
            case SessionEntry.CustomEntry e -> {
                node.put("customType", e.customType());
                node.set("data", e.data());
            }
            case SessionEntry.CustomMessageEntry e -> {
                node.put("customType", e.customType());
                node.set("content", e.content());
                node.put("display", e.display());
                node.set("details", e.details());
                if (e.source() != null && !e.source().isBlank()) {
                    node.put("source", e.source());
                }
            }
            case SessionEntry.LabelEntry e -> {
                node.put("targetId", e.targetId());
                if (e.label() == null) {
                    node.putNull("label");
                } else {
                    node.put("label", e.label());
                }
            }
            case SessionEntry.SessionInfoEntry e -> node.put("name", e.name());
            case SessionEntry.LeafEntry e -> {
                if (e.targetId() == null) {
                    node.putNull("targetId");
                } else {
                    node.put("targetId", e.targetId());
                }
            }
        }
        return JsonCodec.stringify(node);
    }

    public static SessionEntry decode(String line, String source, int lineNumber) {
        JsonNode node = JsonCodec.parse(line);
        if (!node.isObject()) {
            throw invalid(source, lineNumber, "entry is not an object");
        }
        String type = requiredText(node, source, lineNumber, "type");
        String id = requiredText(node, source, lineNumber, "id");
        String parentId = nullableText(node, "parentId");
        Instant timestamp = Instant.parse(requiredText(node, source, lineNumber, "timestamp"));
        return switch (type) {
            case "message" -> new SessionEntry.MessageEntry(id, parentId, timestamp, node.get("message"));
            case "thinking_level_change" -> new SessionEntry.ThinkingLevelChangeEntry(id, parentId, timestamp,
                    requiredText(node, source, lineNumber, "thinkingLevel"));
            case "model_change" -> new SessionEntry.ModelChangeEntry(id, parentId, timestamp,
                    requiredText(node, source, lineNumber, "provider"),
                    requiredText(node, source, lineNumber, "modelId"));
            case "active_tools_change" -> new SessionEntry.ActiveToolsChangeEntry(id, parentId, timestamp,
                    textList(node.get("activeToolNames")));
            case "compaction" -> new SessionEntry.CompactionEntry(id, parentId, timestamp,
                    requiredText(node, source, lineNumber, "summary"),
                    requiredText(node, source, lineNumber, "firstKeptEntryId"),
                    node.path("tokensBefore").asInt(),
                    node.get("details"),
                    node.path("fromHook").asBoolean(false));
            case "branch_summary" -> new SessionEntry.BranchSummaryEntry(id, parentId, timestamp,
                    requiredText(node, source, lineNumber, "summary"),
                    requiredText(node, source, lineNumber, "fromId"),
                    node.get("details"),
                    node.path("fromHook").asBoolean(false));
            case "custom" -> new SessionEntry.CustomEntry(id, parentId, timestamp,
                    requiredText(node, source, lineNumber, "customType"), node.get("data"));
            case "custom_message" -> new SessionEntry.CustomMessageEntry(id, parentId, timestamp,
                    requiredText(node, source, lineNumber, "customType"), node.get("content"),
                    node.path("display").asBoolean(false), node.get("details"), nullableText(node, "source"));
            case "label" -> new SessionEntry.LabelEntry(id, parentId, timestamp,
                    requiredText(node, source, lineNumber, "targetId"), nullableText(node, "label"));
            case "session_info" -> new SessionEntry.SessionInfoEntry(id, parentId, timestamp,
                    requiredText(node, source, lineNumber, "name"));
            case "leaf" -> new SessionEntry.LeafEntry(id, parentId, timestamp, nullableText(node, "targetId"));
            default -> throw invalid(source, lineNumber, "unknown entry type: " + type);
        };
    }

    private static List<String> textList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> values.add(item.asText()));
        return List.copyOf(values);
    }

    private static String requiredText(JsonNode node, String source, int lineNumber, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw invalid(source, lineNumber, "missing " + field);
        }
        return value.asText();
    }

    private static String nullableText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private static IllegalArgumentException invalid(String source, int lineNumber, String message) {
        return new IllegalArgumentException("Invalid JSONL session " + source + " line " + lineNumber + ": " + message);
    }
}
