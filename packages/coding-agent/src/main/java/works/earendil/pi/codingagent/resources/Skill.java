package works.earendil.pi.codingagent.resources;

import java.nio.file.Path;

public record Skill(
        String name,
        String description,
        Path filePath,
        Path baseDir,
        SourceInfo sourceInfo,
        boolean disableModelInvocation
) {
}
