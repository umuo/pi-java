package works.earendil.pi.codingagent.core.extensions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import works.earendil.pi.agent.core.AgentMessage;
import works.earendil.pi.ai.model.Content;
import works.earendil.pi.codingagent.core.AgentSession;
import works.earendil.pi.common.json.JsonCodec;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

public final class ExtensionCommandContext {
    public enum UserMessageDelivery {
        STEER,
        FOLLOW_UP
    }

    public enum MessageDelivery {
        STEER,
        FOLLOW_UP,
        NEXT_TURN
    }

    public record UiContext(String mode, boolean hasUi, int terminalColumns, int terminalRows) {
        public UiContext(boolean interactive, int terminalColumns, int terminalRows) {
            this(interactive ? "tui" : "print", interactive, terminalColumns, terminalRows);
        }

        public UiContext {
            mode = normalizeMode(mode);
            hasUi = "tui".equals(mode) || "rpc".equals(mode);
            terminalColumns = Math.max(0, terminalColumns);
            terminalRows = Math.max(0, terminalRows);
        }

        public static UiContext none() {
            return print();
        }

        public static UiContext print() {
            return new UiContext("print", false, 0, 0);
        }

        public static UiContext json() {
            return new UiContext("json", false, 0, 0);
        }

        public static UiContext rpc() {
            return new UiContext("rpc", true, 0, 0);
        }

        public static UiContext interactive(int terminalColumns, int terminalRows) {
            return tui(terminalColumns, terminalRows);
        }

        public static UiContext tui(int terminalColumns, int terminalRows) {
            return new UiContext("tui", true, terminalColumns, terminalRows);
        }

        public boolean interactive() {
            return "tui".equals(mode);
        }

        private static String normalizeMode(String mode) {
            if (mode == null || mode.isBlank()) {
                return "print";
            }
            return switch (mode.trim().toLowerCase()) {
                case "tui", "interactive" -> "tui";
                case "rpc" -> "rpc";
                case "json" -> "json";
                default -> "print";
            };
        }
    }

    private final AgentSession session;
    private final Path cwd;
    private final String commandName;
    private final String arguments;
    private final List<String> argv;
    private final Map<String, String> options;
    private final List<String> flags;
    private final List<String> positionals;
    private final UserMessageSender userMessageSender;
    private final UiContext ui;

    public ExtensionCommandContext(AgentSession session) {
        this(session, "", "");
    }

    public ExtensionCommandContext(Path cwd) {
        this.session = null;
        this.cwd = Objects.requireNonNull(cwd, "cwd").toAbsolutePath().normalize();
        this.commandName = "";
        this.arguments = "";
        this.argv = List.of();
        this.options = Map.of();
        this.flags = List.of();
        this.positionals = List.of();
        this.userMessageSender = (content, source) -> {
            throw new IllegalStateException("Cannot send user messages without an agent session");
        };
        this.ui = UiContext.none();
    }

    public ExtensionCommandContext(AgentSession session, String commandName, String arguments) {
        this(session, commandName, arguments, null, UiContext.none());
    }

    public ExtensionCommandContext(AgentSession session, String commandName, String arguments,
                                   UserMessageSender userMessageSender) {
        this(session, commandName, arguments, userMessageSender, UiContext.none());
    }

    public ExtensionCommandContext(AgentSession session, String commandName, String arguments, UiContext ui) {
        this(session, commandName, arguments, null, ui);
    }

    public ExtensionCommandContext(AgentSession session, String commandName, String arguments,
                                   UserMessageSender userMessageSender, UiContext ui) {
        this.session = Objects.requireNonNull(session, "session");
        this.cwd = session.sessionManager().cwd();
        this.commandName = commandName == null ? "" : commandName.trim();
        this.arguments = arguments == null ? "" : arguments.trim();
        this.argv = parseArgv(this.arguments);
        ParsedArguments parsed = parseStructuredArguments(this.argv);
        this.options = parsed.options();
        this.flags = parsed.flags();
        this.positionals = parsed.positionals();
        this.userMessageSender = userMessageSender == null ? session::promptRaw : userMessageSender;
        this.ui = ui == null ? UiContext.none() : ui;
    }

    public Path cwd() {
        return cwd;
    }

    public String commandName() {
        return commandName;
    }

    public String arguments() {
        return arguments;
    }

    public List<String> argv() {
        return argv;
    }

    public Map<String, String> options() {
        return options;
    }

    public Optional<String> option(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(options.get(stripOptionPrefix(name)));
    }

    public List<String> flags() {
        return flags;
    }

    public boolean hasFlag(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return flags.contains(stripOptionPrefix(name));
    }

    public List<String> positionals() {
        return positionals;
    }

    public UiContext ui() {
        return ui;
    }

    public String mode() {
        return ui.mode();
    }

    public boolean hasUi() {
        return ui.hasUi();
    }

    public boolean interactive() {
        return ui.interactive();
    }

    public int terminalColumns() {
        return ui.terminalColumns();
    }

    public int terminalRows() {
        return ui.terminalRows();
    }

    public String sessionId() {
        return session.sessionManager().sessionId();
    }

    public Optional<Path> sessionFile() {
        return session.sessionFile();
    }

    public Optional<String> sessionName() {
        return session.sessionManager().sessionName();
    }

    public AgentSession.SessionStats stats() {
        return session.stats();
    }

    public boolean isIdle() {
        return session == null || session.isIdle();
    }

    public boolean hasPendingMessages() {
        return session != null && session.hasPendingMessages();
    }

    public Optional<CompletionStage<Void>> abortSignal() {
        return session == null ? Optional.empty() : session.abortSignal();
    }

    public boolean abortRequested() {
        return session != null && session.abortRequested();
    }

    public void abort() {
        if (session != null) {
            session.abort();
        }
    }

    public String setSessionName(String name) throws IOException {
        return session.setSessionName(name);
    }

    public String clearSessionName() throws IOException {
        return session.setSessionName("");
    }

    public String appendEntry(String customType, JsonNode data) throws IOException {
        return session.sessionManager().appendCustomEntry(customType, data == null ? NullNode.getInstance() : data);
    }

    public String appendEntry(String customType, Map<String, ?> data) throws IOException {
        JsonNode node = JsonCodec.mapper().valueToTree(data == null ? Map.of() : data);
        return appendEntry(customType, node);
    }

    public String setLabel(String entryId, String label) throws IOException {
        return session.sessionManager().appendLabelChange(entryId, label);
    }

    public String clearLabel(String entryId) throws IOException {
        return setLabel(entryId, null);
    }

    public Optional<String> label(String entryId) {
        return session.sessionManager().label(entryId);
    }

    public java.util.List<AgentMessage> sendUserMessage(String content) throws Exception {
        String message = content == null ? "" : content;
        if (session != null && !session.isIdle()) {
            return session.sendUserMessage(message, null, "extension");
        }
        return userMessageSender.send(List.of(new Content.Text(message)), "extension");
    }

    public java.util.List<AgentMessage> sendUserMessage(List<Content> content) throws Exception {
        if (session == null) {
            return userMessageSender.send(content, "extension");
        }
        if (!session.isIdle()) {
            return session.sendUserMessage(content, null, "extension");
        }
        return userMessageSender.send(content, "extension");
    }

    public java.util.List<AgentMessage> sendUserMessage(String content, UserMessageDelivery delivery) throws Exception {
        if (delivery == null) {
            return sendUserMessage(content);
        }
        AgentSession.UserMessageDelivery mode = switch (delivery) {
            case STEER -> AgentSession.UserMessageDelivery.STEER;
            case FOLLOW_UP -> AgentSession.UserMessageDelivery.FOLLOW_UP;
        };
        return session.sendUserMessage(content == null ? "" : content, mode, "extension");
    }

    public java.util.List<AgentMessage> sendUserMessage(List<Content> content, UserMessageDelivery delivery)
            throws Exception {
        if (delivery == null) {
            return sendUserMessage(content);
        }
        AgentSession.UserMessageDelivery mode = switch (delivery) {
            case STEER -> AgentSession.UserMessageDelivery.STEER;
            case FOLLOW_UP -> AgentSession.UserMessageDelivery.FOLLOW_UP;
        };
        return session.sendUserMessage(content, mode, "extension");
    }

    public java.util.List<AgentMessage> sendMessage(ExtensionPlugin.CustomMessage message) throws Exception {
        return session.sendMessage(message, null, false);
    }

    public java.util.List<AgentMessage> sendMessage(ExtensionPlugin.CustomMessage message, MessageDelivery delivery,
                                                    boolean triggerTurn) throws Exception {
        AgentSession.CustomMessageDelivery mode = delivery == null ? null : switch (delivery) {
            case STEER -> AgentSession.CustomMessageDelivery.STEER;
            case FOLLOW_UP -> AgentSession.CustomMessageDelivery.FOLLOW_UP;
            case NEXT_TURN -> AgentSession.CustomMessageDelivery.NEXT_TURN;
        };
        return session.sendMessage(message, mode, triggerTurn);
    }

    @FunctionalInterface
    public interface UserMessageSender {
        List<AgentMessage> send(List<Content> content, String source) throws Exception;
    }

    private static List<String> parseArgv(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        boolean escaping = false;
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (escaping) {
                current.append(ch);
                escaping = false;
                continue;
            }
            if (ch == '\\') {
                escaping = true;
                continue;
            }
            if (quote != 0) {
                if (ch == quote) {
                    quote = 0;
                } else {
                    current.append(ch);
                }
                continue;
            }
            if (ch == '"' || ch == '\'') {
                quote = ch;
                continue;
            }
            if (Character.isWhitespace(ch)) {
                if (!current.isEmpty()) {
                    args.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(ch);
        }
        if (escaping) {
            current.append('\\');
        }
        if (!current.isEmpty()) {
            args.add(current.toString());
        }
        return List.copyOf(args);
    }

    private static ParsedArguments parseStructuredArguments(List<String> argv) {
        Map<String, String> options = new LinkedHashMap<>();
        List<String> flags = new ArrayList<>();
        List<String> positionals = new ArrayList<>();
        for (int i = 0; i < argv.size(); i++) {
            String arg = argv.get(i);
            if (!arg.startsWith("--") || "--".equals(arg)) {
                positionals.add(arg);
                continue;
            }
            String option = arg.substring(2);
            int equals = option.indexOf('=');
            if (equals >= 0) {
                String key = option.substring(0, equals).trim();
                if (!key.isBlank()) {
                    options.put(key, option.substring(equals + 1));
                }
                continue;
            }
            if (i + 1 < argv.size() && !argv.get(i + 1).startsWith("--")) {
                options.put(option, argv.get(++i));
            } else {
                flags.add(option);
            }
        }
        return new ParsedArguments(Map.copyOf(options), List.copyOf(flags), List.copyOf(positionals));
    }

    private static String stripOptionPrefix(String name) {
        return name.trim().replaceFirst("^-+", "");
    }

    private record ParsedArguments(Map<String, String> options, List<String> flags, List<String> positionals) {
    }
}
