package works.earendil.pi.codingagent.core.extensions;

import works.earendil.pi.agent.core.AgentTool;
import works.earendil.pi.ai.model.Content;
import works.earendil.pi.ai.model.Tool;
import works.earendil.pi.codingagent.core.SlashCommands;
import works.earendil.pi.common.json.JsonCodec;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ExtensionRunner {
    private static final Set<String> RESERVED_INTERACTIVE_COMMANDS = Set.of("help", "exit", "quit", "clear");
    private final List<ExtensionPlugin> plugins;

    public ExtensionRunner(List<ExtensionPlugin> plugins) {
        this.plugins = plugins == null ? List.of() : List.copyOf(plugins);
    }

    public List<Tool> collectTools() {
        List<Tool> tools = new ArrayList<>();
        for (ExtensionPlugin plugin : plugins) {
            try {
                for (Tool tool : plugin.registerTools()) {
                    if (isUsableTool(tool)) {
                        tools.add(tool);
                    }
                }
            } catch (Exception ignored) {}
        }
        return List.copyOf(tools);
    }

    public List<AgentTool> collectAgentTools() {
        List<AgentTool> tools = new ArrayList<>();
        for (ExtensionPlugin plugin : plugins) {
            try {
                for (Tool tool : plugin.registerTools()) {
                    if (isUsableTool(tool)) {
                        tools.add(new RegisteredExtensionTool(plugin, tool));
                    }
                }
            } catch (Exception ignored) {}
        }
        return List.copyOf(tools);
    }

    public List<SlashCommands.SlashCommandInfo> collectCommands() {
        Map<String, SlashCommands.SlashCommandInfo> commands = new LinkedHashMap<>();
        for (ExtensionPlugin plugin : plugins) {
            try {
                for (SlashCommands.SlashCommandInfo command : plugin.registerCommands()) {
                    if (isUsableCommand(command)) {
                        commands.putIfAbsent(normalizeCommandName(command.name()), command);
                    }
                }
            } catch (Exception ignored) {}
        }
        return List.copyOf(commands.values());
    }

    public boolean hasCommand(String commandName) {
        String normalized = normalizeCommandName(commandName);
        if (normalized.isBlank()) {
            return false;
        }
        for (ExtensionPlugin plugin : plugins) {
            if (pluginHasCommand(plugin, normalized)) {
                return true;
            }
        }
        return false;
    }

    public Optional<String> executeCommand(String commandName, String arguments) throws Exception {
        return executeCommand(commandName, arguments, null);
    }

    public Optional<String> executeCommand(String commandName, String arguments,
                                           ExtensionCommandContext context) throws Exception {
        String normalized = normalizeCommandName(commandName);
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        for (ExtensionPlugin plugin : plugins) {
            if (!pluginHasCommand(plugin, normalized)) {
                continue;
            }
            String result = plugin.executeCommand(normalized, arguments == null ? "" : arguments, context);
            return Optional.ofNullable(result);
        }
        return Optional.empty();
    }

    public Map<String, String> collectFlags() {
        Map<String, String> flags = new HashMap<>();
        for (ExtensionPlugin plugin : plugins) {
            try {
                flags.putAll(plugin.registerFlags());
            } catch (Exception ignored) {}
        }
        return Map.copyOf(flags);
    }

    public void emitBeforeTurn(String prompt) {
        for (ExtensionPlugin plugin : plugins) {
            try {
                plugin.onBeforeTurn(prompt);
            } catch (Exception ignored) {}
        }
    }

    public void emitAfterTurn(String response) {
        for (ExtensionPlugin plugin : plugins) {
            try {
                plugin.onAfterTurn(response);
            } catch (Exception ignored) {}
        }
    }

    public void emitBeforeCompact(int tokensBefore, int summarizedMessages, int turnPrefixMessages) {
        for (ExtensionPlugin plugin : plugins) {
            try {
                plugin.onBeforeCompact(tokensBefore, summarizedMessages, turnPrefixMessages);
            } catch (Exception ignored) {}
        }
    }

    public void emitAfterCompact(String entryId, String summary) {
        for (ExtensionPlugin plugin : plugins) {
            try {
                plugin.onAfterCompact(entryId, summary);
            } catch (Exception ignored) {}
        }
    }

    public Optional<ExtensionPlugin.InputResult> emitInput(String text, ExtensionCommandContext context) {
        String originalText = text == null ? "" : text;
        String currentText = originalText;
        String output = null;
        for (ExtensionPlugin plugin : plugins) {
            try {
                ExtensionPlugin.InputResult result = plugin.onInput(currentText, context);
                if (result == null) {
                    continue;
                }
                if (result.output() != null && !result.output().isBlank()) {
                    output = result.output();
                }
                if (result.handled()) {
                    return Optional.of(new ExtensionPlugin.InputResult(currentText, output, true));
                }
                if (result.text() != null) {
                    currentText = result.text();
                }
            } catch (Exception ignored) {}
        }
        if (!currentText.equals(originalText) || output != null) {
            return Optional.of(new ExtensionPlugin.InputResult(currentText, output, false));
        }
        return Optional.empty();
    }

    public Optional<ExtensionPlugin.UserBashResult> emitUserBash(String command, boolean excludeFromContext, Path cwd) {
        for (ExtensionPlugin plugin : plugins) {
            try {
                ExtensionPlugin.UserBashResult result = plugin.onUserBash(command, excludeFromContext, cwd);
                if (result != null && (result.result() != null || result.operations() != null)) {
                    return Optional.of(result);
                }
            } catch (Exception ignored) {}
        }
        return Optional.empty();
    }

    public void emitBeforeToolCall(String toolName, String input) {
        for (ExtensionPlugin plugin : plugins) {
            try {
                plugin.onBeforeToolCall(toolName, input);
            } catch (Exception ignored) {}
        }
    }

    public Optional<ExtensionPlugin.ToolCallResult> emitToolCall(String toolName, Object input,
                                                                 ExtensionCommandContext context) {
        Object currentInput = input;
        boolean changed = false;
        for (ExtensionPlugin plugin : plugins) {
            try {
                plugin.onBeforeToolCall(toolName, payload(currentInput));
                ExtensionPlugin.ToolCallResult result = plugin.onToolCall(toolName, currentInput, context);
                if (result == null) {
                    continue;
                }
                if (result.block()) {
                    return Optional.of(new ExtensionPlugin.ToolCallResult(currentInput, result.reason(), true));
                }
                if (result.input() != null) {
                    currentInput = result.input();
                    changed = true;
                }
            } catch (Exception ignored) {}
        }
        if (changed) {
            return Optional.of(ExtensionPlugin.ToolCallResult.transform(currentInput));
        }
        return Optional.empty();
    }

    public void emitAfterToolCall(String toolName, String output) {
        for (ExtensionPlugin plugin : plugins) {
            try {
                plugin.onAfterToolCall(toolName, output);
            } catch (Exception ignored) {}
        }
    }

    public AgentTool.AgentToolResult emitToolResult(String toolName, Object input, AgentTool.AgentToolResult result,
                                                    ExtensionCommandContext context) {
        AgentTool.AgentToolResult current = result;
        for (ExtensionPlugin plugin : plugins) {
            try {
                ExtensionPlugin.ToolResultPatch patch = plugin.onToolResult(toolName, input, current, context);
                if (patch == null) {
                    continue;
                }
                List<Content> content = patch.content() == null ? current.content() : List.copyOf(patch.content());
                Object details = patch.details() == null ? current.details() : patch.details();
                boolean error = patch.error() == null ? current.error() : patch.error();
                current = new AgentTool.AgentToolResult(content, details, error, current.terminate());
            } catch (Exception ignored) {}
        }
        emitAfterToolCall(toolName, textFromContent(current.content()));
        return current;
    }

    private static boolean isUsableTool(Tool tool) {
        return tool != null && tool.name() != null && !tool.name().isBlank();
    }

    private static boolean isUsableCommand(SlashCommands.SlashCommandInfo command) {
        return command != null
                && command.name() != null
                && !normalizeCommandName(command.name()).isBlank()
                && !SlashCommands.isBuiltin(command.name())
                && !RESERVED_INTERACTIVE_COMMANDS.contains(normalizeCommandName(command.name()));
    }

    private static boolean pluginHasCommand(ExtensionPlugin plugin, String normalizedCommandName) {
        try {
            for (SlashCommands.SlashCommandInfo command : plugin.registerCommands()) {
                if (isUsableCommand(command)
                        && normalizeCommandName(command.name()).equals(normalizedCommandName)) {
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static String normalizeCommandName(String commandName) {
        return commandName == null ? "" : commandName.trim().replaceFirst("^/+", "").toLowerCase();
    }

    private static String payload(Object value) {
        if (value == null) {
            return "";
        }
        try {
            return JsonCodec.stringify(JsonCodec.mapper().valueToTree(value));
        } catch (Exception ignored) {
            return value.toString();
        }
    }

    private static String textFromContent(List<Content> content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (Content item : content) {
            if (item instanceof Content.Text t) {
                text.append(t.text());
            }
        }
        return text.toString();
    }

    private record RegisteredExtensionTool(ExtensionPlugin plugin, Tool tool) implements AgentTool {
        @Override
        public Tool definition() {
            return tool;
        }

        @Override
        public AgentToolResult execute(Object input) throws Exception {
            String toolName = tool.name();
            String extension = plugin.name() == null || plugin.name().isBlank() ? "unknown" : plugin.name();
            AgentToolResult result = plugin.executeTool(toolName, input);
            if (result != null) {
                return result;
            }
            return new AgentToolResult(List.of(new Content.Text("Extension tool '" + toolName
                    + "' from '" + extension
                    + "' is registered, but this Java extension SPI does not provide a tool executor yet.")),
                    Map.of("extension", extension, "tool", toolName), true, false);
        }
    }
}
