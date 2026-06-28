package works.earendil.pi.codingagent.tools;

import works.earendil.pi.common.glob.GlobMatcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class GrepTool {
    private final Path cwd;

    public GrepTool(Path cwd) {
        this.cwd = cwd;
    }

    public List<Match> grep(String regex, List<String> includes) throws IOException {
        Pattern pattern = Pattern.compile(regex);
        GlobMatcher includeMatcher = includes == null || includes.isEmpty() ? null : GlobMatcher.compile(includes);
        List<Match> matches = new ArrayList<>();
        try (var paths = Files.walk(cwd)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                Path rel = cwd.relativize(path);
                if (includeMatcher != null && !includeMatcher.matches(rel)) {
                    continue;
                }
                List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    if (pattern.matcher(lines.get(i)).find()) {
                        matches.add(new Match(rel.toString(), i + 1, lines.get(i)));
                    }
                }
            }
        }
        return List.copyOf(matches);
    }

    public record Match(String path, int line, String text) {
    }
}
