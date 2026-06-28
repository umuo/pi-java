package works.earendil.pi.codingagent.tools;

import works.earendil.pi.common.glob.GlobMatcher;
import works.earendil.pi.common.text.Truncation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class FindTool {
    public static final int DEFAULT_LIMIT = 1000;

    private final Path cwd;

    public FindTool(Path cwd) {
        this.cwd = cwd;
    }

    public Result find(String pattern, String path, int limit) throws IOException {
        Path root = PathUtils.resolveInside(cwd, path == null || path.isBlank() ? "." : path);
        if (!Files.exists(root)) {
            throw new IllegalArgumentException("Path not found: " + root);
        }
        boolean pathPattern = pattern.contains("/");
        GlobMatcher matcher = GlobMatcher.compile(List.of(pathPattern ? pattern : "**/" + pattern));
        GlobMatcher basenameMatcher = pathPattern ? null : GlobMatcher.compile(List.of(pattern));
        List<String> matches = new ArrayList<>();
        try (var stream = Files.walk(root)) {
            for (Path candidate : stream
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(Path::toString))
                    .toList()) {
                Path rel = root.relativize(candidate);
                String relText = rel.toString().replace('\\', '/');
                if (relText.contains("/node_modules/") || relText.startsWith("node_modules/")
                        || relText.contains("/.git/") || relText.startsWith(".git/")) {
                    continue;
                }
                if (matcher.matches(rel) || (basenameMatcher != null && basenameMatcher.matches(rel.getFileName()))) {
                    matches.add(relText);
                    if (matches.size() >= limit) {
                        break;
                    }
                }
            }
        }
        if (matches.isEmpty()) {
            return new Result("No files found matching pattern", false, null, false);
        }
        String output = String.join("\n", matches);
        Truncation.Result truncation = Truncation.truncateHead(output,
                new Truncation.Options(Integer.MAX_VALUE, Truncation.DEFAULT_MAX_BYTES));
        boolean limitReached = matches.size() >= limit;
        String content = truncation.content();
        if (limitReached || truncation.truncated()) {
            List<String> notices = new ArrayList<>();
            if (limitReached) {
                notices.add(limit + " results limit reached");
            }
            if (truncation.truncated()) {
                notices.add(Truncation.formatSize(Truncation.DEFAULT_MAX_BYTES) + " limit reached");
            }
            content += "\n\n[" + String.join(". ", notices) + "]";
        }
        return new Result(content, limitReached, truncation.truncated() ? truncation : null, false);
    }

    public record Result(String output, boolean resultLimitReached, Truncation.Result truncation, boolean error) {
    }
}
