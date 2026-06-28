package works.earendil.pi.codingagent.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public final class LsTool {
    private final Path cwd;

    public LsTool(Path cwd) {
        this.cwd = cwd;
    }

    public List<String> list(String path) throws IOException {
        Path target = PathUtils.resolveInside(cwd, path == null || path.isBlank() ? "." : path);
        try (var stream = Files.list(target)) {
            return stream
                    .sorted(Comparator
                            .comparing((Path item) -> Files.isRegularFile(item))
                            .thenComparing(Path::toString))
                    .map(item -> Files.isDirectory(item) ? item.getFileName() + "/" : item.getFileName().toString())
                    .toList();
        }
    }
}
