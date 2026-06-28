package works.earendil.pi.codingagent.resources;

import java.nio.file.Path;

public sealed interface ResourceDiagnostic permits ResourceDiagnostic.Warning, ResourceDiagnostic.Error, ResourceDiagnostic.Collision {
    String type();

    String message();

    Path path();

    record Warning(String message, Path path) implements ResourceDiagnostic {
        @Override
        public String type() {
            return "warning";
        }
    }

    record Error(String message, Path path) implements ResourceDiagnostic {
        @Override
        public String type() {
            return "error";
        }
    }

    record Collision(String resourceType, String name, Path winnerPath, Path loserPath,
                     String winnerSource, String loserSource) implements ResourceDiagnostic {
        public Collision(String resourceType, String name, Path winnerPath, Path loserPath) {
            this(resourceType, name, winnerPath, loserPath, null, null);
        }

        @Override
        public String type() {
            return "collision";
        }

        @Override
        public String message() {
            return "name \"" + name + "\" collision";
        }

        @Override
        public Path path() {
            return loserPath;
        }
    }
}
