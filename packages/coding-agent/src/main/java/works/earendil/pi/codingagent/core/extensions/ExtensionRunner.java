package works.earendil.pi.codingagent.core.extensions;

import works.earendil.pi.agent.core.AgentTool;
import works.earendil.pi.ai.model.Content;
import works.earendil.pi.ai.model.Tool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ExtensionRunner {
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
                        tools.add(new RegisteredExtensionTool(plugin.name(), tool));
                    }
                }
            } catch (Exception ignored) {}
        }
        return List.copyOf(tools);
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

    public void emitBeforeToolCall(String toolName, String input) {
        for (ExtensionPlugin plugin : plugins) {
            try {
                plugin.onBeforeToolCall(toolName, input);
            } catch (Exception ignored) {}
        }
    }

    public void emitAfterToolCall(String toolName, String output) {
        for (ExtensionPlugin plugin : plugins) {
            try {
                plugin.onAfterToolCall(toolName, output);
            } catch (Exception ignored) {}
        }
    }

    private static boolean isUsableTool(Tool tool) {
        return tool != null && tool.name() != null && !tool.name().isBlank();
    }

    private record RegisteredExtensionTool(String extensionName, Tool tool) implements AgentTool {
        @Override
        public Tool definition() {
            return tool;
        }

        @Override
        public AgentToolResult execute(Object input) {
            String toolName = tool.name();
            String extension = extensionName == null || extensionName.isBlank() ? "unknown" : extensionName;
            return new AgentToolResult(List.of(new Content.Text("Extension tool '" + toolName
                    + "' from '" + extension
                    + "' is registered, but this Java extension SPI does not provide a tool executor yet.")),
                    Map.of("extension", extension, "tool", toolName), true, false);
        }
    }
}
