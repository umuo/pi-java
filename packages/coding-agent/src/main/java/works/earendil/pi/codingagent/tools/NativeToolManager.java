package works.earendil.pi.codingagent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import works.earendil.pi.common.json.JsonCodec;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class NativeToolManager {
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private NativeToolManager() {}

    public static Path getBinDir() {
        String userHome = System.getProperty("user.home");
        return Paths.get(userHome, ".pi", "agent", "bin");
    }

    public static Optional<String> getToolPath(String tool) {
        boolean isWin = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        String binaryName = tool + (isWin ? ".exe" : "");
        Path localPath = getBinDir().resolve(binaryName);
        if (Files.exists(localPath)) {
            return Optional.of(localPath.toString());
        }

        List<String> sysNames = "fd".equals(tool) ? List.of("fd", "fdfind") : List.of("rg", "ripgrep");
        for (String name : sysNames) {
            if (commandExists(name)) {
                return Optional.of(name);
            }
        }
        return Optional.empty();
    }

    private static boolean commandExists(String cmd) {
        try {
            Process process = new ProcessBuilder(cmd, "--version").start();
            boolean finished = process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static synchronized Optional<String> ensureTool(String tool, boolean silent) {
        Optional<String> existing = getToolPath(tool);
        if (existing.isPresent()) {
            return existing;
        }

        String offline = System.getenv("PI_OFFLINE");
        if ("1".equals(offline) || "true".equalsIgnoreCase(offline)) {
            if (!silent) {
                System.out.println(tool + " not found. Offline mode enabled, using Java fallback.");
            }
            return Optional.empty();
        }

        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        boolean isMac = os.contains("mac");
        boolean isWin = os.contains("win");
        boolean isLinux = os.contains("linux") || (!isMac && !isWin);
        boolean isArm = arch.contains("aarch64") || arch.contains("arm64");

        String assetName;
        String repo;
        String version;

        if ("fd".equals(tool)) {
            repo = "sharkdp/fd";
            version = "v10.2.0";
            String archStr = isArm ? "aarch64" : "x86_64";
            if (isMac) {
                assetName = "fd-" + version + "-" + archStr + "-apple-darwin.tar.gz";
            } else if (isLinux) {
                assetName = "fd-" + version + "-" + archStr + "-unknown-linux-gnu.tar.gz";
            } else {
                assetName = "fd-" + version + "-" + archStr + "-pc-windows-msvc.zip";
            }
        } else if ("rg".equals(tool)) {
            repo = "BurntSushi/ripgrep";
            version = "14.1.1";
            String archStr = isArm ? "aarch64" : "x86_64";
            if (isMac) {
                assetName = "ripgrep-" + version + "-" + archStr + "-apple-darwin.tar.gz";
            } else if (isLinux) {
                assetName = "ripgrep-" + version + "-" + archStr + "-unknown-linux-musl.tar.gz";
            } else {
                assetName = "ripgrep-" + version + "-" + archStr + "-pc-windows-msvc.zip";
            }
        } else {
            return Optional.empty();
        }

        if (!silent) {
            System.out.println(tool + " not found. Downloading...");
        }

        try {
            String latestTag = fetchLatestVersionTag(repo);
            if (latestTag != null && !latestTag.isBlank()) {
                if ("fd".equals(tool) && !latestTag.startsWith("v")) latestTag = "v" + latestTag;
                if ("rg".equals(tool) && latestTag.startsWith("v")) latestTag = latestTag.substring(1);
                String tagVersion = "fd".equals(tool) ? latestTag : latestTag;
                if ("fd".equals(tool)) {
                    String archStr = isArm ? "aarch64" : "x86_64";
                    if (isMac) assetName = "fd-" + tagVersion + "-" + archStr + "-apple-darwin.tar.gz";
                    else if (isLinux) assetName = "fd-" + tagVersion + "-" + archStr + "-unknown-linux-gnu.tar.gz";
                    else assetName = "fd-" + tagVersion + "-" + archStr + "-pc-windows-msvc.zip";
                } else {
                    String archStr = isArm ? "aarch64" : "x86_64";
                    if (isMac) assetName = "ripgrep-" + tagVersion + "-" + archStr + "-apple-darwin.tar.gz";
                    else if (isLinux) assetName = "ripgrep-" + tagVersion + "-" + archStr + "-unknown-linux-musl.tar.gz";
                    else assetName = "ripgrep-" + tagVersion + "-" + archStr + "-pc-windows-msvc.zip";
                }
                version = latestTag;
            }

            String downloadUrl = "https://github.com/" + repo + "/releases/download/" + ("fd".equals(tool) ? version : version) + "/" + assetName;
            Path binDir = getBinDir();
            Files.createDirectories(binDir);
            Path archivePath = binDir.resolve(assetName);

            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(downloadUrl)).GET().build();
            HttpResponse<InputStream> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode());
            }
            Files.copy(response.body(), archivePath, StandardCopyOption.REPLACE_EXISTING);

            Path extractDir = binDir.resolve("tmp_extract_" + tool + "_" + System.currentTimeMillis());
            Files.createDirectories(extractDir);

            try {
                ProcessBuilder pb;
                if (assetName.endsWith(".tar.gz")) {
                    pb = new ProcessBuilder("tar", "-xzf", archivePath.toString(), "-C", extractDir.toString());
                } else {
                    pb = new ProcessBuilder("tar", "-xf", archivePath.toString(), "-C", extractDir.toString());
                }
                Process p = pb.start();
                p.waitFor();

                String binaryName = tool + (isWin ? ".exe" : "");
                Optional<Path> extractedBin = Files.walk(extractDir)
                        .filter(Files::isRegularFile)
                        .filter(f -> f.getFileName().toString().equals(binaryName))
                        .findFirst();

                if (extractedBin.isPresent()) {
                    Path targetBin = binDir.resolve(binaryName);
                    Files.move(extractedBin.get(), targetBin, StandardCopyOption.REPLACE_EXISTING);
                    targetBin.toFile().setExecutable(true);
                    if (!silent) {
                        System.out.println(tool + " installed to " + targetBin);
                    }
                    return Optional.of(targetBin.toString());
                } else {
                    throw new IOException("Binary " + binaryName + " not found inside downloaded archive.");
                }
            } finally {
                Files.deleteIfExists(archivePath);
                deleteRecursively(extractDir);
            }
        } catch (Exception e) {
            if (!silent) {
                System.out.println("Failed to download " + tool + ": " + e.getMessage() + ". Using Java fallback.");
            }
        }
        return Optional.empty();
    }

    private static String fetchLatestVersionTag(String repo) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com/repos/" + repo + "/releases/latest"))
                    .header("User-Agent", "pi-java-coding-agent")
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode node = JsonCodec.parse(resp.body());
                return node.path("tag_name").asText();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var entries = Files.list(path)) {
                for (Path entry : entries.toList()) {
                    deleteRecursively(entry);
                }
            }
        }
        Files.deleteIfExists(path);
    }
}
