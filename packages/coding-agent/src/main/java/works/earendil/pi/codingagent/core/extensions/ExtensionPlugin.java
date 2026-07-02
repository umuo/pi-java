package works.earendil.pi.codingagent.core.extensions;

import works.earendil.pi.ai.model.Tool;

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

    default Map<String, String> registerFlags() {
        return Collections.emptyMap();
    }

    default void onBeforeTurn(String prompt) {}

    default void onAfterTurn(String response) {}

    default void onBeforeToolCall(String toolName, String input) {}

    default void onAfterToolCall(String toolName, String output) {}
}
