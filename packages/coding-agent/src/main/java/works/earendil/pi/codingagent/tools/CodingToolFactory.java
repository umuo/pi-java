package works.earendil.pi.codingagent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import works.earendil.pi.agent.core.AgentTool;
import works.earendil.pi.ai.model.Content;
import works.earendil.pi.ai.model.Tool;
import works.earendil.pi.codingagent.core.BashExecutor;
import works.earendil.pi.codingagent.core.LocalBashOperations;
import works.earendil.pi.common.json.JsonCodec;
import works.earendil.pi.common.text.Truncation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CodingToolFactory {
    private CodingToolFactory() {
    }

    public record BashConfig(String commandPrefix, String shellPath) {
    }

    public enum ToolName {
        READ("read"),
        BASH("bash"),
        EDIT("edit"),
        WRITE("write"),
        GREP("grep"),
        FIND("find"),
        LS("ls");

        private final String value;

        ToolName(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    public static AgentTool createTool(ToolName toolName, Path cwd) {
        return createTool(toolName, cwd, null);
    }

    public static AgentTool createTool(ToolName toolName, Path cwd, BashConfig bashConfig) {
        return switch (toolName) {
            case READ -> read(cwd);
            case BASH -> bash(cwd, bashConfig);
            case EDIT -> edit(cwd);
            case WRITE -> write(cwd);
            case GREP -> grep(cwd);
            case FIND -> find(cwd);
            case LS -> ls(cwd);
        };
    }

    public static List<AgentTool> createCodingTools(Path cwd) {
        return List.of(read(cwd), bash(cwd), edit(cwd), write(cwd));
    }

    public static List<AgentTool> createReadOnlyTools(Path cwd) {
        return List.of(read(cwd), grep(cwd), find(cwd), ls(cwd));
    }

    public static Map<String, AgentTool> createAllTools(Path cwd) {
        return createAllTools(cwd, null);
    }

    public static Map<String, AgentTool> createAllTools(Path cwd, BashConfig bashConfig) {
        Map<String, AgentTool> tools = new LinkedHashMap<>();
        for (ToolName name : ToolName.values()) {
            AgentTool tool = createTool(name, cwd, bashConfig);
            tools.put(tool.name(), tool);
        }
        return Map.copyOf(tools);
    }

    public static AgentTool read(Path cwd) {
        ReadTool readTool = new ReadTool(cwd);
        return simpleWithSchema("read", "Read file contents",
                "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\",\"description\":\"File path to read\"},\"limit\":{\"type\":\"integer\",\"description\":\"Max lines\"}},\"required\":[\"path\"]}",
                input -> {
            Map<String, Object> args = object(input);
            Truncation.Result result = readTool.read(requiredString(args, "path"),
                    new Truncation.Options(number(args, "limit", Truncation.DEFAULT_MAX_LINES), Truncation.DEFAULT_MAX_BYTES));
            return new AgentTool.AgentToolResult(List.of(new Content.Text(result.content())), result, false, false);
        });
    }

    public static AgentTool write(Path cwd) {
        WriteTool writeTool = new WriteTool(cwd);
        return simpleWithSchema("write", "Write file contents",
                "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\",\"description\":\"File path to write\"},\"content\":{\"type\":\"string\",\"description\":\"Content to write\"},\"overwrite\":{\"type\":\"boolean\"}},\"required\":[\"path\",\"content\"]}",
                input -> {
            Map<String, Object> args = object(input);
            String rawPath = requiredString(args, "path");
            WriteTool.Result write = writeTool.write(rawPath, requiredString(args, "content"),
                    booleanValue(args, "overwrite", true));
            String diff = EditDiff.unifiedPatch(rawPath, write.oldContent(), write.newContent(), 3);
            return new AgentTool.AgentToolResult(List.of(new Content.Text("Wrote " + write.path())),
                    Map.of("path", write.path().toString(), "created", write.created(),
                            "bytes", write.newContent().getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                            "diff", diff, "patch", diff), false, false);
        });
    }

    public static AgentTool ls(Path cwd) {
        LsTool lsTool = new LsTool(cwd);
        return simpleWithSchema("ls", "List directory contents",
                "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\",\"description\":\"Directory path to list\"}}}",
                input -> {
            Map<String, Object> args = object(input);
            List<String> files = lsTool.list(string(args, "path", "."));
            return new AgentTool.AgentToolResult(List.of(new Content.Text(String.join("\n", files))), Map.of("count", files.size()), false, false);
        });
    }

    public static AgentTool grep(Path cwd) {
        GrepTool grepTool = new GrepTool(cwd);
        return simpleWithSchema("grep", "Search file contents by regex",
                "{\"type\":\"object\",\"properties\":{\"pattern\":{\"type\":\"string\",\"description\":\"Regex pattern to search\"}},\"required\":[\"pattern\"]}",
                input -> {
            Map<String, Object> args = object(input);
            List<GrepTool.Match> matches = grepTool.grep(requiredString(args, "pattern"), List.of("**/*"));
            String output = matches.stream()
                    .map(match -> match.path() + ":" + match.line() + ":" + match.text())
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("No matches found");
            return new AgentTool.AgentToolResult(List.of(new Content.Text(output)), Map.of("count", matches.size()), false, false);
        });
    }

    public static AgentTool find(Path cwd) {
        FindTool findTool = new FindTool(cwd);
        return simpleWithSchema("find", "Find files by glob pattern",
                "{\"type\":\"object\",\"properties\":{\"pattern\":{\"type\":\"string\",\"description\":\"Glob pattern\"},\"path\":{\"type\":\"string\"}},\"required\":[\"pattern\"]}",
                input -> {
            Map<String, Object> args = object(input);
            FindTool.Result result = findTool.find(requiredString(args, "pattern"), string(args, "path", "."),
                    number(args, "limit", FindTool.DEFAULT_LIMIT));
            return new AgentTool.AgentToolResult(List.of(new Content.Text(result.output())), result, result.error(), false);
        });
    }

    public static AgentTool edit(Path cwd) {
        return simpleWithSchema("edit", "Edit file contents by exact replacement",
                "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\",\"description\":\"File path to edit\"},\"edits\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{\"oldText\":{\"type\":\"string\",\"description\":\"Exact text to replace\"},\"newText\":{\"type\":\"string\",\"description\":\"Replacement text\"}},\"required\":[\"oldText\",\"newText\"]},\"description\":\"One or more targeted replacements matched against the original file\"},\"oldText\":{\"type\":\"string\",\"description\":\"Legacy exact text to replace\"},\"newText\":{\"type\":\"string\",\"description\":\"Legacy replacement text\"}},\"required\":[\"path\"]}",
                input -> {
            Map<String, Object> args = object(input);
            Path path = PathUtils.resolveInside(cwd, requiredString(args, "path"));
            return FileMutationQueue.withFileMutationQueue(path, () -> {
                String oldContent = Files.readString(path);
                List<EditDiff.Edit> edits = editList(args);
                EditDiff.Applied applied = EditDiff.apply(oldContent, edits);
                Files.writeString(path, applied.content());
                String diff = EditDiff.unifiedPatch(path.toString(), oldContent, applied.content(), 3);
                String noun = applied.replacements() == 1 ? "block" : "blocks";
                return new AgentTool.AgentToolResult(List.of(new Content.Text("Successfully replaced "
                        + applied.replacements() + " " + noun + " in " + path)),
                        Map.of("path", path.toString(), "replacements", applied.replacements(), "diff", diff,
                                "patch", diff), false, false);
            });
        });
    }

    public static AgentTool bash(Path cwd) {
        return bash(cwd, null);
    }

    public static AgentTool bash(Path cwd, BashConfig bashConfig) {
        return simpleWithSchema("bash", "Execute a bash command",
                "{\"type\":\"object\",\"properties\":{\"command\":{\"type\":\"string\",\"description\":\"Bash command to execute\"},\"timeout\":{\"type\":\"integer\"}},\"required\":[\"command\"]}",
                input -> {
            Map<String, Object> args = object(input);
            Duration timeout = args.containsKey("timeout") ? Duration.ofSeconds(number(args, "timeout", 0)) : null;
            String command = requiredString(args, "command");
            String resolvedCommand = withCommandPrefix(command, bashConfig == null ? null : bashConfig.commandPrefix());
            BashExecutor.Result result = BashExecutor.execute(resolvedCommand, cwd,
                    new LocalBashOperations(bashConfig == null ? null : bashConfig.shellPath()),
                    new BashExecutor.Options(null, timeout));
            boolean error = result.exitCode() != null && result.exitCode() != 0;
            return new AgentTool.AgentToolResult(List.of(new Content.Text(result.output())),
                    Map.of("exitCode", result.exitCode(), "truncated", result.truncated()), error, false);
        });
    }

    private static String withCommandPrefix(String command, String prefix) {
        return prefix == null || prefix.isBlank() ? command : prefix + "\n" + command;
    }

    private static AgentTool simple(String name, String description, ToolExecutor executor) {
        return simpleWithSchema(name, description, "{\"type\":\"object\",\"additionalProperties\":true}", executor);
    }

    private static AgentTool simpleWithSchema(String name, String description, String jsonSchema, ToolExecutor executor) {
        return new AgentTool() {
            @Override
            public Tool definition() {
                JsonNode schema = JsonCodec.parse(jsonSchema);
                return new Tool(name, description, schema, null);
            }

            @Override
            public AgentToolResult execute(Object input) throws Exception {
                return executor.execute(input);
            }
        };
    }

    @FunctionalInterface
    private interface ToolExecutor {
        AgentTool.AgentToolResult execute(Object input) throws Exception;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object input) {
        if (input instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private static String requiredString(Map<String, Object> args, String name) {
        Object value = args.get(name);
        if (value == null) {
            throw new IllegalArgumentException("Missing required argument: " + name);
        }
        return value.toString();
    }

    private static String string(Map<String, Object> args, String name, String fallback) {
        Object value = args.get(name);
        return value == null ? fallback : value.toString();
    }

    private static int number(Map<String, Object> args, String name, int fallback) {
        Object value = args.get(name);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            return Integer.parseInt(value.toString());
        }
        return fallback;
    }

    private static boolean booleanValue(Map<String, Object> args, String name, boolean fallback) {
        Object value = args.get(name);
        return value == null ? fallback : Boolean.parseBoolean(value.toString());
    }

    @SuppressWarnings("unchecked")
    private static List<EditDiff.Edit> editList(Map<String, Object> args) {
        List<EditDiff.Edit> edits = new java.util.ArrayList<>();
        Object rawEdits = args.get("edits");
        if (rawEdits instanceof String text && !text.isBlank()) {
            rawEdits = JsonCodec.parse(text);
        }
        if (rawEdits instanceof JsonNode node && node.isArray()) {
            for (JsonNode item : node) {
                edits.add(new EditDiff.Edit(item.path("oldText").asText(), item.path("newText").asText()));
            }
        } else if (rawEdits instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    edits.add(new EditDiff.Edit(requiredString((Map<String, Object>) map, "oldText"),
                            requiredString((Map<String, Object>) map, "newText")));
                }
            }
        }
        if (args.containsKey("oldText") || args.containsKey("newText")) {
            edits.add(new EditDiff.Edit(requiredString(args, "oldText"), requiredString(args, "newText")));
        }
        if (edits.isEmpty()) {
            throw new IllegalArgumentException("Edit tool input is invalid. edits must contain at least one replacement.");
        }
        return List.copyOf(edits);
    }
}
