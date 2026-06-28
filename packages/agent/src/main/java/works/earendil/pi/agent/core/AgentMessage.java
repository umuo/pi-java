package works.earendil.pi.agent.core;

import works.earendil.pi.ai.model.Message;

public sealed interface AgentMessage permits AgentMessage.Llm, AgentMessage.Custom {
    String role();

    record Llm(Message message) implements AgentMessage {
        @Override
        public String role() {
            return message.role();
        }
    }

    record Custom(String customType, Object content, boolean display, Object details) implements AgentMessage {
        @Override
        public String role() {
            return "custom";
        }
    }
}
