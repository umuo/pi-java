package works.earendil.pi.codingagent.resources;

import works.earendil.pi.codingagent.util.Frontmatter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PromptTemplateLoader {
    private static final Pattern SUBSTITUTION = Pattern.compile("\\$\\{(\\d+):-([^}]*)}|\\$\\{@:(\\d+)(?::(\\d+))?}|\\$(ARGUMENTS|@|\\d+)");
    private static final Pattern COMMAND = Pattern.compile("^/([^\\s]+)(?:\\s+([\\s\\S]*))?$");

    private PromptTemplateLoader() {
    }

    public record LoadPromptTemplatesOptions(Path cwd, Path agentDir, List<Path> promptPaths, boolean includeDefaults) {
    }

    public static List<String> parseCommandArgs(String argsString) {
        List<String> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        Character quote = null;
        for (int i = 0; i < argsString.length(); i++) {
            char c = argsString.charAt(i);
            if (quote != null) {
                if (c == quote) {
                    quote = null;
                } else {
                    current.append(c);
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (Character.isWhitespace(c)) {
                if (!current.isEmpty()) {
                    args.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            args.add(current.toString());
        }
        return List.copyOf(args);
    }

    public static String substituteArgs(String content, List<String> args) {
        String allArgs = String.join(" ", args);
        Matcher matcher = SUBSTITUTION.matcher(content);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String replacement;
            if (matcher.group(1) != null) {
                int index = Integer.parseInt(matcher.group(1)) - 1;
                String value = index >= 0 && index < args.size() ? args.get(index) : "";
                replacement = value.isEmpty() ? matcher.group(2) : value;
            } else if (matcher.group(3) != null) {
                int start = Math.max(0, Integer.parseInt(matcher.group(3)) - 1);
                if (matcher.group(4) != null) {
                    int length = Integer.parseInt(matcher.group(4));
                    replacement = String.join(" ", args.subList(Math.min(start, args.size()), Math.min(start + length, args.size())));
                } else {
                    replacement = String.join(" ", args.subList(Math.min(start, args.size()), args.size()));
                }
            } else {
                String simple = matcher.group(5);
                if ("ARGUMENTS".equals(simple) || "@".equals(simple)) {
                    replacement = allArgs;
                } else {
                    int index = Integer.parseInt(simple) - 1;
                    replacement = index >= 0 && index < args.size() ? args.get(index) : "";
                }
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    public static List<PromptTemplate> loadPromptTemplates(LoadPromptTemplatesOptions options) {
        List<PromptTemplate> templates = new ArrayList<>();
        Path global = options.agentDir().resolve("prompts");
        Path project = options.cwd().resolve(".pi").resolve("prompts");
        if (options.includeDefaults()) {
            templates.addAll(loadTemplatesFromDir(global, path -> sourceInfo(path, global, project)));
            templates.addAll(loadTemplatesFromDir(project, path -> sourceInfo(path, global, project)));
        }
        for (Path raw : options.promptPaths()) {
            Path path = options.cwd().resolve(raw).normalize().toAbsolutePath();
            if (!Files.exists(path)) {
                continue;
            }
            if (Files.isDirectory(path)) {
                templates.addAll(loadTemplatesFromDir(path, p -> sourceInfo(p, global, project)));
            } else if (Files.isRegularFile(path) && path.getFileName().toString().endsWith(".md")) {
                loadTemplateFromFile(path, sourceInfo(path, global, project)).ifPresent(templates::add);
            }
        }
        return List.copyOf(templates);
    }

    public static String expandPromptTemplate(String text, List<PromptTemplate> templates) {
        if (!text.startsWith("/")) {
            return text;
        }
        Matcher matcher = COMMAND.matcher(text);
        if (!matcher.matches()) {
            return text;
        }
        String name = matcher.group(1);
        String argsString = matcher.group(2) == null ? "" : matcher.group(2);
        return templates.stream()
                .filter(template -> template.name().equals(name))
                .findFirst()
                .map(template -> substituteArgs(template.content(), parseCommandArgs(argsString)))
                .orElse(text);
    }

    private static List<PromptTemplate> loadTemplatesFromDir(Path dir, java.util.function.Function<Path, SourceInfo> sourceInfo) {
        if (!Files.exists(dir)) {
            return List.of();
        }
        List<PromptTemplate> templates = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            for (Path file : stream.sorted(Comparator.comparing(Path::toString)).toList()) {
                if (Files.isRegularFile(file) && file.getFileName().toString().endsWith(".md")) {
                    loadTemplateFromFile(file, sourceInfo.apply(file)).ifPresent(templates::add);
                }
            }
        } catch (IOException ignored) {
        }
        return List.copyOf(templates);
    }

    private static java.util.Optional<PromptTemplate> loadTemplateFromFile(Path file, SourceInfo sourceInfo) {
        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            Frontmatter.Parsed parsed = Frontmatter.parse(raw);
            String fileName = file.getFileName().toString();
            String name = fileName.endsWith(".md") ? fileName.substring(0, fileName.length() - 3) : fileName;
            String description = value(parsed.frontmatter().get("description"));
            if (description.isEmpty()) {
                description = parsed.body().lines()
                        .filter(line -> !line.trim().isEmpty())
                        .findFirst()
                        .map(line -> line.length() > 60 ? line.substring(0, 60) + "..." : line)
                        .orElse("");
            }
            String hint = value(parsed.frontmatter().get("argument-hint"));
            return java.util.Optional.of(new PromptTemplate(name, description, hint.isEmpty() ? null : hint,
                    parsed.body(), sourceInfo, file.toAbsolutePath().normalize()));
        } catch (IOException e) {
            return java.util.Optional.empty();
        }
    }

    private static SourceInfo sourceInfo(Path path, Path global, Path project) {
        Path resolved = path.toAbsolutePath().normalize();
        Path g = global.toAbsolutePath().normalize();
        Path p = project.toAbsolutePath().normalize();
        if (resolved.startsWith(g)) {
            return SourceInfo.local(resolved, "user", g);
        }
        if (resolved.startsWith(p)) {
            return SourceInfo.local(resolved, "project", p);
        }
        Path base = Files.isDirectory(resolved) ? resolved : resolved.getParent();
        return SourceInfo.local(resolved, null, base);
    }

    private static String value(Object value) {
        return value instanceof String s ? s : "";
    }
}
