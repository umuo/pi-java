package works.earendil.pi.codingagent.core;

import com.fasterxml.jackson.databind.JsonNode;
import works.earendil.pi.agent.core.AgentMessage;
import works.earendil.pi.agent.session.SessionEntry;
import works.earendil.pi.ai.model.Content;
import works.earendil.pi.ai.model.Message;
import works.earendil.pi.ai.model.StopReason;
import works.earendil.pi.ai.model.Usage;
import works.earendil.pi.common.json.JsonCodec;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class CompactionSupport {
    public static final String SUMMARIZATION_SYSTEM_PROMPT = """
            You are a context summarization assistant. Your task is to read a conversation between a user and an AI assistant, then produce a structured summary following the exact format specified.

            Do NOT continue the conversation. Do NOT respond to any questions in the conversation. ONLY output the structured summary.""";
    public static final Settings DEFAULT_SETTINGS = new Settings(true, 16_384, 20_000);
    private static final int ESTIMATED_IMAGE_CHARS = 4_800;
    private static final int TOOL_RESULT_MAX_CHARS = 2_000;

    private CompactionSupport() {
    }

    public record Settings(boolean enabled, int reserveTokens, int keepRecentTokens) {
    }

    public record FileOperations(Set<String> read, Set<String> written, Set<String> edited) {
    }

    public record FileLists(List<String> readFiles, List<String> modifiedFiles) {
    }

    public record ContextUsageEstimate(int tokens, int usageTokens, int trailingTokens, Integer lastUsageIndex) {
    }

    public record CutPointResult(int firstKeptEntryIndex, int turnStartIndex, boolean splitTurn) {
    }

    public record CompactionPreparation(
            String firstKeptEntryId,
            List<AgentMessage> messagesToSummarize,
            List<AgentMessage> turnPrefixMessages,
            boolean splitTurn,
            int tokensBefore,
            String previousSummary,
            FileOperations fileOperations,
            Settings settings
    ) {
    }

    public static FileOperations createFileOps() {
        return new FileOperations(new LinkedHashSet<>(), new LinkedHashSet<>(), new LinkedHashSet<>());
    }

    public static void extractFileOpsFromMessage(AgentMessage message, FileOperations fileOps) {
        if (!(message instanceof AgentMessage.Llm llm) || !(llm.message() instanceof Message.Assistant assistant)) {
            return;
        }
        for (Content content : assistant.content()) {
            if (!(content instanceof Content.ToolCall toolCall) || toolCall.input() == null || !toolCall.input().isObject()) {
                continue;
            }
            JsonNode path = toolCall.input().get("path");
            if (path == null || !path.isTextual()) {
                continue;
            }
            switch (toolCall.name()) {
                case "read" -> fileOps.read().add(path.asText());
                case "write" -> fileOps.written().add(path.asText());
                case "edit" -> fileOps.edited().add(path.asText());
                default -> {
                }
            }
        }
    }

    public static FileLists computeFileLists(FileOperations fileOps) {
        Set<String> modified = new LinkedHashSet<>();
        modified.addAll(fileOps.edited());
        modified.addAll(fileOps.written());
        List<String> readOnly = fileOps.read().stream()
                .filter(file -> !modified.contains(file))
                .sorted(Comparator.naturalOrder())
                .toList();
        List<String> modifiedFiles = modified.stream().sorted(Comparator.naturalOrder()).toList();
        return new FileLists(readOnly, modifiedFiles);
    }

    public static String formatFileOperations(List<String> readFiles, List<String> modifiedFiles) {
        List<String> sections = new ArrayList<>();
        if (!readFiles.isEmpty()) {
            sections.add("<read-files>\n" + String.join("\n", readFiles) + "\n</read-files>");
        }
        if (!modifiedFiles.isEmpty()) {
            sections.add("<modified-files>\n" + String.join("\n", modifiedFiles) + "\n</modified-files>");
        }
        return sections.isEmpty() ? "" : "\n\n" + String.join("\n\n", sections);
    }

    public static String serializeConversation(List<Message> messages) {
        List<String> parts = new ArrayList<>();
        for (Message message : messages) {
            if (message instanceof Message.User user) {
                String content = textOnly(user.content());
                if (!content.isEmpty()) {
                    parts.add("[User]: " + content);
                }
            } else if (message instanceof Message.Assistant assistant) {
                List<String> textParts = new ArrayList<>();
                List<String> thinkingParts = new ArrayList<>();
                List<String> toolCalls = new ArrayList<>();
                for (Content block : assistant.content()) {
                    if (block instanceof Content.Text text) {
                        textParts.add(text.text());
                    } else if (block instanceof Content.Thinking thinking) {
                        thinkingParts.add(thinking.text());
                    } else if (block instanceof Content.ToolCall toolCall) {
                        toolCalls.add(toolCall.name() + "(" + argsString(toolCall.input()) + ")");
                    }
                }
                if (!thinkingParts.isEmpty()) {
                    parts.add("[Assistant thinking]: " + String.join("\n", thinkingParts));
                }
                if (!textParts.isEmpty()) {
                    parts.add("[Assistant]: " + String.join("\n", textParts));
                }
                if (!toolCalls.isEmpty()) {
                    parts.add("[Assistant tool calls]: " + String.join("; ", toolCalls));
                }
            } else if (message instanceof Message.ToolResult result) {
                String content = textOnly(result.content());
                if (!content.isEmpty()) {
                    parts.add("[Tool result]: " + truncateForSummary(content, TOOL_RESULT_MAX_CHARS));
                }
            }
        }
        return String.join("\n\n", parts);
    }

    public static int calculateContextTokens(Usage usage) {
        return usage.totalTokens();
    }

    public static ContextUsageEstimate estimateContextTokens(List<AgentMessage> messages) {
        UsageInfo usageInfo = lastAssistantUsage(messages);
        if (usageInfo == null) {
            int estimated = messages.stream().mapToInt(CompactionSupport::estimateTokens).sum();
            return new ContextUsageEstimate(estimated, 0, estimated, null);
        }
        int usageTokens = calculateContextTokens(usageInfo.usage());
        int trailingTokens = 0;
        for (int i = usageInfo.index() + 1; i < messages.size(); i++) {
            trailingTokens += estimateTokens(messages.get(i));
        }
        return new ContextUsageEstimate(usageTokens + trailingTokens, usageTokens, trailingTokens, usageInfo.index());
    }

    public static boolean shouldCompact(int contextTokens, int contextWindow, Settings settings) {
        return settings.enabled() && contextTokens > contextWindow - settings.reserveTokens();
    }

    public static int estimateTokens(AgentMessage message) {
        if (message instanceof AgentMessage.Llm llm) {
            return estimateTokens(llm.message());
        }
        Object content = message instanceof AgentMessage.Custom custom ? custom.content() : "";
        if (content instanceof CodingAgentMessages.BashExecutionMessage bash) {
            return ceilTokens(safeLength(bash.command()) + safeLength(bash.output()));
        }
        if (content instanceof CodingAgentMessages.BranchSummaryMessage branch) {
            return ceilTokens(safeLength(branch.summary()));
        }
        if (content instanceof CodingAgentMessages.CompactionSummaryMessage compaction) {
            return ceilTokens(safeLength(compaction.summary()));
        }
        if (content instanceof CodingAgentMessages.CustomMessage customMessage) {
            return ceilTokens(String.valueOf(customMessage.content()).length());
        }
        return ceilTokens(String.valueOf(content).length());
    }

    public static int estimateTokens(Message message) {
        int chars = 0;
        if (message instanceof Message.User user) {
            chars = estimateContentChars(user.content());
        } else if (message instanceof Message.Assistant assistant) {
            chars = estimateContentChars(assistant.content());
        } else if (message instanceof Message.ToolResult result) {
            chars = estimateContentChars(result.content());
        }
        return ceilTokens(chars);
    }

    public static CutPointResult findCutPoint(List<SessionEntry> entries, int startIndex, int endIndex, int keepRecentTokens) {
        List<Integer> cutPoints = findValidCutPoints(entries, startIndex, endIndex);
        if (cutPoints.isEmpty()) {
            return new CutPointResult(startIndex, -1, false);
        }
        int accumulatedTokens = 0;
        int cutIndex = cutPoints.getFirst();
        for (int i = endIndex - 1; i >= startIndex; i--) {
            SessionEntry entry = entries.get(i);
            if (!(entry instanceof SessionEntry.MessageEntry messageEntry)) {
                continue;
            }
            accumulatedTokens += estimateTokens(messageFromJson(messageEntry.message()));
            if (accumulatedTokens >= keepRecentTokens) {
                for (Integer cutPoint : cutPoints) {
                    if (cutPoint >= i) {
                        cutIndex = cutPoint;
                        break;
                    }
                }
                break;
            }
        }
        while (cutIndex > startIndex) {
            SessionEntry previous = entries.get(cutIndex - 1);
            if (previous instanceof SessionEntry.CompactionEntry || previous instanceof SessionEntry.MessageEntry) {
                break;
            }
            cutIndex--;
        }
        SessionEntry cutEntry = entries.get(cutIndex);
        boolean userMessage = cutEntry instanceof SessionEntry.MessageEntry messageEntry
                && "user".equals(messageEntry.message().path("role").asText());
        int turnStartIndex = userMessage ? -1 : findTurnStartIndex(entries, cutIndex, startIndex);
        return new CutPointResult(cutIndex, turnStartIndex, !userMessage && turnStartIndex != -1);
    }

    public static int findTurnStartIndex(List<SessionEntry> entries, int entryIndex, int startIndex) {
        for (int i = entryIndex; i >= startIndex; i--) {
            SessionEntry entry = entries.get(i);
            if (entry instanceof SessionEntry.BranchSummaryEntry || entry instanceof SessionEntry.CustomMessageEntry) {
                return i;
            }
            if (entry instanceof SessionEntry.MessageEntry messageEntry) {
                String role = messageEntry.message().path("role").asText();
                if ("user".equals(role) || "bashExecution".equals(role)) {
                    return i;
                }
            }
        }
        return -1;
    }

    public static CompactionPreparation prepareCompaction(List<SessionEntry> pathEntries, Settings settings) {
        if (!pathEntries.isEmpty() && pathEntries.getLast() instanceof SessionEntry.CompactionEntry) {
            return null;
        }
        int prevCompactionIndex = -1;
        for (int i = pathEntries.size() - 1; i >= 0; i--) {
            if (pathEntries.get(i) instanceof SessionEntry.CompactionEntry) {
                prevCompactionIndex = i;
                break;
            }
        }
        String previousSummary = null;
        int boundaryStart = 0;
        if (prevCompactionIndex >= 0) {
            SessionEntry.CompactionEntry prevCompaction = (SessionEntry.CompactionEntry) pathEntries.get(prevCompactionIndex);
            previousSummary = prevCompaction.summary();
            int firstKeptIndex = indexOfEntry(pathEntries, prevCompaction.firstKeptEntryId());
            boundaryStart = firstKeptIndex >= 0 ? firstKeptIndex : prevCompactionIndex + 1;
        }
        int boundaryEnd = pathEntries.size();
        List<AgentMessage> contextMessages = buildSessionContext(pathEntries);
        int tokensBefore = estimateContextTokens(contextMessages).tokens();
        CutPointResult cutPoint = findCutPoint(pathEntries, boundaryStart, boundaryEnd, settings.keepRecentTokens());
        if (cutPoint.firstKeptEntryIndex() < 0 || cutPoint.firstKeptEntryIndex() >= pathEntries.size()) {
            return null;
        }
        String firstKeptEntryId = pathEntries.get(cutPoint.firstKeptEntryIndex()).id();
        int historyEnd = cutPoint.splitTurn() ? cutPoint.turnStartIndex() : cutPoint.firstKeptEntryIndex();

        List<AgentMessage> messagesToSummarize = new ArrayList<>();
        for (int i = boundaryStart; i < historyEnd; i++) {
            AgentMessage message = getMessageFromEntryForCompaction(pathEntries.get(i));
            if (message != null) {
                messagesToSummarize.add(message);
            }
        }

        List<AgentMessage> turnPrefixMessages = new ArrayList<>();
        if (cutPoint.splitTurn()) {
            for (int i = cutPoint.turnStartIndex(); i < cutPoint.firstKeptEntryIndex(); i++) {
                AgentMessage message = getMessageFromEntryForCompaction(pathEntries.get(i));
                if (message != null) {
                    turnPrefixMessages.add(message);
                }
            }
        }

        if (messagesToSummarize.isEmpty() && turnPrefixMessages.isEmpty()) {
            return null;
        }
        FileOperations fileOps = extractFileOperations(messagesToSummarize, pathEntries, prevCompactionIndex);
        if (cutPoint.splitTurn()) {
            turnPrefixMessages.forEach(message -> extractFileOpsFromMessage(message, fileOps));
        }
        return new CompactionPreparation(firstKeptEntryId, List.copyOf(messagesToSummarize),
                List.copyOf(turnPrefixMessages), cutPoint.splitTurn(), tokensBefore, previousSummary, fileOps, settings);
    }

    public static List<AgentMessage> buildSessionContext(List<SessionEntry> entries) {
        int lastCompactionIndex = -1;
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (entries.get(i) instanceof SessionEntry.CompactionEntry) {
                lastCompactionIndex = i;
                break;
            }
        }
        if (lastCompactionIndex < 0) {
            return entries.stream().map(CompactionSupport::getMessageFromEntry).filter(java.util.Objects::nonNull).toList();
        }
        SessionEntry.CompactionEntry compaction = (SessionEntry.CompactionEntry) entries.get(lastCompactionIndex);
        int firstKept = indexOfEntry(entries, compaction.firstKeptEntryId());
        int start = firstKept >= 0 ? firstKept : lastCompactionIndex + 1;
        List<AgentMessage> messages = new ArrayList<>();
        messages.add(new AgentMessage.Custom("compactionSummary",
                CodingAgentMessages.createCompactionSummaryMessage(compaction.summary(), compaction.tokensBefore(), compaction.timestamp()),
                true, compaction.details()));
        for (int i = start; i < entries.size(); i++) {
            if (i == lastCompactionIndex) {
                continue;
            }
            AgentMessage message = getMessageFromEntry(entries.get(i));
            if (message != null) {
                messages.add(message);
            }
        }
        return List.copyOf(messages);
    }

    private static FileOperations extractFileOperations(List<AgentMessage> messages, List<SessionEntry> entries, int prevCompactionIndex) {
        FileOperations fileOps = createFileOps();
        if (prevCompactionIndex >= 0 && entries.get(prevCompactionIndex) instanceof SessionEntry.CompactionEntry previous
                && !previous.fromHook() && previous.details() != null) {
            JsonNode details = previous.details();
            details.path("readFiles").forEach(file -> {
                if (file.isTextual()) {
                    fileOps.read().add(file.asText());
                }
            });
            details.path("modifiedFiles").forEach(file -> {
                if (file.isTextual()) {
                    fileOps.edited().add(file.asText());
                }
            });
        }
        messages.forEach(message -> extractFileOpsFromMessage(message, fileOps));
        return fileOps;
    }

    private static AgentMessage getMessageFromEntryForCompaction(SessionEntry entry) {
        return entry instanceof SessionEntry.CompactionEntry ? null : getMessageFromEntry(entry);
    }

    private static AgentMessage getMessageFromEntry(SessionEntry entry) {
        if (entry instanceof SessionEntry.MessageEntry messageEntry) {
            return new AgentMessage.Llm(messageFromJson(messageEntry.message()));
        }
        if (entry instanceof SessionEntry.CustomMessageEntry customMessage) {
            if ("bashExecution".equals(customMessage.customType())) {
                return new AgentMessage.Custom("bashExecution", bashExecutionFromJson(customMessage.content()),
                        customMessage.display(), customMessage.details());
            }
            return new AgentMessage.Custom("custom",
                    CodingAgentMessages.createCustomMessage(customMessage.customType(), customMessage.content(),
                            customMessage.display(), customMessage.details(), customMessage.timestamp()),
                    customMessage.display(), customMessage.details());
        }
        if (entry instanceof SessionEntry.BranchSummaryEntry branchSummary) {
            return new AgentMessage.Custom("branchSummary",
                    CodingAgentMessages.createBranchSummaryMessage(branchSummary.summary(), branchSummary.fromId(),
                            branchSummary.timestamp()),
                    true, branchSummary.details());
        }
        if (entry instanceof SessionEntry.CompactionEntry compaction) {
            return new AgentMessage.Custom("compactionSummary",
                    CodingAgentMessages.createCompactionSummaryMessage(compaction.summary(), compaction.tokensBefore(),
                            compaction.timestamp()),
                    true, compaction.details());
        }
        return null;
    }

    private static CodingAgentMessages.BashExecutionMessage bashExecutionFromJson(JsonNode node) {
        if (node == null || node.isNull()) {
            return new CodingAgentMessages.BashExecutionMessage("", "", null, false, false, null, Instant.now(), false);
        }
        try {
            return JsonCodec.mapper().treeToValue(node, CodingAgentMessages.BashExecutionMessage.class);
        } catch (Exception ignored) {
            String timestamp = node.path("timestamp").asText(null);
            Instant parsedTimestamp = timestamp == null || timestamp.isBlank() ? Instant.now() : Instant.parse(timestamp);
            return new CodingAgentMessages.BashExecutionMessage(
                    node.path("command").asText(""),
                    node.path("output").asText(""),
                    node.hasNonNull("exitCode") ? node.path("exitCode").asInt() : null,
                    node.path("cancelled").asBoolean(false),
                    node.path("truncated").asBoolean(false),
                    node.path("fullOutputPath").asText(null),
                    parsedTimestamp,
                    node.path("excludeFromContext").asBoolean(false));
        }
    }

    private static Message messageFromJson(JsonNode node) {
        String role = node.path("role").asText();
        Instant timestamp = parseTimestamp(node.path("timestamp").asText(null));
        if ("assistant".equals(role)) {
            return new Message.Assistant(readContent(node), node.path("provider").asText(null),
                    node.path("model").asText(null), StopReason.STOP, readUsage(node), null, timestamp, node.path("responseId").asText(null));
        }
        if ("toolResult".equals(role) || "tool".equals(role)) {
            return new Message.ToolResult(node.path("toolCallId").asText(null),
                    node.path("toolName").asText(null), readContent(node), node.path("error").asBoolean(false),
                    node.get("details"), timestamp);
        }
        return new Message.User(readContent(node), timestamp);
    }

    private static Usage readUsage(JsonNode node) {
        JsonNode usage = node.get("usage");
        if (usage == null || !usage.isObject()) {
            return null;
        }
        return new Usage(
                usage.path("inputTokens").asInt(0),
                usage.path("outputTokens").asInt(0),
                usage.path("cacheCreationInputTokens").asInt(0),
                usage.path("cacheReadInputTokens").asInt(0),
                usage.path("reasoningTokens").asInt(0));
    }

    private static List<Content> readContent(JsonNode node) {
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
                } else if ("text".equals(item.path("type").asText()) || item.has("text")) {
                    values.add(new Content.Text(item.path("text").asText()));
                } else if ("thinking".equals(item.path("type").asText()) || item.has("signature")) {
                    values.add(new Content.Thinking(item.path("text").asText(item.path("thinking").asText()),
                            item.path("signature").asText(null)));
                } else if ("toolCall".equals(item.path("type").asText()) || item.has("name")) {
                    values.add(new Content.ToolCall(item.path("id").asText(null), item.path("name").asText(),
                            item.has("input") ? item.get("input") : item.get("arguments"), List.of()));
                }
            }
            return List.copyOf(values);
        }
        return List.of(new Content.Text(content.toString()));
    }

    private static List<Integer> findValidCutPoints(List<SessionEntry> entries, int startIndex, int endIndex) {
        List<Integer> cutPoints = new ArrayList<>();
        for (int i = startIndex; i < endIndex; i++) {
            SessionEntry entry = entries.get(i);
            if (entry instanceof SessionEntry.MessageEntry messageEntry) {
                String role = messageEntry.message().path("role").asText();
                if (!"toolResult".equals(role) && !"tool".equals(role)) {
                    cutPoints.add(i);
                }
            } else if (entry instanceof SessionEntry.BranchSummaryEntry || entry instanceof SessionEntry.CustomMessageEntry) {
                cutPoints.add(i);
            }
        }
        return cutPoints;
    }

    private static UsageInfo lastAssistantUsage(List<AgentMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof AgentMessage.Llm llm && llm.message() instanceof Message.Assistant assistant
                    && assistant.usage() != null && assistant.stopReason() != StopReason.ABORTED
                    && assistant.stopReason() != StopReason.ERROR && assistant.usage().totalTokens() > 0) {
                return new UsageInfo(assistant.usage(), i);
            }
        }
        return null;
    }

    private static int estimateContentChars(List<Content> content) {
        int chars = 0;
        for (Content block : content) {
            if (block instanceof Content.Text text) {
                chars += safeLength(text.text());
            } else if (block instanceof Content.Thinking thinking) {
                chars += safeLength(thinking.text());
            } else if (block instanceof Content.Image) {
                chars += ESTIMATED_IMAGE_CHARS;
            } else if (block instanceof Content.ToolCall toolCall) {
                chars += safeLength(toolCall.name()) + String.valueOf(toolCall.input()).length();
            }
        }
        return chars;
    }

    private static String textOnly(List<Content> content) {
        StringBuilder text = new StringBuilder();
        for (Content block : content) {
            if (block instanceof Content.Text textBlock) {
                text.append(textBlock.text());
            }
        }
        return text.toString();
    }

    private static String argsString(JsonNode input) {
        if (input == null || !input.isObject()) {
            return "";
        }
        List<String> args = new ArrayList<>();
        input.fields().forEachRemaining(entry -> args.add(entry.getKey() + "=" + entry.getValue()));
        return String.join(", ", args);
    }

    private static String truncateForSummary(String text, int maxChars) {
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "\n\n[... " + (text.length() - maxChars) + " more characters truncated]";
    }

    private static int indexOfEntry(List<SessionEntry> entries, String id) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private static Instant parseTimestamp(String timestamp) {
        try {
            return timestamp == null ? Instant.now() : Instant.parse(timestamp);
        } catch (RuntimeException e) {
            return Instant.now();
        }
    }

    private static int ceilTokens(int chars) {
        return (int) Math.ceil(chars / 4.0);
    }

    private static int safeLength(String value) {
        return value == null ? 0 : value.length();
    }

    private record UsageInfo(Usage usage, int index) {
    }
}
