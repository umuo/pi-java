package works.earendil.pi.common.glob;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;

public final class GlobMatcher {
    private final List<PathMatcher> matchers;

    private GlobMatcher(List<PathMatcher> matchers) {
        this.matchers = List.copyOf(matchers);
    }

    public static GlobMatcher compile(List<String> patterns) {
        List<PathMatcher> compiled = new ArrayList<>();
        for (String pattern : patterns) {
            String normalized = pattern.replace('\\', '/');
            compiled.add(FileSystems.getDefault().getPathMatcher("glob:" + normalized));
            if (!normalized.startsWith("**/")) {
                compiled.add(FileSystems.getDefault().getPathMatcher("glob:**/" + normalized));
            }
        }
        return new GlobMatcher(compiled);
    }

    public boolean matches(Path path) {
        Path normalized = Path.of(path.toString().replace('\\', '/'));
        for (PathMatcher matcher : matchers) {
            if (matcher.matches(normalized)) {
                return true;
            }
        }
        return false;
    }
}
