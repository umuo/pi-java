package works.earendil.pi.codingagent.resources;

import java.nio.file.Path;
import java.util.Map;

public record SourceInfo(Path path, String source, String scope, String origin, Path baseDir, Map<String, Object> metadata) {
    public SourceInfo {
        scope = scope == null ? "temporary" : scope;
        origin = origin == null ? "top-level" : origin;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static SourceInfo local(Path path, String scope, Path baseDir) {
        return new SourceInfo(path, "local", scope, "top-level", baseDir, Map.of());
    }

    public static SourceInfo synthetic(Path path, String source, Path baseDir) {
        return new SourceInfo(path, source, "temporary", "top-level", baseDir, Map.of());
    }

    public static SourceInfo packageSource(Path path, String source, String scope, Path baseDir) {
        return new SourceInfo(path, source, scope, "package", baseDir, Map.of());
    }
}
