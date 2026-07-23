package works.earendil.pi.agent.core;

import works.earendil.pi.ai.model.Message;
import works.earendil.pi.ai.stream.AssistantMessageEvent;

import java.util.List;

public sealed interface AgentEvent permits AgentEvent.AgentStart, AgentEvent.AgentEnd, AgentEvent.AgentSettled, AgentEvent.TurnStart,
        AgentEvent.TurnEnd, AgentEvent.MessageStart, AgentEvent.MessageUpdate, AgentEvent.MessageEnd,
        AgentEvent.ToolExecutionStart, AgentEvent.ToolExecutionUpdate, AgentEvent.ToolExecutionEnd {
    String type();

    record AgentStart() implements AgentEvent {
        @Override
        public String type() {
            return "agent_start";
        }
    }

    record AgentEnd(List<AgentMessage> messages) implements AgentEvent {
        @Override
        public String type() {
            return "agent_end";
        }
    }

    record AgentSettled(List<AgentMessage> messages) implements AgentEvent {
        @Override
        public String type() {
            return "agent_settled";
        }
    }

    record TurnStart() implements AgentEvent {
        @Override
        public String type() {
            return "turn_start";
        }
    }

    record TurnEnd(AgentMessage message, List<Message.ToolResult> toolResults) implements AgentEvent {
        @Override
        public String type() {
            return "turn_end";
        }
    }

    record MessageStart(AgentMessage message) implements AgentEvent {
        @Override
        public String type() {
            return "message_start";
        }
    }

    record MessageUpdate(AgentMessage message, AssistantMessageEvent assistantMessageEvent) implements AgentEvent {
        @Override
        public String type() {
            return "message_update";
        }
    }

    record MessageEnd(AgentMessage message) implements AgentEvent {
        @Override
        public String type() {
            return "message_end";
        }
    }

    record ToolExecutionStart(String toolCallId, String toolName, Object args) implements AgentEvent {
        @Override
        public String type() {
            return "tool_execution_start";
        }
    }

    record ToolExecutionUpdate(String toolCallId, String toolName, Object args, AgentTool.AgentToolResult partialResult)
            implements AgentEvent {
        @Override
        public String type() {
            return "tool_execution_update";
        }
    }

    record ToolExecutionEnd(String toolCallId, String toolName, AgentTool.AgentToolResult result, boolean error)
            implements AgentEvent {
        @Override
        public String type() {
            return "tool_execution_end";
        }
    }
}
