package works.earendil.pi.codingagent.cli;

import works.earendil.pi.agent.core.AgentEvent;
import works.earendil.pi.ai.model.Content;
import works.earendil.pi.ai.model.Model;
import works.earendil.pi.ai.provider.Provider;
import works.earendil.pi.ai.stream.AssistantMessageEvent;
import works.earendil.pi.codingagent.core.AgentSession;
import works.earendil.pi.codingagent.core.AgentSessionRuntime;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public final class RpcModeRunner {

    private RpcModeRunner() {
    }

    public static int run(AgentSessionRuntime runtime, CliArgs args) {
        AgentSession session = runtime.session();

        AutoCloseable unsubscribe = session.subscribe(event -> {
            if (event instanceof AgentSession.AgentSessionEvent.AgentEventEnvelope env) {
                if (env.event() instanceof AgentEvent.MessageUpdate mu &&
                        mu.assistantMessageEvent() instanceof AssistantMessageEvent.ContentDelta cd &&
                        cd.content() instanceof Content.Text t) {
                    System.out.println("{\"jsonrpc\":\"2.0\",\"method\":\"event\",\"params\":{\"type\":\"content_delta\",\"text\":\"" + escapeJson(t.text()) + "\"}}");
                    System.out.flush();
                }
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
}
