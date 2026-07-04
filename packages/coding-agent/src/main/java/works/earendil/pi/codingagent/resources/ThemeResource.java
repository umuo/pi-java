package works.earendil.pi.codingagent.resources;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Path;

public record ThemeResource(String name, JsonNode content, SourceInfo sourceInfo, Path filePath) {
    public ThemeResource {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("theme name is required");
        }
        content = content == null ? null : content.deepCopy();
        filePath = filePath == null ? null : filePath.toAbsolutePath().normalize();
    }
}
