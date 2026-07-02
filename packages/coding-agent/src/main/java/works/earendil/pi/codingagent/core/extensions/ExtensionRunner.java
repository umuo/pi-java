package works.earendil.pi.codingagent.core.extensions;

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
                tools.addAll(plugin.registerTools());
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
}
