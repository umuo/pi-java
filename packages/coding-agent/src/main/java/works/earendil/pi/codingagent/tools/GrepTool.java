package works.earendil.pi.codingagent.tools;

import works.earendil.pi.common.glob.GlobMatcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public final class GrepTool {
    private final Path cwd;

    public GrepTool(Path cwd) {
        this.cwd = cwd;
    }

    public List<Match> grep(String regex, List<String> includes) throws IOException {
        Optional<String> rgBin = NativeToolManager.ensureTool("rg", true);
        if (rgBin.isPresent()) {
            try {
                List<String> cmd = new ArrayList<>(List.of(rgBin.get(), "-n", "--color=never", regex, cwd.toString()));
                if (includes != null && !includes.isEmpty()) {
                    for (String inc : includes) {
                        cmd.add("-g");
                        cmd.add(inc);
                    }
                }
                ProcessBuilder pb = new ProcessBuilder(cmd);
                Process process = pb.start();
                String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                process.waitFor();
                if (process.exitValue() == 0) {
                    List<Match> rgMatches = new ArrayList<>();
                    for (String l : stdout.split("\n")) {
                        if (l.isBlank()) continue;
                        int firstColon = l.indexOf(':');
                        if (firstColon > 0) {
                            int secondColon = l.indexOf(':', firstColon + 1);
                            if (secondColon > firstColon) {
                                String filePart = l.substring(0, firstColon);
                                int lineNum = Integer.parseInt(l.substring(firstColon + 1, secondColon));
                                String textPart = l.substring(secondColon + 1);
                                Path p = Path.of(filePart);
                                String relPath;
                                try { relPath = cwd.relativize(p).toString().replace('\\', '/'); }
                                catch (Exception e) { relPath = filePart.replace('\\', '/'); }
                                rgMatches.add(new Match(relPath, lineNum, textPart));
                            }
                        }
                    }
                    if (!rgMatches.isEmpty()) {
                        return List.copyOf(rgMatches);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        Pattern pattern = Pattern.compile(regex);
        GlobMatcher includeMatcher = includes == null || includes.isEmpty() ? null : GlobMatcher.compile(includes);
        List<Match> matches = new ArrayList<>();
        try (var paths = Files.walk(cwd)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                Path rel = cwd.relativize(path);
                if (includeMatcher != null && !includeMatcher.matches(rel)) {
                    continue;
                }
                List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    if (pattern.matcher(lines.get(i)).find()) {
                        matches.add(new Match(rel.toString(), i + 1, lines.get(i)));
                    }
                }
            }
        }
        return List.copyOf(matches);
    }

    public record Match(String path, int line, String text) {
    }
}
