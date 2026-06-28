package works.earendil.pi.codingagent.resources;

import works.earendil.pi.codingagent.util.Frontmatter;
import works.earendil.pi.common.glob.IgnoreRules;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SkillLoader {
    private static final int MAX_NAME_LENGTH = 64;
    private static final int MAX_DESCRIPTION_LENGTH = 1024;
    private static final List<String> IGNORE_FILE_NAMES = List.of(".gitignore", ".ignore", ".fdignore");

    private SkillLoader() {
    }

    public record LoadSkillsFromDirOptions(Path dir, String source) {
    }

    public record LoadSkillsOptions(Path cwd, Path agentDir, List<Path> skillPaths, boolean includeDefaults) {
    }

    public record LoadSkillsResult(List<Skill> skills, List<ResourceDiagnostic> diagnostics) {
    }

    public static LoadSkillsResult loadSkillsFromDir(LoadSkillsFromDirOptions options) {
        return loadSkillsFromDirInternal(options.dir(), options.source(), true, List.of(), options.dir());
    }

    public static LoadSkillsResult loadSkills(LoadSkillsOptions options) {
        Map<String, Skill> skillsByName = new LinkedHashMap<>();
        Set<Path> realPaths = new LinkedHashSet<>();
        List<ResourceDiagnostic> diagnostics = new ArrayList<>();

        java.util.function.Consumer<LoadSkillsResult> add = result -> {
            diagnostics.addAll(result.diagnostics());
            for (Skill skill : result.skills()) {
                Path realPath = canonical(skill.filePath());
                if (realPaths.contains(realPath)) {
                    continue;
                }
                Skill existing = skillsByName.get(skill.name());
                if (existing != null) {
                    diagnostics.add(new ResourceDiagnostic.Collision("skill", skill.name(), existing.filePath(), skill.filePath()));
                    continue;
                }
                skillsByName.put(skill.name(), skill);
                realPaths.add(realPath);
            }
        };

        Path userSkills = options.agentDir().resolve("skills");
        Path projectSkills = options.cwd().resolve(".pi").resolve("skills");
        if (options.includeDefaults()) {
            add.accept(loadSkillsFromDirInternal(userSkills, "user", true, List.of(), userSkills));
            add.accept(loadSkillsFromDirInternal(projectSkills, "project", true, List.of(), projectSkills));
        }
        for (Path raw : options.skillPaths()) {
            Path path = options.cwd().resolve(raw).normalize().toAbsolutePath();
            if (!Files.exists(path)) {
                diagnostics.add(new ResourceDiagnostic.Warning("skill path does not exist", path));
                continue;
            }
            String source = !options.includeDefaults() && isUnder(path, userSkills) ? "user"
                    : !options.includeDefaults() && isUnder(path, projectSkills) ? "project"
                    : "path";
            if (Files.isDirectory(path)) {
                add.accept(loadSkillsFromDirInternal(path, source, true, List.of(), path));
            } else if (path.getFileName().toString().endsWith(".md")) {
                add.accept(loadSkillFromFile(path, source));
            }
        }
        return new LoadSkillsResult(List.copyOf(skillsByName.values()), List.copyOf(diagnostics));
    }

    public static String formatSkillsForPrompt(List<Skill> skills) {
        List<Skill> visible = skills.stream().filter(skill -> !skill.disableModelInvocation()).toList();
        if (visible.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        out.append("\n\nThe following skills provide specialized instructions for specific tasks.\n");
        out.append("Use the read tool to load a skill's file when the task matches its description.\n");
        out.append("When a skill file references a relative path, resolve it against the skill directory (parent of SKILL.md / dirname of the path) and use that absolute path in tool commands.\n\n");
        out.append("<available_skills>");
        for (Skill skill : visible) {
            out.append("\n  <skill>");
            out.append("\n    <name>").append(escapeXml(skill.name())).append("</name>");
            out.append("\n    <description>").append(escapeXml(skill.description())).append("</description>");
            out.append("\n    <location>").append(escapeXml(skill.filePath().toString())).append("</location>");
            out.append("\n  </skill>");
        }
        out.append("\n</available_skills>");
        return out.toString();
    }

    private static LoadSkillsResult loadSkillsFromDirInternal(Path dir, String source, boolean includeRootFiles,
                                                              List<IgnoreRules> inheritedIgnoreRules, Path root) {
        List<Skill> skills = new ArrayList<>();
        List<ResourceDiagnostic> diagnostics = new ArrayList<>();
        if (!Files.exists(dir)) {
            return new LoadSkillsResult(skills, diagnostics);
        }
        List<IgnoreRules> ignoreRules = new ArrayList<>(inheritedIgnoreRules);
        readIgnoreRules(dir).ifPresent(ignoreRules::add);
        try (var entriesStream = Files.list(dir)) {
            List<Path> entries = entriesStream.sorted(Comparator.comparing(Path::toString)).toList();
            for (Path entry : entries) {
                if (entry.getFileName().toString().equals("SKILL.md") && Files.isRegularFile(entry) && !ignored(root, entry, ignoreRules)) {
                    return loadSkillFromFile(entry, source);
                }
            }
            for (Path entry : entries) {
                String name = entry.getFileName().toString();
                if (name.startsWith(".") || name.equals("node_modules") || ignored(root, entry, ignoreRules)) {
                    continue;
                }
                if (Files.isDirectory(entry)) {
                    LoadSkillsResult result = loadSkillsFromDirInternal(entry, source, false, ignoreRules, root);
                    skills.addAll(result.skills());
                    diagnostics.addAll(result.diagnostics());
                } else if (includeRootFiles && Files.isRegularFile(entry) && name.endsWith(".md")) {
                    LoadSkillsResult result = loadSkillFromFile(entry, source);
                    skills.addAll(result.skills());
                    diagnostics.addAll(result.diagnostics());
                }
            }
        } catch (IOException ignored) {
        }
        return new LoadSkillsResult(List.copyOf(skills), List.copyOf(diagnostics));
    }

    private static LoadSkillsResult loadSkillFromFile(Path filePath, String source) {
        List<ResourceDiagnostic> diagnostics = new ArrayList<>();
        try {
            String raw = Files.readString(filePath, StandardCharsets.UTF_8);
            Frontmatter.Parsed parsed = Frontmatter.parse(raw);
            Path skillDir = filePath.getParent();
            String fallbackName = skillDir == null ? stripMd(filePath.getFileName().toString()) : skillDir.getFileName().toString();
            String name = stringValue(parsed.frontmatter().get("name"), fallbackName);
            String description = stringValue(parsed.frontmatter().get("description"), "");
            validateDescription(description).forEach(error -> diagnostics.add(new ResourceDiagnostic.Warning(error, filePath)));
            validateName(name).forEach(error -> diagnostics.add(new ResourceDiagnostic.Warning(error, filePath)));
            if (description.trim().isEmpty()) {
                return new LoadSkillsResult(List.of(), diagnostics);
            }
            boolean disable = Boolean.TRUE.equals(parsed.frontmatter().get("disable-model-invocation"));
            Skill skill = new Skill(name, description, filePath.toAbsolutePath().normalize(), skillDir.toAbsolutePath().normalize(),
                    createSkillSourceInfo(filePath.toAbsolutePath().normalize(), skillDir.toAbsolutePath().normalize(), source), disable);
            return new LoadSkillsResult(List.of(skill), diagnostics);
        } catch (Exception e) {
            diagnostics.add(new ResourceDiagnostic.Warning(e.getMessage(), filePath));
            return new LoadSkillsResult(List.of(), diagnostics);
        }
    }

    private static List<String> validateName(String name) {
        List<String> errors = new ArrayList<>();
        if (name.length() > MAX_NAME_LENGTH) {
            errors.add("name exceeds " + MAX_NAME_LENGTH + " characters (" + name.length() + ")");
        }
        if (!name.matches("^[a-z0-9-]+$")) {
            errors.add("name contains invalid characters (must be lowercase a-z, 0-9, hyphens only)");
        }
        if (name.startsWith("-") || name.endsWith("-")) {
            errors.add("name must not start or end with a hyphen");
        }
        if (name.contains("--")) {
            errors.add("name must not contain consecutive hyphens");
        }
        return errors;
    }

    private static List<String> validateDescription(String description) {
        if (description == null || description.trim().isEmpty()) {
            return List.of("description is required");
        }
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            return List.of("description exceeds " + MAX_DESCRIPTION_LENGTH + " characters (" + description.length() + ")");
        }
        return List.of();
    }

    private static java.util.Optional<IgnoreRules> readIgnoreRules(Path dir) {
        StringBuilder content = new StringBuilder();
        for (String file : IGNORE_FILE_NAMES) {
            Path ignore = dir.resolve(file);
            if (Files.isRegularFile(ignore)) {
                try {
                    content.append(Files.readString(ignore, StandardCharsets.UTF_8)).append('\n');
                } catch (IOException ignored) {
                }
            }
        }
        return content.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(IgnoreRules.parse(content.toString()));
    }

    private static boolean ignored(Path root, Path path, List<IgnoreRules> rules) {
        Path rel = root.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize());
        return rules.stream().anyMatch(rule -> rule.ignores(rel));
    }

    private static SourceInfo createSkillSourceInfo(Path filePath, Path baseDir, String source) {
        return switch (source) {
            case "user" -> SourceInfo.local(filePath, "user", baseDir);
            case "project" -> SourceInfo.local(filePath, "project", baseDir);
            case "path" -> SourceInfo.local(filePath, null, baseDir);
            default -> SourceInfo.synthetic(filePath, source, baseDir);
        };
    }

    private static boolean isUnder(Path target, Path root) {
        Path normalizedTarget = target.toAbsolutePath().normalize();
        Path normalizedRoot = root.toAbsolutePath().normalize();
        return normalizedTarget.equals(normalizedRoot) || normalizedTarget.startsWith(normalizedRoot);
    }

    private static Path canonical(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            return path.toAbsolutePath().normalize();
        }
    }

    private static String stringValue(Object value, String fallback) {
        return value instanceof String s && !s.isBlank() ? s : fallback;
    }

    private static String stripMd(String name) {
        return name.endsWith(".md") ? name.substring(0, name.length() - 3) : name;
    }

    private static String escapeXml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
