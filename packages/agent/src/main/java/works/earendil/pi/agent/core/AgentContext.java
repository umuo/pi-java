package works.earendil.pi.agent.core;

import works.earendil.pi.ai.model.Context;
import works.earendil.pi.ai.model.Message;

import java.util.List;
import java.util.function.Function;

public record AgentContext(List<AgentMessage> messages, String systemPrompt, List<AgentTool> tools) {
    public Context toLlmContext() {
        return toLlmContext(AgentContext::defaultTransformToLlm);
    }

    public Context toLlmContext(Function<List<AgentMessage>, List<Message>> transformToLlm) {
        List<Message> llmMessages = transformToLlm.apply(messages);
        return new Context(llmMessages, systemPrompt, tools.stream().map(AgentTool::definition).toList(),
                works.earendil.pi.ai.model.ThinkingLevel.OFF);
    }

    private static List<Message> defaultTransformToLlm(List<AgentMessage> messages) {
        return messages.stream()
                .filter(AgentMessage.Llm.class::isInstance)
                .map(AgentMessage.Llm.class::cast)
                .map(AgentMessage.Llm::message)
                .toList();
    }

    public AgentContext append(AgentMessage message) {
        java.util.ArrayList<AgentMessage> next = new java.util.ArrayList<>(messages);
        next.add(message);
        return new AgentContext(List.copyOf(next), systemPrompt, tools);
    }
}
