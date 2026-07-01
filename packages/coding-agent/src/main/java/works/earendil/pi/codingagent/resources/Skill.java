package works.earendil.pi.codingagent.resources;

import java.nio.file.Path;
import java.util.List;

public record Skill(
        String name,
        String description,
        Path filePath,
        Path baseDir,
        SourceInfo sourceInfo,
        boolean disableModelInvocation,
        String modelInvocation,
        List<String> triggerTerms,
        List<String> triggerPatterns,
        List<String> triggerGlobs
) {
    public Skill(String name, String description, Path filePath, Path baseDir, SourceInfo sourceInfo,
                 boolean disableModelInvocation) {
        this(name, description, filePath, baseDir, sourceInfo, disableModelInvocation,
                disableModelInvocation ? "manual" : "auto", List.of(), List.of(), List.of());
    }

    public Skill {
        modelInvocation = modelInvocation == null || modelInvocation.isBlank()
                ? (disableModelInvocation ? "manual" : "auto")
                : modelInvocation;
        triggerTerms = triggerTerms == null ? List.of() : List.copyOf(triggerTerms);
        triggerPatterns = triggerPatterns == null ? List.of() : List.copyOf(triggerPatterns);
        triggerGlobs = triggerGlobs == null ? List.of() : List.copyOf(triggerGlobs);
    }

    public boolean hasTriggerHints() {
        return !triggerTerms.isEmpty() || !triggerPatterns.isEmpty() || !triggerGlobs.isEmpty();
    }
}
