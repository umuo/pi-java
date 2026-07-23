package works.earendil.pi.agent.session;

import com.fasterxml.jackson.databind.JsonNode;
import works.earendil.pi.ai.model.Usage;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public interface SessionStorage {
    record Statistics(int entries, int messages, int toolResults, int compactions, int branchSummaries,
                      int cachedTokens, int uncachedTokens, int totalTokens) {
        public Statistics(int entries, int messages, int toolResults, int compactions, int branchSummaries) {
            this(entries, messages, toolResults, compactions, branchSummaries, 0, 0, 0);
        }
    }

    String createEntryId();

    Optional<String> leafId();

    void setLeafId(String id) throws IOException;

    Optional<SessionEntry> entry(String id);

    List<SessionEntry> entries();

    default List<SessionEntry> entriesAfter(String cursor, int limit) {
        List<SessionEntry> all = entries();
        int start = 0;
        if (cursor != null) {
            for (int i = 0; i < all.size(); i++) {
                if (cursor.equals(all.get(i).id())) {
                    start = i + 1;
                    break;
                }
            }
        }
        int end = limit <= 0 ? all.size() : Math.min(all.size(), start + limit);
        return List.copyOf(all.subList(Math.min(start, all.size()), end));
    }

    List<SessionEntry> pathToRoot(String leafId);

    default List<SessionEntry> pathToRootOrCompaction(String leafId) {
        List<SessionEntry> path = pathToRoot(leafId);
        int checkpoint = -1;
        for (int i = 0; i < path.size(); i++) {
            if (path.get(i) instanceof SessionEntry.CompactionEntry) {
                checkpoint = i;
            }
        }
        return checkpoint < 0 ? path : List.copyOf(path.subList(checkpoint, path.size()));
    }

    void append(SessionEntry entry) throws IOException;

    default Optional<String> sessionName() {
        List<SessionEntry> names = findByType("session_info");
        if (names.isEmpty()) {
            return Optional.empty();
        }
        String name = ((SessionEntry.SessionInfoEntry) names.getLast()).name();
        return name == null || name.isBlank() ? Optional.empty() : Optional.of(name.trim());
    }

    default Statistics statistics() {
        List<SessionEntry> all = entries();
        int cachedTokens = 0;
        int uncachedTokens = 0;
        int totalTokens = 0;
        for (SessionEntry entry : all) {
            Usage usage = entryUsage(entry);
            if (usage != null) {
                cachedTokens += usage.cacheReadInputTokens();
                uncachedTokens += usage.inputTokens() + usage.cacheCreationInputTokens();
                totalTokens += usage.totalTokens();
            }
        }
        return new Statistics(
                all.size(),
                (int) all.stream().filter(SessionEntry.MessageEntry.class::isInstance).count(),
                (int) all.stream().filter(entry -> entry instanceof SessionEntry.MessageEntry message
                        && ("tool".equals(message.message().path("role").asText())
                        || "toolResult".equals(message.message().path("role").asText()))).count(),
                (int) all.stream().filter(SessionEntry.CompactionEntry.class::isInstance).count(),
                (int) all.stream().filter(SessionEntry.BranchSummaryEntry.class::isInstance).count(),
                cachedTokens, uncachedTokens, totalTokens);
    }

    private static Usage entryUsage(SessionEntry entry) {
        if (entry instanceof SessionEntry.CompactionEntry compaction) {
            return compaction.usage();
        }
        if (entry instanceof SessionEntry.BranchSummaryEntry branchSummary) {
            return branchSummary.usage();
        }
        if (!(entry instanceof SessionEntry.MessageEntry message)) {
            return null;
        }
        JsonNode usage = message.message().path("usage");
        if (usage.isMissingNode() || usage.isNull()) {
            return null;
        }
        return new Usage(
                usage.path("inputTokens").asInt(0),
                usage.path("outputTokens").asInt(0),
                usage.path("cacheCreationInputTokens").asInt(0),
                usage.path("cacheReadInputTokens").asInt(0),
                usage.path("reasoningTokens").asInt(0));
    }

    default List<SessionEntry> findByType(String type) {
        return entries().stream().filter(entry -> entry.type().equals(type)).toList();
    }
}
