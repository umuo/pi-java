package works.earendil.pi.codingagent.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import works.earendil.pi.agent.core.AgentContext;
import works.earendil.pi.agent.core.AgentEvent;
import works.earendil.pi.agent.core.AgentLoop;
import works.earendil.pi.agent.core.AgentMessage;
import works.earendil.pi.agent.core.AgentTool;
import works.earendil.pi.agent.session.SessionEntry;
import works.earendil.pi.ai.model.Content;
import works.earendil.pi.ai.model.Message;
import works.earendil.pi.ai.model.Model;
import works.earendil.pi.ai.model.ThinkingLevel;
import works.earendil.pi.ai.provider.StreamOptions;
import works.earendil.pi.codingagent.session.SessionManager;
import works.earendil.pi.common.json.JsonCodec;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public final class AgentSession {
    private final SessionManager sessionManager;
    private final ModelRegistry modelRegistry;
    private final List<ModelResolver.ScopedModel> scopedModels;
    private final List<AgentTool> tools;
    private final AgentLoop.StreamFunction streamFunction;
    private final List<AgentSessionEventListener> listeners = new CopyOnWriteArrayList<>();
    private final List<AgentMessage> messages = new ArrayList<>();
    private final String systemPrompt;
    private Model model;
    private ThinkingLevel thinkingLevel;
    private boolean disposed;

    public AgentSession(Config config) {
        this.sessionManager = Objects.requireNonNull(config.sessionManager());
        this.modelRegistry = Objects.requireNonNull(config.modelRegistry());
        this.model = config.model();
        this.thinkingLevel = config.thinkingLevel() == null ? Defaults.DEFAULT_THINKING_LEVEL : config.thinkingLevel();
        this.scopedModels = List.copyOf(config.scopedModels() == null ? List.of() : config.scopedModels());
        this.tools = List.copyOf(config.tools() == null ? List.of() : config.tools());
        this.streamFunction = config.streamFunction();
        this.systemPrompt = config.systemPrompt() == null ? "" : config.systemPrompt();
        restoreMessagesFromSession();
    }

    public record Config(
            SessionManager sessionManager,
            ModelRegistry modelRegistry,
            Model model,
            ThinkingLevel thinkingLevel,
            List<ModelResolver.ScopedModel> scopedModels,
            List<AgentTool> tools,
            String systemPrompt,
            AgentLoop.StreamFunction streamFunction) {
    }

    public sealed interface AgentSessionEvent permits
            AgentSessionEvent.QueueUpdate,
            AgentSessionEvent.ThinkingLevelChanged,
            AgentSessionEvent.SessionInfoChanged,
            AgentSessionEvent.AgentEventEnvelope,
            AgentSessionEvent.Disposed {
        String type();

        record QueueUpdate(List<String> steering, List<String> followUp) implements AgentSessionEvent {
            @Override
            public String type() {
                return "queue_update";
            }
        }

        record ThinkingLevelChanged(ThinkingLevel level) implements AgentSessionEvent {
            @Override
            public String type() {
                return "thinking_level_changed";
            }
        }

        record SessionInfoChanged(String name) implements AgentSessionEvent {
            @Override
            public String type() {
                return "session_info_changed";
            }
        }

        record AgentEventEnvelope(AgentEvent event) implements AgentSessionEvent {
            @Override
            public String type() {
                return event.type();
            }
        }

        record Disposed() implements AgentSessionEvent {
            @Override
            public String type() {
                return "disposed";
            }
        }
    }

    @FunctionalInterface
    public interface AgentSessionEventListener {
        void onEvent(AgentSessionEvent event);
    }

    public AutoCloseable subscribe(AgentSessionEventListener listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public List<AgentMessage> prompt(String text) throws Exception {
        ensureActive();
        if (model == null) {
            throw new IllegalStateException(AuthGuidance.formatNoModelSelectedMessage(java.nio.file.Path.of("docs")));
        }
        Message.User user = new Message.User(List.of(new Content.Text(text)), Instant.now());
        AgentMessage.Llm prompt = new AgentMessage.Llm(user);
        sessionManager.appendMessage(messageNode(user));
        List<AgentMessage> newMessages = AgentLoop.run(List.of(prompt),
                new AgentContext(List.copyOf(messages), systemPrompt, tools),
                new AgentLoop.Config(model, StreamOptions.defaults(), List::of, List::of,
                        AgentLoop.ToolExecutionMode.SEQUENTIAL, CodingAgentMessages::convertToLlm),
                streamFunction,
                this::handleAgentEvent);
        messages.addAll(newMessages);
        persistNewLlmMessages(newMessages.subList(1, newMessages.size()));
        return newMessages;
    }

    public void setModel(Model model) throws IOException {
        ensureActive();
        this.model = model;
        sessionManager.appendModelChange(model.provider(), model.modelId());
    }

    public Optional<ModelCycleResult> cycleModel() throws IOException {
        ensureActive();
        List<ModelResolver.ScopedModel> cycle = scopedModels.isEmpty()
                ? modelRegistry.getAvailable().stream().map(m -> new ModelResolver.ScopedModel(m, null)).toList()
                : scopedModels;
        if (cycle.isEmpty()) {
            return Optional.empty();
        }
        int currentIndex = -1;
        for (int i = 0; i < cycle.size(); i++) {
            if (model != null && modelsAreEqual(cycle.get(i).model(), model)) {
                currentIndex = i;
                break;
            }
        }
        ModelResolver.ScopedModel next = cycle.get((currentIndex + 1) % cycle.size());
        model = next.model();
        if (next.thinkingLevel() != null) {
            setThinkingLevel(next.thinkingLevel());
        }
        sessionManager.appendModelChange(model.provider(), model.modelId());
        return Optional.of(new ModelCycleResult(model, thinkingLevel, !scopedModels.isEmpty()));
    }

    public void setThinkingLevel(ThinkingLevel thinkingLevel) throws IOException {
        ensureActive();
        this.thinkingLevel = thinkingLevel;
        sessionManager.appendThinkingLevelChange(thinkingLevel.wireName());
        emit(new AgentSessionEvent.ThinkingLevelChanged(thinkingLevel));
    }

    public String setSessionName(String name) throws IOException {
        ensureActive();
        String id = sessionManager.appendSessionInfo(name);
        emit(new AgentSessionEvent.SessionInfoChanged(sessionManager.sessionName().orElse(null)));
        return id;
    }

    public SessionStats stats() {
        int userMessages = 0;
        int assistantMessages = 0;
        int toolResults = 0;
        for (AgentMessage message : messages) {
            if (message instanceof AgentMessage.Llm llm) {
                if (llm.message() instanceof Message.User) {
                    userMessages++;
                } else if (llm.message() instanceof Message.Assistant) {
                    assistantMessages++;
                } else if (llm.message() instanceof Message.ToolResult) {
                    toolResults++;
                }
            }
        }
        return new SessionStats(sessionManager.sessionFile().orElse(null), sessionManager.sessionId(),
                userMessages, assistantMessages, toolResults, messages.size());
    }

    public void dispose() {
        if (!disposed) {
            disposed = true;
            emit(new AgentSessionEvent.Disposed());
        }
    }

    public boolean disposed() {
        return disposed;
    }

    public SessionManager sessionManager() {
        return sessionManager;
    }

    public Optional<java.nio.file.Path> sessionFile() {
        return sessionManager.sessionFile();
    }

    public Model model() {
        return model;
    }

    public ThinkingLevel thinkingLevel() {
        return thinkingLevel;
    }

    public List<AgentTool> tools() {
        return tools;
    }

    public List<AgentMessage> messages() {
        return List.copyOf(messages);
    }

    public record ModelCycleResult(Model model, ThinkingLevel thinkingLevel, boolean scoped) {
    }

    public record SessionStats(java.nio.file.Path sessionFile, String sessionId, int userMessages,
                               int assistantMessages, int toolResults, int totalMessages) {
    }

    private void handleAgentEvent(AgentEvent event) {
        emit(new AgentSessionEvent.AgentEventEnvelope(event));
    }

    private void persistNewLlmMessages(List<AgentMessage> newMessages) throws IOException {
        for (AgentMessage message : newMessages) {
            if (message instanceof AgentMessage.Llm llm) {
                sessionManager.appendMessage(messageNode(llm.message()));
            }
        }
    }

    private void restoreMessagesFromSession() {
        for (SessionEntry entry : sessionManager.branch()) {
            if (entry instanceof SessionEntry.MessageEntry messageEntry) {
                messageFromJson(messageEntry.message()).ifPresent(message -> messages.add(new AgentMessage.Llm(message)));
            }
        }
    }

    private Optional<Message> messageFromJson(JsonNode node) {
        String role = node.path("role").asText();
        if ("user".equals(role)) {
            return Optional.of(new Message.User(readContent(node), readTimestamp(node)));
        }
        if ("assistant".equals(role)) {
            return Optional.of(new Message.Assistant(readContent(node),
                    node.path("provider").asText(null),
                    node.path("model").asText(null),
                    works.earendil.pi.ai.model.StopReason.STOP,
                    null,
                    null,
                    readTimestamp(node)));
        }
        return Optional.empty();
    }

    private JsonNode messageNode(Message message) {
        ObjectNode node = JsonCodec.mapper().createObjectNode();
        node.put("timestamp", Instant.now().toString());
        if (message instanceof Message.User user) {
            node.put("role", "user");
            node.set("content", JsonCodec.mapper().valueToTree(user.content()));
        } else if (message instanceof Message.Assistant assistant) {
            node.put("role", "assistant");
            node.set("content", JsonCodec.mapper().valueToTree(assistant.content()));
            if (assistant.provider() != null) {
                node.put("provider", assistant.provider());
            }
            if (assistant.model() != null) {
                node.put("model", assistant.model());
            }
            if (assistant.stopReason() != null) {
                node.put("stopReason", assistant.stopReason().name().toLowerCase(java.util.Locale.ROOT));
            }
            if (assistant.usage() != null) {
                node.set("usage", JsonCodec.mapper().valueToTree(assistant.usage()));
            }
        } else if (message instanceof Message.ToolResult toolResult) {
            node.put("role", "toolResult");
            node.put("toolCallId", toolResult.toolCallId());
            node.put("toolName", toolResult.toolName());
            node.set("content", JsonCodec.mapper().valueToTree(toolResult.content()));
            node.put("error", toolResult.error());
            if (toolResult.details() != null) {
                node.set("details", JsonCodec.mapper().valueToTree(toolResult.details()));
            }
        } else {
            node.put("role", "custom");
            node.set("content", JsonCodec.mapper().valueToTree(message));
        }
        return node;
    }

    private List<Content> readContent(JsonNode node) {
        JsonNode content = node.get("content");
        if (content == null || content.isNull()) {
            return List.of();
        }
        if (content.isTextual()) {
            return List.of(new Content.Text(content.asText()));
        }
        if (content.isArray()) {
            List<Content> values = new ArrayList<>();
            for (JsonNode item : content) {
                if (item.isTextual()) {
                    values.add(new Content.Text(item.asText()));
                } else if ("text".equals(item.path("type").asText())) {
                    values.add(new Content.Text(item.path("text").asText()));
                }
            }
            return List.copyOf(values);
        }
        return List.of(new Content.Text(content.toString()));
    }

    private Instant readTimestamp(JsonNode node) {
        try {
            return node.hasNonNull("timestamp") ? Instant.parse(node.path("timestamp").asText()) : Instant.now();
        } catch (RuntimeException e) {
            return Instant.now();
        }
    }

    private void emit(AgentSessionEvent event) {
        for (AgentSessionEventListener listener : listeners) {
            listener.onEvent(event);
        }
    }

    private void ensureActive() {
        if (disposed) {
            throw new IllegalStateException("AgentSession has been disposed");
        }
    }

    private static boolean modelsAreEqual(Model a, Model b) {
        return a.provider().equals(b.provider()) && a.modelId().equals(b.modelId());
    }
}
