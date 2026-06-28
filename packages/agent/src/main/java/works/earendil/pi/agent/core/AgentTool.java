package works.earendil.pi.agent.core;

import works.earendil.pi.ai.model.Content;
import works.earendil.pi.ai.model.Tool;

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

    record AgentToolResult(List<Content> content, Object details, boolean error, boolean terminate) {
    }
}
