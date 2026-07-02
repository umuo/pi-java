package works.earendil.pi.codingagent.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import works.earendil.pi.agent.session.SessionEntry;
import works.earendil.pi.codingagent.resources.SkillLoader;
import works.earendil.pi.codingagent.session.SessionFileInfo;
import works.earendil.pi.codingagent.session.SessionManager;
import works.earendil.pi.common.json.JsonCodec;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SkillDiagnosticHistory {
    public static final String CUSTOM_TYPE = "skill_trigger_diagnostics";

    private static final int MAX_ENTRIES = 20;

    private final List<Entry> entries = new ArrayList<>();

    public static SkillDiagnosticHistory fromSession(SessionManager sessionManager) {
        return fromSession(sessionManager, null);
    }

    public static SkillDiagnosticHistory fromSession(SessionManager sessionManager, String branchId) {
        SkillDiagnosticHistory history = new SkillDiagnosticHistory();
        history.restoreFromSession(sessionManager, branchId);
        return history;
    }

    public void record(AgentSession.AgentSessionEvent.SkillTriggerDiagnostic diagnostic) {
        if (diagnostic == null || diagnostic.matches().isEmpty()) {
            return;
        }
        entries.add(new Entry(Instant.now().toString(), diagnostic.matches()));
        trim();
    }

    public AgentSession.AgentSessionEvent.SkillTriggerDiagnostic latest() {
        if (entries.isEmpty()) {
            return null;
        }
        return new AgentSession.AgentSessionEvent.SkillTriggerDiagnostic(entries.getLast().matches());
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    public List<Entry> entries(Filter filter) {
        Filter normalized = filter == null ? Filter.empty() : filter;
        if (normalized.isEmpty()) {
            return entries();
        }
        List<Entry> filtered = new ArrayList<>();
        for (Entry entry : entries) {
            List<SkillLoader.SkillTriggerMatch> matches = entry.matches().stream()
                    .filter(normalized::matches)
                    .toList();
            if (!matches.isEmpty()) {
                filtered.add(new Entry(entry.capturedAt(), matches));
            }
        }
        return List.copyOf(filtered);
    }

    public void clear() {
        entries.clear();
    }

    public String persist(SessionManager sessionManager) throws IOException {
        return sessionManager.appendCustomEntry(CUSTOM_TYPE, toJson());
    }

    public void restoreFromSession(SessionManager sessionManager) {
        restoreFromSession(sessionManager, null);
    }

    public void restoreFromSession(SessionManager sessionManager, String branchId) {
        String normalizedBranch = normalize(branchId);
        List<SessionEntry> branch = normalizedBranch.isBlank()
                ? sessionManager.branch()
                : sessionManager.branch(normalizedBranch);
        if (!normalizedBranch.isBlank() && branch.isEmpty()) {
            throw new IllegalArgumentException("Entry " + normalizedBranch + " not found");
        }
        for (int i = branch.size() - 1; i >= 0; i--) {
            SessionEntry entry = branch.get(i);
            if (entry instanceof SessionEntry.CustomEntry customEntry
                    && CUSTOM_TYPE.equals(customEntry.customType())) {
                restore(customEntry.data());
                return;
            }
        }
    }

    JsonNode toJson() {
        return toJson(Filter.empty());
    }

    public JsonNode toJson(Filter filter) {
        return toJson(new Query(filter, 0, 0, "oldest", false));
    }

    public JsonNode toJson(Query query) {
        return toJson(query, null);
    }

    public JsonNode toJson(Query query, Source source) {
        ObjectNode root = JsonCodec.mapper().createObjectNode();
        root.put("schemaVersion", 1);
        if (source != null) {
            ObjectNode sourceNode = root.putObject("source");
            sourceNode.put("sessionId", source.sessionId());
            sourceNode.put("sessionFile", source.sessionFile());
            sourceNode.put("branch", source.branch());
        }
        ObjectNode filterNode = root.putObject("filter");
        Query normalized = query == null ? Query.defaultQuery() : query;
        filterNode.put("skill", normalized.filter().skill());
        filterNode.put("model", normalized.filter().model());
        filterNode.put("reason", normalized.filter().reason());
        List<Entry> filteredEntries = entries(normalized.filter());
        List<Entry> pageEntries = page(sort(filteredEntries, normalized.sort()), normalized.offset(), normalized.limit());
        ObjectNode pageNode = root.putObject("page");
        pageNode.put("offset", normalized.offset());
        pageNode.put("limit", normalized.limit());
        pageNode.put("sort", normalized.sort());
        pageNode.put("totalEntries", filteredEntries.size());
        pageNode.put("returnedEntries", pageEntries.size());
        if (normalized.includeSummary()) {
            appendSummary(root, filteredEntries);
        }
        ArrayNode entryNodes = root.putArray("entries");
        for (Entry entry : pageEntries) {
            ObjectNode entryNode = entryNodes.addObject();
            entryNode.put("capturedAt", entry.capturedAt());
            ArrayNode matchNodes = entryNode.putArray("matches");
            for (SkillLoader.SkillTriggerMatch match : entry.matches()) {
                ObjectNode matchNode = matchNodes.addObject();
                matchNode.put("skill", match.skillName());
                matchNode.put("path", match.skillPath() == null ? "" : match.skillPath().toString());
                matchNode.put("modelVisible", match.modelVisible());
                ArrayNode reasonNodes = matchNode.putArray("reasons");
                match.reasons().forEach(reasonNodes::add);
            }
        }
        return root;
    }

    public static JsonNode sourceIndex(SessionManager current, int sessionLimit, boolean includeEmpty)
            throws IOException {
        ObjectNode root = JsonCodec.mapper().createObjectNode();
        root.put("schemaVersion", 1);
        root.set("current", JsonCodec.mapper().valueToTree(Source.from(current, "")));
        ArrayNode sessionNodes = root.putArray("sessions");
        int limit = sessionLimit <= 0 ? 20 : sessionLimit;
        if (!current.isPersisted() || current.sessionDir() == null) {
            appendSessionSource(sessionNodes, current, null, true, includeEmpty);
            return root;
        }

        List<SessionFileInfo> sessions = SessionManager.list(current.cwd(), current.sessionDir(), null);
        int emitted = 0;
        for (SessionFileInfo info : sessions) {
            if (emitted >= limit) {
                break;
            }
            boolean isCurrent = current.sessionFile()
                    .map(path -> path.equals(info.path()))
                    .orElse(false);
            SessionManager manager = isCurrent ? current : SessionManager.open(info.path(), current.sessionDir(), current.cwd());
            if (appendSessionSource(sessionNodes, manager, info, isCurrent, includeEmpty)) {
                emitted++;
            }
        }
        return root;
    }

    public static JsonNode sourcePicker(SessionManager current, int sessionLimit, boolean includeEmpty)
            throws IOException {
        JsonNode index = sourceIndex(current, sessionLimit, includeEmpty);
        ObjectNode root = JsonCodec.mapper().createObjectNode();
        root.put("schemaVersion", 1);
        root.set("current", index.path("current"));
        ArrayNode items = root.putArray("items");
        int[] position = new int[]{1};
        for (JsonNode sessionNode : index.path("sessions")) {
            for (JsonNode branchNode : sessionNode.path("branchTree")) {
                appendPickerItems(items, sessionNode, branchNode, 0, position);
            }
        }
        root.put("totalItems", items.size());
        return root;
    }

    private List<Entry> sort(List<Entry> values, String sort) {
        List<Entry> sorted = new ArrayList<>(values);
        Comparator<Entry> byCapturedAt = Comparator.comparing(Entry::capturedAt);
        if ("newest".equals(sort)) {
            byCapturedAt = byCapturedAt.reversed();
        }
        sorted.sort(byCapturedAt);
        return sorted;
    }

    private List<Entry> page(List<Entry> values, int offset, int limit) {
        int start = Math.min(Math.max(0, offset), values.size());
        int end = limit <= 0 ? values.size() : Math.min(values.size(), start + limit);
        return List.copyOf(values.subList(start, end));
    }

    private static boolean appendSessionSource(ArrayNode target, SessionManager manager, SessionFileInfo info,
                                               boolean current, boolean includeEmpty) {
        ArrayNode branchNodes = JsonCodec.mapper().createArrayNode();
        for (SessionManager.SessionTreeNode node : manager.tree()) {
            ObjectNode branchNode = branchSourceNode(manager, node, includeEmpty);
            if (branchNode != null) {
                branchNodes.add(branchNode);
            }
        }
        if (!includeEmpty && branchNodes.isEmpty()) {
            return false;
        }
        ObjectNode sessionNode = target.addObject();
        sessionNode.put("sessionId", manager.sessionId());
        sessionNode.put("sessionFile", manager.sessionFile().map(Path::toString).orElse(""));
        sessionNode.put("cwd", manager.cwd().toString());
        sessionNode.put("name", info == null || info.name() == null ? "" : info.name());
        sessionNode.put("created", info == null || info.created() == null ? "" : info.created().toString());
        sessionNode.put("modified", info == null || info.modified() == null ? "" : info.modified().toString());
        sessionNode.put("current", current);
        sessionNode.set("branchTree", branchNodes);
        return true;
    }

    private static ObjectNode branchSourceNode(SessionManager manager, SessionManager.SessionTreeNode node,
                                               boolean includeEmpty) {
        SkillDiagnosticHistory history = SkillDiagnosticHistory.fromSession(manager, node.entry().id());
        ObjectNode branchNode = JsonCodec.mapper().createObjectNode();
        branchNode.put("id", node.entry().id());
        branchNode.put("parentId", node.entry().parentId() == null ? "" : node.entry().parentId());
        branchNode.put("type", node.entry().type());
        branchNode.put("timestamp", node.entry().timestamp().toString());
        branchNode.put("label", node.label() == null ? "" : node.label());
        appendBranchDiagnostics(branchNode.putObject("diagnostics"), history.entries());
        ArrayNode children = branchNode.putArray("children");
        for (SessionManager.SessionTreeNode child : node.children()) {
            ObjectNode childNode = branchSourceNode(manager, child, includeEmpty);
            if (childNode != null) {
                children.add(childNode);
            }
        }
        if (!includeEmpty && history.entries().isEmpty() && children.isEmpty()) {
            return null;
        }
        return branchNode;
    }

    private static void appendPickerItems(ArrayNode target, JsonNode sessionNode, JsonNode branchNode,
                                          int depth, int[] position) {
        JsonNode diagnostics = branchNode.path("diagnostics");
        ObjectNode item = target.addObject();
        int index = position[0]++;
        String label = branchNode.path("label").asText("");
        String branchId = branchNode.path("id").asText("");
        String topSkill = topCountValue(diagnostics.path("skills"));
        String topReason = topCountValue(diagnostics.path("reasons"));
        int matches = diagnostics.path("matches").asInt(0);
        item.put("index", index);
        item.put("sessionId", sessionNode.path("sessionId").asText(""));
        item.put("sessionFile", sessionNode.path("sessionFile").asText(""));
        item.put("sessionName", sessionNode.path("name").asText(""));
        item.put("currentSession", sessionNode.path("current").asBoolean(false));
        item.put("branch", branchId);
        item.put("parentBranch", branchNode.path("parentId").asText(""));
        item.put("label", label);
        item.put("depth", depth);
        item.put("type", branchNode.path("type").asText(""));
        item.put("timestamp", branchNode.path("timestamp").asText(""));
        item.put("entries", diagnostics.path("entries").asInt(0));
        item.put("matches", matches);
        item.put("visible", diagnostics.path("visible").asInt(0));
        item.put("manualOnly", diagnostics.path("manualOnly").asInt(0));
        item.put("latestCapturedAt", diagnostics.path("latestCapturedAt").asText(""));
        item.put("topSkill", topSkill);
        item.put("topReason", topReason);
        item.put("title", pickerTitle(index, label, branchId, matches, topSkill));
        item.put("subtitle", pickerSubtitle(sessionNode, diagnostics, topReason));
        for (JsonNode child : branchNode.path("children")) {
            appendPickerItems(target, sessionNode, child, depth + 1, position);
        }
    }

    private static String pickerTitle(int index, String label, String branchId, int matches, String topSkill) {
        String name = label == null || label.isBlank() ? branchId : label;
        String skill = topSkill == null || topSkill.isBlank() ? "no top skill" : topSkill;
        return index + ". " + name + " | matches: " + matches + " | top skill: " + skill;
    }

    private static String pickerSubtitle(JsonNode sessionNode, JsonNode diagnostics, String topReason) {
        List<String> parts = new ArrayList<>();
        if (sessionNode.path("current").asBoolean(false)) {
            parts.add("current session");
        }
        String sessionName = sessionNode.path("name").asText("");
        if (!sessionName.isBlank()) {
            parts.add("session: " + sessionName);
        }
        String latest = diagnostics.path("latestCapturedAt").asText("");
        if (!latest.isBlank()) {
            parts.add("latest: " + latest);
        }
        if (topReason != null && !topReason.isBlank()) {
            parts.add("top reason: " + topReason);
        }
        String sessionFile = sessionNode.path("sessionFile").asText("");
        if (!sessionFile.isBlank()) {
            parts.add(sessionFile);
        }
        return String.join(" | ", parts);
    }

    private static String topCountValue(JsonNode counts) {
        if (counts == null || !counts.isArray() || counts.isEmpty()) {
            return "";
        }
        JsonNode first = counts.get(0);
        String value = first.path("value").asText("");
        int count = first.path("count").asInt(0);
        return value.isBlank() ? "" : value + "=" + count;
    }

    private static void appendBranchDiagnostics(ObjectNode diagnostics, List<Entry> sourceEntries) {
        List<SkillLoader.SkillTriggerMatch> matches = sourceEntries.stream()
                .flatMap(entry -> entry.matches().stream())
                .toList();
        long visible = matches.stream().filter(SkillLoader.SkillTriggerMatch::modelVisible).count();
        diagnostics.put("entries", sourceEntries.size());
        diagnostics.put("matches", matches.size());
        diagnostics.put("visible", visible);
        diagnostics.put("manualOnly", matches.size() - visible);
        diagnostics.put("latestCapturedAt", sourceEntries.isEmpty() ? "" : sourceEntries.getLast().capturedAt());
        appendCounts(diagnostics.putArray("skills"), count(matches.stream()
                .map(SkillLoader.SkillTriggerMatch::skillName)
                .toList()));
        appendCounts(diagnostics.putArray("reasons"), count(matches.stream()
                .flatMap(match -> match.reasons().stream())
                .toList()));
    }

    private static void appendSummary(ObjectNode root, List<Entry> sourceEntries) {
        ObjectNode summary = root.putObject("summary");
        List<SkillLoader.SkillTriggerMatch> matches = sourceEntries.stream()
                .flatMap(entry -> entry.matches().stream())
                .toList();
        long visible = matches.stream().filter(SkillLoader.SkillTriggerMatch::modelVisible).count();
        summary.put("entries", sourceEntries.size());
        summary.put("matches", matches.size());
        summary.put("visible", visible);
        summary.put("manualOnly", matches.size() - visible);
        appendCounts(summary.putArray("skills"), count(matches.stream()
                .map(SkillLoader.SkillTriggerMatch::skillName)
                .toList()));
        appendCounts(summary.putArray("reasons"), count(matches.stream()
                .flatMap(match -> match.reasons().stream())
                .toList()));
    }

    private static Map<String, Integer> count(List<String> values) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        values.stream()
                .filter(value -> value != null && !value.isBlank())
                .sorted()
                .forEach(value -> counts.merge(value, 1, Integer::sum));
        return counts;
    }

    private static void appendCounts(ArrayNode target, Map<String, Integer> counts) {
        counts.entrySet().stream()
                .sorted((left, right) -> {
                    int count = Integer.compare(right.getValue(), left.getValue());
                    return count != 0 ? count : left.getKey().compareTo(right.getKey());
                })
                .forEach(entry -> {
                    ObjectNode item = target.addObject();
                    item.put("value", entry.getKey());
                    item.put("count", entry.getValue());
                });
    }

    private void restore(JsonNode data) {
        entries.clear();
        if (data == null || !data.isObject()) {
            return;
        }
        JsonNode entryNodes = data.get("entries");
        if (entryNodes == null || !entryNodes.isArray()) {
            return;
        }
        for (JsonNode entryNode : entryNodes) {
            String capturedAt = entryNode.path("capturedAt").asText("");
            List<SkillLoader.SkillTriggerMatch> matches = restoreMatches(entryNode.get("matches"));
            if (!matches.isEmpty()) {
                entries.add(new Entry(capturedAt.isBlank() ? Instant.EPOCH.toString() : capturedAt, matches));
            }
        }
        trim();
    }

    private List<SkillLoader.SkillTriggerMatch> restoreMatches(JsonNode matchNodes) {
        if (matchNodes == null || !matchNodes.isArray()) {
            return List.of();
        }
        List<SkillLoader.SkillTriggerMatch> matches = new ArrayList<>();
        for (JsonNode matchNode : matchNodes) {
            String skill = matchNode.path("skill").asText("").trim();
            if (skill.isBlank()) {
                continue;
            }
            String path = matchNode.path("path").asText("").trim();
            List<String> reasons = restoreReasons(matchNode.get("reasons"));
            matches.add(new SkillLoader.SkillTriggerMatch(skill, path.isBlank() ? null : Path.of(path),
                    matchNode.path("modelVisible").asBoolean(false), reasons));
        }
        return List.copyOf(matches);
    }

    private List<String> restoreReasons(JsonNode reasonNodes) {
        if (reasonNodes == null || !reasonNodes.isArray()) {
            return List.of();
        }
        List<String> reasons = new ArrayList<>();
        for (JsonNode reasonNode : reasonNodes) {
            String reason = reasonNode.asText("").trim();
            if (!reason.isBlank()) {
                reasons.add(reason);
            }
        }
        return List.copyOf(reasons);
    }

    private void trim() {
        while (entries.size() > MAX_ENTRIES) {
            entries.removeFirst();
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    public record Filter(String skill, String model, String reason) {
        public Filter {
            skill = normalize(skill);
            model = normalize(model).toLowerCase(Locale.ROOT);
            reason = normalize(reason);
        }

        public static Filter empty() {
            return new Filter("", "", "");
        }

        public boolean isEmpty() {
            return skill.isBlank() && model.isBlank() && reason.isBlank();
        }

        public boolean matches(SkillLoader.SkillTriggerMatch match) {
            if (!skill.isBlank() && !match.skillName().equalsIgnoreCase(skill)) {
                return false;
            }
            if (!model.isBlank() && !matchesModel(match.modelVisible())) {
                return false;
            }
            if (!reason.isBlank() && match.reasons().stream().noneMatch(value -> containsIgnoreCase(value, reason))) {
                return false;
            }
            return true;
        }

        public String describe() {
            if (isEmpty()) {
                return "none";
            }
            List<String> parts = new ArrayList<>();
            if (!skill.isBlank()) {
                parts.add("skill=" + skill);
            }
            if (!model.isBlank()) {
                parts.add("model=" + model);
            }
            if (!reason.isBlank()) {
                parts.add("reason=" + reason);
            }
            return String.join(" ", parts);
        }

        private boolean matchesModel(boolean modelVisible) {
            return switch (model) {
                case "visible", "auto", "model" -> modelVisible;
                case "manual", "hidden" -> !modelVisible;
                default -> false;
            };
        }

        private static boolean containsIgnoreCase(String value, String needle) {
            return value != null && value.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
        }
    }

    public record Query(Filter filter, int offset, int limit, String sort, boolean includeSummary) {
        public Query {
            filter = filter == null ? Filter.empty() : filter;
            offset = Math.max(0, offset);
            limit = Math.max(0, limit);
            sort = normalizeSort(sort);
        }

        public static Query defaultQuery() {
            return new Query(Filter.empty(), 0, 0, "oldest", false);
        }

        private static String normalizeSort(String value) {
            String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "newest", "latest", "desc", "descending" -> "newest";
                default -> "oldest";
            };
        }
    }

    public record Source(String sessionId, String sessionFile, String branch) {
        public Source {
            sessionId = normalize(sessionId);
            sessionFile = normalize(sessionFile);
            branch = normalize(branch);
        }

        public static Source from(SessionManager sessionManager, String branchId) {
            String branch = normalize(branchId);
            if (branch.isBlank()) {
                branch = sessionManager.leafId().orElse("");
            }
            return new Source(
                    sessionManager.sessionId(),
                    sessionManager.sessionFile().map(Path::toString).orElse(""),
                    branch
            );
        }
    }

    public record Entry(String capturedAt, List<SkillLoader.SkillTriggerMatch> matches) {
        public Entry {
            capturedAt = capturedAt == null || capturedAt.isBlank() ? Instant.EPOCH.toString() : capturedAt;
            matches = matches == null ? List.of() : List.copyOf(matches);
        }
    }
}
