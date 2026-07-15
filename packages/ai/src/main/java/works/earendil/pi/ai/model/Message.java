package works.earendil.pi.ai.model;

import java.time.Instant;
import java.util.List;

public sealed interface Message permits Message.User, Message.Assistant, Message.ToolResult {
    String role();

    Instant timestamp();

    record User(List<Content> content, Instant timestamp, String source) implements Message {
        public User(List<Content> content, Instant timestamp) {
            this(content, timestamp, null);
        }

        public User {
            source = source == null || source.isBlank() ? null : source.trim();
        }

        @Override
        public String role() {
            return "user";
        }
    }

    record Assistant(
            List<Content> content,
            String provider,
            String model,
            StopReason stopReason,
            Usage usage,
            String errorMessage,
            Instant timestamp,
            String responseId
    ) implements Message {
        public Assistant(List<Content> content, String provider, String model, StopReason stopReason, Usage usage, String errorMessage, Instant timestamp) {
            this(content, provider, model, stopReason, usage, errorMessage, timestamp, null);
        }

        @Override
        public String role() {
            return "assistant";
        }
    }

    record ToolResult(
            String toolCallId,
            String toolName,
            List<Content> content,
            boolean error,
            Object details,
            Instant timestamp
    ) implements Message {
        @Override
        public String role() {
            return "tool";
        }
    }
}
