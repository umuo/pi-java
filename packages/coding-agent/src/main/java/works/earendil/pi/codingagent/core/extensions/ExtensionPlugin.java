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

    record CustomMessage(String customType, Object content, boolean display, Object details) {
        public CustomMessage {
            customType = customType == null || customType.isBlank() ? "extension" : customType.trim();
        }

        public static CustomMessage of(String customType, Object content) {
            return new CustomMessage(customType, content, true, null);
        }

        public static CustomMessage hidden(String customType, Object content) {
            return new CustomMessage(customType, content, false, null);
        }
    }

    record BeforeAgentStartResult(String systemPrompt, List<CustomMessage> messages) {
        public static BeforeAgentStartResult systemPrompt(String systemPrompt) {
            return new BeforeAgentStartResult(systemPrompt, null);
        }

        public static BeforeAgentStartResult message(CustomMessage message) {
            return new BeforeAgentStartResult(null, message == null ? null : List.of(message));
        }

        public static BeforeAgentStartResult of(String systemPrompt, List<CustomMessage> messages) {
            return new BeforeAgentStartResult(systemPrompt, messages);
        }
    }

    record SessionBeforeResult(boolean cancel, String reason) {
        public static SessionBeforeResult proceed() {
            return new SessionBeforeResult(false, null);
        }

        public static SessionBeforeResult cancel(String reason) {
            return new SessionBeforeResult(true, reason);
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

    default BeforeAgentStartResult onBeforeAgentStart(String prompt, String systemPrompt,
                                                     ExtensionCommandContext context) throws Exception {
        return null;
    }

    default void onAfterTurn(String response) {}

    default void onBeforeCompact(int tokensBefore, int summarizedMessages, int turnPrefixMessages) {}

    default void onAfterCompact(String entryId, String summary) {}

    default Object onBeforeProviderRequest(Object payload, ExtensionCommandContext context) throws Exception {
        return null;
    }

    default void onAfterProviderResponse(int status, Map<String, String> headers,
                                         ExtensionCommandContext context) throws Exception {
    }

    default SessionBeforeResult onSessionBeforeSwitch(String reason, Path targetSessionFile,
                                                      ExtensionCommandContext context) throws Exception {
        return null;
    }

    default SessionBeforeResult onSessionBeforeFork(String entryId, String position,
                                                    ExtensionCommandContext context) throws Exception {
        return null;
    }

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
