package works.earendil.pi.codingagent.resources;

import works.earendil.pi.codingagent.util.Frontmatter;
import works.earendil.pi.common.glob.IgnoreRules;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class SkillLoader {
    private static final int MAX_NAME_LENGTH = 64;
    private static final int MAX_DESCRIPTION_LENGTH = 1024;
    private static final List<String> IGNORE_FILE_NAMES = List.of(".gitignore", ".ignore", ".fdignore");

    private SkillLoader() {
    }

    public record LoadSkillsFromDirOptions(Path dir, String source) {
    }

    public record LoadSkillsOptions(Path cwd, Path agentDir, List<Path> skillPaths, boolean includeDefaults,
                                    boolean projectTrusted) {
    }

    public record LoadSkillsResult(List<Skill> skills, List<ResourceDiagnostic> diagnostics) {
    }

    public record SkillPromptContext(Path cwd, Path agentDir, LocalDate date, Map<String, String> variables) {
        public SkillPromptContext {
            variables = variables == null ? Map.of() : Map.copyOf(variables);
        }
    }

    public record SkillCommandExpansion(Skill skill, String additionalInstructions, String expandedText) {
    }

    public record SkillTriggerMatch(String skillName, Path skillPath, boolean modelVisible, List<String> reasons) {
        public SkillTriggerMatch {
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
        }
    }

    public record SkillCommandResolution(boolean command, Skill skill, String skillName, String additionalInstructions,
                                         String expandedText, String errorMessage) {
        public boolean expanded() {
            return expandedText != null;
        }

        public static SkillCommandResolution notCommand() {
            return new SkillCommandResolution(false, null, "", "", null, null);
        }
    }

    public record SkillRecommendationQuery(String query, String reasonFilter, boolean filterByReason,
                                           boolean includeManualOnly, int limit) {
    }

    public record SkillRecommendationItem(String skillName, String description, boolean modelVisible,
                                          int score, List<String> matchedReasons, List<String> matchedKeywords) {
        public SkillRecommendationItem {
            matchedReasons = matchedReasons == null ? List.of() : List.copyOf(matchedReasons);
            matchedKeywords = matchedKeywords == null ? List.of() : List.copyOf(matchedKeywords);
        }
    }

    public record SkillRecommendationResult(List<SkillRecommendationItem> items, int totalMatched,
                                            String query, boolean filteredByReason) {
        public SkillRecommendationResult {
            items = items == null ? List.of() : List.copyOf(items);
        }
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
            if (options.projectTrusted()) {
                add.accept(loadSkillsFromDirInternal(projectSkills, "project", true, List.of(), projectSkills));
                for (Path projectAgentsSkills : projectAgentsSkillDirs(options.cwd())) {
                    add.accept(loadSkillsFromDirInternal(projectAgentsSkills, "project-agents", false, List.of(),
                            projectAgentsSkills));
                }
            }
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
        return formatSkillsForPrompt(skills, null);
    }

    public static String formatSkillsForPrompt(List<Skill> skills, SkillPromptContext context) {
        List<Skill> visible = skills.stream().filter(skill -> !skill.disableModelInvocation()).toList();
        if (visible.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        out.append("\n\nThe following skills provide specialized instructions for specific tasks.\n");
        out.append("Use the read tool to load a skill's file when the task matches its description.\n");
        out.append("When trigger hints are present, use them as additional matching guidance and still prefer the user's explicit request.\n");
        out.append("When a skill file references a relative path, resolve it against the skill directory (parent of SKILL.md / dirname of the path) and use that absolute path in tool commands.\n\n");
        out.append("<available_skills>");
        for (Skill skill : visible) {
            out.append("\n  <skill>");
            out.append("\n    <name>").append(escapeXml(skill.name())).append("</name>");
            out.append("\n    <description>").append(escapeXml(renderVariables(skill.description(), skill, context)))
                    .append("</description>");
            if (skill.hasTriggerHints()) {
                out.append("\n    <activation>");
                appendActivationList(out, "trigger_terms", skill.triggerTerms());
                appendActivationList(out, "trigger_patterns", skill.triggerPatterns());
                appendActivationList(out, "trigger_globs", skill.triggerGlobs());
                out.append("\n    </activation>");
            }
            out.append("\n    <location>").append(escapeXml(skill.filePath().toString())).append("</location>");
            out.append("\n  </skill>");
        }
        out.append("\n</available_skills>");
        return out.toString();
    }

    public static java.util.Optional<SkillCommandExpansion> expandSkillCommand(String text, List<Skill> skills) {
        SkillCommandResolution resolution = resolveSkillCommand(text, skills);
        if (!resolution.expanded()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new SkillCommandExpansion(resolution.skill(), resolution.additionalInstructions(),
                resolution.expandedText()));
    }

    public static List<SkillTriggerMatch> matchTriggerHints(String text, List<Skill> skills) {
        if (text == null || text.isBlank() || skills == null || skills.isEmpty()) {
            return List.of();
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        List<String> pathTokens = pathTokens(text);
        List<SkillTriggerMatch> matches = new ArrayList<>();
        for (Skill skill : skills) {
            if (!skill.hasTriggerHints()) {
                continue;
            }
            List<String> reasons = new ArrayList<>();
            for (String term : skill.triggerTerms()) {
                if (normalized.contains(term.toLowerCase(Locale.ROOT))) {
                    reasons.add("term:" + term);
                }
            }
            for (String pattern : skill.triggerPatterns()) {
                if (patternMatches(pattern, text)) {
                    reasons.add("pattern:" + pattern);
                }
            }
            for (String glob : skill.triggerGlobs()) {
                if (globMatches(glob, pathTokens)) {
                    reasons.add("glob:" + glob);
                }
            }
            if (!reasons.isEmpty()) {
                matches.add(new SkillTriggerMatch(skill.name(), skill.filePath(), !skill.disableModelInvocation(),
                        reasons));
            }
        }
        return List.copyOf(matches);
    }

    public static SkillRecommendationResult recommendSkills(List<Skill> skills, SkillRecommendationQuery query) {
        if (skills == null || skills.isEmpty()) {
            return new SkillRecommendationResult(List.of(), 0, query == null ? "" : query.query(), query != null && query.filterByReason());
        }
        String q = query == null || query.query() == null ? "" : query.query().trim();
        String reasonFilter = query == null || query.reasonFilter() == null ? "" : query.reasonFilter().trim();
        boolean filterByReason = query != null && query.filterByReason();
        boolean includeManualOnly = query == null || query.includeManualOnly();
        int limit = query == null || query.limit() <= 0 ? 20 : query.limit();

        String[] queryWords = q.toLowerCase(Locale.ROOT).split("\\s+");
        List<SkillRecommendationItem> matchedItems = new ArrayList<>();

        for (Skill skill : skills) {
            boolean modelVisible = !skill.disableModelInvocation();
            if (!modelVisible && !includeManualOnly) {
                continue;
            }

            int score = 0;
            List<String> matchedKeywords = new ArrayList<>();
            List<String> matchedReasons = new ArrayList<>();

            if (!q.isBlank()) {
                String lowerName = skill.name().toLowerCase(Locale.ROOT);
                String lowerQuery = q.toLowerCase(Locale.ROOT);
                if (lowerName.equals(lowerQuery)) {
                    score += 100;
                    matchedKeywords.add(skill.name());
                } else if (lowerName.contains(lowerQuery) || lowerQuery.contains(lowerName)) {
                    score += 50;
                    matchedKeywords.add(skill.name());
                }

                String lowerDesc = skill.description() == null ? "" : skill.description().toLowerCase(Locale.ROOT);
                for (String word : queryWords) {
                    if (!word.isBlank() && lowerDesc.contains(word)) {
                        score += 15;
                        if (!matchedKeywords.contains(word)) {
                            matchedKeywords.add(word);
                        }
                    }
                }

                List<SkillTriggerMatch> triggerMatches = matchTriggerHints(q, List.of(skill));
                if (!triggerMatches.isEmpty()) {
                    for (SkillTriggerMatch tm : triggerMatches) {
                        matchedReasons.addAll(tm.reasons());
                        score += tm.reasons().size() * 30;
                    }
                }
            }

            if (!reasonFilter.isBlank()) {
                String lowerRF = reasonFilter.toLowerCase(Locale.ROOT);
                List<String> rfMatched = new ArrayList<>();
                List<String> allHints = new ArrayList<>();
                allHints.addAll(skill.triggerTerms());
                allHints.addAll(skill.triggerPatterns());
                allHints.addAll(skill.triggerGlobs());
                for (String hint : allHints) {
                    if (hint.toLowerCase(Locale.ROOT).contains(lowerRF)) {
                        rfMatched.add("hint:" + hint);
                    }
                }
                for (String mr : matchedReasons) {
                    if (mr.toLowerCase(Locale.ROOT).contains(lowerRF) && !rfMatched.contains(mr)) {
                        rfMatched.add(mr);
                    }
                }
                if (rfMatched.isEmpty()) {
                    continue;
                }
                for (String rm : rfMatched) {
                    if (!matchedReasons.contains(rm)) {
                        matchedReasons.add(rm);
                    }
                }
                score += rfMatched.size() * 25;
            } else if (filterByReason && matchedReasons.isEmpty()) {
                continue;
            }

            if (q.isBlank() && reasonFilter.isBlank() && !filterByReason) {
                score = 10;
            } else if (score == 0 && !q.isBlank()) {
                continue;
            }

            matchedItems.add(new SkillRecommendationItem(
                    skill.name(),
                    skill.description() == null ? "" : skill.description(),
                    modelVisible,
                    score,
                    matchedReasons,
                    matchedKeywords
            ));
        }

        matchedItems.sort(Comparator.comparingInt(SkillRecommendationItem::score).reversed()
                .thenComparing(SkillRecommendationItem::skillName));

        int total = matchedItems.size();
        List<SkillRecommendationItem> limited = matchedItems.stream().limit(limit).toList();
        return new SkillRecommendationResult(limited, total, q, filterByReason || !reasonFilter.isBlank());
    }

    public static SkillCommandResolution resolveSkillCommand(String text, List<Skill> skills) {
        if (text == null || skills == null || skills.isEmpty()) {
            if (text != null && text.stripLeading().startsWith("/skill:")) {
                String candidate = text.stripLeading();
                int spaceIndex = candidate.indexOf(' ');
                String skillName = spaceIndex == -1 ? candidate.substring(7) : candidate.substring(7, spaceIndex);
                return new SkillCommandResolution(true, null, skillName, "", null, "Unknown skill: " + skillName);
            }
            return SkillCommandResolution.notCommand();
        }
        String candidate = text.stripLeading();
        if (!candidate.startsWith("/skill:")) {
            return SkillCommandResolution.notCommand();
        }
        int spaceIndex = candidate.indexOf(' ');
        String skillName = spaceIndex == -1 ? candidate.substring(7) : candidate.substring(7, spaceIndex);
        if (skillName.isBlank()) {
            return new SkillCommandResolution(true, null, skillName, "", null, "Skill name is required");
        }
        Skill skill = skills.stream().filter(s -> s.name().equals(skillName)).findFirst().orElse(null);
        if (skill == null) {
            return new SkillCommandResolution(true, null, skillName, "", null, "Unknown skill: " + skillName);
        }
        String additionalInstructions = spaceIndex == -1 ? "" : candidate.substring(spaceIndex + 1).trim();
        try {
            String raw = Files.readString(skill.filePath(), StandardCharsets.UTF_8);
            String body = Frontmatter.parse(raw).body().trim();
            StringBuilder expanded = new StringBuilder();
            expanded.append("<skill name=\"").append(escapeXml(skill.name())).append("\" location=\"")
                    .append(escapeXml(skill.filePath().toString())).append("\">\n");
            expanded.append("References are relative to ").append(skill.baseDir()).append(".\n\n");
            expanded.append(body).append("\n</skill>");
            if (!additionalInstructions.isBlank()) {
                expanded.append("\n\n").append(additionalInstructions);
            }
            return new SkillCommandResolution(true, skill, skillName, additionalInstructions, expanded.toString(), null);
        } catch (IOException e) {
            return new SkillCommandResolution(true, skill, skillName, additionalInstructions, null,
                    "Failed to read skill '" + skillName + "': " + e.getMessage());
        }
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
            Invocation invocation = resolveInvocation(parsed.frontmatter(), filePath, diagnostics);
            Skill skill = new Skill(name, description, filePath.toAbsolutePath().normalize(), skillDir.toAbsolutePath().normalize(),
                    createSkillSourceInfo(filePath.toAbsolutePath().normalize(), skillDir.toAbsolutePath().normalize(), source),
                    invocation.disableModelInvocation(), invocation.modelInvocation(),
                    stringList(parsed.frontmatter().get("trigger-terms")),
                    stringList(parsed.frontmatter().get("trigger-patterns")),
                    stringList(parsed.frontmatter().get("trigger-globs")));
            return new LoadSkillsResult(List.of(skill), diagnostics);
        } catch (Exception e) {
            diagnostics.add(new ResourceDiagnostic.Warning(e.getMessage(), filePath));
            return new LoadSkillsResult(List.of(), diagnostics);
        }
    }

    private record Invocation(boolean disableModelInvocation, String modelInvocation) {
    }

    private static Invocation resolveInvocation(Map<String, Object> frontmatter, Path filePath,
                                                List<ResourceDiagnostic> diagnostics) {
        if (Boolean.TRUE.equals(frontmatter.get("disable-model-invocation"))) {
            return new Invocation(true, "manual");
        }
        Object value = frontmatter.get("model-invocation");
        if (value == null) {
            return new Invocation(false, "auto");
        }
        String normalized = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "auto", "automatic", "model", "enabled", "true" -> new Invocation(false, "auto");
            case "manual", "explicit", "disabled", "false", "never" -> new Invocation(true, "manual");
            default -> {
                diagnostics.add(new ResourceDiagnostic.Warning(
                        "model-invocation must be auto or manual: " + value, filePath));
                yield new Invocation(false, "auto");
            }
        };
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
            case "project-agents" -> SourceInfo.local(filePath, "project-agents", baseDir);
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

    private static List<String> stringList(Object value) {
        if (value == null) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                addStringValue(values, item);
            }
        } else {
            addStringValue(values, value);
        }
        return List.copyOf(values);
    }

    private static void addStringValue(List<String> values, Object value) {
        String text = value == null ? "" : String.valueOf(value).trim();
        if (!text.isBlank()) {
            values.add(text);
        }
    }

    private static boolean patternMatches(String pattern, String text) {
        try {
            return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(text).find();
        } catch (PatternSyntaxException ignored) {
            return false;
        }
    }

    private static boolean globMatches(String glob, List<String> pathTokens) {
        if (pathTokens.isEmpty()) {
            return false;
        }
        java.nio.file.PathMatcher matcher;
        try {
            matcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
        for (String token : pathTokens) {
            try {
                if (matcher.matches(Path.of(token))) {
                    return true;
                }
            } catch (RuntimeException ignored) {
                // Ignore malformed path-like tokens from user text.
            }
        }
        return false;
    }

    private static List<String> pathTokens(String text) {
        List<String> tokens = new ArrayList<>();
        for (String raw : text.split("\\s+")) {
            String token = raw.strip()
                    .replaceAll("^[`'\"(<\\[]+", "")
                    .replaceAll("[`'\"),.;:>\\]]+$", "");
            if (token.contains("/") || token.contains("\\") || token.contains(".")) {
                tokens.add(token.replace('\\', '/'));
            }
        }
        return List.copyOf(tokens);
    }

    private static String stripMd(String name) {
        return name.endsWith(".md") ? name.substring(0, name.length() - 3) : name;
    }

    private static List<Path> projectAgentsSkillDirs(Path cwd) {
        List<Path> dirs = new ArrayList<>();
        Path current = cwd.toAbsolutePath().normalize();
        while (current != null) {
            dirs.add(current.resolve(".agents").resolve("skills"));
            if (Files.exists(current.resolve(".git"))) {
                break;
            }
            current = current.getParent();
        }
        return dirs;
    }

    private static String renderVariables(String value, Skill skill, SkillPromptContext context) {
        if (value == null || value.isEmpty() || context == null) {
            return value;
        }
        Map<String, String> variables = new LinkedHashMap<>();
        putPath(variables, "cwd", context.cwd());
        putPath(variables, "project_cwd", context.cwd());
        putPath(variables, "agent_dir", context.agentDir());
        if (context.date() != null) {
            variables.put("date", context.date().toString());
        }
        variables.put("skill_name", skill.name());
        putPath(variables, "skill_dir", skill.baseDir());
        putPath(variables, "skill_path", skill.filePath());
        variables.putAll(context.variables());
        String rendered = value;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", entry.getValue());
            rendered = rendered.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return rendered;
    }

    private static void putPath(Map<String, String> variables, String key, Path path) {
        if (path != null) {
            variables.put(key, path.toAbsolutePath().normalize().toString());
        }
    }

    private static void appendActivationList(StringBuilder out, String tag, List<String> values) {
        if (values.isEmpty()) {
            return;
        }
        out.append("\n      <").append(tag).append(">");
        for (String value : values) {
            out.append("\n        <item>").append(escapeXml(value)).append("</item>");
        }
        out.append("\n      </").append(tag).append(">");
    }

    private static String escapeXml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
