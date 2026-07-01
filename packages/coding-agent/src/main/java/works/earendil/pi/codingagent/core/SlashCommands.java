package works.earendil.pi.codingagent.core;

import works.earendil.pi.codingagent.resources.SourceInfo;
import works.earendil.pi.codingagent.resources.Skill;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SlashCommands {
    public static final List<BuiltinSlashCommand> BUILTIN_SLASH_COMMANDS = builtins();

    private static final Map<String, BuiltinSlashCommand> BUILTINS_BY_NAME = byName(BUILTIN_SLASH_COMMANDS);

    private SlashCommands() {
    }

    public enum SlashCommandSource {
        EXTENSION("extension"),
        PROMPT("prompt"),
        SKILL("skill");

        private final String wireName;

        SlashCommandSource(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }
    }

    public record SlashCommandInfo(
            String name,
            String description,
            SlashCommandSource source,
            SourceInfo sourceInfo
    ) {
    }

    public record BuiltinSlashCommand(String name, String description) {
    }

    public static Optional<BuiltinSlashCommand> findBuiltin(String name) {
        return Optional.ofNullable(BUILTINS_BY_NAME.get(normalizeName(name)));
    }

    public static boolean isBuiltin(String name) {
        return findBuiltin(name).isPresent();
    }

    public static List<SlashCommandInfo> mergeExternalCommands(List<SlashCommandInfo> extensionCommands,
                                                               List<SlashCommandInfo> promptCommands,
                                                               List<SlashCommandInfo> skillCommands) {
        List<SlashCommandInfo> commands = new ArrayList<>();
        if (extensionCommands != null) {
            commands.addAll(extensionCommands);
        }
        if (promptCommands != null) {
            commands.addAll(promptCommands);
        }
        if (skillCommands != null) {
            commands.addAll(skillCommands);
        }
        return List.copyOf(commands);
    }

    public static List<SlashCommandInfo> skillCommands(List<Skill> skills) {
        if (skills == null || skills.isEmpty()) {
            return List.of();
        }
        List<SlashCommandInfo> commands = new ArrayList<>();
        for (Skill skill : skills) {
            String description = skill.disableModelInvocation()
                    ? skill.description() + " (manual only)"
                    : skill.description();
            commands.add(new SlashCommandInfo("skill:" + skill.name(), description,
                    SlashCommandSource.SKILL, skill.sourceInfo()));
        }
        return List.copyOf(commands);
    }

    public static String invocationName(String rawText) {
        if (rawText == null) {
            return "";
        }
        String trimmed = rawText.trim();
        if (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        int split = firstWhitespace(trimmed);
        return split < 0 ? trimmed : trimmed.substring(0, split);
    }

    public static String invocationArguments(String rawText) {
        if (rawText == null) {
            return "";
        }
        String trimmed = rawText.trim();
        if (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        int split = firstWhitespace(trimmed);
        return split < 0 ? "" : trimmed.substring(split).trim();
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.trim().replaceFirst("^/+", "");
    }

    private static int firstWhitespace(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static Map<String, BuiltinSlashCommand> byName(List<BuiltinSlashCommand> commands) {
        Map<String, BuiltinSlashCommand> map = new LinkedHashMap<>();
        for (BuiltinSlashCommand command : commands) {
            map.put(command.name(), command);
        }
        return Collections.unmodifiableMap(map);
    }

    private static List<BuiltinSlashCommand> builtins() {
        List<BuiltinSlashCommand> commands = new ArrayList<>();
        commands.add(new BuiltinSlashCommand("settings", "Open settings menu"));
        commands.add(new BuiltinSlashCommand("model", "Select model (opens selector UI)"));
        commands.add(new BuiltinSlashCommand("models", "List or refresh available models"));
        commands.add(new BuiltinSlashCommand("scoped-models", "Enable/disable models for Ctrl+P cycling"));
        commands.add(new BuiltinSlashCommand("export", "Export session (HTML default, or specify path: .html/.jsonl)"));
        commands.add(new BuiltinSlashCommand("import", "Import and resume a session from a JSONL file"));
        commands.add(new BuiltinSlashCommand("share", "Share session as a secret GitHub gist"));
        commands.add(new BuiltinSlashCommand("copy", "Copy last agent message to clipboard"));
        commands.add(new BuiltinSlashCommand("name", "Set session display name"));
        commands.add(new BuiltinSlashCommand("session", "Show session info and stats"));
        commands.add(new BuiltinSlashCommand("grill-me", "Start an interactive design interview"));
        commands.add(new BuiltinSlashCommand("teamwork-preview", "Preview or execute the multi-agent team plan"));
        commands.add(new BuiltinSlashCommand("orchestrator-status", "Show orchestrator instances, logs, runtime settings, stderr tails, or live RPC events"));
        commands.add(new BuiltinSlashCommand("changelog", "Show changelog entries"));
        commands.add(new BuiltinSlashCommand("hotkeys", "Show all keyboard shortcuts"));
        commands.add(new BuiltinSlashCommand("fork", "Create a new fork from a previous user message"));
        commands.add(new BuiltinSlashCommand("clone", "Duplicate the current session at the current position"));
        commands.add(new BuiltinSlashCommand("tree", "Navigate session tree (switch branches)"));
        commands.add(new BuiltinSlashCommand("trust", "Save project trust decision for future sessions"));
        commands.add(new BuiltinSlashCommand("login", "Configure provider authentication"));
        commands.add(new BuiltinSlashCommand("logout", "Remove provider authentication"));
        commands.add(new BuiltinSlashCommand("new", "Start a new session"));
        commands.add(new BuiltinSlashCommand("compact", "Manually compact the session context"));
        commands.add(new BuiltinSlashCommand("resume", "Resume a different session"));
        commands.add(new BuiltinSlashCommand("reload", "Reload keybindings, extensions, skills, prompts, and themes"));
        commands.add(new BuiltinSlashCommand("quit", "Quit pi"));
        return Collections.unmodifiableList(commands);
    }
}
