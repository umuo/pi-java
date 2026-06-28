package works.earendil.pi.agent.core;

import com.fasterxml.jackson.databind.JsonNode;
import works.earendil.pi.ai.model.Content;
import works.earendil.pi.ai.model.Context;
import works.earendil.pi.ai.model.Message;
import works.earendil.pi.ai.model.Model;
import works.earendil.pi.ai.model.StopReason;
import works.earendil.pi.ai.provider.StreamOptions;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class AgentLoop {
    private AgentLoop() {
    }

    @FunctionalInterface
    public interface StreamFunction {
        Message.Assistant stream(Model model, Context context, StreamOptions options) throws Exception;
    }

    public record Config(
            Model model,
            StreamOptions streamOptions,
            Supplier<List<AgentMessage>> steeringMessages,
            Supplier<List<AgentMessage>> followUpMessages,
            ToolExecutionMode toolExecutionMode,
            Function<List<AgentMessage>, List<Message>> transformToLlm
    ) {
        public Config(Model model, StreamOptions streamOptions, Supplier<List<AgentMessage>> steeringMessages,
                      Supplier<List<AgentMessage>> followUpMessages, ToolExecutionMode toolExecutionMode) {
            this(model, streamOptions, steeringMessages, followUpMessages, toolExecutionMode, null);
        }

        public Config {
            if (streamOptions == null) {
                streamOptions = StreamOptions.defaults();
            }
            if (steeringMessages == null) {
                steeringMessages = List::of;
            }
            if (followUpMessages == null) {
                followUpMessages = List::of;
            }
            if (toolExecutionMode == null) {
                toolExecutionMode = ToolExecutionMode.PARALLEL;
            }
            if (transformToLlm == null) {
                transformToLlm = contextMessages -> contextMessages.stream()
                        .filter(AgentMessage.Llm.class::isInstance)
                        .map(AgentMessage.Llm.class::cast)
                        .map(AgentMessage.Llm::message)
                        .toList();
            }
        }
    }

    public enum ToolExecutionMode {
        SEQUENTIAL,
        PARALLEL
    }

    public static List<AgentMessage> run(List<AgentMessage> prompts, AgentContext context, Config config,
                                         StreamFunction streamFunction, Consumer<AgentEvent> emit) throws Exception {
        List<AgentMessage> newMessages = new ArrayList<>(prompts);
        AgentContext current = context;
        emit.accept(new AgentEvent.AgentStart());
        emit.accept(new AgentEvent.TurnStart());
        for (AgentMessage prompt : prompts) {
            emit.accept(new AgentEvent.MessageStart(prompt));
            emit.accept(new AgentEvent.MessageEnd(prompt));
            current = current.append(prompt);
        }
        runLoop(current, newMessages, config, streamFunction, emit);
        return List.copyOf(newMessages);
    }

    public static List<AgentMessage> continueRun(AgentContext context, Config config, StreamFunction streamFunction,
                                                 Consumer<AgentEvent> emit) throws Exception {
        if (context.messages().isEmpty()) {
            throw new IllegalArgumentException("Cannot continue: no messages in context");
        }
        if (context.messages().getLast() instanceof AgentMessage.Llm llm
                && llm.message() instanceof Message.Assistant) {
            throw new IllegalArgumentException("Cannot continue from message role: assistant");
        }
        List<AgentMessage> newMessages = new ArrayList<>();
        emit.accept(new AgentEvent.AgentStart());
        emit.accept(new AgentEvent.TurnStart());
        runLoop(context, newMessages, config, streamFunction, emit);
        return List.copyOf(newMessages);
    }

    private static void runLoop(AgentContext initialContext, List<AgentMessage> newMessages, Config config,
                                StreamFunction streamFunction, Consumer<AgentEvent> emit) throws Exception {
        AgentContext current = initialContext;
        boolean firstTurn = true;
        List<AgentMessage> pending = new ArrayList<>(config.steeringMessages().get());

        while (true) {
            boolean hasMoreToolCalls = true;
            while (hasMoreToolCalls || !pending.isEmpty()) {
                if (!firstTurn) {
                    emit.accept(new AgentEvent.TurnStart());
                }
                firstTurn = false;

                for (AgentMessage message : pending) {
                    emit.accept(new AgentEvent.MessageStart(message));
                    emit.accept(new AgentEvent.MessageEnd(message));
                    current = current.append(message);
                    newMessages.add(message);
                }
                pending = new ArrayList<>();

                Message.Assistant assistant = streamFunction.stream(config.model(),
                        current.toLlmContext(config.transformToLlm()), config.streamOptions());
                AgentMessage assistantMessage = new AgentMessage.Llm(assistant);
                current = current.append(assistantMessage);
                newMessages.add(assistantMessage);
                emit.accept(new AgentEvent.MessageStart(assistantMessage));
                emit.accept(new AgentEvent.MessageEnd(assistantMessage));

                if (assistant.stopReason() == StopReason.ERROR || assistant.stopReason() == StopReason.ABORTED) {
                    emit.accept(new AgentEvent.TurnEnd(assistantMessage, List.of()));
                    emit.accept(new AgentEvent.AgentEnd(List.copyOf(newMessages)));
                    return;
                }

                List<Message.ToolResult> toolResults = executeToolCalls(current, assistant, config, emit);
                for (Message.ToolResult toolResult : toolResults) {
                    AgentMessage resultMessage = new AgentMessage.Llm(toolResult);
                    current = current.append(resultMessage);
                    newMessages.add(resultMessage);
                }
                hasMoreToolCalls = !toolResults.isEmpty();
                emit.accept(new AgentEvent.TurnEnd(assistantMessage, toolResults));

                pending = new ArrayList<>(config.steeringMessages().get());
            }

            List<AgentMessage> followUps = config.followUpMessages().get();
            if (!followUps.isEmpty()) {
                pending = new ArrayList<>(followUps);
                continue;
            }
            break;
        }
        emit.accept(new AgentEvent.AgentEnd(List.copyOf(newMessages)));
    }

    private static List<Message.ToolResult> executeToolCalls(AgentContext context, Message.Assistant assistant,
                                                             Config config, Consumer<AgentEvent> emit) {
        List<Content.ToolCall> toolCalls = assistant.content().stream()
                .filter(Content.ToolCall.class::isInstance)
                .map(Content.ToolCall.class::cast)
                .toList();
        if (toolCalls.isEmpty()) {
            return List.of();
        }
        Map<String, AgentTool> tools = new LinkedHashMap<>();
        context.tools().forEach(tool -> tools.put(tool.name(), tool));
        List<Message.ToolResult> results = new ArrayList<>();
        for (Content.ToolCall toolCall : toolCalls) {
            emit.accept(new AgentEvent.ToolExecutionStart(toolCall.id(), toolCall.name(), toolCall.input()));
            AgentTool.AgentToolResult result;
            boolean error = false;
            try {
                AgentTool tool = Optional.ofNullable(tools.get(toolCall.name()))
                        .orElseThrow(() -> new IllegalArgumentException("Tool " + toolCall.name() + " not found"));
                result = tool.execute(toJavaValue(toolCall.input()));
                error = result.error();
            } catch (Exception e) {
                result = new AgentTool.AgentToolResult(List.of(new Content.Text(e.getMessage())), null, true, false);
                error = true;
            }
            emit.accept(new AgentEvent.ToolExecutionEnd(toolCall.id(), toolCall.name(), result, error));
            Message.ToolResult toolResult = new Message.ToolResult(toolCall.id(), toolCall.name(), result.content(),
                    error, result.details(), Instant.now());
            AgentMessage resultMessage = new AgentMessage.Llm(toolResult);
            emit.accept(new AgentEvent.MessageStart(resultMessage));
            emit.accept(new AgentEvent.MessageEnd(resultMessage));
            results.add(toolResult);
            if (result.terminate()) {
                break;
            }
            if (config.toolExecutionMode() == ToolExecutionMode.SEQUENTIAL) {
                continue;
            }
        }
        return List.copyOf(results);
    }

    private static Object toJavaValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry -> map.put(entry.getKey(), toJavaValue(entry.getValue())));
            return map;
        }
        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            node.forEach(item -> list.add(toJavaValue(item)));
            return list;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        return node.toString();
    }
}
