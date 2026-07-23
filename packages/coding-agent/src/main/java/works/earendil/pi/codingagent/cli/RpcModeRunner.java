package works.earendil.pi.codingagent.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import works.earendil.pi.agent.core.AgentEvent;
import works.earendil.pi.agent.session.SessionEntry;
import works.earendil.pi.ai.model.Content;
import works.earendil.pi.ai.model.Model;
import works.earendil.pi.ai.provider.Provider;
import works.earendil.pi.ai.stream.AssistantMessageEvent;
import works.earendil.pi.codingagent.core.AgentSession;
import works.earendil.pi.codingagent.core.AgentSessionRuntime;
import works.earendil.pi.codingagent.core.SkillDiagnosticHistory;
import works.earendil.pi.codingagent.resources.SkillLoader;
import works.earendil.pi.codingagent.session.SessionFileInfo;
import works.earendil.pi.codingagent.session.SessionManager;
import works.earendil.pi.common.json.JsonCodec;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class RpcModeRunner {

    private RpcModeRunner() {
    }

    public static int run(AgentSessionRuntime runtime, CliArgs args) {
        try (RpcSessionState state = new RpcSessionState(runtime.session());
             BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
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

                if ("get_available_thinking_levels".equalsIgnoreCase(method)) {
                    if (id != null) {
                        String levels = state.session.availableThinkingLevels().stream()
                                .map(level -> "\"" + level.wireName() + "\"")
                                .reduce((left, right) -> left + "," + right)
                                .orElse("");
                        sendResponse(id, "{\"levels\":[" + levels + "]}");
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
                        state.session.prompt(text);
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

                if ("session_info".equalsIgnoreCase(method)) {
                    String branch = extractJsonField(trimmed, "branch");
                    String sessionPath = extractJsonField(trimmed, "session");
                    String sessionId = extractJsonField(trimmed, "sessionId");
                    String index = extractJsonField(trimmed, "index");
                    String all = extractJsonField(trimmed, "all");
                    String query = extractJsonField(trimmed, "query");
                    String sort = extractJsonField(trimmed, "sort");
                    if (branch == null) {
                        branch = extractNestedField(trimmed, "params", "branch");
                    }
                    if (sessionPath == null) {
                        sessionPath = extractNestedField(trimmed, "params", "session");
                    }
                    if (sessionId == null) {
                        sessionId = extractNestedField(trimmed, "params", "sessionId");
                    }
                    if (index == null) {
                        index = extractNestedField(trimmed, "params", "index");
                    }
                    if (all == null) {
                        all = extractNestedField(trimmed, "params", "all");
                    }
                    if (query == null) {
                        query = extractNestedField(trimmed, "params", "query");
                    }
                    if (sort == null) {
                        sort = extractNestedField(trimmed, "params", "sort");
                    }
                    if (id != null) {
                        try {
                            ResolvedSessionTarget resolvedTarget = hasSessionTarget(sessionPath, sessionId, index)
                                    ? resolveSessionTarget(state.session.sessionManager(), sessionPath, sessionId, index,
                                    parseBoolean(all), query, normalizeSessionListSort(sort), "info")
                                    : null;
                            SessionManager infoSession = resolvedTarget == null
                                    ? state.session.sessionManager()
                                    : SessionManager.open(resolvedTarget.path());
                            sendResponse(id, sessionInfoJson(state.session, infoSession, branch, resolvedTarget));
                        } catch (IllegalArgumentException e) {
                            sendError(id, -32602, e.getMessage());
                        } catch (Exception e) {
                            sendError(id, -32000, "Could not build session info: " + e.getMessage());
                        }
                    }
                    continue;
                }

                if ("session_tree".equalsIgnoreCase(method)) {
                    String branch = extractJsonField(trimmed, "branch");
                    String sessionPath = extractJsonField(trimmed, "session");
                    String flat = extractJsonField(trimmed, "flat");
                    String query = extractJsonField(trimmed, "query");
                    String offset = extractJsonField(trimmed, "offset");
                    String limit = extractJsonField(trimmed, "limit");
                    String collapsed = firstNonBlank(extractJsonArrayField(trimmed, "collapsed"),
                            extractJsonField(trimmed, "collapsed"));
                    String collapsedIds = firstNonBlank(extractJsonArrayField(trimmed, "collapsedIds"),
                            extractJsonField(trimmed, "collapsedIds"));
                    if (branch == null) {
                        branch = extractNestedField(trimmed, "params", "branch");
                    }
                    if (sessionPath == null) {
                        sessionPath = extractNestedField(trimmed, "params", "session");
                    }
                    if (flat == null) {
                        flat = extractNestedField(trimmed, "params", "flat");
                    }
                    if (query == null) {
                        query = extractNestedField(trimmed, "params", "query");
                    }
                    if (offset == null) {
                        offset = extractNestedField(trimmed, "params", "offset");
                    }
                    if (limit == null) {
                        limit = extractNestedField(trimmed, "params", "limit");
                    }
                    if (collapsed == null) {
                        collapsed = firstNonBlank(extractNestedArrayField(trimmed, "params", "collapsed"),
                                extractNestedField(trimmed, "params", "collapsed"));
                    }
                    if (collapsedIds == null) {
                        collapsedIds = firstNonBlank(extractNestedArrayField(trimmed, "params", "collapsedIds"),
                                extractNestedField(trimmed, "params", "collapsedIds"));
                    }
                    if (id != null) {
                        try {
                            SessionManager treeSession = diagnosticsSession(state.session.sessionManager(), sessionPath);
                            sendResponse(id, sessionTreeJson(treeSession, branch, parseBoolean(flat), query,
                                    parseNonNegativeInt(offset), parseOptionalPositiveInt(limit),
                                    parseIdSet(collapsedIds == null ? collapsed : collapsedIds)));
                        } catch (IllegalArgumentException e) {
                            sendError(id, -32602, e.getMessage());
                        } catch (Exception e) {
                            sendError(id, -32000, "Could not build session tree: " + e.getMessage());
                        }
                    }
                    continue;
                }

                if ("session_user_messages".equalsIgnoreCase(method)) {
                    String branch = extractJsonField(trimmed, "branch");
                    String sessionPath = extractJsonField(trimmed, "session");
                    String sessionId = extractJsonField(trimmed, "sessionId");
                    String index = extractJsonField(trimmed, "index");
                    String all = extractJsonField(trimmed, "all");
                    String sessionQuery = extractJsonField(trimmed, "sessionQuery");
                    String sort = extractJsonField(trimmed, "sort");
                    String query = extractJsonField(trimmed, "query");
                    String offset = extractJsonField(trimmed, "offset");
                    String limit = extractJsonField(trimmed, "limit");
                    if (branch == null) {
                        branch = extractNestedField(trimmed, "params", "branch");
                    }
                    if (sessionPath == null) {
                        sessionPath = extractNestedField(trimmed, "params", "session");
                    }
                    if (sessionId == null) {
                        sessionId = extractNestedField(trimmed, "params", "sessionId");
                    }
                    if (index == null) {
                        index = extractNestedField(trimmed, "params", "index");
                    }
                    if (all == null) {
                        all = extractNestedField(trimmed, "params", "all");
                    }
                    if (sessionQuery == null) {
                        sessionQuery = extractNestedField(trimmed, "params", "sessionQuery");
                    }
                    if (sort == null) {
                        sort = extractNestedField(trimmed, "params", "sort");
                    }
                    if (query == null) {
                        query = extractNestedField(trimmed, "params", "query");
                    }
                    if (offset == null) {
                        offset = extractNestedField(trimmed, "params", "offset");
                    }
                    if (limit == null) {
                        limit = extractNestedField(trimmed, "params", "limit");
                    }
                    if (id != null) {
                        try {
                            ResolvedSessionTarget resolvedTarget = hasSessionTarget(null, sessionId, index)
                                    ? resolveSessionTarget(state.session.sessionManager(), null, sessionId, index,
                                    parseBoolean(all), sessionQuery, normalizeSessionListSort(sort), "user messages")
                                    : null;
                            SessionManager targetSession = resolvedTarget == null
                                    ? diagnosticsSession(state.session.sessionManager(), sessionPath)
                                    : SessionManager.open(resolvedTarget.path());
                            sendResponse(id, sessionUserMessagesJson(targetSession, branch, query,
                                    parseNonNegativeInt(offset), parsePositiveInt(limit, 20), resolvedTarget));
                        } catch (IllegalArgumentException e) {
                            sendError(id, -32602, e.getMessage());
                        } catch (Exception e) {
                            sendError(id, -32000, "Could not list session user messages: " + e.getMessage());
                        }
                    }
                    continue;
                }

                if ("session_list".equalsIgnoreCase(method)) {
                    String all = extractJsonField(trimmed, "all");
                    String query = extractJsonField(trimmed, "query");
                    String offset = extractJsonField(trimmed, "offset");
                    String limit = extractJsonField(trimmed, "limit");
                    String sort = extractJsonField(trimmed, "sort");
                    if (all == null) {
                        all = extractNestedField(trimmed, "params", "all");
                    }
                    if (query == null) {
                        query = extractNestedField(trimmed, "params", "query");
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
                    if (id != null) {
                        try {
                            sendResponse(id, sessionListJson(state.session.sessionManager(),
                                    parseBoolean(all), query, parseNonNegativeInt(offset),
                                    parsePositiveInt(limit, 20), normalizeSessionListSort(sort)));
                        } catch (Exception e) {
                            sendError(id, -32000, "Could not list sessions: " + e.getMessage());
                        }
                    }
                    continue;
                }

                if ("session_rename".equalsIgnoreCase(method)) {
                    String sessionPath = extractJsonField(trimmed, "session");
                    String sessionId = extractJsonField(trimmed, "sessionId");
                    String index = extractJsonField(trimmed, "index");
                    String all = extractJsonField(trimmed, "all");
                    String query = extractJsonField(trimmed, "query");
                    String sort = extractJsonField(trimmed, "sort");
                    String name = extractJsonField(trimmed, "name");
                    String clear = extractJsonField(trimmed, "clear");
                    if (sessionPath == null) {
                        sessionPath = extractNestedField(trimmed, "params", "session");
                    }
                    if (sessionId == null) {
                        sessionId = extractNestedField(trimmed, "params", "sessionId");
                    }
                    if (index == null) {
                        index = extractNestedField(trimmed, "params", "index");
                    }
                    if (all == null) {
                        all = extractNestedField(trimmed, "params", "all");
                    }
                    if (query == null) {
                        query = extractNestedField(trimmed, "params", "query");
                    }
                    if (sort == null) {
                        sort = extractNestedField(trimmed, "params", "sort");
                    }
                    if (name == null) {
                        name = extractNestedField(trimmed, "params", "name");
                    }
                    if (clear == null) {
                        clear = extractNestedField(trimmed, "params", "clear");
                    }
                    if (id != null) {
                        try {
                            ResolvedSessionTarget resolvedTarget = hasSessionTarget(sessionPath, sessionId, index)
                                    ? resolveSessionTarget(state.session.sessionManager(), sessionPath, sessionId, index,
                                    parseBoolean(all), query, normalizeSessionListSort(sort), "rename")
                                    : null;
                            SessionManager target = resolvedTarget == null
                                    ? state.session.sessionManager()
                                    : SessionManager.open(resolvedTarget.path());
                            sendResponse(id, sessionRenameJson(target, name, parseBoolean(clear), resolvedTarget));
                        } catch (IllegalArgumentException e) {
                            sendError(id, -32602, e.getMessage());
                        } catch (Exception e) {
                            sendError(id, -32000, "Could not rename session: " + e.getMessage());
                        }
                    }
                    continue;
                }

                if ("session_delete".equalsIgnoreCase(method)) {
                    String sessionPath = extractJsonField(trimmed, "session");
                    String sessionId = extractJsonField(trimmed, "sessionId");
                    String index = extractJsonField(trimmed, "index");
                    String all = extractJsonField(trimmed, "all");
                    String query = extractJsonField(trimmed, "query");
                    String sort = extractJsonField(trimmed, "sort");
                    if (sessionPath == null) {
                        sessionPath = extractNestedField(trimmed, "params", "session");
                    }
                    if (sessionId == null) {
                        sessionId = extractNestedField(trimmed, "params", "sessionId");
                    }
                    if (index == null) {
                        index = extractNestedField(trimmed, "params", "index");
                    }
                    if (all == null) {
                        all = extractNestedField(trimmed, "params", "all");
                    }
                    if (query == null) {
                        query = extractNestedField(trimmed, "params", "query");
                    }
                    if (sort == null) {
                        sort = extractNestedField(trimmed, "params", "sort");
                    }
                    if (id != null) {
                        try {
                            ResolvedSessionTarget target = resolveSessionTarget(state.session.sessionManager(),
                                    sessionPath, sessionId, index, parseBoolean(all), query,
                                    normalizeSessionListSort(sort), "delete");
                            sendResponse(id, sessionDeleteJson(state.session.sessionManager(), target));
                        } catch (IllegalArgumentException e) {
                            sendError(id, -32602, e.getMessage());
                        } catch (Exception e) {
                            sendError(id, -32000, "Could not delete session: " + e.getMessage());
                        }
                    }
                    continue;
                }

                if ("session_switch".equalsIgnoreCase(method) || "session_resume".equalsIgnoreCase(method)) {
                    String sessionPath = extractJsonField(trimmed, "session");
                    String sessionId = extractJsonField(trimmed, "sessionId");
                    String index = extractJsonField(trimmed, "index");
                    String all = extractJsonField(trimmed, "all");
                    String query = extractJsonField(trimmed, "query");
                    String sort = extractJsonField(trimmed, "sort");
                    if (sessionPath == null) {
                        sessionPath = extractNestedField(trimmed, "params", "session");
                    }
                    if (sessionId == null) {
                        sessionId = extractNestedField(trimmed, "params", "sessionId");
                    }
                    if (index == null) {
                        index = extractNestedField(trimmed, "params", "index");
                    }
                    if (all == null) {
                        all = extractNestedField(trimmed, "params", "all");
                    }
                    if (query == null) {
                        query = extractNestedField(trimmed, "params", "query");
                    }
                    if (sort == null) {
                        sort = extractNestedField(trimmed, "params", "sort");
                    }
                    if (id != null) {
                        try {
                            ResolvedSessionTarget target = resolveSessionTarget(state.session.sessionManager(),
                                    sessionPath, sessionId, index, parseBoolean(all), query,
                                    normalizeSessionListSort(sort), "switch");
                            Path targetPath = target.path();
                            AgentSessionRuntime.ReplacementResult result = runtime.switchSession(targetPath, null);
                            state.rebind(runtime.session());
                            sendResponse(id, sessionSwitchJson(state.session, result, target));
                        } catch (IllegalArgumentException e) {
                            sendError(id, -32602, e.getMessage());
                        } catch (Exception e) {
                            sendError(id, -32000, "Could not switch session: " + e.getMessage());
                        }
                    }
                    continue;
                }

                if ("session_fork".equalsIgnoreCase(method)) {
                    String entryId = extractJsonField(trimmed, "entryId");
                    String forkBeforeEntryId = extractJsonField(trimmed, "forkBeforeEntryId");
                    String forkAtEntryId = extractJsonField(trimmed, "forkAtEntryId");
                    String index = extractJsonField(trimmed, "index");
                    String branchIndex = extractJsonField(trimmed, "branchIndex");
                    String branch = extractJsonField(trimmed, "branch");
                    String position = extractJsonField(trimmed, "position");
                    if (entryId == null) {
                        entryId = extractNestedField(trimmed, "params", "entryId");
                    }
                    if (forkBeforeEntryId == null) {
                        forkBeforeEntryId = extractNestedField(trimmed, "params", "forkBeforeEntryId");
                    }
                    if (forkAtEntryId == null) {
                        forkAtEntryId = extractNestedField(trimmed, "params", "forkAtEntryId");
                    }
                    if (index == null) {
                        index = extractNestedField(trimmed, "params", "index");
                    }
                    if (branchIndex == null) {
                        branchIndex = extractNestedField(trimmed, "params", "branchIndex");
                    }
                    if (branch == null) {
                        branch = extractNestedField(trimmed, "params", "branch");
                    }
                    if (position == null) {
                        position = extractNestedField(trimmed, "params", "position");
                    }
                    if (id != null) {
                        try {
                            ResolvedForkEntry resolvedForkEntry = resolveForkEntry(state.session.sessionManager(), branch,
                                    entryId, forkBeforeEntryId, forkAtEntryId, index, branchIndex);
                            AgentSessionRuntime.ForkPosition forkPosition = resolvedForkEntry.position() == null
                                    ? parseForkPosition(position) : resolvedForkEntry.position();
                            AgentSessionRuntime.ReplacementResult result = runtime.fork(resolvedForkEntry.entryId(), forkPosition);
                            state.rebind(runtime.session());
                            sendResponse(id, sessionForkJson(state.session, result, forkPosition, resolvedForkEntry));
                        } catch (IllegalArgumentException e) {
                            sendError(id, -32602, e.getMessage());
                        } catch (Exception e) {
                            sendError(id, -32000, "Could not fork session: " + e.getMessage());
                        }
                    }
                    continue;
                }

                if ("session_clone".equalsIgnoreCase(method)) {
                    if (id != null) {
                        try {
                            String leafId = state.session.sessionManager().leafId().orElse(null);
                            AgentSessionRuntime.ReplacementResult result = leafId == null
                                    ? runtime.newSession(state.session.sessionFile().orElse(null))
                                    : runtime.fork(leafId, AgentSessionRuntime.ForkPosition.AT);
                            state.rebind(runtime.session());
                            sendResponse(id, sessionCloneJson(state.session, result));
                        } catch (IllegalArgumentException e) {
                            sendError(id, -32602, e.getMessage());
                        } catch (Exception e) {
                            sendError(id, -32000, "Could not clone session: " + e.getMessage());
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
                                    state.session.sessionManager(),
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
                                    state.session.sessionManager(),
                                    parseNonNegativeInt(limit),
                                    parseBoolean(includeEmpty))));
                        } catch (Exception e) {
                            sendError(id, -32000, "Could not render skill diagnostic picker: " + e.getMessage());
                        }
                    }
                    continue;
                }

                if ("skill_diagnostic_inspect".equalsIgnoreCase(method)) {
                    String index = extractJsonField(trimmed, "index");
                    String branch = extractJsonField(trimmed, "branch");
                    String sessionPath = extractJsonField(trimmed, "session");
                    String skill = extractJsonField(trimmed, "skill");
                    String modelFilter = extractJsonField(trimmed, "model");
                    String reason = extractJsonField(trimmed, "reason");
                    String limit = extractJsonField(trimmed, "limit");
                    if (index == null) index = extractNestedField(trimmed, "params", "index");
                    if (branch == null) branch = extractNestedField(trimmed, "params", "branch");
                    if (sessionPath == null) sessionPath = extractNestedField(trimmed, "params", "session");
                    if (skill == null) skill = extractNestedField(trimmed, "params", "skill");
                    if (modelFilter == null) modelFilter = extractNestedField(trimmed, "params", "model");
                    if (reason == null) reason = extractNestedField(trimmed, "params", "reason");
                    if (limit == null) limit = extractNestedField(trimmed, "params", "limit");

                    if (id != null) {
                        try {
                            String selector = "";
                            if (index != null && !index.isBlank()) {
                                selector = index;
                            } else {
                                List<String> parts = new ArrayList<>();
                                if (sessionPath != null && !sessionPath.isBlank()) {
                                    parts.add("session=" + sessionPath);
                                }
                                if (branch != null && !branch.isBlank()) {
                                    parts.add("branch=" + branch);
                                }
                                selector = String.join(" ", parts);
                            }
                            int entryLimit = limit == null ? 10 : parseNonNegativeInt(limit);
                            JsonNode inspectResult = SkillDiagnosticHistory.inspect(
                                    state.session.sessionManager(),
                                    selector,
                                    new SkillDiagnosticHistory.Query(
                                            new SkillDiagnosticHistory.Filter(
                                                    skill == null ? "" : skill,
                                                    modelFilter == null ? "" : modelFilter,
                                                    reason == null ? "" : reason
                                            ),
                                            0, entryLimit, "newest", true
                                    ),
                                    50, false
                            );
                            sendResponse(id, JsonCodec.mapper().writeValueAsString(inspectResult));
                        } catch (Exception e) {
                            sendError(id, -32000, "Could not inspect skill diagnostics: " + e.getMessage());
                        }
                    }
                    continue;
                }

                if ("skill_recommend".equalsIgnoreCase(method) || "skill_search_and_recommend".equalsIgnoreCase(method)) {
                    String queryText = extractJsonField(trimmed, "query");
                    String reasonFilter = extractJsonField(trimmed, "reason");
                    String filterByReasonStr = extractJsonField(trimmed, "filterByReason");
                    String limitStr = extractJsonField(trimmed, "limit");
                    if (queryText == null) queryText = extractNestedField(trimmed, "params", "query");
                    if (reasonFilter == null) reasonFilter = extractNestedField(trimmed, "params", "reason");
                    if (filterByReasonStr == null) filterByReasonStr = extractNestedField(trimmed, "params", "filterByReason");
                    if (limitStr == null) limitStr = extractNestedField(trimmed, "params", "limit");

                    if (id != null) {
                        try {
                            boolean filterByReason = parseBoolean(filterByReasonStr);
                            int limit = limitStr == null ? 10 : parseNonNegativeInt(limitStr);
                            SkillLoader.SkillRecommendationQuery req = new SkillLoader.SkillRecommendationQuery(
                                    queryText == null ? "" : queryText,
                                    reasonFilter == null ? "" : reasonFilter,
                                    filterByReason, true, limit);
                            List<works.earendil.pi.codingagent.resources.Skill> loadedSkills = runtime.services().resourceLoader().skills().skills();
                            sendResponse(id, JsonCodec.mapper().writeValueAsString(SkillLoader.recommendSkills(loadedSkills, req)));
                        } catch (Exception e) {
                            sendError(id, -32000, "Could not recommend skills: " + e.getMessage());
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
                            SessionManager diagnosticsSession = diagnosticsSession(state.session.sessionManager(), sessionPath);
                            SkillDiagnosticHistory scopedDiagnostics = useCurrentDiagnostics(sessionPath, branch)
                                    ? state.skillDiagnostics
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
        }
    }

    private static final class RpcSessionState implements AutoCloseable {
        private AgentSession session;
        private SkillDiagnosticHistory skillDiagnostics;
        private AutoCloseable unsubscribe;

        RpcSessionState(AgentSession session) {
            rebind(session);
        }

        void rebind(AgentSession nextSession) {
            closeQuietly();
            this.session = nextSession;
            this.skillDiagnostics = SkillDiagnosticHistory.fromSession(nextSession.sessionManager());
            this.unsubscribe = nextSession.subscribe(event -> {
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
                        skillDiagnostics.persist(this.session.sessionManager());
                    } catch (Exception e) {
                        System.err.println("RPC skill diagnostics persist warning: " + e.getMessage());
                    }
                    System.out.println("{\"jsonrpc\":\"2.0\",\"method\":\"event\",\"params\":{\"type\":\"skill_trigger_diagnostic\",\"matches\":"
                            + skillMatchesJson(diagnostic.matches()) + "}}");
                    System.out.flush();
                }
            });
        }

        @Override
        public void close() {
            closeQuietly();
        }

        private void closeQuietly() {
            if (unsubscribe == null) {
                return;
            }
            try {
                unsubscribe.close();
            } catch (Exception ignored) {
            }
            unsubscribe = null;
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

    private static String sessionInfoJson(AgentSession session, SessionManager manager, String branch,
                                          ResolvedSessionTarget target) throws Exception {
        boolean currentRuntime = manager == session.sessionManager() && (branch == null || branch.isBlank());
        String selectedLeaf = selectedLeaf(manager, branch);
        List<SessionEntry> branchEntries = manager.branch("root".equals(selectedLeaf) ? null : selectedLeaf);
        RpcSessionStats stats = currentRuntime
                ? RpcSessionStats.from(session.stats())
                : RpcSessionStats.from(manager, branchEntries);
        ObjectNode root = JsonCodec.mapper().createObjectNode();
        root.put("schemaVersion", 1);
        root.put("sessionId", manager.sessionId());
        manager.sessionName().ifPresentOrElse(name -> root.put("name", name), () -> root.putNull("name"));
        root.put("sessionFile", stats.sessionFile() == null ? null : stats.sessionFile().toString());
        root.put("cwd", manager.cwd().toString());
        root.put("persisted", manager.isPersisted());
        root.put("leaf", selectedLeaf);
        root.put("entries", manager.entries().size());
        root.put("branchEntries", branchEntries.size());
        if (currentRuntime) {
            root.put("thinking", session.thinkingLevel().wireName());
            root.put("skills", session.skills().size());
            root.put("tools", session.tools().size());
        } else {
            root.putNull("thinking");
            root.put("skills", 0);
            root.put("tools", 0);
        }

        ObjectNode modelNode = root.putObject("model");
        if (!currentRuntime || session.model() == null) {
            modelNode.putNull("provider");
            modelNode.putNull("model");
            modelNode.putNull("name");
        } else {
            modelNode.put("provider", session.model().provider());
            modelNode.put("model", session.model().modelId());
            modelNode.put("name", session.model().displayName());
        }

        ObjectNode messages = root.putObject("messages");
        messages.put("user", stats.userMessages());
        messages.put("assistant", stats.assistantMessages());
        messages.put("tool", stats.toolResults());
        messages.put("total", stats.totalMessages());

        ObjectNode tokens = root.putObject("tokens");
        tokens.put("input", stats.inputTokens());
        tokens.put("output", stats.outputTokens());
        tokens.put("cache", stats.cacheInputTokens());
        tokens.put("reasoning", stats.reasoningTokens());
        tokens.put("total", stats.totalTokens());

        ObjectNode sources = root.putObject("sources");
        addSourceCounts(sources.putObject("messages"), collectMessageSources(branchEntries));
        addSourceCounts(sources.putObject("customMessages"), collectCustomMessageSources(branchEntries));
        if (target != null) {
            addResolvedSessionTarget(root, target);
        }
        return JsonCodec.mapper().writeValueAsString(root);
    }

    private static String selectedLeaf(SessionManager manager, String branch) {
        if (branch == null || branch.isBlank()) {
            return manager.leafId().orElse("root");
        }
        String selected = branch.trim();
        if (!"root".equals(selected) && manager.entry(selected).isEmpty()) {
            throw new IllegalArgumentException("Session info branch not found: " + selected);
        }
        return selected;
    }

    private static ResolvedForkEntry resolveForkEntry(SessionManager manager, String branch, String entryId,
                                                      String forkBeforeEntryId, String forkAtEntryId,
                                                      String index, String branchIndex) {
        if (forkBeforeEntryId != null && !forkBeforeEntryId.isBlank()) {
            return new ResolvedForkEntry(forkBeforeEntryId.trim(), AgentSessionRuntime.ForkPosition.BEFORE,
                    "forkBeforeEntryId");
        }
        if (forkAtEntryId != null && !forkAtEntryId.isBlank()) {
            return new ResolvedForkEntry(forkAtEntryId.trim(), AgentSessionRuntime.ForkPosition.AT,
                    "forkAtEntryId");
        }
        if (entryId != null && !entryId.isBlank()) {
            return new ResolvedForkEntry(entryId.trim(), null, "entryId");
        }
        Integer treeIndex = parseOptionalPositiveInt(index);
        if (treeIndex != null) {
            SessionEntry entry = sessionTreeEntryByIndex(manager.tree(), treeIndex, new int[]{1});
            if (entry == null) {
                throw new IllegalArgumentException("Session fork index not found: " + treeIndex);
            }
            return new ResolvedForkEntry(entry.id(), null, "index");
        }
        Integer selectedBranchIndex = parseOptionalPositiveInt(branchIndex);
        if (selectedBranchIndex != null) {
            String selectedLeaf = selectedLeaf(manager, branch);
            List<SessionEntry> branchEntries = manager.branch("root".equals(selectedLeaf) ? null : selectedLeaf);
            if (selectedBranchIndex > branchEntries.size()) {
                throw new IllegalArgumentException("Session fork branchIndex not found: " + selectedBranchIndex);
            }
            return new ResolvedForkEntry(branchEntries.get(selectedBranchIndex - 1).id(), null, "branchIndex");
        }
        throw new IllegalArgumentException("Session fork requires params.entryId, forkBeforeEntryId, forkAtEntryId, index, or branchIndex");
    }

    private static SessionEntry sessionTreeEntryByIndex(List<SessionManager.SessionTreeNode> nodes, int target,
                                                        int[] index) {
        for (SessionManager.SessionTreeNode node : nodes) {
            if (index[0] == target) {
                return node.entry();
            }
            index[0]++;
            SessionEntry child = sessionTreeEntryByIndex(node.children(), target, index);
            if (child != null) {
                return child;
            }
        }
        return null;
    }

    private static String sessionListJson(SessionManager manager, boolean all, String query, int offset, int limit,
                                          String sort) throws Exception {
        List<SessionFileInfo> sessions = sortSessions(filterSessions(loadSessions(manager, all), query), sort);
        int start = Math.min(offset, sessions.size());
        int end = Math.min(start + limit, sessions.size());
        ObjectNode root = JsonCodec.mapper().createObjectNode();
        root.put("schemaVersion", 1);
        root.put("scope", all ? "all" : "project");
        root.put("query", query == null || query.isBlank() ? null : query.trim());
        root.put("sort", sort);
        root.put("total", sessions.size());
        root.put("offset", offset);
        root.put("limit", limit);
        root.put("returned", end - start);
        root.put("hasMore", end < sessions.size());
        root.put("currentSessionId", manager.sessionId());
        root.put("currentSessionFile", manager.sessionFile().map(Path::toString).orElse(null));
        ArrayNode items = root.putArray("items");
        Path currentPath = manager.sessionFile().orElse(null);
        for (int i = start; i < end; i++) {
            SessionFileInfo info = sessions.get(i);
            ObjectNode item = items.addObject();
            item.put("index", i + 1);
            item.put("sessionId", info.id());
            item.put("name", info.name());
            item.put("path", info.path() == null ? null : info.path().toString());
            item.put("cwd", info.cwd() == null ? null : info.cwd().toString());
            item.put("parentSessionPath", info.parentSessionPath() == null ? null : info.parentSessionPath().toString());
            item.put("created", info.created() == null ? null : info.created().toString());
            item.put("modified", info.modified() == null ? null : info.modified().toString());
            item.put("messageCount", info.messageCount());
            item.put("firstMessage", info.firstMessage());
            item.put("current", currentPath != null && currentPath.equals(info.path()));
        }
        return JsonCodec.mapper().writeValueAsString(root);
    }

    private static String sessionUserMessagesJson(SessionManager manager, String branch, String query, int offset,
                                                  int limit, ResolvedSessionTarget target) throws Exception {
        String selectedLeaf = selectedLeaf(manager, branch);
        List<SessionEntry> branchEntries = manager.branch("root".equals(selectedLeaf) ? null : selectedLeaf);
        String normalizedQuery = query == null || query.isBlank() ? "" : query.trim();
        List<ObjectNode> matched = new ArrayList<>();
        int branchIndex = 0;
        for (SessionEntry entry : branchEntries) {
            branchIndex++;
            if (!(entry instanceof SessionEntry.MessageEntry message)) {
                continue;
            }
            if (!"user".equals(message.message().path("role").asText())) {
                continue;
            }
            ObjectNode item = sessionUserMessageItem(message, branchIndex);
            if (normalizedQuery.isBlank() || sessionUserMessageMatches(item, normalizedQuery)) {
                matched.add(item);
            }
        }
        int start = Math.min(offset, matched.size());
        int end = Math.min(start + limit, matched.size());
        ObjectNode root = JsonCodec.mapper().createObjectNode();
        root.put("schemaVersion", 1);
        root.put("sessionId", manager.sessionId());
        root.put("sessionFile", manager.sessionFile().map(Path::toString).orElse(null));
        root.put("leaf", selectedLeaf);
        root.put("query", normalizedQuery.isBlank() ? null : normalizedQuery);
        root.put("total", matched.size());
        root.put("offset", offset);
        root.put("limit", limit);
        root.put("returned", end - start);
        root.put("hasMore", end < matched.size());
        ArrayNode items = root.putArray("items");
        for (int i = start; i < end; i++) {
            ObjectNode item = matched.get(i);
            item.put("index", i + 1);
            items.add(item);
        }
        if (target != null) {
            addResolvedSessionTarget(root, target);
        }
        return JsonCodec.mapper().writeValueAsString(root);
    }

    private static ObjectNode sessionUserMessageItem(SessionEntry.MessageEntry message, int branchIndex) {
        ObjectNode item = JsonCodec.mapper().createObjectNode();
        item.put("branchIndex", branchIndex);
        item.put("entryId", message.id());
        item.put("parentId", message.parentId());
        item.put("timestamp", message.timestamp() == null ? null : message.timestamp().toString());
        item.put("role", "user");
        String source = message.message().path("source").asText("");
        if (source.isBlank()) {
            item.putNull("source");
        } else {
            item.put("source", source.trim());
        }
        String text = previewMessage(message.message().get("content"));
        item.put("text", text);
        ObjectNode actions = item.putObject("actions");
        actions.put("forkBeforeEntryId", message.id());
        actions.put("forkAtEntryId", message.id());
        actions.put("branch", message.id());
        return item;
    }

    private static boolean sessionUserMessageMatches(ObjectNode item, String query) {
        String needle = query.toLowerCase(Locale.ROOT).trim();
        return String.join("\n",
                item.path("entryId").asText(""),
                item.path("parentId").asText(""),
                item.path("text").asText(""),
                item.path("source").asText("")
        ).toLowerCase(Locale.ROOT).contains(needle);
    }

    private static String sessionRenameJson(SessionManager manager, String name, boolean clear,
                                            ResolvedSessionTarget target) throws Exception {
        String nextName = clear ? "" : (name == null ? "" : name.trim());
        if (!clear && nextName.isBlank()) {
            throw new IllegalArgumentException("Session rename requires params.name or params.clear=true");
        }
        String entryId = manager.appendSessionInfo(nextName);
        ObjectNode root = JsonCodec.mapper().createObjectNode();
        root.put("schemaVersion", 1);
        root.put("status", clear ? "cleared" : "renamed");
        root.put("sessionId", manager.sessionId());
        root.put("sessionFile", manager.sessionFile().map(Path::toString).orElse(null));
        root.put("entryId", entryId);
        manager.sessionName().ifPresentOrElse(currentName -> root.put("name", currentName), () -> root.putNull("name"));
        if (target != null) {
            addResolvedSessionTarget(root, target);
        }
        return JsonCodec.mapper().writeValueAsString(root);
    }

    private static String sessionDeleteJson(SessionManager current, ResolvedSessionTarget target) throws Exception {
        Path targetPath = target.path();
        Path currentPath = current.sessionFile().orElse(null);
        if (currentPath != null && currentPath.toAbsolutePath().normalize().equals(targetPath)) {
            throw new IllegalArgumentException("Refusing to delete the current session");
        }
        SessionFileInfo info = SessionManager.buildSessionInfo(targetPath)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + targetPath));
        Files.delete(targetPath);
        ObjectNode root = JsonCodec.mapper().createObjectNode();
        root.put("schemaVersion", 1);
        root.put("status", "deleted");
        root.put("sessionId", info.id());
        root.put("sessionFile", targetPath.toString());
        root.put("deleted", true);
        addResolvedSessionTarget(root, target);
        return JsonCodec.mapper().writeValueAsString(root);
    }

    private record ResolvedSessionTarget(Path path, String sessionId, Integer index, String selector) {
    }

    private static boolean hasSessionTarget(String sessionPath, String sessionId, String index) {
        return (sessionPath != null && !sessionPath.isBlank())
                || (sessionId != null && !sessionId.isBlank())
                || (index != null && !index.isBlank());
    }

    private static ResolvedSessionTarget resolveSessionTarget(SessionManager current, String sessionPath,
                                                              String sessionId, String index, boolean all,
                                                              String query, String sort, String operation)
            throws Exception {
        if (sessionPath != null && !sessionPath.isBlank()) {
            return new ResolvedSessionTarget(resolveSessionPath(current, sessionPath), null, null, "session");
        }
        if (sessionId != null && !sessionId.isBlank()) {
            String selectedId = sessionId.trim();
            List<SessionFileInfo> matches = loadSessions(current, all).stream()
                    .filter(info -> selectedId.equals(info.id()) || (info.id() != null && info.id().startsWith(selectedId)))
                    .toList();
            if (matches.isEmpty()) {
                throw new IllegalArgumentException("Session " + operation + " sessionId not found: " + selectedId);
            }
            if (matches.size() > 1) {
                throw new IllegalArgumentException("Session " + operation + " sessionId is ambiguous: " + selectedId);
            }
            SessionFileInfo info = matches.getFirst();
            return new ResolvedSessionTarget(info.path(), info.id(), null, "sessionId");
        }
        Integer selectedIndex = parseOptionalPositiveInt(index);
        if (selectedIndex != null) {
            List<SessionFileInfo> sessions = sortSessions(filterSessions(loadSessions(current, all), query), sort);
            if (selectedIndex > sessions.size()) {
                throw new IllegalArgumentException("Session " + operation + " index not found: " + selectedIndex);
            }
            SessionFileInfo info = sessions.get(selectedIndex - 1);
            return new ResolvedSessionTarget(info.path(), info.id(), selectedIndex, "index");
        }
        throw new IllegalArgumentException("Session " + operation + " requires params.session, sessionId, or index");
    }

    private static String sessionSwitchJson(AgentSession session, AgentSessionRuntime.ReplacementResult result,
                                            ResolvedSessionTarget target)
            throws Exception {
        ObjectNode root = JsonCodec.mapper().createObjectNode();
        root.put("schemaVersion", 1);
        root.put("status", result.cancelled() ? "cancelled" : "switched");
        root.put("cancelled", result.cancelled());
        if (result.cancelReason() == null || result.cancelReason().isBlank()) {
            root.putNull("reason");
        } else {
            root.put("reason", result.cancelReason());
        }
        root.put("sessionId", session.sessionManager().sessionId());
        root.put("sessionFile", session.sessionFile().map(Path::toString).orElse(null));
        root.put("previousSessionFile", result.previousSessionFile() == null ? null : result.previousSessionFile().toString());
        root.put("currentSessionFile", result.currentSessionFile() == null ? null : result.currentSessionFile().toString());
        root.put("cwd", session.sessionManager().cwd().toString());
        addResolvedSessionTarget(root, target);
        return JsonCodec.mapper().writeValueAsString(root);
    }

    private static void addResolvedSessionTarget(ObjectNode root, ResolvedSessionTarget target) {
        root.put("resolvedSessionFile", target.path().toString());
        if (target.sessionId() == null || target.sessionId().isBlank()) {
            root.putNull("resolvedSessionId");
        } else {
            root.put("resolvedSessionId", target.sessionId());
        }
        if (target.index() == null) {
            root.putNull("resolvedIndex");
        } else {
            root.put("resolvedIndex", target.index());
        }
        root.put("selector", target.selector());
    }

    private record ResolvedForkEntry(String entryId, AgentSessionRuntime.ForkPosition position, String selector) {
    }

    private static String sessionForkJson(AgentSession session, AgentSessionRuntime.ReplacementResult result,
                                          AgentSessionRuntime.ForkPosition position,
                                          ResolvedForkEntry resolvedForkEntry) throws Exception {
        ObjectNode root = replacementJson(session, result, result.cancelled() ? "cancelled" : "forked");
        root.put("position", position == AgentSessionRuntime.ForkPosition.AT ? "at" : "before");
        if (result.selectedText() == null || result.selectedText().isBlank()) {
            root.putNull("selected");
        } else {
            root.put("selected", previewText(result.selectedText()));
        }
        root.put("resolvedEntryId", resolvedForkEntry.entryId());
        root.put("selector", resolvedForkEntry.selector());
        return JsonCodec.mapper().writeValueAsString(root);
    }

    private static String sessionCloneJson(AgentSession session, AgentSessionRuntime.ReplacementResult result)
            throws Exception {
        ObjectNode root = replacementJson(session, result, result.cancelled() ? "cancelled" : "cloned");
        return JsonCodec.mapper().writeValueAsString(root);
    }

    private static ObjectNode replacementJson(AgentSession session, AgentSessionRuntime.ReplacementResult result,
                                              String status) {
        ObjectNode root = JsonCodec.mapper().createObjectNode();
        root.put("schemaVersion", 1);
        root.put("status", status);
        root.put("cancelled", result.cancelled());
        if (result.cancelReason() == null || result.cancelReason().isBlank()) {
            root.putNull("reason");
        } else {
            root.put("reason", result.cancelReason());
        }
        root.put("sessionId", session.sessionManager().sessionId());
        root.put("sessionFile", session.sessionFile().map(Path::toString).orElse(null));
        root.put("previousSessionFile", result.previousSessionFile() == null ? null : result.previousSessionFile().toString());
        root.put("currentSessionFile", result.currentSessionFile() == null ? null : result.currentSessionFile().toString());
        root.put("cwd", session.sessionManager().cwd().toString());
        return root;
    }

    private static List<SessionFileInfo> loadSessions(SessionManager manager, boolean all) throws Exception {
        Path sessionDir = manager.sessionDir();
        if (sessionDir == null) {
            return List.of();
        }
        if (!all) {
            return SessionManager.list(manager.cwd(), sessionDir, null);
        }
        Map<Path, SessionFileInfo> byPath = new LinkedHashMap<>();
        for (SessionFileInfo info : SessionManager.listSessionsFromDir(sessionDir, null)) {
            byPath.put(info.path(), info);
        }
        Path root = sessionDir.getParent();
        if (root != null) {
            for (SessionFileInfo info : SessionManager.listAll(root, null)) {
                byPath.put(info.path(), info);
            }
        }
        return byPath.values().stream()
                .sorted(Comparator.comparing(SessionFileInfo::modified).reversed())
                .toList();
    }

    private static List<SessionFileInfo> filterSessions(List<SessionFileInfo> sessions, String query) {
        if (query == null || query.isBlank()) {
            return sessions;
        }
        String needle = query.toLowerCase(Locale.ROOT).trim();
        return sessions.stream()
                .filter(info -> sessionSearchText(info).contains(needle))
                .toList();
    }

    private static List<SessionFileInfo> sortSessions(List<SessionFileInfo> sessions, String sort) {
        Comparator<SessionFileInfo> newest = Comparator
                .comparing(SessionFileInfo::modified, Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed()
                .thenComparing(info -> nullSafe(info.id()));
        Comparator<SessionFileInfo> comparator = switch (sort) {
            case "oldest" -> Comparator
                    .comparing(SessionFileInfo::modified, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(info -> nullSafe(info.id()));
            case "name" -> Comparator
                    .comparing(RpcModeRunner::sessionListSortName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(newest);
            case "messages" -> Comparator
                    .comparingInt(SessionFileInfo::messageCount)
                    .reversed()
                    .thenComparing(newest);
            default -> newest;
        };
        return sessions.stream().sorted(comparator).toList();
    }

    private static String sessionListSortName(SessionFileInfo info) {
        if (info.name() != null && !info.name().isBlank()) {
            return info.name().trim();
        }
        if (info.firstMessage() != null && !info.firstMessage().isBlank()) {
            return info.firstMessage().trim();
        }
        return nullSafe(info.id());
    }

    private static String normalizeSessionListSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return "newest";
        }
        return switch (sort.trim().toLowerCase(Locale.ROOT)) {
            case "oldest", "name", "messages" -> sort.trim().toLowerCase(Locale.ROOT);
            default -> "newest";
        };
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static String sessionSearchText(SessionFileInfo info) {
        return String.join("\n",
                info.id() == null ? "" : info.id(),
                info.name() == null ? "" : info.name(),
                info.cwd() == null ? "" : info.cwd().toString(),
                info.firstMessage() == null ? "" : info.firstMessage(),
                info.allMessagesText() == null ? "" : info.allMessagesText()
        ).toLowerCase(Locale.ROOT);
    }

    private static String sessionTreeJson(SessionManager manager, String branch, boolean flat, String query,
                                          int offset, Integer limit, Set<String> collapsedIds)
            throws Exception {
        ObjectNode root = JsonCodec.mapper().createObjectNode();
        String currentLeaf = branch == null || branch.isBlank() ? manager.leafId().orElse("root") : branch.trim();
        if (!"root".equals(currentLeaf) && manager.entry(currentLeaf).isEmpty()) {
            throw new IllegalArgumentException("Session tree branch not found: " + currentLeaf);
        }
        root.put("schemaVersion", 1);
        root.put("sessionId", manager.sessionId());
        root.put("sessionFile", manager.sessionFile().map(Path::toString).orElse(null));
        root.put("leaf", currentLeaf);
        root.put("entries", manager.entries().size());
        List<SessionManager.SessionTreeNode> tree = manager.tree();
        ArrayNode roots = root.putArray("roots");
        for (SessionManager.SessionTreeNode node : tree) {
            roots.add(sessionTreeNodeJson(node, currentLeaf));
        }
        if (flat) {
            String itemQuery = query == null || query.isBlank() ? "" : query.trim();
            root.put("itemQuery", itemQuery.isBlank() ? null : itemQuery);
            Set<String> collapsed = collapsedIds == null ? Set.of() : collapsedIds;
            ArrayNode collapsedArray = root.putArray("collapsedIds");
            collapsed.forEach(collapsedArray::add);
            root.put("collapsedCount", collapsed.size());
            int total = countSessionTreeNodes(tree);
            int[] returned = {0};
            ArrayNode filteredItems = JsonCodec.mapper().createArrayNode();
            int[] index = {1};
            for (SessionManager.SessionTreeNode node : tree) {
                appendSessionTreeFlatItems(filteredItems, node, currentLeaf, 0, index, itemQuery, collapsed, returned);
            }
            int pageStart = Math.min(offset, returned[0]);
            int pageEnd = limit == null ? returned[0] : (int) Math.min((long) returned[0], (long) pageStart + limit);
            ArrayNode items = root.putArray("items");
            for (int i = pageStart; i < pageEnd; i++) {
                items.add(filteredItems.get(i));
            }
            root.put("itemTotal", total);
            root.put("itemReturned", returned[0]);
            root.put("itemOffset", offset);
            if (limit == null) {
                root.putNull("itemLimit");
            } else {
                root.put("itemLimit", limit);
            }
            root.put("itemPageReturned", pageEnd - pageStart);
            root.put("itemHasMore", pageEnd < returned[0]);
        }
        return JsonCodec.mapper().writeValueAsString(root);
    }

    private static ObjectNode sessionTreeNodeJson(SessionManager.SessionTreeNode node, String currentLeaf) {
        SessionEntry entry = node.entry();
        ObjectNode out = JsonCodec.mapper().createObjectNode();
        out.put("id", entry.id());
        out.put("parentId", entry.parentId());
        out.put("type", entry.type());
        out.put("timestamp", entry.timestamp() == null ? null : entry.timestamp().toString());
        out.put("current", entry.id().equals(currentLeaf));
        out.put("summary", entrySummary(entry));
        if (node.label() == null || node.label().isBlank()) {
            out.putNull("label");
        } else {
            out.put("label", node.label().trim());
        }
        out.put("labelTimestamp", node.labelTimestamp() == null ? null : node.labelTimestamp().toString());
        addEntryDetails(out, entry);
        ArrayNode children = out.putArray("children");
        for (SessionManager.SessionTreeNode child : node.children()) {
            children.add(sessionTreeNodeJson(child, currentLeaf));
        }
        return out;
    }

    private static void appendSessionTreeFlatItems(ArrayNode items, SessionManager.SessionTreeNode node,
                                                   String currentLeaf, int depth, int[] index, String query,
                                                   Set<String> collapsedIds, int[] returned) {
        SessionEntry entry = node.entry();
        ObjectNode out = JsonCodec.mapper().createObjectNode();
        out.put("index", index[0]++);
        out.put("depth", depth);
        out.put("id", entry.id());
        out.put("parentId", entry.parentId());
        out.put("type", entry.type());
        out.put("timestamp", entry.timestamp() == null ? null : entry.timestamp().toString());
        out.put("current", entry.id().equals(currentLeaf));
        out.put("summary", entrySummary(entry));
        if (node.label() == null || node.label().isBlank()) {
            out.putNull("label");
        } else {
            out.put("label", node.label().trim());
        }
        out.put("labelTimestamp", node.labelTimestamp() == null ? null : node.labelTimestamp().toString());
        out.put("hasChildren", !node.children().isEmpty());
        out.put("childCount", node.children().size());
        int descendantCount = countSessionTreeNode(node) - 1;
        boolean collapsed = collapsedIds.contains(entry.id());
        out.put("descendantCount", descendantCount);
        out.put("collapsed", collapsed);
        addEntryDetails(out, entry);
        ObjectNode actions = out.putObject("actions");
        actions.put("branch", entry.id());
        actions.put("forkAtEntryId", entry.id());
        if (node.children().isEmpty()) {
            actions.putNull("toggleCollapseId");
        } else {
            actions.put("toggleCollapseId", entry.id());
        }
        if (entry instanceof SessionEntry.MessageEntry message
                && "user".equals(message.message().path("role").asText())) {
            actions.put("forkBeforeEntryId", entry.id());
        } else {
            actions.putNull("forkBeforeEntryId");
        }
        if (query == null || query.isBlank() || sessionTreeItemMatches(out, query)) {
            items.add(out);
            returned[0]++;
        }
        if (collapsed) {
            return;
        }
        for (SessionManager.SessionTreeNode child : node.children()) {
            appendSessionTreeFlatItems(items, child, currentLeaf, depth + 1, index, query, collapsedIds, returned);
        }
    }

    private static int countSessionTreeNodes(List<SessionManager.SessionTreeNode> nodes) {
        int total = 0;
        for (SessionManager.SessionTreeNode node : nodes) {
            total += countSessionTreeNode(node);
        }
        return total;
    }

    private static int countSessionTreeNode(SessionManager.SessionTreeNode node) {
        int total = 1;
        for (SessionManager.SessionTreeNode child : node.children()) {
            total += countSessionTreeNode(child);
        }
        return total;
    }

    private static boolean sessionTreeItemMatches(ObjectNode item, String query) {
        String needle = query.toLowerCase(Locale.ROOT).trim();
        return String.join("\n",
                item.path("id").asText(""),
                item.path("parentId").asText(""),
                item.path("type").asText(""),
                item.path("summary").asText(""),
                item.path("label").asText(""),
                item.path("role").asText(""),
                item.path("source").asText(""),
                item.path("customType").asText(""),
                item.path("name").asText(""),
                item.path("targetId").asText("")
        ).toLowerCase(Locale.ROOT).contains(needle);
    }

    private static void addEntryDetails(ObjectNode out, SessionEntry entry) {
        switch (entry) {
            case SessionEntry.MessageEntry message -> {
                out.put("role", message.message().path("role").asText("unknown"));
                String source = message.message().path("source").asText("");
                if (!source.isBlank()) {
                    out.put("source", source.trim());
                }
            }
            case SessionEntry.CustomEntry custom -> out.put("customType", custom.customType());
            case SessionEntry.CustomMessageEntry customMessage -> {
                out.put("customType", customMessage.customType());
                if (customMessage.source() != null && !customMessage.source().isBlank()) {
                    out.put("source", customMessage.source().trim());
                }
            }
            case SessionEntry.ThinkingLevelChangeEntry thinking -> out.put("thinking", thinking.thinkingLevel());
            case SessionEntry.ModelChangeEntry model -> {
                out.put("provider", model.provider());
                out.put("model", model.modelId());
            }
            case SessionEntry.ActiveToolsChangeEntry tools -> {
                ArrayNode toolNames = out.putArray("activeToolNames");
                tools.activeToolNames().forEach(toolNames::add);
            }
            case SessionEntry.CompactionEntry compaction -> out.put("summaryText", compaction.summary());
            case SessionEntry.BranchSummaryEntry summary -> out.put("summaryText", summary.summary());
            case SessionEntry.LabelEntry label -> out.put("targetId", label.targetId());
            case SessionEntry.SessionInfoEntry info -> out.put("name", info.name());
            case SessionEntry.LeafEntry leaf -> out.put("targetId", leaf.targetId());
        }
    }

    private static String entrySummary(SessionEntry entry) {
        return switch (entry) {
            case SessionEntry.MessageEntry message -> "message " + message.message().path("role").asText("unknown")
                    + treeSourceLabel(message.message().path("source").asText(""))
                    + " " + previewMessage(message.message().get("content"));
            case SessionEntry.ThinkingLevelChangeEntry thinking -> "thinking " + thinking.thinkingLevel();
            case SessionEntry.ModelChangeEntry model -> "model " + model.provider() + "/" + model.modelId();
            case SessionEntry.ActiveToolsChangeEntry tools -> "active_tools " + tools.activeToolNames();
            case SessionEntry.CompactionEntry compaction -> "compaction " + previewText(compaction.summary());
            case SessionEntry.BranchSummaryEntry summary -> "branch_summary " + previewText(summary.summary());
            case SessionEntry.CustomEntry custom -> "custom " + custom.customType();
            case SessionEntry.CustomMessageEntry custom -> "custom_message " + custom.customType()
                    + treeSourceLabel(custom.source());
            case SessionEntry.LabelEntry label -> "label " + label.targetId();
            case SessionEntry.SessionInfoEntry info -> "session_info " + previewText(info.name());
            case SessionEntry.LeafEntry leaf -> "leaf " + (leaf.targetId() == null ? "root" : leaf.targetId());
        };
    }

    private static String treeSourceLabel(String source) {
        if (source == null || source.isBlank()) {
            return "";
        }
        return " source=" + previewText(source);
    }

    private static String previewMessage(JsonNode content) {
        if (content == null || content.isNull()) {
            return "";
        }
        if (content.isTextual()) {
            return previewText(content.asText());
        }
        if (content.isArray()) {
            StringBuilder text = new StringBuilder();
            for (JsonNode part : content) {
                if (part.isTextual()) {
                    text.append(part.asText());
                } else if (part.has("text")) {
                    text.append(part.path("text").asText());
                }
                if (text.length() > 0) {
                    text.append(' ');
                }
            }
            return previewText(text.toString());
        }
        return previewText(content.toString());
    }

    private static String previewText(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 77) + "...";
    }

    private record RpcSessionStats(Path sessionFile, String sessionId, int userMessages,
                                   int assistantMessages, int toolResults, int totalMessages,
                                   long inputTokens, long outputTokens, long cacheCreationInputTokens,
                                   long cacheReadInputTokens, long reasoningTokens, long totalTokens) {
        static RpcSessionStats from(AgentSession.SessionStats stats) {
            return new RpcSessionStats(stats.sessionFile(), stats.sessionId(), stats.userMessages(),
                    stats.assistantMessages(), stats.toolResults(), stats.totalMessages(), stats.inputTokens(),
                    stats.outputTokens(), stats.cacheCreationInputTokens(), stats.cacheReadInputTokens(),
                    stats.reasoningTokens(), stats.totalTokens());
        }

        static RpcSessionStats from(SessionManager manager, List<SessionEntry> branchEntries) {
            int userMessages = 0;
            int assistantMessages = 0;
            int toolResults = 0;
            int totalMessages = 0;
            long inputTokens = 0;
            long outputTokens = 0;
            long cacheCreationInputTokens = 0;
            long cacheReadInputTokens = 0;
            long reasoningTokens = 0;
            for (SessionEntry entry : branchEntries) {
                if (!(entry instanceof SessionEntry.MessageEntry message)) {
                    continue;
                }
                totalMessages++;
                JsonNode node = message.message();
                String role = node.path("role").asText();
                if ("user".equals(role)) {
                    userMessages++;
                } else if ("assistant".equals(role)) {
                    assistantMessages++;
                    JsonNode usage = node.get("usage");
                    if (usage != null && usage.isObject()) {
                        inputTokens += usage.path("inputTokens").asLong(0);
                        outputTokens += usage.path("outputTokens").asLong(0);
                        cacheCreationInputTokens += usage.path("cacheCreationInputTokens").asLong(0);
                        cacheReadInputTokens += usage.path("cacheReadInputTokens").asLong(0);
                        reasoningTokens += usage.path("reasoningTokens").asLong(0);
                    }
                } else if ("toolResult".equals(role)) {
                    toolResults++;
                }
            }
            long totalTokens = inputTokens + outputTokens + cacheCreationInputTokens + cacheReadInputTokens
                    + reasoningTokens;
            return new RpcSessionStats(manager.sessionFile().orElse(null), manager.sessionId(), userMessages,
                    assistantMessages, toolResults, totalMessages, inputTokens, outputTokens,
                    cacheCreationInputTokens, cacheReadInputTokens, reasoningTokens, totalTokens);
        }

        long cacheInputTokens() {
            return cacheCreationInputTokens + cacheReadInputTokens;
        }
    }

    private static Map<String, Integer> collectMessageSources(List<SessionEntry> entries) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (SessionEntry entry : entries) {
            if (entry instanceof SessionEntry.MessageEntry message) {
                incrementSource(counts, message.message().path("source").asText(""));
            }
        }
        return counts;
    }

    private static Map<String, Integer> collectCustomMessageSources(List<SessionEntry> entries) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (SessionEntry entry : entries) {
            if (entry instanceof SessionEntry.CustomMessageEntry customMessage) {
                incrementSource(counts, customMessage.source());
            }
        }
        return counts;
    }

    private static void incrementSource(Map<String, Integer> counts, String source) {
        if (source == null || source.isBlank()) {
            return;
        }
        counts.merge(source.trim(), 1, Integer::sum);
    }

    private static void addSourceCounts(ObjectNode target, Map<String, Integer> counts) {
        counts.forEach(target::put);
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
        String parentObject = extractNestedObject(json, parentField);
        if (parentObject == null) {
            return null;
        }
        return extractJsonField(parentObject, field);
    }

    private static String extractNestedArrayField(String json, String parentField, String field) {
        String parentObject = extractNestedObject(json, parentField);
        if (parentObject == null) {
            return null;
        }
        return extractJsonArrayField(parentObject, field);
    }

    private static String extractNestedObject(String json, String parentField) {
        String parentKey = "\"" + parentField + "\"";
        int idx = json.indexOf(parentKey);
        if (idx < 0) return null;
        int braceIdx = json.indexOf('{', idx + parentKey.length());
        if (braceIdx < 0) return null;
        int endBraceIdx = findMatchingDelimiter(json, braceIdx, '{', '}');
        if (endBraceIdx < 0) return null;
        return json.substring(braceIdx, endBraceIdx + 1);
    }

    private static String extractJsonArrayField(String json, String field) {
        String key = "\"" + field + "\"";
        int idx = json.indexOf(key);
        if (idx < 0) return null;
        int colonIdx = json.indexOf(':', idx + key.length());
        if (colonIdx < 0) return null;
        int startIdx = colonIdx + 1;
        while (startIdx < json.length() && Character.isWhitespace(json.charAt(startIdx))) {
            startIdx++;
        }
        if (startIdx >= json.length() || json.charAt(startIdx) != '[') {
            return null;
        }
        int endIdx = findMatchingDelimiter(json, startIdx, '[', ']');
        if (endIdx < 0) {
            return null;
        }
        return json.substring(startIdx, endIdx + 1);
    }

    private static int findMatchingDelimiter(String value, int startIdx, char open, char close) {
        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        for (int i = startIdx; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (ch == '\\') {
                escaped = inString;
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (ch == open) {
                depth++;
            } else if (ch == close) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
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

    private static int parsePositiveInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Math.max(1, Integer.parseInt(value.trim()));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static Integer parseOptionalPositiveInt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Math.max(1, Integer.parseInt(value.trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Set<String> parseIdSet(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        if (value.trim().startsWith("[")) {
            return parseIdArray(value);
        }
        Set<String> ids = new LinkedHashSet<>();
        for (String part : value.trim().split("[,\\s]+")) {
            if (!part.isBlank()) {
                ids.add(part.trim());
            }
        }
        return ids;
    }

    private static Set<String> parseIdArray(String value) {
        try {
            JsonNode node = JsonCodec.mapper().readTree(value);
            if (!node.isArray()) {
                return Set.of();
            }
            Set<String> ids = new LinkedHashSet<>();
            for (JsonNode item : node) {
                String id = item.asText("");
                if (!id.isBlank()) {
                    ids.add(id.trim());
                }
            }
            return ids;
        } catch (Exception e) {
            return Set.of();
        }
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
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

    private static AgentSessionRuntime.ForkPosition parseForkPosition(String value) {
        if (value == null || value.isBlank() || "before".equalsIgnoreCase(value.trim())) {
            return AgentSessionRuntime.ForkPosition.BEFORE;
        }
        if ("at".equalsIgnoreCase(value.trim())) {
            return AgentSessionRuntime.ForkPosition.AT;
        }
        throw new IllegalArgumentException("Invalid fork position: " + value);
    }

    private static SessionManager diagnosticsSession(SessionManager current, String sessionPath) throws Exception {
        if (sessionPath == null || sessionPath.isBlank()) {
            return current;
        }
        return SessionManager.open(resolveSessionPath(current, sessionPath));
    }

    private static Path resolveSessionPath(SessionManager current, String sessionPath) {
        Path path = Path.of(sessionPath.trim());
        if (!path.isAbsolute()) {
            path = current.cwd().resolve(path);
        }
        return path.toAbsolutePath().normalize();
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
