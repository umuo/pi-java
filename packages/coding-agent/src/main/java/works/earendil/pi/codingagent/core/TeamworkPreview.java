package works.earendil.pi.codingagent.core;

import works.earendil.pi.ai.model.Model;
import works.earendil.pi.codingagent.resources.Skill;
import works.earendil.pi.orchestrator.config.OrchestratorConfig;
import works.earendil.pi.orchestrator.service.OrchestratorSupervisor;
import works.earendil.pi.orchestrator.service.SubAgentTaskCoordinator;
import works.earendil.pi.orchestrator.storage.OrchestratorStorage;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TeamworkPreview {
    private static final Duration DEFAULT_RPC_TIMEOUT = Duration.ofSeconds(60);
    private static final OrchestratorExecutor DEFAULT_EXECUTOR = new DefaultOrchestratorExecutor();

    private TeamworkPreview() {
    }

    public record Role(String id, String purpose, List<String> tools, String handoff) {
        public Role {
            tools = tools == null ? List.of() : List.copyOf(tools);
        }
    }

    public record Preview(
            Path cwd,
            String mainModel,
            int availableProviders,
            int loadedSkills,
            String steeringMode,
            String followUpMode,
            List<Role> roles) {
        public Preview {
            roles = roles == null ? List.of() : List.copyOf(roles);
        }

        public String render() {
            StringBuilder out = new StringBuilder();
            out.append("Teamwork preview\n");
            out.append("cwd: ").append(cwd).append("\n");
            out.append("main model: ").append(mainModel).append("\n");
            out.append("providers: ").append(availableProviders).append(" available\n");
            out.append("skills: ").append(loadedSkills).append(" loaded\n");
            out.append("coordination: steering=").append(steeringMode)
                    .append(", followUp=").append(followUpMode).append("\n\n");
            out.append("Planned sub-agents:\n");
            for (int i = 0; i < roles.size(); i++) {
                Role role = roles.get(i);
                out.append(i + 1).append(". ").append(role.id()).append(" - ").append(role.purpose()).append("\n");
                out.append("   tools: ").append(String.join(", ", role.tools())).append("\n");
                out.append("   handoff: ").append(role.handoff()).append("\n");
            }
            return out.toString().trim();
        }
    }

    public record ExecutionOptions(boolean execute, String objective, boolean compact, Duration rpcTimeout,
                                   boolean stopOnCompletion) {
        public ExecutionOptions {
            objective = objective == null ? "" : objective.trim();
            rpcTimeout = rpcTimeout == null ? DEFAULT_RPC_TIMEOUT : rpcTimeout;
        }
    }

    public record ExecutionRequest(Path cwd, String objective, List<Role> roles, Duration rpcTimeout,
                                   boolean stopOnCompletion) {
        public ExecutionRequest {
            if (cwd == null) {
                throw new IllegalArgumentException("cwd is required");
            }
            if (objective == null || objective.isBlank()) {
                throw new IllegalArgumentException("objective is required");
            }
            roles = roles == null ? List.of() : List.copyOf(roles);
            rpcTimeout = rpcTimeout == null ? DEFAULT_RPC_TIMEOUT : rpcTimeout;
        }
    }

    public record ExecutionRoleResult(String roleId, String instanceId, String response, int events,
                                      String error, boolean stopped) {
    }

    public record ExecutionResult(List<ExecutionRoleResult> results) {
        public ExecutionResult {
            results = results == null ? List.of() : List.copyOf(results);
        }
    }

    public record ExecutionReport(Preview preview, ExecutionOptions options, ExecutionResult result, String error) {
        public String render() {
            StringBuilder out = new StringBuilder();
            out.append("Teamwork execution\n");
            out.append("cwd: ").append(preview.cwd()).append("\n");
            out.append("objective: ").append(options.objective().isBlank() ? "(missing)" : options.objective()).append("\n");
            out.append("roles: ").append(preview.roles().size()).append("\n");
            out.append("mode: ").append(options.compact() ? "compact" : "standard").append("\n");
            if (error != null && !error.isBlank()) {
                out.append("error: ").append(error).append("\n");
                out.append("usage: /teamwork-preview run <objective>\n");
                return out.toString().trim();
            }
            if (result == null || result.results().isEmpty()) {
                out.append("results: none");
                return out.toString().trim();
            }
            out.append("\nResults:\n");
            for (int i = 0; i < result.results().size(); i++) {
                ExecutionRoleResult role = result.results().get(i);
                out.append(i + 1).append(". ").append(role.roleId()).append(" - ")
                        .append(role.error() == null ? "ok" : "error").append("\n");
                out.append("   instance: ").append(role.instanceId() == null ? "none" : role.instanceId()).append("\n");
                out.append("   events: ").append(role.events()).append("\n");
                out.append("   stopped: ").append(role.stopped()).append("\n");
                if (role.error() != null) {
                    out.append("   error: ").append(role.error()).append("\n");
                }
                if (role.response() != null && !role.response().isBlank()) {
                    out.append("   response: ").append(truncate(role.response(), 240)).append("\n");
                }
            }
            return out.toString().trim();
        }
    }

    @FunctionalInterface
    public interface OrchestratorExecutor {
        ExecutionResult execute(ExecutionRequest request) throws Exception;
    }

    public static Preview fromServices(AgentSession session, AgentSessionServices services, String arguments) {
        List<Model> available = services.modelRegistry().getAvailable();
        int providerCount = (int) available.stream()
                .map(Model::provider)
                .distinct()
                .count();
        List<Skill> skills = services.resourceLoader().skills().skills();
        return new Preview(
                services.cwd(),
                modelRef(session.model()),
                providerCount,
                skills.size(),
                services.settingsManager().getSteeringMode(),
                services.settingsManager().getFollowUpMode(),
                roles(arguments)
        );
    }

    public static boolean shouldExecute(String arguments) {
        return parseOptions(arguments).execute();
    }

    public static ExecutionOptions parseOptions(String arguments) {
        String raw = arguments == null ? "" : arguments.trim();
        if (raw.isBlank()) {
            return new ExecutionOptions(false, "", false, DEFAULT_RPC_TIMEOUT, true);
        }
        boolean execute = false;
        boolean compact = false;
        List<String> objective = new ArrayList<>();
        for (String token : raw.split("\\s+")) {
            String normalized = token.toLowerCase(Locale.ROOT);
            if ("run".equals(normalized) || "execute".equals(normalized)
                    || "--run".equals(normalized) || "--execute".equals(normalized)) {
                execute = true;
            } else if ("compact".equals(normalized) || "small".equals(normalized) || "--compact".equals(normalized)) {
                compact = true;
            } else {
                objective.add(token);
            }
        }
        return new ExecutionOptions(execute, String.join(" ", objective), compact, DEFAULT_RPC_TIMEOUT, true);
    }

    public static ExecutionReport executeFromServices(AgentSession session, AgentSessionServices services,
                                                       String arguments) {
        return executeFromServices(session, services, arguments, DEFAULT_EXECUTOR);
    }

    static ExecutionReport executeFromServices(AgentSession session, AgentSessionServices services, String arguments,
                                               OrchestratorExecutor executor) {
        ExecutionOptions options = parseOptions(arguments);
        Preview preview = fromServices(session, services, options.compact() ? "compact" : "");
        if (!options.execute()) {
            return new ExecutionReport(preview, options, null, "teamwork execution was not requested");
        }
        if (options.objective().isBlank()) {
            return new ExecutionReport(preview, options, null, "objective is required");
        }
        try {
            ExecutionResult result = executor.execute(new ExecutionRequest(
                    services.cwd(), options.objective(), preview.roles(), options.rpcTimeout(),
                    options.stopOnCompletion()));
            return new ExecutionReport(preview, options, result, null);
        } catch (Exception e) {
            return new ExecutionReport(preview, options, null, e.getMessage());
        }
    }

    private static List<Role> roles(String arguments) {
        String normalized = arguments == null ? "" : arguments.trim().toLowerCase(Locale.ROOT);
        boolean compact = normalized.contains("compact") || normalized.contains("small");
        if (compact) {
            return List.of(
                    new Role("implementer", "make the scoped code change", List.of("read", "edit", "write", "bash"),
                            "return changed files, tests run, and remaining risks"),
                    new Role("reviewer", "check the patch for regressions", List.of("read", "grep", "bash"),
                            "return findings with file and line references")
            );
        }
        return List.of(
                new Role("researcher", "map the codebase and collect evidence", List.of("read", "ls", "grep", "find"),
                        "return the relevant files, APIs, and constraints"),
                new Role("implementer", "make the scoped code change", List.of("read", "edit", "write", "bash"),
                        "return changed files and verification commands"),
                new Role("reviewer", "check behavior, tests, and edge cases", List.of("read", "grep", "bash"),
                        "return ordered findings and residual risks")
        );
    }

    private static String modelRef(Model model) {
        if (model == null) {
            return "none";
        }
        return model.provider() + "/" + model.modelId();
    }

    private static String roleInstructions(Role role) {
        return "Purpose: " + role.purpose() + "\n"
                + "Allowed tools: " + String.join(", ", role.tools()) + "\n"
                + "Handoff: " + role.handoff();
    }

    private static String truncate(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, Math.max(0, maxChars - 3)) + "...";
    }

    private static final class DefaultOrchestratorExecutor implements OrchestratorExecutor {
        @Override
        public ExecutionResult execute(ExecutionRequest request) throws Exception {
            OrchestratorStorage storage = new OrchestratorStorage(new OrchestratorConfig());
            OrchestratorSupervisor supervisor = new OrchestratorSupervisor(storage);
            SubAgentTaskCoordinator coordinator = new SubAgentTaskCoordinator(supervisor);
            List<SubAgentTaskCoordinator.Role> roles = request.roles().stream()
                    .map(role -> new SubAgentTaskCoordinator.Role(role.id(), roleInstructions(role)))
                    .toList();
            SubAgentTaskCoordinator.TaskResult result = coordinator.execute(new SubAgentTaskCoordinator.TaskRequest(
                    request.cwd().toString(), request.objective(), roles, request.rpcTimeout(),
                    request.stopOnCompletion()));
            return new ExecutionResult(result.results().stream()
                    .map(role -> new ExecutionRoleResult(role.roleId(), role.instanceId(), role.response(),
                            role.events().size(), role.error(), role.stopped()))
                    .toList());
        }
    }
}
