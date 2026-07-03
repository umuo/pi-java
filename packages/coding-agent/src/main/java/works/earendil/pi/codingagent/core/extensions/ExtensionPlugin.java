package works.earendil.pi.codingagent.core.extensions;

import works.earendil.pi.agent.core.AgentTool;
import works.earendil.pi.ai.model.Content;
import works.earendil.pi.ai.model.Tool;
import works.earendil.pi.codingagent.core.BashExecutor;
import works.earendil.pi.codingagent.core.BashOperations;
import works.earendil.pi.codingagent.core.SlashCommands;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public interface ExtensionPlugin {
    record InputResult(String text, String output, boolean handled) {
        public static InputResult transform(String text) {
            return new InputResult(text, null, false);
        }

        public static InputResult handledResult() {
            return new InputResult(null, null, true);
        }

        public static InputResult handledWithOutput(String output) {
            return new InputResult(null, output, true);
        }
    }

    record UserBashResult(BashOperations operations, BashExecutor.Result result) {
        public static UserBashResult operations(BashOperations operations) {
            return new UserBashResult(operations, null);
        }

        public static UserBashResult result(BashExecutor.Result result) {
            return new UserBashResult(null, result);
        }
    }

    record ToolCallResult(Object input, String reason, boolean block) {
        public static ToolCallResult transform(Object input) {
            return new ToolCallResult(input, null, false);
        }

        public static ToolCallResult block(String reason) {
            return new ToolCallResult(null, reason, true);
        }
    }

    record ToolResultPatch(List<Content> content, Object details, Boolean error) {
        public static ToolResultPatch content(List<Content> content) {
            return new ToolResultPatch(content, null, null);
        }

        public static ToolResultPatch details(Object details) {
            return new ToolResultPatch(null, details, null);
        }

        public static ToolResultPatch error(boolean error) {
            return new ToolResultPatch(null, null, error);
        }

        public static ToolResultPatch of(List<Content> content, Object details, Boolean error) {
            return new ToolResultPatch(content, details, error);
        }
    }

    String name();

    default String description() {
        return "";
    }

    default List<Tool> registerTools() {
        return Collections.emptyList();
    }

    default AgentTool.AgentToolResult executeTool(String toolName, Object input) throws Exception {
        return null;
    }

    default List<SlashCommands.SlashCommandInfo> registerCommands() {
        return Collections.emptyList();
    }

    default String executeCommand(String commandName, String arguments) throws Exception {
        return null;
    }

    default String executeCommand(String commandName, String arguments, ExtensionCommandContext context) throws Exception {
        return executeCommand(commandName, arguments);
    }

    default Map<String, String> registerFlags() {
        return Collections.emptyMap();
    }

    default void onBeforeTurn(String prompt) {}

    default void onAfterTurn(String response) {}

    default void onBeforeCompact(int tokensBefore, int summarizedMessages, int turnPrefixMessages) {}

    default void onAfterCompact(String entryId, String summary) {}

    default InputResult onInput(String text, ExtensionCommandContext context) throws Exception {
        return null;
    }

    default UserBashResult onUserBash(String command, boolean excludeFromContext, Path cwd) throws Exception {
        return null;
    }

    default void onBeforeToolCall(String toolName, String input) {}

    default ToolCallResult onToolCall(String toolName, Object input, ExtensionCommandContext context) throws Exception {
        return null;
    }

    default ToolResultPatch onToolResult(String toolName, Object input, AgentTool.AgentToolResult result,
                                        ExtensionCommandContext context) throws Exception {
        return null;
    }

    default void onAfterToolCall(String toolName, String output) {}
}
