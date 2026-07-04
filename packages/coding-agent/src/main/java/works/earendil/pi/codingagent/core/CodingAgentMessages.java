package works.earendil.pi.codingagent.core;

import works.earendil.pi.agent.core.AgentMessage;
import works.earendil.pi.ai.model.Content;
import works.earendil.pi.ai.model.Message;
import works.earendil.pi.common.json.JsonCodec;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class CodingAgentMessages {
    public static final String COMPACTION_SUMMARY_PREFIX = """
            The conversation history before this point was compacted into the following summary:

            <summary>
            """;
    public static final String COMPACTION_SUMMARY_SUFFIX = "\n</summary>";
    public static final String BRANCH_SUMMARY_PREFIX = """
            The following is a summary of a branch that this conversation came back from:

            <summary>
            """;
    public static final String BRANCH_SUMMARY_SUFFIX = "</summary>";

    private CodingAgentMessages() {
    }

    public record BashExecutionMessage(
            String command,
            String output,
            Integer exitCode,
            boolean cancelled,
            boolean truncated,
            String fullOutputPath,
            Instant timestamp,
            boolean excludeFromContext
    ) {
    }

    public record CustomMessage(
            String customType,
            Object content,
            boolean display,
            Object details,
            Instant timestamp
    ) {
    }

    public record BranchSummaryMessage(String summary, String fromId, Instant timestamp) {
    }

    public record CompactionSummaryMessage(String summary, int tokensBefore, Instant timestamp) {
    }

    public static String bashExecutionToText(BashExecutionMessage message) {
        StringBuilder text = new StringBuilder();
        text.append("Ran `").append(message.command()).append("`\n");
        if (message.output() != null && !message.output().isEmpty()) {
            text.append("```\n").append(message.output()).append("\n```");
        } else {
            text.append("(no output)");
        }
        if (message.cancelled()) {
            text.append("\n\n(command cancelled)");
        } else if (message.exitCode() != null && message.exitCode() != 0) {
            text.append("\n\nCommand exited with code ").append(message.exitCode());
        }
        if (message.truncated() && message.fullOutputPath() != null) {
            text.append("\n\n[Output truncated. Full output: ").append(message.fullOutputPath()).append(']');
        }
        return text.toString();
    }

    public static BranchSummaryMessage createBranchSummaryMessage(String summary, String fromId, Instant timestamp) {
        return new BranchSummaryMessage(summary, fromId, timestamp);
    }

    public static CompactionSummaryMessage createCompactionSummaryMessage(String summary, int tokensBefore, Instant timestamp) {
        return new CompactionSummaryMessage(summary, tokensBefore, timestamp);
    }

    public static CustomMessage createCustomMessage(String customType, Object content, boolean display,
                                                    Object details, Instant timestamp) {
        return new CustomMessage(customType, content, display, details, timestamp);
    }

    public static List<Message> convertToLlm(List<AgentMessage> messages) {
        return convertToLlm(messages, false);
    }

    public static List<Message> convertToLlm(List<AgentMessage> messages, boolean blockImages) {
        List<Message> converted = new ArrayList<>();
        for (AgentMessage message : messages) {
            Message llm = convertOne(message);
            if (llm != null) {
                converted.add(blockImages ? withoutImages(llm) : llm);
            }
        }
        return List.copyOf(converted);
    }

    @SuppressWarnings("unchecked")
    private static Message convertOne(AgentMessage message) {
        if (message instanceof AgentMessage.Llm llm) {
            return llm.message();
        }
        if (!(message instanceof AgentMessage.Custom custom)) {
            return null;
        }
        Object content = custom.content();
        if (content instanceof BashExecutionMessage bash) {
            if (bash.excludeFromContext()) {
                return null;
            }
            return user(List.of(new Content.Text(bashExecutionToText(bash))), timestampOrNow(bash.timestamp()));
        }
        if (content instanceof CustomMessage customMessage) {
            return user(toContent(customMessage.content()), timestampOrNow(customMessage.timestamp()));
        }
        if (content instanceof BranchSummaryMessage branchSummary) {
            return user(List.of(new Content.Text(BRANCH_SUMMARY_PREFIX + branchSummary.summary() + BRANCH_SUMMARY_SUFFIX)),
                    timestampOrNow(branchSummary.timestamp()));
        }
        if (content instanceof CompactionSummaryMessage compactionSummary) {
            return user(List.of(new Content.Text(COMPACTION_SUMMARY_PREFIX + compactionSummary.summary() + COMPACTION_SUMMARY_SUFFIX)),
                    timestampOrNow(compactionSummary.timestamp()));
        }
        if ("branchSummary".equals(custom.customType())) {
            return user(List.of(new Content.Text(BRANCH_SUMMARY_PREFIX + String.valueOf(content) + BRANCH_SUMMARY_SUFFIX)),
                    Instant.now());
        }
        if ("compactionSummary".equals(custom.customType())) {
            return user(List.of(new Content.Text(COMPACTION_SUMMARY_PREFIX + String.valueOf(content) + COMPACTION_SUMMARY_SUFFIX)),
                    Instant.now());
        }
        return user(toContent(content), Instant.now());
    }

    private static Message.User user(List<Content> content, Instant timestamp) {
        return new Message.User(content, timestamp);
    }

    private static Instant timestampOrNow(Instant timestamp) {
        return timestamp == null ? Instant.now() : timestamp;
    }

    @SuppressWarnings("unchecked")
    private static List<Content> toContent(Object content) {
        if (content instanceof List<?> list) {
            boolean allContent = list.stream().allMatch(Content.class::isInstance);
            if (allContent) {
                return (List<Content>) list;
            }
        }
        if (content instanceof Content single) {
            return List.of(single);
        }
        if (content instanceof String text) {
            return List.of(new Content.Text(text));
        }
        return List.of(new Content.Text(JsonCodec.stringify(content)));
    }

    private static Message withoutImages(Message message) {
        if (message instanceof Message.User user) {
            return new Message.User(withoutImages(user.content()), user.timestamp());
        }
        if (message instanceof Message.Assistant assistant) {
            return new Message.Assistant(withoutImages(assistant.content()), assistant.provider(), assistant.model(),
                    assistant.stopReason(), assistant.usage(), assistant.errorMessage(), assistant.timestamp(),
                    assistant.responseId());
        }
        if (message instanceof Message.ToolResult result) {
            return new Message.ToolResult(result.toolCallId(), result.toolName(), withoutImages(result.content()),
                    result.error(), result.details(), result.timestamp());
        }
        return message;
    }

    private static List<Content> withoutImages(List<Content> content) {
        List<Content> filtered = new ArrayList<>();
        int omitted = 0;
        for (Content block : content) {
            if (block instanceof Content.Image) {
                omitted++;
            } else {
                filtered.add(block);
            }
        }
        if (omitted > 0) {
            filtered.add(new Content.Text("[Image content omitted because images.blockImages is enabled: "
                    + omitted + " image" + (omitted == 1 ? "" : "s") + "]"));
        }
        return List.copyOf(filtered);
    }
}
