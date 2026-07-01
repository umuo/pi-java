package works.earendil.pi.codingagent.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import works.earendil.pi.agent.session.SessionEntry;
import works.earendil.pi.codingagent.session.SessionManager;
import works.earendil.pi.common.json.JsonCodec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class GrillMeInterview {
    public static final String CUSTOM_TYPE = "grill_me_interview";

    private static final String DEFAULT_TOPIC = "the user's current product, design, or implementation decision";
    private static final int MAX_QUESTION_SUMMARY_CHARS = 240;

    private String topic;
    private final List<String> answers = new ArrayList<>();
    private final List<String> assistantQuestions = new ArrayList<>();
    private boolean active;

    public static GrillMeInterview fromSession(SessionManager sessionManager) {
        GrillMeInterview interview = new GrillMeInterview();
        interview.restoreFromSession(sessionManager);
        return interview;
    }

    public String start(String arguments) {
        topic = normalizeTopic(arguments);
        answers.clear();
        assistantQuestions.clear();
        active = true;
        return GrillMePrompt.build(topic, phase(), answers, assistantQuestions, controls());
    }

    public String answer(String answer) {
        if (!active) {
            throw new IllegalStateException("no active /grill-me interview; start one with /grill-me <topic>");
        }
        if (answer == null || answer.isBlank()) {
            throw new IllegalArgumentException("answer text is required");
        }
        answers.add(answer.trim());
        return GrillMePrompt.build(topic, phase(), answers, assistantQuestions, controls());
    }

    public String reset() {
        if (!active) {
            return "/grill-me interview\nstatus: none";
        }
        String stoppedTopic = topic;
        topic = null;
        answers.clear();
        assistantQuestions.clear();
        active = false;
        return "/grill-me interview\nstatus: reset\ntopic: " + stoppedTopic;
    }

    public String status() {
        StringBuilder out = new StringBuilder();
        out.append("/grill-me interview\n");
        out.append("status: ").append(active ? "active" : "none").append("\n");
        if (!active) {
            out.append("start: /grill-me <topic>");
            return out.toString();
        }
        out.append("topic: ").append(topic).append("\n");
        out.append("phase: ").append(phase()).append("\n");
        out.append("answers: ").append(answers.size()).append("\n");
        for (int i = 0; i < answers.size(); i++) {
            out.append(i + 1).append(". ").append(answers.get(i)).append("\n");
        }
        if (!assistantQuestions.isEmpty()) {
            out.append("assistant questions: ").append(assistantQuestions.size()).append("\n");
            for (int i = 0; i < assistantQuestions.size(); i++) {
                out.append("q").append(i + 1).append(". ").append(assistantQuestions.get(i)).append("\n");
            }
        }
        out.append("next: /grill-me answer <your answer>");
        return out.toString().trim();
    }

    public boolean active() {
        return active;
    }

    public String topic() {
        return topic;
    }

    public List<String> answers() {
        return List.copyOf(answers);
    }

    public List<String> assistantQuestions() {
        return List.copyOf(assistantQuestions);
    }

    public String phase() {
        if (answers.size() < 2) {
            return "discovery";
        }
        if (answers.size() < 4) {
            return "constraints";
        }
        return "synthesis";
    }

    public void captureLatestAssistantQuestion(SessionManager sessionManager) {
        Optional<String> question = extractLatestAssistantQuestion(sessionManager);
        if (question.isEmpty()) {
            return;
        }
        String value = question.get();
        if (assistantQuestions.isEmpty() || !assistantQuestions.getLast().equals(value)) {
            assistantQuestions.add(value);
        }
    }

    public Optional<String> extractLatestAssistantQuestion(SessionManager sessionManager) {
        List<SessionEntry> branch = sessionManager.branch();
        for (int i = branch.size() - 1; i >= 0; i--) {
            SessionEntry entry = branch.get(i);
            if (entry instanceof SessionEntry.MessageEntry messageEntry
                    && "assistant".equals(messageEntry.message().path("role").asText())) {
                return summarizeAssistantQuestion(assistantText(messageEntry.message()));
            }
        }
        return Optional.empty();
    }

    public String persist(SessionManager sessionManager) throws IOException {
        return sessionManager.appendCustomEntry(CUSTOM_TYPE, toJson());
    }

    public void restoreFromSession(SessionManager sessionManager) {
        List<SessionEntry> branch = sessionManager.branch();
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
        ObjectNode node = JsonCodec.mapper().createObjectNode();
        node.put("schemaVersion", 1);
        node.put("active", active);
        if (topic == null) {
            node.putNull("topic");
        } else {
            node.put("topic", topic);
        }
        node.put("phase", phase());
        ArrayNode answerNodes = node.putArray("answers");
        answers.forEach(answerNodes::add);
        ArrayNode questionNodes = node.putArray("assistantQuestions");
        assistantQuestions.forEach(questionNodes::add);
        return node;
    }

    private void restore(JsonNode data) {
        topic = null;
        answers.clear();
        assistantQuestions.clear();
        active = false;
        if (data == null || !data.isObject()) {
            return;
        }
        active = data.path("active").asBoolean(false);
        String restoredTopic = data.path("topic").asText("");
        topic = active ? normalizeTopic(restoredTopic) : null;
        copyTextArray(data.get("answers"), answers);
        copyTextArray(data.get("assistantQuestions"), assistantQuestions);
    }

    private static void copyTextArray(JsonNode node, List<String> target) {
        if (node == null || !node.isArray()) {
            return;
        }
        for (JsonNode item : node) {
            String value = item.asText("").trim();
            if (!value.isBlank()) {
                target.add(value);
            }
        }
    }

    private static Optional<String> summarizeAssistantQuestion(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        List<String> candidates = new ArrayList<>();
        for (String rawLine : text.split("\\R+")) {
            String line = cleanQuestionLine(rawLine);
            if (line.isBlank()) {
                continue;
            }
            if (line.contains("?") || line.contains("？")) {
                candidates.add(line);
            }
        }
        String summary = candidates.isEmpty() ? firstNonBlankLine(text) : candidates.getLast();
        summary = truncate(summary, MAX_QUESTION_SUMMARY_CHARS);
        return summary.isBlank() ? Optional.empty() : Optional.of(summary);
    }

    private static String assistantText(JsonNode message) {
        JsonNode content = message.get("content");
        if (content == null || content.isNull()) {
            return "";
        }
        if (content.isTextual()) {
            return content.asText();
        }
        if (!content.isArray()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (JsonNode item : content) {
            if (item.isTextual()) {
                parts.add(item.asText());
            } else if (item.path("text").isTextual()
                    && (!item.path("type").isTextual() || "text".equals(item.path("type").asText()))) {
                parts.add(item.path("text").asText());
            }
        }
        return String.join("\n", parts);
    }

    private static String firstNonBlankLine(String text) {
        for (String rawLine : text.split("\\R+")) {
            String line = cleanQuestionLine(rawLine);
            if (!line.isBlank()) {
                return line;
            }
        }
        return "";
    }

    private static String cleanQuestionLine(String line) {
        return line == null ? "" : line.trim()
                .replaceFirst("^[-*•]\\s+", "")
                .replaceFirst("^\\d+[.)]\\s+", "")
                .trim();
    }

    private static String truncate(String text, int maxChars) {
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, Math.max(0, maxChars - 3)).trim() + "...";
    }

    private static String normalizeTopic(String arguments) {
        return arguments == null || arguments.isBlank() ? DEFAULT_TOPIC : arguments.trim();
    }

    private static String controls() {
        return """
                Local interview controls:
                - /grill-me answer <answer> records the user's answer and asks for the next focused question.
                - /grill-me status shows the current topic, phase, and answer history.
                - /grill-me reset clears the active interview.
                """.trim();
    }
}
