package works.earendil.pi.codingagent.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import works.earendil.pi.codingagent.tools.PathUtils;
import works.earendil.pi.common.json.JsonCodec;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class TrustManager {
    public static final String CONFIG_DIR_NAME = ".pi";
    private static final List<String> TRUST_REQUIRING_PROJECT_CONFIG_RESOURCES = List.of(
            "settings.json",
            "extensions",
            "skills",
            "prompts",
            "themes",
            "SYSTEM.md",
            "APPEND_SYSTEM.md");

    private TrustManager() {
    }

    public record ProjectTrustStoreEntry(Path path, boolean decision) {
    }

    public record ProjectTrustUpdate(Path path, Boolean decision) {
    }

    public record ProjectTrustOption(String label, boolean trusted, List<ProjectTrustUpdate> updates, Path savedPath) {
    }

    public static Path normalizeCwd(Path cwd) {
        return PathUtils.canonicalizePath(PathUtils.resolvePath(cwd.toString()));
    }

    public static Optional<ProjectTrustStoreEntry> findNearestTrustEntry(Map<Path, Boolean> data, Path cwd) {
        Path currentDir = normalizeCwd(cwd);
        while (currentDir != null) {
            Boolean value = data.get(currentDir);
            if (value != null) {
                return Optional.of(new ProjectTrustStoreEntry(currentDir, value));
            }
            currentDir = currentDir.getParent();
        }
        return Optional.empty();
    }

    public static Optional<Path> getProjectTrustParentPath(Path cwd) {
        Path trustPath = normalizeCwd(cwd);
        return Optional.ofNullable(trustPath.getParent());
    }

    public static List<ProjectTrustOption> getProjectTrustOptions(Path cwd) {
        return getProjectTrustOptions(cwd, false);
    }

    public static List<ProjectTrustOption> getProjectTrustOptions(Path cwd, boolean includeSessionOnly) {
        Path trustPath = normalizeCwd(cwd);
        List<ProjectTrustOption> options = new ArrayList<>();
        options.add(new ProjectTrustOption("Trust", true,
                List.of(new ProjectTrustUpdate(trustPath, true)), trustPath));
        getProjectTrustParentPath(cwd).ifPresent(parentPath -> options.add(new ProjectTrustOption(
                "Trust parent folder (" + parentPath + ")",
                true,
                List.of(new ProjectTrustUpdate(parentPath, true), new ProjectTrustUpdate(trustPath, null)),
                parentPath)));
        if (includeSessionOnly) {
            options.add(new ProjectTrustOption("Trust (this session only)", true, List.of(), null));
        }
        options.add(new ProjectTrustOption("Do not trust", false,
                List.of(new ProjectTrustUpdate(trustPath, false)), trustPath));
        if (includeSessionOnly) {
            options.add(new ProjectTrustOption("Do not trust (this session only)", false, List.of(), null));
        }
        return List.copyOf(options);
    }

    public static boolean hasTrustRequiringProjectResources(Path cwd) {
        return hasTrustRequiringProjectResources(cwd, Path.of(System.getProperty("user.home")));
    }

    public static boolean hasTrustRequiringProjectResources(Path cwd, Path homeDir) {
        Path home = PathUtils.canonicalizePath(PathUtils.resolvePath(homeDir.toString()));
        Path userAgentsSkillsDir = home.resolve(".agents").resolve("skills");
        Path currentDir = normalizeCwd(cwd);

        Path configDir = currentDir.resolve(CONFIG_DIR_NAME);
        for (String entry : TRUST_REQUIRING_PROJECT_CONFIG_RESOURCES) {
            if (Files.exists(configDir.resolve(entry))) {
                return true;
            }
        }

        while (currentDir != null) {
            Path agentsSkillsDir = currentDir.resolve(".agents").resolve("skills");
            if (!agentsSkillsDir.equals(userAgentsSkillsDir) && Files.exists(agentsSkillsDir)) {
                return true;
            }
            currentDir = currentDir.getParent();
        }
        return false;
    }

    public static final class ProjectTrustStore {
        private final Path trustPath;

        public ProjectTrustStore(Path agentDir) {
            this.trustPath = PathUtils.resolvePath(agentDir.toString()).resolve("trust.json");
        }

        public Boolean get(Path cwd) throws IOException {
            return getEntry(cwd).map(ProjectTrustStoreEntry::decision).orElse(null);
        }

        public Optional<ProjectTrustStoreEntry> getEntry(Path cwd) throws IOException {
            return withTrustFileLock(() -> findNearestTrustEntry(readTrustFile(trustPath), cwd));
        }

        public void set(Path cwd, Boolean decision) throws IOException {
            setMany(List.of(new ProjectTrustUpdate(cwd, decision)));
        }

        public void setMany(List<ProjectTrustUpdate> decisions) throws IOException {
            withTrustFileLock(() -> {
                Map<Path, Boolean> data = readTrustFile(trustPath);
                for (ProjectTrustUpdate update : decisions) {
                    Path key = normalizeCwd(update.path());
                    if (update.decision() == null) {
                        data.remove(key);
                    } else {
                        data.put(key, update.decision());
                    }
                }
                writeTrustFile(trustPath, data);
                return null;
            });
        }

        public Path trustPath() {
            return trustPath;
        }

        private <T> T withTrustFileLock(IoSupplier<T> supplier) throws IOException {
            Files.createDirectories(trustPath.getParent());
            Path lockPath = trustPath.resolveSibling(trustPath.getFileName() + ".lock");
            try (FileChannel channel = FileChannel.open(lockPath,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock ignored = channel.lock()) {
                return supplier.get();
            }
        }
    }

    private static Map<Path, Boolean> readTrustFile(Path path) throws IOException {
        if (!Files.exists(path)) {
            return new LinkedHashMap<>();
        }
        JsonNode parsed;
        try {
            parsed = JsonCodec.parse(Files.readString(path, StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            throw new IOException("Failed to read trust store " + path + ": " + e.getMessage(), e);
        }
        if (!parsed.isObject()) {
            throw new IOException("Invalid trust store " + path + ": expected an object");
        }
        Map<Path, Boolean> data = new LinkedHashMap<>();
        parsed.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            if (value.isBoolean()) {
                data.put(Path.of(entry.getKey()), value.asBoolean());
            } else if (!value.isNull()) {
                throw new IllegalArgumentException("Invalid trust store " + path
                        + ": value for " + JsonCodec.stringify(entry.getKey()) + " must be true, false, or null");
            }
        });
        return data;
    }

    private static void writeTrustFile(Path path, Map<Path, Boolean> data) throws IOException {
        Files.createDirectories(path.getParent());
        ObjectNode sorted = JsonCodec.mapper().createObjectNode();
        data.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .forEach(entry -> sorted.put(entry.getKey().toString(), entry.getValue()));
        Files.writeString(path, JsonCodec.mapper().writerWithDefaultPrettyPrinter().writeValueAsString(sorted) + "\n",
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    @FunctionalInterface
    private interface IoSupplier<T> {
        T get() throws IOException;
    }
}
