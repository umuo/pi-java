package works.earendil.pi.codingagent.cli;

import com.fasterxml.jackson.databind.JsonNode;
import works.earendil.pi.codingagent.config.SettingsManager;
import works.earendil.pi.codingagent.resources.ResourceLoader;
import works.earendil.pi.codingagent.resources.ThemeResource;
import works.earendil.pi.tui.style.TerminalTheme;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class TerminalThemeResolver {
    private TerminalThemeResolver() {
    }

    static TerminalTheme resolve(SettingsManager settings, ResourceLoader resourceLoader) {
        String themeName = activeThemeName(settings == null ? null : settings.getThemeSetting());
        if (themeName == null || resourceLoader == null) {
            return TerminalTheme.standard();
        }
        return resourceLoader.themes().themes().stream()
                .filter(theme -> theme.name().equals(themeName))
                .findFirst()
                .flatMap(TerminalThemeResolver::fromThemeResource)
                .orElseGet(TerminalTheme::standard);
    }

    static Optional<TerminalTheme> fromThemeResource(ThemeResource resource) {
        if (resource == null || resource.content() == null) {
            return Optional.empty();
        }
        JsonNode colors = resource.content().path("colors");
        if (!colors.isObject()) {
            return Optional.empty();
        }
        JsonNode vars = resource.content().path("vars");
        Map<String, String> tokenColors = new LinkedHashMap<>();
        colors.fields().forEachRemaining(entry -> colorValue(entry.getValue(), vars, Set.of())
                .ifPresent(color -> tokenColors.put(entry.getKey(), color)));
        if (tokenColors.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(TerminalTheme.fromTokenSgr(tokenColors));
    }

    static String activeThemeName(String themeSetting) {
        if (themeSetting == null || themeSetting.isBlank()) {
            return null;
        }
        String trimmed = themeSetting.trim();
        int slash = trimmed.indexOf('/');
        if (slash < 0 || trimmed.indexOf('/', slash + 1) >= 0) {
            return trimmed;
        }
        String darkTheme = trimmed.substring(slash + 1).trim();
        return darkTheme.isBlank() ? null : darkTheme;
    }

    private static Optional<String> colorValue(JsonNode value, JsonNode vars, Set<String> resolving) {
        if (value == null || value.isNull()) {
            return Optional.empty();
        }
        if (value.isInt()) {
            return xtermSgr(value.asInt());
        }
        if (!value.isTextual()) {
            return Optional.empty();
        }
        String text = value.asText().trim();
        if (text.isEmpty()) {
            return Optional.empty();
        }
        if (text.startsWith("#")) {
            return hexSgr(text);
        }
        if (!vars.isObject() || resolving.contains(text)) {
            return Optional.empty();
        }
        JsonNode varValue = vars.get(text);
        if (varValue == null) {
            return Optional.empty();
        }
        Set<String> nextResolving = new LinkedHashSet<>(resolving);
        nextResolving.add(text);
        return colorValue(varValue, vars, nextResolving);
    }

    private static Optional<String> hexSgr(String hex) {
        String value = hex.startsWith("#") ? hex.substring(1) : hex;
        if (value.length() != 6) {
            return Optional.empty();
        }
        try {
            int r = Integer.parseInt(value.substring(0, 2), 16);
            int g = Integer.parseInt(value.substring(2, 4), 16);
            int b = Integer.parseInt(value.substring(4, 6), 16);
            return Optional.of("\u001B[38;2;" + r + ";" + g + ";" + b + "m");
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static Optional<String> xtermSgr(int index) {
        if (index < 0 || index > 255) {
            return Optional.empty();
        }
        return Optional.of("\u001B[38;5;" + index + "m");
    }
}
