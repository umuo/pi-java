package works.earendil.pi.ai.model;

import java.time.Instant;
import java.util.List;

public sealed interface Message permits Message.User, Message.Assistant, Message.ToolResult {
    String role();

    Instant timestamp();

    record User(List<Content> content, Instant timestamp) implements Message {
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
            Instant timestamp
    ) implements Message {
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
