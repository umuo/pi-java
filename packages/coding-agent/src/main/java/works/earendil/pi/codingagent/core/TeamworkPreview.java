package works.earendil.pi.codingagent.core;

import works.earendil.pi.ai.model.Model;
import works.earendil.pi.codingagent.resources.Skill;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class TeamworkPreview {
    private TeamworkPreview() {
    }

    public record Role(String id, String purpose, List<String> tools, String handoff) {
        public Role {
            tools = tools == null ? List.of() : List.copyOf(tools);
        }
    }

    public record Preview(
            Path cwd,
            String mainModel,
            int availableProviders,
            int loadedSkills,
            String steeringMode,
            String followUpMode,
            List<Role> roles) {
        public Preview {
            roles = roles == null ? List.of() : List.copyOf(roles);
        }

        public String render() {
            StringBuilder out = new StringBuilder();
            out.append("Teamwork preview\n");
            out.append("cwd: ").append(cwd).append("\n");
            out.append("main model: ").append(mainModel).append("\n");
            out.append("providers: ").append(availableProviders).append(" available\n");
            out.append("skills: ").append(loadedSkills).append(" loaded\n");
            out.append("coordination: steering=").append(steeringMode)
                    .append(", followUp=").append(followUpMode).append("\n\n");
            out.append("Planned sub-agents:\n");
            for (int i = 0; i < roles.size(); i++) {
                Role role = roles.get(i);
                out.append(i + 1).append(". ").append(role.id()).append(" - ").append(role.purpose()).append("\n");
                out.append("   tools: ").append(String.join(", ", role.tools())).append("\n");
                out.append("   handoff: ").append(role.handoff()).append("\n");
            }
            return out.toString().trim();
        }
    }

    public static Preview fromServices(AgentSession session, AgentSessionServices services, String arguments) {
        List<Model> available = services.modelRegistry().getAvailable();
        int providerCount = (int) available.stream()
                .map(Model::provider)
                .distinct()
                .count();
        List<Skill> skills = services.resourceLoader().skills().skills();
        return new Preview(
                services.cwd(),
                modelRef(session.model()),
                providerCount,
                skills.size(),
                services.settingsManager().getSteeringMode(),
                services.settingsManager().getFollowUpMode(),
                roles(arguments)
        );
    }

    private static List<Role> roles(String arguments) {
        String normalized = arguments == null ? "" : arguments.trim().toLowerCase(Locale.ROOT);
        boolean compact = normalized.contains("compact") || normalized.contains("small");
        if (compact) {
            return List.of(
                    new Role("implementer", "make the scoped code change", List.of("read", "edit", "write", "bash"),
                            "return changed files, tests run, and remaining risks"),
                    new Role("reviewer", "check the patch for regressions", List.of("read", "grep", "bash"),
                            "return findings with file and line references")
            );
        }
        return List.of(
                new Role("researcher", "map the codebase and collect evidence", List.of("read", "ls", "grep", "find"),
                        "return the relevant files, APIs, and constraints"),
                new Role("implementer", "make the scoped code change", List.of("read", "edit", "write", "bash"),
                        "return changed files and verification commands"),
                new Role("reviewer", "check behavior, tests, and edge cases", List.of("read", "grep", "bash"),
                        "return ordered findings and residual risks")
        );
    }

    private static String modelRef(Model model) {
        if (model == null) {
            return "none";
        }
        return model.provider() + "/" + model.modelId();
    }
}
