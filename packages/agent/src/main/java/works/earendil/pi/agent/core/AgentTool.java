package works.earendil.pi.agent.core;

import works.earendil.pi.ai.model.Content;
import works.earendil.pi.ai.model.Tool;
import works.earendil.pi.ai.model.Usage;

import java.util.List;
import java.util.Map;

public interface AgentTool {
    Tool definition();

    AgentToolResult execute(Object input) throws Exception;

    default String name() {
        return definition().name();
    }

    default Map<String, Object> prepareArguments(Map<String, Object> input) {
        return input;
    }

    default ExecutionMode executionMode() {
        return ExecutionMode.PARALLEL;
    }

    enum ExecutionMode {
        SEQUENTIAL,
        PARALLEL
    }

    record AgentToolResult(List<Content> content, Object details, boolean error, boolean terminate, Usage usage,
                           List<String> addedToolNames) {
        public AgentToolResult(List<Content> content, Object details, boolean error, boolean terminate) {
            this(content, details, error, terminate, null, List.of());
        }

        public AgentToolResult {
            content = content == null ? List.of() : List.copyOf(content);
            addedToolNames = addedToolNames == null ? List.of() : List.copyOf(addedToolNames);
        }
    }
}
