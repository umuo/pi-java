package works.earendil.pi.codingagent.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class SessionCwd {
    private SessionCwd() {
    }

    public record SessionCwdIssue(Path sessionFile, Path sessionCwd, Path fallbackCwd) {
    }

    public interface SessionCwdSource {
        Path cwd();

        Optional<Path> sessionFile();
    }

    public static Optional<SessionCwdIssue> getMissingSessionCwdIssue(SessionCwdSource source, Path fallbackCwd) {
        Optional<Path> sessionFile = source.sessionFile();
        if (sessionFile.isEmpty()) {
            return Optional.empty();
        }
        Path sessionCwd = source.cwd();
        if (sessionCwd == null || Files.exists(sessionCwd)) {
            return Optional.empty();
        }
        return Optional.of(new SessionCwdIssue(sessionFile.get(), sessionCwd, fallbackCwd));
    }

    public static String formatMissingSessionCwdError(SessionCwdIssue issue) {
        String sessionFile = issue.sessionFile() == null ? "" : "\nSession file: " + issue.sessionFile();
        return "Stored session working directory does not exist: " + issue.sessionCwd()
                + sessionFile
                + "\nCurrent working directory: " + issue.fallbackCwd();
    }

    public static String formatMissingSessionCwdPrompt(SessionCwdIssue issue) {
        return "cwd from session file does not exist\n"
                + issue.sessionCwd()
                + "\n\ncontinue in current cwd\n"
                + issue.fallbackCwd();
    }

    public static void assertSessionCwdExists(SessionCwdSource source, Path fallbackCwd) {
        getMissingSessionCwdIssue(source, fallbackCwd).ifPresent(issue -> {
            throw new MissingSessionCwdException(issue);
        });
    }

    public static final class MissingSessionCwdException extends RuntimeException {
        private final SessionCwdIssue issue;

        public MissingSessionCwdException(SessionCwdIssue issue) {
            super(formatMissingSessionCwdError(issue));
            this.issue = issue;
        }

        public SessionCwdIssue issue() {
            return issue;
        }
    }
}
