package works.earendil.pi.ai.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public sealed interface Content permits Content.Text, Content.Thinking, Content.Image, Content.ToolCall {
    String type();

    record Text(String text) implements Content {
        @Override
        public String type() {
            return "text";
        }
    }

    record Thinking(String text, String signature) implements Content {
        @Override
        public String type() {
            return "thinking";
        }
    }

    record Image(String mimeType, String data, String url) implements Content {
        @Override
        public String type() {
            return "image";
        }
    }

    record ToolCall(String id, String name, JsonNode input, List<Content> displayContent) implements Content {
        @Override
        public String type() {
            return "toolCall";
        }
    }
}
