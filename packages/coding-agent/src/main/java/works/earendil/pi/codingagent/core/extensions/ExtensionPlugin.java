package works.earendil.pi.codingagent.core.extensions;

import works.earendil.pi.agent.core.AgentTool;
import works.earendil.pi.ai.model.Tool;
import works.earendil.pi.codingagent.core.SlashCommands;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public interface ExtensionPlugin {
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

    default void onBeforeToolCall(String toolName, String input) {}

    default void onAfterToolCall(String toolName, String output) {}
}
