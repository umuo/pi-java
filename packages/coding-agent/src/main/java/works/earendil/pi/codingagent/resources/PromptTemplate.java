package works.earendil.pi.codingagent.resources;

import java.nio.file.Path;

public record PromptTemplate(
        String name,
        String description,
        String argumentHint,
        String content,
        SourceInfo sourceInfo,
        Path filePath
) {
}
