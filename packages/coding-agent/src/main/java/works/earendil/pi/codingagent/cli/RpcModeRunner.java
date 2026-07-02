package works.earendil.pi.codingagent.cli;

import works.earendil.pi.agent.core.AgentEvent;
import works.earendil.pi.ai.model.Content;
import works.earendil.pi.ai.model.Model;
import works.earendil.pi.ai.provider.Provider;
import works.earendil.pi.ai.stream.AssistantMessageEvent;
import works.earendil.pi.codingagent.core.AgentSession;
import works.earendil.pi.codingagent.core.AgentSessionRuntime;
import works.earendil.pi.codingagent.core.SkillDiagnosticHistory;
import works.earendil.pi.codingagent.resources.SkillLoader;
import works.earendil.pi.codingagent.session.SessionManager;
import works.earendil.pi.common.json.JsonCodec;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.List;

public final class RpcModeRunner {

    private RpcModeRunner() {
    }

    public static int run(AgentSessionRuntime runtime, CliArgs args) {
        AgentSession session = runtime.session();
        SkillDiagnosticHistory skillDiagnostics = SkillDiagnosticHistory.fromSession(session.sessionManager());

        AutoCloseable unsubscribe = session.subscribe(event -> {
            if (event instanceof AgentSession.AgentSessionEvent.AgentEventEnvelope env) {
                if (env.event() instanceof AgentEvent.MessageUpdate mu &&
                        mu.assistantMessageEvent() instanceof AssistantMessageEvent.ContentDelta cd &&
                        cd.content() instanceof Content.Text t) {
                    System.out.println("{\"jsonrpc\":\"2.0\",\"method\":\"event\",\"params\":{\"type\":\"content_delta\",\"text\":\"" + escapeJson(t.text()) + "\"}}");
                    System.out.flush();
                }
            } else if (event instanceof AgentSession.AgentSessionEvent.SkillCommand skillCommand) {
                System.out.println("{\"jsonrpc\":\"2.0\",\"method\":\"event\",\"params\":{\"type\":\"skill_command\",\"phase\":\""
                        + escapeJson(skillCommand.phase()) + "\",\"skill\":\"" + escapeJson(skillCommand.skillName())
                        + "\",\"path\":" + jsonString(skillCommand.skillPath() == null ? null : skillCommand.skillPath().toString())
                        + ",\"message\":" + jsonString(skillCommand.message()) + "}}");
                System.out.flush();
            } else if (event instanceof AgentSession.AgentSessionEvent.SkillTriggerDiagnostic diagnostic) {
                skillDiagnostics.record(diagnostic);
                try {
                    skillDiagnostics.persist(session.sessionManager());
                } catch (Exception e) {
                    System.err.println("RPC skill diagnostics persist warning: " + e.getMessage());
                }
                System.out.println("{\"jsonrpc\":\"2.0\",\"method\":\"event\",\"params\":{\"type\":\"skill_trigger_diagnostic\",\"matches\":"
                        + skillMatchesJson(diagnostic.matches()) + "}}");
                System.out.flush();
            }
        });

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                String id = extractJsonField(trimmed, "id");
                String method = extractJsonField(trimmed, "method");
                if (method == null) {
                    continue;
                }

                if ("exit".equalsIgnoreCase(method)) {
                    if (id != null) {
                        sendResponse(id, "{\"status\":\"exiting\"}");
                    }
                    break;
                }

                if ("list_models".equalsIgnoreCase(method)) {
                    if (id != null) {
                        sendResponse(id, "{\"models\":" + modelsJson(runtime) + "}");
                    }
                    continue;
                }

                if ("refresh_models".equalsIgnoreCase(method)) {
                    String provider = extractJsonField(trimmed, "provider");
                    if (provider == null) {
                        provider = extractNestedField(trimmed, "params", "provider");
                    }
                    if (!runtime.services().modelRegistry().refresh(provider)) {
                        if (id != null) {
                            sendError(id, -32602, "Provider not found: " + provider);
                        }
                        continue;
                    }
                    if (id != null) {
                        String providerJson = provider == null || provider.isBlank()
                                ? "null"
                                : "\"" + escapeJson(provider) + "\"";
                        sendResponse(id, "{\"refreshed\":true,\"provider\":" + providerJson
                                + ",\"models\":" + modelsJson(runtime) + "}");
                    }
                    continue;
                }

                if ("prompt".equalsIgnoreCase(method)) {
                    String text = extractJsonField(trimmed, "text");
                    if (text == null) {
                        text = extractNestedField(trimmed, "params", "text");
                    }
                    if (text == null) text = "";
                    try {
                        session.prompt(text);
                        if (id != null) {
                            sendResponse(id, "{\"status\":\"ok\"}");
                        }
                    } catch (Exception e) {
                        if (id != null) {
                            sendError(id, -32000, e.getMessage());
                        }
                    }
                    continue;
                }

                if ("skill_diagnostic_sources".equalsIgnoreCase(method)) {
                    String limit = extractJsonField(trimmed, "limit");
                    String includeEmpty = extractJsonField(trimmed, "includeEmpty");
                    if (limit == null) {
                        limit = extractNestedField(trimmed, "params", "limit");
                    }
                    if (includeEmpty == null) {
                        includeEmpty = extractNestedField(trimmed, "params", "includeEmpty");
                    }
                    if (id != null) {
                        try {
                            sendResponse(id, JsonCodec.mapper().writeValueAsString(SkillDiagnosticHistory.sourceIndex(
                                    session.sessionManager(),
                                    parseNonNegativeInt(limit),
                                    parseBoolean(includeEmpty))));
                        } catch (Exception e) {
                            sendError(id, -32000, "Could not list skill diagnostic sources: " + e.getMessage());
                        }
                    }
                    continue;
                }

                if ("skill_diagnostic_picker".equalsIgnoreCase(method)) {
                    String limit = extractJsonField(trimmed, "limit");
                    String includeEmpty = extractJsonField(trimmed, "includeEmpty");
                    if (limit == null) {
                        limit = extractNestedField(trimmed, "params", "limit");
                    }
                    if (includeEmpty == null) {
                        includeEmpty = extractNestedField(trimmed, "params", "includeEmpty");
                    }
                    if (id != null) {
                        try {
                            sendResponse(id, JsonCodec.mapper().writeValueAsString(SkillDiagnosticHistory.sourcePicker(
                                    session.sessionManager(),
                                    parseNonNegativeInt(limit),
                                    parseBoolean(includeEmpty))));
                        } catch (Exception e) {
                            sendError(id, -32000, "Could not render skill diagnostic picker: " + e.getMessage());
                        }
                    }
                    continue;
                }

                if ("skill_diagnostics".equalsIgnoreCase(method)) {
                    String skill = extractJsonField(trimmed, "skill");
                    String modelFilter = extractJsonField(trimmed, "model");
                    String reason = extractJsonField(trimmed, "reason");
                    String offset = extractJsonField(trimmed, "offset");
                    String limit = extractJsonField(trimmed, "limit");
                    String sort = extractJsonField(trimmed, "sort");
                    String branch = extractJsonField(trimmed, "branch");
                    String sessionPath = extractJsonField(trimmed, "session");
                    if (skill == null) {
                        skill = extractNestedField(trimmed, "params", "skill");
                    }
                    if (modelFilter == null) {
                        modelFilter = extractNestedField(trimmed, "params", "model");
                    }
                    if (reason == null) {
                        reason = extractNestedField(trimmed, "params", "reason");
                    }
                    if (offset == null) {
                        offset = extractNestedField(trimmed, "params", "offset");
                    }
                    if (limit == null) {
                        limit = extractNestedField(trimmed, "params", "limit");
                    }
                    if (sort == null) {
                        sort = extractNestedField(trimmed, "params", "sort");
                    }
                    if (branch == null) {
                        branch = extractNestedField(trimmed, "params", "branch");
                    }
                    if (sessionPath == null) {
                        sessionPath = extractNestedField(trimmed, "params", "session");
                    }
                    if (id != null) {
                        try {
                            SessionManager diagnosticsSession = diagnosticsSession(session.sessionManager(), sessionPath);
                            SkillDiagnosticHistory scopedDiagnostics = useCurrentDiagnostics(sessionPath, branch)
                                    ? skillDiagnostics
                                    : SkillDiagnosticHistory.fromSession(diagnosticsSession, branch);
                            sendResponse(id, JsonCodec.mapper().writeValueAsString(scopedDiagnostics.toJson(
                                    new SkillDiagnosticHistory.Query(
                                            new SkillDiagnosticHistory.Filter(skill, modelFilter, reason),
                                            parseNonNegativeInt(offset),
                                            parseNonNegativeInt(limit),
                                            sort,
                                            true),
                                    SkillDiagnosticHistory.Source.from(diagnosticsSession, branch))));
                        } catch (Exception e) {
                            sendError(id, -32602, "Skill diagnostics source not found: " + e.getMessage());
                        }
                    }
                    continue;
                }

                if (id != null) {
                    sendError(id, -32601, "Method not found: " + method);
                }
            }
            return 0;
        } catch (Exception e) {
            System.err.println("RPC session error: " + e.getMessage());
            return 1;
        } finally {
            try {
                unsubscribe.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static void sendResponse(String id, String resultJson) {
        System.out.println("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":" + resultJson + "}");
        System.out.flush();
    }

    private static void sendError(String id, int code, String message) {
        System.out.println("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"error\":{\"code\":" + code + ",\"message\":\"" + escapeJson(message) + "\"}}");
        System.out.flush();
    }

    private static String modelsJson(AgentSessionRuntime runtime) {
        StringBuilder modelsJson = new StringBuilder("[");
        boolean first = true;
        for (Model m : runtime.services().modelRegistry().getAll()) {
            if (!first) modelsJson.append(",");
            modelsJson.append("{\"provider\":\"").append(escapeJson(m.provider()))
                    .append("\",\"model\":\"").append(escapeJson(m.modelId()))
                    .append("\",\"name\":\"").append(escapeJson(m.displayName())).append("\"}");
            first = false;
        }
        modelsJson.append("]");
        return modelsJson.toString();
    }

    private static String extractJsonField(String json, String field) {
        String key = "\"" + field + "\"";
        int idx = json.indexOf(key);
        if (idx < 0) return null;
        int colonIdx = json.indexOf(':', idx + key.length());
        if (colonIdx < 0) return null;
        int startIdx = colonIdx + 1;
        while (startIdx < json.length() && Character.isWhitespace(json.charAt(startIdx))) {
            startIdx++;
        }
        if (startIdx >= json.length()) return null;
        if (json.charAt(startIdx) == '"') {
            int endIdx = json.indexOf('"', startIdx + 1);
            while (endIdx > 0 && json.charAt(endIdx - 1) == '\\') {
                endIdx = json.indexOf('"', endIdx + 1);
            }
            if (endIdx < 0) return null;
            return json.substring(startIdx + 1, endIdx).replace("\\\"", "\"").replace("\\n", "\n");
        } else {
            int endIdx = startIdx;
            while (endIdx < json.length() && (Character.isLetterOrDigit(json.charAt(endIdx)) || json.charAt(endIdx) == '-' || json.charAt(endIdx) == '.')) {
                endIdx++;
            }
            return json.substring(startIdx, endIdx);
        }
    }

    private static String extractNestedField(String json, String parentField, String field) {
        String parentKey = "\"" + parentField + "\"";
        int idx = json.indexOf(parentKey);
        if (idx < 0) return null;
        int braceIdx = json.indexOf('{', idx + parentKey.length());
        if (braceIdx < 0) return null;
        int endBraceIdx = json.indexOf('}', braceIdx);
        if (endBraceIdx < 0) return null;
        return extractJsonField(json.substring(braceIdx, endBraceIdx + 1), field);
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String jsonString(String value) {
        return value == null ? "null" : "\"" + escapeJson(value) + "\"";
    }

    private static int parseNonNegativeInt(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean useCurrentDiagnostics(String sessionPath, String branch) {
        return (sessionPath == null || sessionPath.isBlank()) && (branch == null || branch.isBlank());
    }

    private static boolean parseBoolean(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return switch (value.trim().toLowerCase()) {
            case "1", "true", "yes", "y", "on" -> true;
            default -> false;
        };
    }

    private static SessionManager diagnosticsSession(SessionManager current, String sessionPath) throws Exception {
        if (sessionPath == null || sessionPath.isBlank()) {
            return current;
        }
        Path path = Path.of(sessionPath.trim());
        if (!path.isAbsolute()) {
            path = current.cwd().resolve(path).normalize();
        }
        return SessionManager.open(path);
    }

    private static String skillMatchesJson(List<SkillLoader.SkillTriggerMatch> matches) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < matches.size(); i++) {
            SkillLoader.SkillTriggerMatch match = matches.get(i);
            if (i > 0) {
                out.append(',');
            }
            out.append("{\"skill\":\"").append(escapeJson(match.skillName())).append("\",")
                    .append("\"path\":").append(jsonString(match.skillPath() == null ? null : match.skillPath().toString())).append(',')
                    .append("\"modelVisible\":").append(match.modelVisible()).append(',')
                    .append("\"reasons\":").append(stringArrayJson(match.reasons())).append('}');
        }
        out.append(']');
        return out.toString();
    }

    private static String stringArrayJson(List<String> values) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(jsonString(values.get(i)));
        }
        out.append(']');
        return out.toString();
    }
}
