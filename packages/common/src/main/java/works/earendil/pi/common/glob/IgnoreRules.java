package works.earendil.pi.common.glob;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class IgnoreRules {
    private final List<Rule> rules;

    private IgnoreRules(List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    public static IgnoreRules parse(String content) {
        List<Rule> rules = new ArrayList<>();
        for (String rawLine : content.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            boolean negated = line.startsWith("!");
            if (negated) {
                line = line.substring(1);
            }
            boolean directoryOnly = line.endsWith("/");
            if (directoryOnly) {
                line = line.substring(0, line.length() - 1) + "/**";
            }
            rules.add(new Rule(negated, GlobMatcher.compile(List.of(line))));
        }
        return new IgnoreRules(rules);
    }

    public boolean ignores(Path path) {
        boolean ignored = false;
        for (Rule rule : rules) {
            if (rule.matcher.matches(path)) {
                ignored = !rule.negated;
            }
        }
        return ignored;
    }

    private record Rule(boolean negated, GlobMatcher matcher) {
    }
}
