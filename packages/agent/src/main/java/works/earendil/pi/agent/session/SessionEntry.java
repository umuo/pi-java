package works.earendil.pi.agent.session;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;

public sealed interface SessionEntry permits
        SessionEntry.MessageEntry,
        SessionEntry.ThinkingLevelChangeEntry,
        SessionEntry.ModelChangeEntry,
        SessionEntry.ActiveToolsChangeEntry,
        SessionEntry.CompactionEntry,
        SessionEntry.BranchSummaryEntry,
        SessionEntry.CustomEntry,
        SessionEntry.CustomMessageEntry,
        SessionEntry.LabelEntry,
        SessionEntry.SessionInfoEntry,
        SessionEntry.LeafEntry {
    String type();

    String id();

    String parentId();

    Instant timestamp();

    record MessageEntry(String id, String parentId, Instant timestamp, JsonNode message) implements SessionEntry {
        @Override
        public String type() {
            return "message";
        }
    }

    record ThinkingLevelChangeEntry(String id, String parentId, Instant timestamp, String thinkingLevel) implements SessionEntry {
        @Override
        public String type() {
            return "thinking_level_change";
        }
    }

    record ModelChangeEntry(String id, String parentId, Instant timestamp, String provider, String modelId) implements SessionEntry {
        @Override
        public String type() {
            return "model_change";
        }
    }

    record ActiveToolsChangeEntry(String id, String parentId, Instant timestamp, List<String> activeToolNames) implements SessionEntry {
        @Override
        public String type() {
            return "active_tools_change";
        }
    }

    record CompactionEntry(String id, String parentId, Instant timestamp, String summary, String firstKeptEntryId,
                           int tokensBefore, JsonNode details, boolean fromHook) implements SessionEntry {
        @Override
        public String type() {
            return "compaction";
        }
    }

    record BranchSummaryEntry(String id, String parentId, Instant timestamp, String summary, String fromId,
                              JsonNode details, boolean fromHook) implements SessionEntry {
        @Override
        public String type() {
            return "branch_summary";
        }
    }

    record CustomEntry(String id, String parentId, Instant timestamp, String customType, JsonNode data) implements SessionEntry {
        @Override
        public String type() {
            return "custom";
        }
    }

    record CustomMessageEntry(String id, String parentId, Instant timestamp, String customType, JsonNode content,
                              boolean display, JsonNode details, String source) implements SessionEntry {
        public CustomMessageEntry(String id, String parentId, Instant timestamp, String customType, JsonNode content,
                                  boolean display, JsonNode details) {
            this(id, parentId, timestamp, customType, content, display, details, null);
        }

        public CustomMessageEntry {
            source = source == null || source.isBlank() ? null : source.trim();
        }

        @Override
        public String type() {
            return "custom_message";
        }
    }

    record LabelEntry(String id, String parentId, Instant timestamp, String targetId, String label) implements SessionEntry {
        @Override
        public String type() {
            return "label";
        }
    }

    record SessionInfoEntry(String id, String parentId, Instant timestamp, String name) implements SessionEntry {
        @Override
        public String type() {
            return "session_info";
        }
    }

    record LeafEntry(String id, String parentId, Instant timestamp, String targetId) implements SessionEntry {
        @Override
        public String type() {
            return "leaf";
        }
    }
}
