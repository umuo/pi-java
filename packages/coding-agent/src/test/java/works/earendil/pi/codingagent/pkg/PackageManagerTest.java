package works.earendil.pi.codingagent.pkg;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import works.earendil.pi.codingagent.config.SettingsManager;
import works.earendil.pi.codingagent.resources.PackageResourceResolver;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PackageManagerTest {
    @TempDir
    Path tempDir;

    @Test
    void installAndRemovePersistGlobalPackageSource() throws Exception {
        Path cwd = tempDir.resolve("project");
        Path agentDir = tempDir.resolve("agent");
        Path source = tempDir.resolve("review-pack");
        Files.createDirectories(cwd);
        Files.createDirectories(agentDir);
        Files.createDirectories(source);
        Files.writeString(source.resolve("package.json"), "{\"name\":\"review-pack\"}");
        SettingsManager settings = new SettingsManager(cwd, agentDir, true);

        String installed = PackageManager.installAndPersist(source.toString(), false, cwd, agentDir, settings);
        String installedAgain = PackageManager.installAndPersist(source.toString(), false, cwd, agentDir, settings);

        assertThat(installed).contains("Installed local package review-pack").contains("Settings packages: added");
        assertThat(installedAgain).contains("Settings packages: already contains");
        assertThat(agentDir.resolve("packages").resolve("review-pack").resolve("package.json")).exists();
        assertThat(packageSources(settings.getGlobalSettings()))
                .containsExactly(agentDir.relativize(source).toString());

        String removed = PackageManager.removeAndPersist(source.toString(), false, cwd, agentDir, settings);

        assertThat(removed).contains("Removed package review-pack").contains("Settings packages: removed");
        assertThat(agentDir.resolve("packages").resolve("review-pack")).doesNotExist();
        assertThat(packageSources(settings.getGlobalSettings())).isEmpty();
    }

    @Test
    void installAndRemovePersistProjectPackageSource() throws Exception {
        Path cwd = tempDir.resolve("project");
        Path agentDir = tempDir.resolve("agent");
        Path source = tempDir.resolve("local-pack");
        Files.createDirectories(cwd);
        Files.createDirectories(agentDir);
        Files.createDirectories(source);
        Files.writeString(source.resolve("SKILL.md"), "---\nname: local-pack\ndescription: Local\n---\nLocal");
        SettingsManager settings = new SettingsManager(cwd, agentDir, true);

        PackageManager.installAndPersist(source.toString(), true, cwd, agentDir, settings);

        assertThat(cwd.resolve(".pi").resolve("packages").resolve("local-pack").resolve("SKILL.md")).exists();
        assertThat(packageSources(settings.getProjectSettings()))
                .containsExactly(cwd.resolve(".pi").relativize(source).toString());
        assertThat(packageSources(settings.getGlobalSettings())).isEmpty();

        PackageManager.removeAndPersist(source.toString(), true, cwd, agentDir, settings);

        assertThat(cwd.resolve(".pi").resolve("packages").resolve("local-pack")).doesNotExist();
        assertThat(packageSources(settings.getProjectSettings())).isEmpty();
    }

    @Test
    void sourceSettingsHelpersUnderstandObjectPackageEntries() throws Exception {
        Path cwd = tempDir.resolve("project");
        Path agentDir = tempDir.resolve("agent");
        Files.createDirectories(cwd.resolve(".pi"));
        Files.createDirectories(agentDir);
        Files.writeString(agentDir.resolve("settings.json"), """
                {"packages":[{"source":"npm:demo","skills":["skills/*.md"]}]}
                """);
        SettingsManager settings = new SettingsManager(cwd, agentDir, true);

        assertThat(PackageManager.addSourceToSettings("npm:demo", false, settings)).isFalse();
        assertThat(PackageManager.removeSourceFromSettings("npm:demo", false, settings)).isTrue();

        assertThat(settings.getGlobalSettings().path("packages")).isEmpty();
    }

    @Test
    void sourceSettingsHelpersNormalizeGitHostWhenMatchingEntries() throws Exception {
        Path cwd = tempDir.resolve("project");
        Path agentDir = tempDir.resolve("agent");
        Files.createDirectories(cwd.resolve(".pi"));
        Files.createDirectories(agentDir);
        Files.writeString(agentDir.resolve("settings.json"), """
                {"packages":["git:GitHub.com/acme/review-pack@v1"]}
                """);
        SettingsManager settings = new SettingsManager(cwd, agentDir, true);

        boolean changed = PackageManager.addSourceToSettings(
                "https://github.com/acme/review-pack.git@v2", false, settings);

        assertThat(changed).isTrue();
        assertThat(packageSources(settings.getGlobalSettings()))
                .containsExactly("https://github.com/acme/review-pack.git@v2");
    }

    @Test
    void configuresPackageResourceFiltersInSettings() throws Exception {
        Path cwd = tempDir.resolve("project");
        Path agentDir = tempDir.resolve("agent");
        Files.createDirectories(cwd);
        Files.createDirectories(agentDir);
        Files.writeString(agentDir.resolve("settings.json"), """
                {"packages":["npm:demo"]}
                """);
        SettingsManager settings = new SettingsManager(cwd, agentDir, true);

        String disabled = PackageManager.configurePackageResource("npm:demo", "skill",
                "./skills/private.md", false, false, settings);
        String enabled = PackageManager.configurePackageResource("npm:demo", "skills",
                "skills/private.md", true, false, settings);
        String listed = PackageManager.listConfiguredPackages(false, settings);

        assertThat(disabled).contains("status: updated").contains("filter: -skills/private.md");
        assertThat(enabled).contains("filter: +skills/private.md");
        JsonNode entry = settings.getGlobalSettings().path("packages").get(0);
        assertThat(entry.path("source").asText()).isEqualTo("npm:demo");
        assertThat(entry.path("skills")).extracting(JsonNode::asText).containsExactly("+skills/private.md");
        assertThat(listed)
                .contains("Configured packages (global):")
                .contains("npm:demo")
                .contains("skills: +skills/private.md");
    }

    @Test
    void reportsMissingPackageSourceWhenConfiguringFilters() throws Exception {
        Path cwd = tempDir.resolve("project");
        Path agentDir = tempDir.resolve("agent");
        Files.createDirectories(cwd);
        Files.createDirectories(agentDir);
        SettingsManager settings = new SettingsManager(cwd, agentDir, true);

        String output = PackageManager.configurePackageResource("npm:missing", "skills",
                "skills/a.md", true, false, settings);

        assertThat(output).contains("status: not found").contains("source: npm:missing");
        assertThat(settings.getGlobalSettings().path("packages").isMissingNode()).isTrue();
    }

    @Test
    void packageManagerCliConfigEnableUpdatesGlobalSettings() throws Exception {
        String originalUserHome = System.getProperty("user.home");
        java.io.PrintStream originalOut = System.out;
        Path home = tempDir.resolve("home");
        Path agentDir = home.resolve(".pi").resolve("agent");
        Files.createDirectories(agentDir);
        Files.writeString(agentDir.resolve("settings.json"), """
                {"packages":["npm:demo"]}
                """);
        java.io.ByteArrayOutputStream stdout = new java.io.ByteArrayOutputStream();

        try {
            System.setProperty("user.home", home.toString());
            System.setOut(new java.io.PrintStream(stdout));

            int exit = PackageManagerCli.handleCommand("config",
                    new String[]{"enable", "npm:demo", "skills", "skills/a.md"});

            SettingsManager settings = new SettingsManager(Path.of(".").toAbsolutePath().normalize(), agentDir, true);
            JsonNode entry = settings.getGlobalSettings().path("packages").get(0);
            assertThat(exit).isZero();
            assertThat(stdout.toString()).contains("status: updated").contains("filter: +skills/a.md");
            assertThat(entry.path("source").asText()).isEqualTo("npm:demo");
            assertThat(entry.path("skills")).extracting(JsonNode::asText).containsExactly("+skills/a.md");
        } finally {
            System.setOut(originalOut);
            System.setProperty("user.home", originalUserHome);
        }
    }

    @Test
    void packageManagerCliUpdateDefaultsToSelfAndExtensionsFlagUpdatesPackages() throws Exception {
        String originalUserHome = System.getProperty("user.home");
        java.io.PrintStream originalOut = System.out;
        Path home = tempDir.resolve("home-update");
        Path agentDir = home.resolve(".pi").resolve("agent");
        Files.createDirectories(agentDir);
        Path fakeNpm = createFakeNpmCommand(tempDir.resolve("fake-npm-cli-update"));
        Files.writeString(agentDir.resolve("settings.json"), """
                {
                  "npmCommand":["%s"],
                  "packages":["npm:@scope/review-pack"]
                }
                """.formatted(fakeNpm.toString().replace("\\", "\\\\")));
        java.io.ByteArrayOutputStream stdout = new java.io.ByteArrayOutputStream();

        try {
            System.setProperty("user.home", home.toString());
            System.setOut(new java.io.PrintStream(stdout));

            int selfExit = PackageManagerCli.handleCommand("update", new String[]{});
            String selfOutput = stdout.toString();
            Path installedRoot = agentDir.resolve("npm").resolve("node_modules")
                    .resolve("@scope").resolve("review-pack");
            assertThat(installedRoot).doesNotExist();
            stdout.reset();
            int extensionsExit = PackageManagerCli.handleCommand("update", new String[]{"--extensions"});
            String extensionsOutput = stdout.toString();

            assertThat(selfExit).isZero();
            assertThat(selfOutput)
                    .contains("Pi Java CLI is managed")
                    .contains("Packages are skipped");
            assertThat(extensionsExit).isZero();
            assertThat(extensionsOutput).contains("Installed npm package @scope/review-pack");
            assertThat(installedRoot.resolve("package.json")).exists();
        } finally {
            System.setOut(originalOut);
            System.setProperty("user.home", originalUserHome);
        }
    }

    @Test
    void packageManagerCliUpdateAllAndExtensionFlagFollowTsTargets() throws Exception {
        String originalUserHome = System.getProperty("user.home");
        java.io.PrintStream originalOut = System.out;
        java.io.PrintStream originalErr = System.err;
        Path home = tempDir.resolve("home-update-flags");
        Path agentDir = home.resolve(".pi").resolve("agent");
        Files.createDirectories(agentDir);
        Path fakeNpm = createFakeNpmCommand(tempDir.resolve("fake-npm-cli-update-flags"));
        Files.writeString(agentDir.resolve("settings.json"), """
                {
                  "npmCommand":["%s"],
                  "packages":["npm:@scope/review-pack","npm:@scope/other-pack"]
                }
                """.formatted(fakeNpm.toString().replace("\\", "\\\\")));
        java.io.ByteArrayOutputStream stdout = new java.io.ByteArrayOutputStream();
        java.io.ByteArrayOutputStream stderr = new java.io.ByteArrayOutputStream();

        try {
            System.setProperty("user.home", home.toString());
            System.setOut(new java.io.PrintStream(stdout));
            System.setErr(new java.io.PrintStream(stderr));

            int extensionExit = PackageManagerCli.handleCommand("update",
                    new String[]{"--extension", "npm:@scope/review-pack@2.0.0"});
            String extensionOutput = stdout.toString();
            assertThat(agentDir.resolve("npm").resolve("node_modules")
                    .resolve("@scope").resolve("other-pack")).doesNotExist();
            stdout.reset();
            int allExit = PackageManagerCli.handleCommand("update", new String[]{"--all"});
            String allOutput = stdout.toString();
            int conflictExit = PackageManagerCli.handleCommand("update", new String[]{"--all", "--extensions"});

            assertThat(extensionExit).isZero();
            assertThat(extensionOutput).contains("Installed npm package @scope/review-pack");
            assertThat(agentDir.resolve("npm").resolve("node_modules")
                    .resolve("@scope").resolve("review-pack").resolve("package.json")).exists();
            assertThat(allExit).isZero();
            assertThat(allOutput)
                    .contains("Pi Java CLI is managed")
                    .contains("Installed npm package @scope/review-pack")
                    .contains("Installed npm package @scope/other-pack");
            assertThat(conflictExit).isEqualTo(1);
            assertThat(stderr.toString()).contains("--all cannot be combined");
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
            System.setProperty("user.home", originalUserHome);
        }
    }

    @Test
    void installsGitProtocolPackageAndReconcilesPinnedRef() throws Exception {
        Path cwd = tempDir.resolve("project");
        Path agentDir = tempDir.resolve("agent");
        Path servedRoot = tempDir.resolve("served");
        Files.createDirectories(cwd);
        Files.createDirectories(agentDir);
        Path bareRepo = createServedGitPackage(servedRoot);
        Path fakeNpm = createFakeNpmCommand(tempDir.resolve("fake-npm-git"));
        Files.writeString(agentDir.resolve("settings.json"), """
                {"npmCommand":["%s"]}
                """.formatted(fakeNpm.toString().replace("\\", "\\\\")));
        String repoUrl = "file://localhost" + bareRepo.toAbsolutePath().normalize().toString().replace('\\', '/');
        String sourceV1 = repoUrl + "@v1";
        String sourceV2 = sourceV1.replace("@v1", "@v2");

        SettingsManager settings = new SettingsManager(cwd, agentDir, true);

        String installed = PackageManager.installAndPersist(sourceV1, false, cwd, agentDir, settings);

        Path installedRoot = gitInstalledRoot(agentDir, "localhost",
                bareRepo.toAbsolutePath().normalize().toString().replaceFirst("^/+", "")
                        .replaceAll("\\.git$", ""));
        assertThat(installed).contains("Installed git package localhost/");
        assertThat(installed).contains("@v1");
        assertThat(Files.readString(installedRoot.resolve("skills").resolve("SKILL.md"))).contains("Version 1");
        assertThat(installedRoot.resolve("node_modules").resolve(".pi-dependencies-installed")).exists();
        assertThat(packageSources(settings.getGlobalSettings())).containsExactly(sourceV1);
        assertThat(PackageResourceResolver.resolve(cwd, agentDir, true, packageEntries(settings.getGlobalSettings()))
                .skills()).containsExactly(installedRoot.resolve("skills").toAbsolutePath().normalize());

        String updated = PackageManager.installAndPersist(sourceV2, false, cwd, agentDir, settings);

        assertThat(updated).contains("Updated git package localhost/");
        assertThat(updated).contains("@v2");
        assertThat(Files.readString(installedRoot.resolve("skills").resolve("SKILL.md"))).contains("Version 2");
        assertThat(installedRoot.resolve("node_modules").resolve(".pi-dependencies-installed")).exists();
        assertThat(packageSources(settings.getGlobalSettings())).containsExactly(sourceV2);
    }

    @Test
    void installsNpmPackageWithConfiguredCommandAndDiscoversResources() throws Exception {
        Path cwd = tempDir.resolve("project");
        Path agentDir = tempDir.resolve("agent");
        Files.createDirectories(cwd);
        Files.createDirectories(agentDir);
        Path fakeNpm = createFakeNpmCommand(tempDir.resolve("fake-npm"));
        Files.writeString(agentDir.resolve("settings.json"), """
                {"npmCommand":["%s"]}
                """.formatted(fakeNpm.toString().replace("\\", "\\\\")));
        SettingsManager settings = new SettingsManager(cwd, agentDir, true);
        String sourceV1 = "npm:@scope/review-pack@1.0.0";
        String sourceV2 = "npm:@scope/review-pack@2.0.0";

        String installed = PackageManager.installAndPersist(sourceV1, false, cwd, agentDir, settings);

        Path installedRoot = agentDir.resolve("npm").resolve("node_modules").resolve("@scope").resolve("review-pack");
        assertThat(installed).contains("Installed npm package @scope/review-pack@1.0.0");
        assertThat(installedRoot.resolve("package.json")).exists();
        assertThat(packageSources(settings.getGlobalSettings())).containsExactly(sourceV1);
        assertThat(PackageResourceResolver.resolve(cwd, agentDir, true, packageEntries(settings.getGlobalSettings()))
                .skills()).containsExactly(installedRoot.resolve("skills").toAbsolutePath().normalize());

        PackageManager.installAndPersist(sourceV2, false, cwd, agentDir, settings);

        assertThat(packageSources(settings.getGlobalSettings())).containsExactly(sourceV2);

        String removed = PackageManager.removeAndPersist("npm:@scope/review-pack", false, cwd, agentDir, settings);

        assertThat(removed).contains("Removed npm package @scope/review-pack");
        assertThat(installedRoot).doesNotExist();
        assertThat(packageSources(settings.getGlobalSettings())).isEmpty();
    }

    @Test
    void updatesConfiguredNpmPackagesAndSkipsPinnedExactVersions() throws Exception {
        Path cwd = tempDir.resolve("project");
        Path agentDir = tempDir.resolve("agent");
        Files.createDirectories(cwd);
        Files.createDirectories(agentDir);
        Path fakeNpm = createFakeNpmCommand(tempDir.resolve("fake-npm-update"));
        Files.writeString(agentDir.resolve("settings.json"), """
                {
                  "npmCommand":["%s"],
                  "packages":["npm:@scope/review-pack","npm:@scope/pinned-pack@1.0.0"]
                }
                """.formatted(fakeNpm.toString().replace("\\", "\\\\")));
        SettingsManager settings = new SettingsManager(cwd, agentDir, true);

        String output = PackageManager.update("all", false, cwd, agentDir, settings);

        Path reviewRoot = agentDir.resolve("npm").resolve("node_modules").resolve("@scope").resolve("review-pack");
        Path pinnedRoot = agentDir.resolve("npm").resolve("node_modules").resolve("@scope").resolve("pinned-pack");
        assertThat(output)
                .contains("Installed npm package @scope/review-pack")
                .contains("Skipped pinned npm package npm:@scope/pinned-pack@1.0.0");
        assertThat(reviewRoot.resolve("package.json")).exists();
        assertThat(pinnedRoot).doesNotExist();
        assertThat(packageSources(settings.getGlobalSettings()))
                .containsExactly("npm:@scope/review-pack", "npm:@scope/pinned-pack@1.0.0");
    }

    @Test
    void updatesOneConfiguredPackageByIdentityAndReportsMissingMatches() throws Exception {
        Path cwd = tempDir.resolve("project");
        Path agentDir = tempDir.resolve("agent");
        Files.createDirectories(cwd);
        Files.createDirectories(agentDir);
        Path fakeNpm = createFakeNpmCommand(tempDir.resolve("fake-npm-single-update"));
        Files.writeString(agentDir.resolve("settings.json"), """
                {
                  "npmCommand":["%s"],
                  "packages":["npm:@scope/review-pack","npm:@scope/other-pack"]
                }
                """.formatted(fakeNpm.toString().replace("\\", "\\\\")));
        SettingsManager settings = new SettingsManager(cwd, agentDir, true);

        String updated = PackageManager.update("npm:@scope/review-pack@2.0.0", false, cwd, agentDir, settings);
        String missing = PackageManager.update("npm:@scope/missing-pack", false, cwd, agentDir, settings);

        assertThat(updated).contains("Installed npm package @scope/review-pack");
        assertThat(agentDir.resolve("npm").resolve("node_modules").resolve("@scope").resolve("review-pack")
                .resolve("package.json")).exists();
        assertThat(agentDir.resolve("npm").resolve("node_modules").resolve("@scope").resolve("other-pack"))
                .doesNotExist();
        assertThat(missing)
                .contains("No configured package matched: npm:@scope/missing-pack")
                .contains("Configured packages (global):")
                .contains("npm:@scope/review-pack")
                .contains("npm:@scope/other-pack");
    }

    @Test
    void updatesConfiguredGitPackageToPinnedRefFromSettings() throws Exception {
        Path cwd = tempDir.resolve("project");
        Path agentDir = tempDir.resolve("agent");
        Path servedRoot = tempDir.resolve("served-update");
        Files.createDirectories(cwd);
        Files.createDirectories(agentDir);
        Path bareRepo = createServedGitPackage(servedRoot);
        Path fakeNpm = createFakeNpmCommand(tempDir.resolve("fake-npm-git-update"));
        Files.writeString(agentDir.resolve("settings.json"), """
                {"npmCommand":["%s"]}
                """.formatted(fakeNpm.toString().replace("\\", "\\\\")));
        String repoUrl = "file://localhost" + bareRepo.toAbsolutePath().normalize().toString().replace('\\', '/');
        String sourceV1 = repoUrl + "@v1";
        String sourceV2 = repoUrl + "@v2";
        SettingsManager settings = new SettingsManager(cwd, agentDir, true);

        PackageManager.installAndPersist(sourceV1, false, cwd, agentDir, settings);
        PackageManager.addSourceToSettings(sourceV2, false, settings);
        String updated = PackageManager.update("all", false, cwd, agentDir, settings);

        Path installedRoot = gitInstalledRoot(agentDir, "localhost",
                bareRepo.toAbsolutePath().normalize().toString().replaceFirst("^/+", "")
                        .replaceAll("\\.git$", ""));
        assertThat(updated).contains("Updated git package localhost/").contains("@v2");
        assertThat(Files.readString(installedRoot.resolve("skills").resolve("SKILL.md"))).contains("Version 2");
        assertThat(installedRoot.resolve("node_modules").resolve(".pi-dependencies-installed")).exists();
        assertThat(packageSources(settings.getGlobalSettings())).containsExactly(sourceV2);
    }

    private static java.util.List<String> packageSources(JsonNode settings) {
        java.util.List<String> sources = new java.util.ArrayList<>();
        settings.path("packages").forEach(entry -> {
            if (entry.isTextual()) {
                sources.add(entry.asText());
            } else if (entry.path("source").isTextual()) {
                sources.add(entry.path("source").asText());
            }
        });
        return sources;
    }

    private static java.util.List<JsonNode> packageEntries(JsonNode settings) {
        java.util.List<JsonNode> entries = new java.util.ArrayList<>();
        settings.path("packages").forEach(entries::add);
        return entries;
    }

    private static Path createServedGitPackage(Path servedRoot) throws Exception {
        Path work = servedRoot.resolve("work");
        Path bare = servedRoot.resolve("org").resolve("repo.git");
        Files.createDirectories(work);
        run(work, "git", "init");
        run(work, "git", "config", "user.email", "pi@example.com");
        run(work, "git", "config", "user.name", "Pi Test");
        writeGitPackageVersion(work, "Version 1");
        run(work, "git", "add", ".");
        run(work, "git", "commit", "-m", "v1");
        run(work, "git", "tag", "v1");
        writeGitPackageVersion(work, "Version 2");
        run(work, "git", "add", ".");
        run(work, "git", "commit", "-m", "v2");
        run(work, "git", "tag", "v2");
        Files.createDirectories(bare.getParent());
        run(servedRoot, "git", "clone", "--bare", work.toString(), bare.toString());
        return bare;
    }

    private static void writeGitPackageVersion(Path root, String version) throws Exception {
        Files.createDirectories(root.resolve("skills"));
        Files.writeString(root.resolve("package.json"), """
                {"name":"git-pack","pi":{"skills":["skills"]}}
                """);
        Files.writeString(root.resolve("skills").resolve("SKILL.md"), version + "\n");
    }

    private static Path gitInstalledRoot(Path agentDir, String host, String path) {
        Path root = agentDir.resolve("git").resolve(host);
        for (String part : path.split("/")) {
            if (!part.isBlank()) {
                root = root.resolve(part.replaceAll("[^A-Za-z0-9._-]", "_"));
            }
        }
        return root;
    }

    private static Path createFakeNpmCommand(Path script) throws Exception {
        Files.writeString(script, """
                #!/bin/sh
                set -eu
                command="${1:-}"
                shift || true
                name_arg="${1:-}"
                if [ $# -gt 0 ]; then
                  shift || true
                fi
                prefix=""
                previous=""
                for arg in "$@"; do
                  if [ "$previous" = "--prefix" ]; then
                    prefix="$arg"
                  fi
                  previous="$arg"
                done
                if [ "$command" = "install" ] && [ -z "$prefix" ]; then
                  mkdir -p node_modules
                  printf 'dependencies installed\\n' > node_modules/.pi-dependencies-installed
                  exit 0
                fi
                name="$name_arg"
                case "$name" in
                  @*/*@*)
                    scope="${name%%/*}"
                    rest="${name#*/}"
                    pkg="${rest%%@*}"
                    name="$scope/$pkg"
                    ;;
                  @*/*)
                    name="$name"
                    ;;
                  *@*)
                    name="${name%%@*}"
                    ;;
                esac
                if [ -z "$prefix" ]; then
                  echo "missing --prefix" >&2
                  exit 1
                fi
                package_dir="$prefix/node_modules/$name"
                if [ "$command" = "install" ]; then
                  mkdir -p "$package_dir/skills"
                  printf '{"name":"%s","pi":{"skills":["skills"]}}\\n' "$name" > "$package_dir/package.json"
                  printf 'Fake npm skill for %s\\n' "$name" > "$package_dir/skills/SKILL.md"
                elif [ "$command" = "uninstall" ]; then
                  rm -rf "$package_dir"
                else
                  echo "unsupported command: $command" >&2
                  exit 1
                fi
                """);
        script.toFile().setExecutable(true);
        return script;
    }

    private static void run(Path cwd, String... command) throws Exception {
        Process process = new ProcessBuilder(command)
                .directory(cwd.toFile())
                .redirectErrorStream(true)
                .start();
        if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("Command timed out: " + String.join(" ", command));
        }
        if (process.exitValue() != 0) {
            throw new AssertionError("Command failed: " + String.join(" ", command)
                    + "\n" + new String(process.getInputStream().readAllBytes()));
        }
    }
}
