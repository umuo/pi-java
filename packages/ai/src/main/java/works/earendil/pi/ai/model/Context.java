package works.earendil.pi.ai.model;

import java.util.List;

public record Context(
        List<Message> messages,
        String systemPrompt,
        List<Tool> tools,
        ThinkingLevel thinkingLevel
) {
    public Context append(Message message) {
        java.util.ArrayList<Message> next = new java.util.ArrayList<>(messages);
        next.add(message);
        return new Context(List.copyOf(next), systemPrompt, tools, thinkingLevel);
    }
}
