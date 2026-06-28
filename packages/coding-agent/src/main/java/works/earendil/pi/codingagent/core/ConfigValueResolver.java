package works.earendil.pi.codingagent.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public final class ConfigValueResolver {
    private static final Pattern ENV_VAR_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern ENV_VAR_NAME_PREFIX = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Map<String, Optional<String>> COMMAND_RESULT_CACHE = new ConcurrentHashMap<>();

    private ConfigValueResolver() {
    }

    public static Optional<String> getConfigValueEnvVarName(String config) {
        ConfigValueReference reference = parseConfigValueReference(config);
        if (!(reference instanceof ConfigValueReference.Template template)) {
            return Optional.empty();
        }
        return template.parts().size() == 1 && template.parts().getFirst() instanceof TemplatePart.Env env
                ? Optional.of(env.name())
                : Optional.empty();
    }

    public static List<String> getConfigValueEnvVarNames(String config) {
        ConfigValueReference reference = parseConfigValueReference(config);
        if (!(reference instanceof ConfigValueReference.Template template)) {
            return List.of();
        }
        Set<String> names = new LinkedHashSet<>();
        for (TemplatePart part : template.parts()) {
            if (part instanceof TemplatePart.Env env) {
                names.add(env.name());
            }
        }
        return List.copyOf(names);
    }

    public static List<String> getMissingConfigValueEnvVarNames(String config, Map<String, String> env) {
        return getConfigValueEnvVarNames(config).stream()
                .filter(name -> resolveEnvConfigValue(name, env).isEmpty())
                .toList();
    }

    public static boolean isCommandConfigValue(String config) {
        return parseConfigValueReference(config) instanceof ConfigValueReference.Command;
    }

    public static boolean isConfigValueConfigured(String config, Map<String, String> env) {
        return getMissingConfigValueEnvVarNames(config, env).isEmpty();
    }

    public static Optional<String> resolveConfigValue(String config) {
        return resolveConfigValue(config, Map.of());
    }

    public static Optional<String> resolveConfigValue(String config, Map<String, String> env) {
        ConfigValueReference reference = parseConfigValueReference(config);
        if (reference instanceof ConfigValueReference.Command command) {
            return executeCommand(command.config());
        }
        return resolveTemplate(((ConfigValueReference.Template) reference).parts(), env);
    }

    public static Optional<String> resolveConfigValueUncached(String config, Map<String, String> env) {
        ConfigValueReference reference = parseConfigValueReference(config);
        if (reference instanceof ConfigValueReference.Command command) {
            return executeCommandUncached(command.config());
        }
        return resolveTemplate(((ConfigValueReference.Template) reference).parts(), env);
    }

    public static String resolveConfigValueOrThrow(String config, String description, Map<String, String> env) {
        Optional<String> resolvedValue = resolveConfigValueUncached(config, env);
        if (resolvedValue.isPresent()) {
            return resolvedValue.get();
        }
        ConfigValueReference reference = parseConfigValueReference(config);
        if (reference instanceof ConfigValueReference.Command command) {
            throw new IllegalStateException("Failed to resolve " + description
                    + " from shell command: " + command.config().substring(1));
        }
        List<String> missingEnvVars = getMissingConfigValueEnvVarNames(config, env);
        if (missingEnvVars.size() == 1) {
            throw new IllegalStateException("Failed to resolve " + description
                    + " from environment variable: " + missingEnvVars.getFirst());
        }
        if (missingEnvVars.size() > 1) {
            throw new IllegalStateException("Failed to resolve " + description
                    + " from environment variables: " + String.join(", ", missingEnvVars));
        }
        throw new IllegalStateException("Failed to resolve " + description);
    }

    public static Map<String, String> resolveHeaders(Map<String, String> headers, Map<String, String> env) {
        if (headers == null) {
            return null;
        }
        Map<String, String> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            resolveConfigValue(entry.getValue(), env).ifPresent(value -> resolved.put(entry.getKey(), value));
        }
        return resolved.isEmpty() ? null : Map.copyOf(resolved);
    }

    public static Map<String, String> resolveHeadersOrThrow(Map<String, String> headers, String description,
                                                            Map<String, String> env) {
        if (headers == null) {
            return null;
        }
        Map<String, String> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            resolved.put(entry.getKey(), resolveConfigValueOrThrow(entry.getValue(),
                    description + " header \"" + entry.getKey() + "\"", env));
        }
        return resolved.isEmpty() ? null : Map.copyOf(resolved);
    }

    public static void clearConfigValueCache() {
        COMMAND_RESULT_CACHE.clear();
    }

    private static ConfigValueReference parseConfigValueReference(String config) {
        if (config.startsWith("!")) {
            return new ConfigValueReference.Command(config);
        }
        return new ConfigValueReference.Template(parseConfigValueTemplate(config));
    }

    private static List<TemplatePart> parseConfigValueTemplate(String config) {
        List<TemplatePart> parts = new ArrayList<>();
        int index = 0;
        while (index < config.length()) {
            int dollarIndex = config.indexOf('$', index);
            if (dollarIndex < 0) {
                appendLiteral(parts, config.substring(index));
                break;
            }
            appendLiteral(parts, config.substring(index, dollarIndex));
            char nextChar = dollarIndex + 1 < config.length() ? config.charAt(dollarIndex + 1) : 0;
            if (nextChar == '$' || nextChar == '!') {
                appendLiteral(parts, String.valueOf(nextChar));
                index = dollarIndex + 2;
                continue;
            }
            if (nextChar == '{') {
                int endIndex = config.indexOf('}', dollarIndex + 2);
                if (endIndex < 0) {
                    appendLiteral(parts, "$");
                    index = dollarIndex + 1;
                    continue;
                }
                String name = config.substring(dollarIndex + 2, endIndex);
                if (ENV_VAR_NAME.matcher(name).matches()) {
                    parts.add(new TemplatePart.Env(name));
                } else {
                    appendLiteral(parts, config.substring(dollarIndex, endIndex + 1));
                }
                index = endIndex + 1;
                continue;
            }
            String suffix = config.substring(dollarIndex + 1);
            java.util.regex.Matcher matcher = ENV_VAR_NAME_PREFIX.matcher(suffix);
            if (matcher.lookingAt()) {
                String name = matcher.group();
                parts.add(new TemplatePart.Env(name));
                index = dollarIndex + 1 + name.length();
                continue;
            }
            appendLiteral(parts, "$");
            index = dollarIndex + 1;
        }
        return List.copyOf(parts);
    }

    private static void appendLiteral(List<TemplatePart> parts, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        if (!parts.isEmpty() && parts.getLast() instanceof TemplatePart.Literal literal) {
            parts.set(parts.size() - 1, new TemplatePart.Literal(literal.value() + value));
        } else {
            parts.add(new TemplatePart.Literal(value));
        }
    }

    private static Optional<String> resolveEnvConfigValue(String name, Map<String, String> env) {
        if (env != null && env.containsKey(name) && env.get(name) != null && !env.get(name).isEmpty()) {
            return Optional.of(env.get(name));
        }
        String systemValue = System.getenv(name);
        return systemValue == null || systemValue.isEmpty() ? Optional.empty() : Optional.of(systemValue);
    }

    private static Optional<String> resolveTemplate(List<TemplatePart> parts, Map<String, String> env) {
        StringBuilder resolved = new StringBuilder();
        for (TemplatePart part : parts) {
            if (part instanceof TemplatePart.Literal literal) {
                resolved.append(literal.value());
            } else if (part instanceof TemplatePart.Env envPart) {
                Optional<String> envValue = resolveEnvConfigValue(envPart.name(), env);
                if (envValue.isEmpty()) {
                    return Optional.empty();
                }
                resolved.append(envValue.get());
            }
        }
        return Optional.of(resolved.toString());
    }

    private static Optional<String> executeCommand(String commandConfig) {
        return COMMAND_RESULT_CACHE.computeIfAbsent(commandConfig, ConfigValueResolver::executeCommandUncached);
    }

    private static Optional<String> executeCommandUncached(String commandConfig) {
        String command = commandConfig.substring(1);
        String os = System.getProperty("os.name").toLowerCase();
        List<String> argv = os.contains("win")
                ? List.of("cmd.exe", "/c", command)
                : List.of("sh", "-c", command);
        ProcessBuilder builder = new ProcessBuilder(argv);
        try {
            Process process = builder.start();
            if (!process.waitFor(Duration.ofSeconds(10).toMillis(), TimeUnit.MILLISECONDS)) {
                ExecCommand.killProcessTree(process);
                return Optional.empty();
            }
            if (process.exitValue() != 0) {
                return Optional.empty();
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return output.isEmpty() ? Optional.empty() : Optional.of(output);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }
    }

    private sealed interface TemplatePart permits TemplatePart.Literal, TemplatePart.Env {
        record Literal(String value) implements TemplatePart {
        }

        record Env(String name) implements TemplatePart {
        }
    }

    private sealed interface ConfigValueReference permits ConfigValueReference.Command, ConfigValueReference.Template {
        record Command(String config) implements ConfigValueReference {
        }

        record Template(List<TemplatePart> parts) implements ConfigValueReference {
        }
    }
}
