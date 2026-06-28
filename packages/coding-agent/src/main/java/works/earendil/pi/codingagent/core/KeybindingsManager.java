package works.earendil.pi.codingagent.core;

import com.fasterxml.jackson.databind.JsonNode;
import works.earendil.pi.common.json.JsonCodec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class KeybindingsManager {
    public static final Map<String, KeybindingDefinition> KEYBINDINGS = definitions();
    private static final Map<String, String> KEYBINDING_NAME_MIGRATIONS = migrations();

    private final Path configPath;
    private Map<String, Object> userBindings;
    private final Map<String, List<String>> keysById = new LinkedHashMap<>();
    private List<KeybindingConflict> conflicts = List.of();

    public KeybindingsManager() {
        this(Map.of(), null);
    }

    public KeybindingsManager(Map<String, Object> userBindings) {
        this(userBindings, null);
    }

    public KeybindingsManager(Map<String, Object> userBindings, Path configPath) {
        this.userBindings = toKeybindingsConfig(userBindings);
        this.configPath = configPath;
        rebuild();
    }

    public static KeybindingsManager create(Path agentDir) {
        Path configPath = agentDir.resolve("keybindings.json");
        return new KeybindingsManager(loadFromFile(configPath), configPath);
    }

    public void reload() {
        if (configPath != null) {
            setUserBindings(loadFromFile(configPath));
        }
    }

    public boolean matches(String data, String keybinding) {
        for (String key : getKeys(keybinding)) {
            if (matchesKey(data, key)) {
                return true;
            }
        }
        return false;
    }

    public List<String> getKeys(String keybinding) {
        return List.copyOf(keysById.getOrDefault(keybinding, List.of()));
    }

    public KeybindingDefinition getDefinition(String keybinding) {
        return KEYBINDINGS.get(keybinding);
    }

    public List<KeybindingConflict> getConflicts() {
        return conflicts.stream()
                .map(conflict -> new KeybindingConflict(conflict.key(), List.copyOf(conflict.keybindings())))
                .toList();
    }

    public void setUserBindings(Map<String, Object> userBindings) {
        this.userBindings = toKeybindingsConfig(userBindings);
        rebuild();
    }

    public Map<String, Object> getUserBindings() {
        return copyConfig(userBindings);
    }

    public Map<String, Object> getEffectiveConfig() {
        Map<String, Object> resolved = new LinkedHashMap<>();
        for (String id : KEYBINDINGS.keySet()) {
            List<String> keys = keysById.getOrDefault(id, List.of());
            resolved.put(id, keys.size() == 1 ? keys.getFirst() : List.copyOf(keys));
        }
        return resolved;
    }

    public static MigrationResult migrateKeybindingsConfig(Map<String, Object> rawConfig) {
        Map<String, Object> config = new LinkedHashMap<>();
        boolean migrated = false;
        for (Map.Entry<String, Object> entry : rawConfig.entrySet()) {
            String nextKey = KEYBINDING_NAME_MIGRATIONS.getOrDefault(entry.getKey(), entry.getKey());
            if (!nextKey.equals(entry.getKey())) {
                migrated = true;
            }
            if (!entry.getKey().equals(nextKey) && rawConfig.containsKey(nextKey)) {
                migrated = true;
                continue;
            }
            config.put(nextKey, entry.getValue());
        }
        return new MigrationResult(orderKeybindingsConfig(config), migrated);
    }

    private void rebuild() {
        keysById.clear();
        Map<String, Set<String>> userClaims = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : userBindings.entrySet()) {
            if (!KEYBINDINGS.containsKey(entry.getKey())) {
                continue;
            }
            for (String key : normalizeKeys(entry.getValue())) {
                userClaims.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(entry.getKey());
            }
        }
        List<KeybindingConflict> nextConflicts = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : userClaims.entrySet()) {
            if (entry.getValue().size() > 1) {
                nextConflicts.add(new KeybindingConflict(entry.getKey(), List.copyOf(entry.getValue())));
            }
        }
        conflicts = List.copyOf(nextConflicts);

        for (Map.Entry<String, KeybindingDefinition> entry : KEYBINDINGS.entrySet()) {
            Object userKeys = userBindings.get(entry.getKey());
            keysById.put(entry.getKey(), normalizeKeys(userKeys == null ? entry.getValue().defaultKeys() : userKeys));
        }
    }

    private static Map<String, Object> loadFromFile(Path path) {
        if (!Files.exists(path)) {
            return Map.of();
        }
        try {
            JsonNode node = JsonCodec.parse(Files.readString(path, StandardCharsets.UTF_8));
            if (!node.isObject()) {
                return Map.of();
            }
            Map<String, Object> raw = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry -> raw.put(entry.getKey(), javaValue(entry.getValue())));
            return toKeybindingsConfig(migrateKeybindingsConfig(raw).config());
        } catch (IOException | RuntimeException ignored) {
            return Map.of();
        }
    }

    private static Object javaValue(JsonNode node) {
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isArray()) {
            List<String> values = new ArrayList<>();
            for (JsonNode item : node) {
                if (!item.isTextual()) {
                    return null;
                }
                values.add(item.asText());
            }
            return List.copyOf(values);
        }
        return null;
    }

    private static Map<String, Object> toKeybindingsConfig(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> config = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                continue;
            }
            Object binding = entry.getValue();
            if (binding instanceof String) {
                config.put(key, binding);
            } else if (binding instanceof List<?> list && list.stream().allMatch(String.class::isInstance)) {
                config.put(key, List.copyOf(list));
            }
        }
        return config;
    }

    private static Map<String, Object> orderKeybindingsConfig(Map<String, Object> config) {
        Map<String, Object> ordered = new LinkedHashMap<>();
        for (String keybinding : KEYBINDINGS.keySet()) {
            if (config.containsKey(keybinding)) {
                ordered.put(keybinding, config.get(keybinding));
            }
        }
        config.keySet().stream()
                .filter(key -> !ordered.containsKey(key))
                .sorted(Comparator.naturalOrder())
                .forEach(key -> ordered.put(key, config.get(key)));
        return ordered;
    }

    private static List<String> normalizeKeys(Object keys) {
        List<?> keyList;
        if (keys == null) {
            return List.of();
        } else if (keys instanceof String key) {
            keyList = List.of(key);
        } else if (keys instanceof List<?> list) {
            keyList = list;
        } else {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        for (Object key : keyList) {
            if (key instanceof String stringKey) {
                seen.add(stringKey);
            }
        }
        return List.copyOf(seen);
    }

    private static Map<String, Object> copyConfig(Map<String, Object> config) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            Object value = entry.getValue();
            copy.put(entry.getKey(), value instanceof List<?> list ? List.copyOf(list) : value);
        }
        return copy;
    }

    private static boolean matchesKey(String data, String keyId) {
        if (data.equals(keyId)) {
            return true;
        }
        String[] parts = keyId.toLowerCase(java.util.Locale.ROOT).split("\\+");
        String key = parts[parts.length - 1];
        Set<String> modifiers = new LinkedHashSet<>(List.of(parts).subList(0, Math.max(0, parts.length - 1)));
        if (modifiers.isEmpty()) {
            return switch (key) {
                case "escape", "esc" -> data.equals("\u001B");
                case "enter", "return" -> data.equals("\r") || data.equals("\n");
                case "tab" -> data.equals("\t");
                case "space" -> data.equals(" ");
                case "backspace" -> data.equals("\u007F") || data.equals("\b");
                case "up" -> data.equals("\u001B[A") || data.equals("\u001BOA");
                case "down" -> data.equals("\u001B[B") || data.equals("\u001BOB");
                case "right" -> data.equals("\u001B[C") || data.equals("\u001BOC");
                case "left" -> data.equals("\u001B[D") || data.equals("\u001BOD");
                default -> data.equals(key);
            };
        }
        if (modifiers.equals(Set.of("ctrl")) && key.length() == 1) {
            char ch = key.charAt(0);
            if (ch >= 'a' && ch <= 'z') {
                return data.length() == 1 && data.charAt(0) == ch - 'a' + 1;
            }
        }
        if (modifiers.equals(Set.of("alt")) && key.length() == 1) {
            return data.equals("\u001B" + key);
        }
        if (modifiers.equals(Set.of("shift")) && key.equals("tab")) {
            return data.equals("\u001B[Z");
        }
        return false;
    }

    private static Map<String, KeybindingDefinition> definitions() {
        Map<String, KeybindingDefinition> definitions = new LinkedHashMap<>();
        define(definitions, "tui.editor.cursorUp", "up", "Move cursor up");
        define(definitions, "tui.editor.cursorDown", "down", "Move cursor down");
        define(definitions, "tui.editor.cursorLeft", List.of("left", "ctrl+b"), "Move cursor left");
        define(definitions, "tui.editor.cursorRight", List.of("right", "ctrl+f"), "Move cursor right");
        define(definitions, "tui.editor.cursorWordLeft", List.of("alt+left", "ctrl+left", "alt+b"), "Move cursor word left");
        define(definitions, "tui.editor.cursorWordRight", List.of("alt+right", "ctrl+right", "alt+f"), "Move cursor word right");
        define(definitions, "tui.editor.cursorLineStart", List.of("home", "ctrl+a"), "Move to line start");
        define(definitions, "tui.editor.cursorLineEnd", List.of("end", "ctrl+e"), "Move to line end");
        define(definitions, "tui.editor.jumpForward", "ctrl+]", "Jump forward to character");
        define(definitions, "tui.editor.jumpBackward", "ctrl+alt+]", "Jump backward to character");
        define(definitions, "tui.editor.pageUp", "pageUp", "Page up");
        define(definitions, "tui.editor.pageDown", "pageDown", "Page down");
        define(definitions, "tui.editor.deleteCharBackward", "backspace", "Delete character backward");
        define(definitions, "tui.editor.deleteCharForward", List.of("delete", "ctrl+d"), "Delete character forward");
        define(definitions, "tui.editor.deleteWordBackward", List.of("ctrl+w", "alt+backspace"), "Delete word backward");
        define(definitions, "tui.editor.deleteWordForward", List.of("alt+d", "alt+delete"), "Delete word forward");
        define(definitions, "tui.editor.deleteToLineStart", "ctrl+u", "Delete to line start");
        define(definitions, "tui.editor.deleteToLineEnd", "ctrl+k", "Delete to line end");
        define(definitions, "tui.editor.yank", "ctrl+y", "Yank");
        define(definitions, "tui.editor.yankPop", "alt+y", "Yank pop");
        define(definitions, "tui.editor.undo", "ctrl+-", "Undo");
        define(definitions, "tui.input.newLine", List.of("shift+enter", "ctrl+j"), "Insert newline");
        define(definitions, "tui.input.submit", "enter", "Submit input");
        define(definitions, "tui.input.tab", "tab", "Tab / autocomplete");
        define(definitions, "tui.input.copy", "ctrl+c", "Copy selection");
        define(definitions, "tui.select.up", "up", "Move selection up");
        define(definitions, "tui.select.down", "down", "Move selection down");
        define(definitions, "tui.select.pageUp", "pageUp", "Selection page up");
        define(definitions, "tui.select.pageDown", "pageDown", "Selection page down");
        define(definitions, "tui.select.confirm", "enter", "Confirm selection");
        define(definitions, "tui.select.cancel", List.of("escape", "ctrl+c"), "Cancel selection");
        defineApp(definitions);
        return Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
    }

    private static void defineApp(Map<String, KeybindingDefinition> definitions) {
        define(definitions, "app.interrupt", "escape", "Cancel or abort");
        define(definitions, "app.clear", "ctrl+c", "Clear editor");
        define(definitions, "app.exit", "ctrl+d", "Exit when editor is empty");
        define(definitions, "app.suspend", isWindows() ? List.of() : "ctrl+z", "Suspend to background");
        define(definitions, "app.thinking.cycle", "shift+tab", "Cycle thinking level");
        define(definitions, "app.model.cycleForward", "ctrl+p", "Cycle to next model");
        define(definitions, "app.model.cycleBackward", "shift+ctrl+p", "Cycle to previous model");
        define(definitions, "app.model.select", "ctrl+l", "Open model selector");
        define(definitions, "app.tools.expand", "ctrl+o", "Toggle tool output");
        define(definitions, "app.thinking.toggle", "ctrl+t", "Toggle thinking blocks");
        define(definitions, "app.session.toggleNamedFilter", "ctrl+n", "Toggle named session filter");
        define(definitions, "app.editor.external", "ctrl+g", "Open external editor");
        define(definitions, "app.message.followUp", "alt+enter", "Queue follow-up message");
        define(definitions, "app.message.dequeue", "alt+up", "Restore queued messages");
        define(definitions, "app.clipboard.pasteImage", isWindows() ? "alt+v" : "ctrl+v", "Paste image from clipboard");
        define(definitions, "app.session.new", List.of(), "Start a new session");
        define(definitions, "app.session.tree", List.of(), "Open session tree");
        define(definitions, "app.session.fork", List.of(), "Fork current session");
        define(definitions, "app.session.resume", List.of(), "Resume a session");
        define(definitions, "app.tree.foldOrUp", List.of("ctrl+left", "alt+left"), "Fold tree branch or move up");
        define(definitions, "app.tree.unfoldOrDown", List.of("ctrl+right", "alt+right"), "Unfold tree branch or move down");
        define(definitions, "app.tree.editLabel", "shift+l", "Edit tree label");
        define(definitions, "app.tree.toggleLabelTimestamp", "shift+t", "Toggle tree label timestamps");
        define(definitions, "app.session.togglePath", "ctrl+p", "Toggle session path display");
        define(definitions, "app.session.toggleSort", "ctrl+s", "Toggle session sort mode");
        define(definitions, "app.session.rename", "ctrl+r", "Rename session");
        define(definitions, "app.session.delete", "ctrl+d", "Delete session");
        define(definitions, "app.session.deleteNoninvasive", "ctrl+backspace", "Delete session when query is empty");
        define(definitions, "app.models.save", "ctrl+s", "Save model selection");
        define(definitions, "app.models.enableAll", "ctrl+a", "Enable all models");
        define(definitions, "app.models.clearAll", "ctrl+x", "Clear all models");
        define(definitions, "app.models.toggleProvider", "ctrl+p", "Toggle all models for provider");
        define(definitions, "app.models.reorderUp", "alt+up", "Move model up in order");
        define(definitions, "app.models.reorderDown", "alt+down", "Move model down in order");
        define(definitions, "app.tree.filter.default", "ctrl+d", "Tree filter: default view");
        define(definitions, "app.tree.filter.noTools", "ctrl+t", "Tree filter: hide tool results");
        define(definitions, "app.tree.filter.userOnly", "ctrl+u", "Tree filter: user messages only");
        define(definitions, "app.tree.filter.labeledOnly", "ctrl+l", "Tree filter: labeled entries only");
        define(definitions, "app.tree.filter.all", "ctrl+a", "Tree filter: show all entries");
        define(definitions, "app.tree.filter.cycleForward", "ctrl+o", "Tree filter: cycle forward");
        define(definitions, "app.tree.filter.cycleBackward", "shift+ctrl+o", "Tree filter: cycle backward");
    }

    private static void define(Map<String, KeybindingDefinition> definitions, String id, Object defaultKeys, String description) {
        definitions.put(id, new KeybindingDefinition(defaultKeys, description));
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    private static Map<String, String> migrations() {
        Map<String, String> migrations = new LinkedHashMap<>();
        migrations.put("cursorUp", "tui.editor.cursorUp");
        migrations.put("cursorDown", "tui.editor.cursorDown");
        migrations.put("cursorLeft", "tui.editor.cursorLeft");
        migrations.put("cursorRight", "tui.editor.cursorRight");
        migrations.put("cursorWordLeft", "tui.editor.cursorWordLeft");
        migrations.put("cursorWordRight", "tui.editor.cursorWordRight");
        migrations.put("cursorLineStart", "tui.editor.cursorLineStart");
        migrations.put("cursorLineEnd", "tui.editor.cursorLineEnd");
        migrations.put("jumpForward", "tui.editor.jumpForward");
        migrations.put("jumpBackward", "tui.editor.jumpBackward");
        migrations.put("pageUp", "tui.editor.pageUp");
        migrations.put("pageDown", "tui.editor.pageDown");
        migrations.put("deleteCharBackward", "tui.editor.deleteCharBackward");
        migrations.put("deleteCharForward", "tui.editor.deleteCharForward");
        migrations.put("deleteWordBackward", "tui.editor.deleteWordBackward");
        migrations.put("deleteWordForward", "tui.editor.deleteWordForward");
        migrations.put("deleteToLineStart", "tui.editor.deleteToLineStart");
        migrations.put("deleteToLineEnd", "tui.editor.deleteToLineEnd");
        migrations.put("yank", "tui.editor.yank");
        migrations.put("yankPop", "tui.editor.yankPop");
        migrations.put("undo", "tui.editor.undo");
        migrations.put("newLine", "tui.input.newLine");
        migrations.put("submit", "tui.input.submit");
        migrations.put("tab", "tui.input.tab");
        migrations.put("copy", "tui.input.copy");
        migrations.put("selectUp", "tui.select.up");
        migrations.put("selectDown", "tui.select.down");
        migrations.put("selectPageUp", "tui.select.pageUp");
        migrations.put("selectPageDown", "tui.select.pageDown");
        migrations.put("selectConfirm", "tui.select.confirm");
        migrations.put("selectCancel", "tui.select.cancel");
        migrations.put("interrupt", "app.interrupt");
        migrations.put("clear", "app.clear");
        migrations.put("exit", "app.exit");
        migrations.put("suspend", "app.suspend");
        migrations.put("cycleThinkingLevel", "app.thinking.cycle");
        migrations.put("cycleModelForward", "app.model.cycleForward");
        migrations.put("cycleModelBackward", "app.model.cycleBackward");
        migrations.put("selectModel", "app.model.select");
        migrations.put("expandTools", "app.tools.expand");
        migrations.put("toggleThinking", "app.thinking.toggle");
        migrations.put("toggleSessionNamedFilter", "app.session.toggleNamedFilter");
        migrations.put("externalEditor", "app.editor.external");
        migrations.put("followUp", "app.message.followUp");
        migrations.put("dequeue", "app.message.dequeue");
        migrations.put("pasteImage", "app.clipboard.pasteImage");
        migrations.put("newSession", "app.session.new");
        migrations.put("tree", "app.session.tree");
        migrations.put("fork", "app.session.fork");
        migrations.put("resume", "app.session.resume");
        migrations.put("treeFoldOrUp", "app.tree.foldOrUp");
        migrations.put("treeUnfoldOrDown", "app.tree.unfoldOrDown");
        migrations.put("treeEditLabel", "app.tree.editLabel");
        migrations.put("treeToggleLabelTimestamp", "app.tree.toggleLabelTimestamp");
        migrations.put("toggleSessionPath", "app.session.togglePath");
        migrations.put("toggleSessionSort", "app.session.toggleSort");
        migrations.put("renameSession", "app.session.rename");
        migrations.put("deleteSession", "app.session.delete");
        migrations.put("deleteSessionNoninvasive", "app.session.deleteNoninvasive");
        return Collections.unmodifiableMap(new LinkedHashMap<>(migrations));
    }

    public record KeybindingDefinition(Object defaultKeys, String description) {
    }

    public record KeybindingConflict(String key, List<String> keybindings) {
    }

    public record MigrationResult(Map<String, Object> config, boolean migrated) {
    }
}
