package works.earendil.pi.ai.stream;

import works.earendil.pi.ai.model.Content;
import works.earendil.pi.ai.model.Message;
import works.earendil.pi.ai.model.Usage;

public sealed interface AssistantMessageEvent permits
        AssistantMessageEvent.Start,
        AssistantMessageEvent.ContentDelta,
        AssistantMessageEvent.UsageDelta,
        AssistantMessageEvent.End,
        AssistantMessageEvent.Error {
    String type();

    record Start(String provider, String model) implements AssistantMessageEvent {
        @Override
        public String type() {
            return "message_start";
        }
    }

    record ContentDelta(Content content) implements AssistantMessageEvent {
        @Override
        public String type() {
            return "content_delta";
        }
    }

    record UsageDelta(Usage usage) implements AssistantMessageEvent {
        @Override
        public String type() {
            return "usage_delta";
        }
    }

    record End(Message.Assistant message) implements AssistantMessageEvent {
        @Override
        public String type() {
            return "message_end";
        }
    }

    record Error(String message, Throwable cause) implements AssistantMessageEvent {
        @Override
        public String type() {
            return "error";
        }
    }
}
