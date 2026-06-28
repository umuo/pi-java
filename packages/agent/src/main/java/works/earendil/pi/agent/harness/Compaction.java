package works.earendil.pi.agent.harness;

import works.earendil.pi.ai.model.Content;
import works.earendil.pi.ai.model.Message;
import works.earendil.pi.ai.model.StopReason;
import works.earendil.pi.ai.model.Usage;
import works.earendil.pi.agent.core.AgentMessage;
import works.earendil.pi.agent.session.SessionEntry;

import java.util.List;

public final class Compaction {
    public static final Settings DEFAULT_SETTINGS = new Settings(true, 16_384, 20_000);
    private static final int ESTIMATED_IMAGE_CHARS = 4_800;
    private static final int TOOL_RESULT_MAX_CHARS = 2_000;

    private Compaction() {
    }

    public record Settings(boolean enabled, int reserveTokens, int keepRecentTokens) {
    }

    public record ContextUsageEstimate(int tokens, int usageTokens, int trailingTokens, Integer lastUsageIndex) {
    }

    public record CutPointResult(String firstKeptEntryId, int startIndex, int tokensKept) {
    }

    public static int calculateContextTokens(Usage usage) {
        return usage.totalTokens();
    }

    public static boolean shouldCompact(int contextTokens, int contextWindow, Settings settings) {
        return settings.enabled && contextTokens > contextWindow - settings.reserveTokens;
    }

    public static ContextUsageEstimate estimateContextTokens(List<AgentMessage> messages) {
        UsageInfo usageInfo = lastAssistantUsage(messages);
        if (usageInfo == null) {
            int total = messages.stream().mapToInt(Compaction::estimateTokens).sum();
            return new ContextUsageEstimate(total, 0, total, null);
        }
        int usageTokens = calculateContextTokens(usageInfo.usage());
        int trailing = 0;
        for (int i = usageInfo.index() + 1; i < messages.size(); i++) {
            trailing += estimateTokens(messages.get(i));
        }
        return new ContextUsageEstimate(usageTokens + trailing, usageTokens, trailing, usageInfo.index());
    }

    public static int estimateTokens(AgentMessage message) {
        if (message instanceof AgentMessage.Llm llm) {
            return estimateTokens(llm.message());
        }
        if (message instanceof AgentMessage.Custom custom) {
            return Math.max(1, String.valueOf(custom.content()).length() / 4);
        }
        return 0;
    }

    public static int estimateTokens(Message message) {
        int chars = 0;
        if (message instanceof Message.User user) {
            chars += estimateContentChars(user.content());
        } else if (message instanceof Message.Assistant assistant) {
            chars += estimateContentChars(assistant.content());
        } else if (message instanceof Message.ToolResult result) {
            chars += estimateContentChars(result.content());
        }
        return (int) Math.ceil(chars / 4.0);
    }

    public static CutPointResult findCutPoint(List<SessionEntry> entries, int keepRecentTokens) {
        int tokens = 0;
        for (int i = entries.size() - 1; i >= 0; i--) {
            SessionEntry entry = entries.get(i);
            int entryTokens = estimateEntryTokens(entry);
            if (tokens + entryTokens > keepRecentTokens && i < entries.size() - 1) {
                SessionEntry firstKept = entries.get(i + 1);
                return new CutPointResult(firstKept.id(), i + 1, tokens);
            }
            tokens += entryTokens;
        }
        if (entries.isEmpty()) {
            return new CutPointResult(null, 0, 0);
        }
        return new CutPointResult(entries.getFirst().id(), 0, tokens);
    }

    public static String serializeConversation(List<Message> messages) {
        StringBuilder out = new StringBuilder();
        for (Message message : messages) {
            if (!out.isEmpty()) {
                out.append("\n\n");
            }
            if (message instanceof Message.User user) {
                out.append("[User]: ").append(extractText(user.content()));
            } else if (message instanceof Message.Assistant assistant) {
                out.append("[Assistant]: ").append(extractText(assistant.content()));
            } else if (message instanceof Message.ToolResult result) {
                out.append("[Tool result]: ").append(truncate(extractText(result.content()), TOOL_RESULT_MAX_CHARS));
            }
        }
        return out.toString();
    }

    private static int estimateEntryTokens(SessionEntry entry) {
        if (entry instanceof SessionEntry.CompactionEntry compaction) {
            return Math.max(1, compaction.summary().length() / 4);
        }
        if (entry instanceof SessionEntry.BranchSummaryEntry summary) {
            return Math.max(1, summary.summary().length() / 4);
        }
        if (entry instanceof SessionEntry.CustomMessageEntry custom) {
            return Math.max(1, custom.content().toString().length() / 4);
        }
        if (entry instanceof SessionEntry.MessageEntry message) {
            return Math.max(1, message.message().toString().length() / 4);
        }
        return 0;
    }

    private static UsageInfo lastAssistantUsage(List<AgentMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            AgentMessage message = messages.get(i);
            if (message instanceof AgentMessage.Llm llm
                    && llm.message() instanceof Message.Assistant assistant
                    && assistant.usage() != null
                    && assistant.stopReason() != StopReason.ABORTED
                    && assistant.stopReason() != StopReason.ERROR
                    && assistant.usage().totalTokens() > 0) {
                return new UsageInfo(assistant.usage(), i);
            }
        }
        return null;
    }

    private static int estimateContentChars(List<Content> content) {
        int chars = 0;
        for (Content block : content) {
            if (block instanceof Content.Text text) {
                chars += text.text().length();
            } else if (block instanceof Content.Thinking thinking) {
                chars += thinking.text().length();
            } else if (block instanceof Content.Image) {
                chars += ESTIMATED_IMAGE_CHARS;
            } else if (block instanceof Content.ToolCall toolCall) {
                chars += toolCall.name().length() + String.valueOf(toolCall.input()).length();
            }
        }
        return chars;
    }

    private static String extractText(List<Content> content) {
        StringBuilder out = new StringBuilder();
        for (Content block : content) {
            if (block instanceof Content.Text text) {
                out.append(text.text());
            } else if (block instanceof Content.Thinking thinking) {
                out.append(thinking.text());
            } else if (block instanceof Content.ToolCall toolCall) {
                out.append(toolCall.name()).append('(').append(toolCall.input()).append(')');
            }
        }
        return out.toString();
    }

    private static String truncate(String text, int maxChars) {
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "\n\n[... " + (text.length() - maxChars) + " more characters truncated]";
    }

    private record UsageInfo(Usage usage, int index) {
    }
}
