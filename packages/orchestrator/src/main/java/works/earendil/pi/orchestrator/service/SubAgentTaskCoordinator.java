package works.earendil.pi.orchestrator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import works.earendil.pi.orchestrator.model.InstanceRecord;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

public final class SubAgentTaskCoordinator {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration DEFAULT_RPC_TIMEOUT = Duration.ofSeconds(60);

    private final OrchestratorSupervisor supervisor;
    private final AtomicLong requestIds = new AtomicLong(1);

    public SubAgentTaskCoordinator(OrchestratorSupervisor supervisor) {
        this.supervisor = supervisor;
    }

    public TaskResult execute(TaskRequest request) throws IOException {
        if (request.roles().isEmpty()) {
            return new TaskResult(List.of());
        }
        List<Future<SubAgentResult>> futures = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Role role : request.roles()) {
                futures.add(executor.submit(() -> runRole(request, role)));
            }
            List<SubAgentResult> results = new ArrayList<>();
            for (Future<SubAgentResult> future : futures) {
                try {
                    results.add(future.get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for sub-agent tasks", e);
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof IOException io) {
                        throw io;
                    }
                    throw new IOException("Sub-agent task failed: " + cause.getMessage(), cause);
                }
            }
            return new TaskResult(results);
        }
    }

    private SubAgentResult runRole(TaskRequest request, Role role) throws IOException {
        InstanceRecord instance;
        try {
            instance = supervisor.spawnInstance(request.cwd(), role.id());
        } catch (IOException e) {
            return SubAgentResult.error(role.id(), null, "spawn failed: " + e.getMessage(), false);
        }

        SubAgentResult result;
        try {
            String promptRequest = promptRequest(buildPrompt(request.objective(), role));
            Optional<OrchestratorSupervisor.RpcExchange> exchange = supervisor.sendRpcExchange(
                    instance.id(), promptRequest, request.rpcTimeout());
            if (exchange.isEmpty()) {
                result = SubAgentResult.error(role.id(), instance.id(), "rpc response timed out", false);
            } else {
                result = new SubAgentResult(role.id(), instance.id(), exchange.get().response(),
                        exchange.get().events(), null, false);
            }
        } catch (IOException e) {
            result = SubAgentResult.error(role.id(), instance.id(), "rpc failed: " + e.getMessage(), false);
        }
        if (!request.stopOnCompletion()) {
            return result;
        }
        supervisor.stopInstance(instance.id());
        return result.withStopped(true);
    }

    private String promptRequest(String prompt) throws IOException {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("jsonrpc", "2.0");
        root.put("id", requestIds.getAndIncrement());
        root.put("method", "prompt");
        ObjectNode params = root.putObject("params");
        params.put("text", prompt);
        return MAPPER.writeValueAsString(root);
    }

    private static String buildPrompt(String objective, Role role) {
        return """
                You are a background sub-agent managed by the Pi orchestrator.

                Objective:
                %s

                Your role:
                %s

                Instructions:
                %s

                Return a concise handoff with findings, changed files if any, verification commands, and remaining risks.
                """.formatted(objective, role.id(), role.instructions()).trim();
    }

    public record TaskRequest(String cwd, String objective, List<Role> roles, Duration rpcTimeout,
                              boolean stopOnCompletion) {
        public TaskRequest {
            if (cwd == null || cwd.isBlank()) {
                throw new IllegalArgumentException("cwd is required");
            }
            if (objective == null || objective.isBlank()) {
                throw new IllegalArgumentException("objective is required");
            }
            roles = roles == null ? List.of() : List.copyOf(roles);
            rpcTimeout = rpcTimeout == null ? DEFAULT_RPC_TIMEOUT : rpcTimeout;
        }
    }

    public record Role(String id, String instructions) {
        public Role {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("role id is required");
            }
            instructions = instructions == null ? "" : instructions;
        }
    }

    public record TaskResult(List<SubAgentResult> results) {
        public TaskResult {
            results = results == null ? List.of() : List.copyOf(results);
        }
    }

    public record SubAgentResult(String roleId, String instanceId, String response, List<String> events,
                                 String error, boolean stopped) {
        public SubAgentResult {
            events = events == null ? List.of() : List.copyOf(events);
        }

        static SubAgentResult error(String roleId, String instanceId, String error, boolean stopped) {
            return new SubAgentResult(roleId, instanceId, null, List.of(), error, stopped);
        }

        SubAgentResult withStopped(boolean stopped) {
            return new SubAgentResult(roleId, instanceId, response, events, error, stopped);
        }
    }
}
