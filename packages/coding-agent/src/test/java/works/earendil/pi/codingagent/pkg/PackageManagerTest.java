package works.earendil.pi.codingagent.pkg;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import works.earendil.pi.codingagent.config.SettingsManager;
import works.earendil.pi.codingagent.resources.PackageResourceResolver;
import works.earendil.pi.common.json.JsonCodec;

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
        JsonNode listedJson = JsonCodec.parse(PackageManager.listConfiguredPackagesJson(false, settings));

        assertThat(disabled).contains("status: updated").contains("filter: -skills/private.md");
        assertThat(enabled).contains("filter: +skills/private.md");
        JsonNode entry = settings.getGlobalSettings().path("packages").get(0);
        assertThat(entry.path("source").asText()).isEqualTo("npm:demo");
        assertThat(entry.path("skills")).extracting(JsonNode::asText).containsExactly("+skills/private.md");
        assertThat(listed)
                .contains("Configured packages (global):")
                .contains("npm:demo")
                .contains("skills: +skills/private.md");
        assertThat(listedJson.path("scope").asText()).isEqualTo("global");
        assertThat(listedJson.path("packages").get(0).path("source").asText()).isEqualTo("npm:demo");
        assertThat(listedJson.path("packages").get(0).path("skills"))
                .extracting(JsonNode::asText)
                .containsExactly("+skills/private.md");
        assertThat(listedJson.path("packages").get(0).path("themes")).isEmpty();
    }

    @Test
    void configuresTopLevelResourceFiltersInSettings() throws Exception {
        Path cwd = tempDir.resolve("project");
        Path agentDir = tempDir.resolve("agent");
        Files.createDirectories(cwd.resolve(".pi"));
        Files.createDirectories(agentDir);
        Files.writeString(agentDir.resolve("settings.json"), """
                {"skills":["-skills/demo/SKILL.md"],"themes":["themes/old.json"]}
                """);
        Files.writeString(cwd.resolve(".pi").resolve("settings.json"), """
                {"prompts":["prompts/old.md"]}
                """);
        SettingsManager settings = new SettingsManager(cwd, agentDir, true);

        String globalOutput = PackageManager.configureTopLevelResource("skill",
                "./skills/demo/SKILL.md", true, false, settings);
        String projectOutput = PackageManager.configureTopLevelResource("prompts",
                "prompts/old.md", false, true, settings);
        String listed = PackageManager.listConfiguredResources(false, settings);
        JsonNode listedJson = JsonCodec.parse(PackageManager.listConfiguredResourcesJson(false, settings));

        assertThat(globalOutput).contains("Resource config").contains("filter: +skills/demo/SKILL.md");
        assertThat(projectOutput).contains("scope: project").contains("filter: -prompts/old.md");
        assertThat(settings.getGlobalSettings().path("skills"))
                .extracting(JsonNode::asText)
                .containsExactly("+skills/demo/SKILL.md");
        assertThat(settings.getProjectSettings().path("prompts"))
                .extracting(JsonNode::asText)
                .containsExactly("-prompts/old.md");
        assertThat(listed)
                .contains("Configured resources (global):")
                .contains("skills: +skills/demo/SKILL.md")
                .contains("themes: themes/old.json");
        assertThat(listedJson.path("scope").asText()).isEqualTo("global");
        assertThat(listedJson.path("resources").path("skills"))
                .extracting(JsonNode::asText)
                .containsExactly("+skills/demo/SKILL.md");
        assertThat(listedJson.path("resources").path("themes"))
                .extracting(JsonNode::asText)
                .containsExactly("themes/old.json");
        assertThat(listedJson.path("resources").path("extensions")).isEmpty();
    }

    @Test
    void configListTopLevelJsonResolvedReportsTopLevelResourceItems() throws Exception {
        Path cwd = tempDir.resolve("project-top-level-resolved");
        Path agentDir = tempDir.resolve("agent-top-level-resolved");
        Files.createDirectories(cwd.resolve("skills"));
        Files.createDirectories(agentDir);
        Files.writeString(cwd.resolve("skills").resolve("public.md"),
                "---\nname: public\ndescription: Public skill\n---\nPublic");
        Files.writeString(cwd.resolve("skills").resolve("private.md"),
                "---\nname: private\ndescription: Private skill\n---\nPrivate");
        Files.writeString(agentDir.resolve("settings.json"), """
                {"skills":["+skills/public.md","-skills/private.md"]}
                """);
        SettingsManager settings = new SettingsManager(cwd, agentDir, true);

        JsonNode output = JsonCodec.parse(PackageManager.listConfiguredResourcesJson(false, settings,
                cwd, agentDir, true));

        assertThat(output.path("resolvedTopLevelResources").path("skills"))
                .extracting(JsonNode::asText)
                .containsExactly(cwd.resolve("skills").resolve("public.md")
                        .toAbsolutePath().normalize().toString());
        JsonNode enabledItem = output.path("resolvedTopLevelResourceItems").get(0);
        JsonNode disabledItem = output.path("resolvedTopLevelResourceItems").get(1);
        assertThat(enabledItem.path("type").asText()).isEqualTo("skills");
        assertThat(enabledItem.path("relativePath").asText()).isEqualTo("skills/public.md");
        assertThat(enabledItem.path("scope").asText()).isEqualTo("global");
        assertThat(enabledItem.path("filter").asText()).isEqualTo("+skills/public.md");
        assertThat(enabledItem.path("enabled").asBoolean()).isTrue();
        assertThat(enabledItem.path("actions").path("disable"))
                .extracting(JsonNode::asText)
                .containsExactly("config", "disable", "--top-level", "skills", "skills/public.md");
        assertThat(disabledItem.path("relativePath").asText()).isEqualTo("skills/private.md");
        assertThat(disabledItem.path("filter").asText()).isEqualTo("-skills/private.md");
        assertThat(disabledItem.path("enabled").asBoolean()).isFalse();
        assertThat(disabledItem.path("disabledReason").asText())
                .isEqualTo("disabled-by-top-level-resource-filter");
        assertThat(disabledItem.path("actions").path("enable"))
                .extracting(JsonNode::asText)
                .containsExactly("config", "enable", "--top-level", "skills", "skills/private.md");
    }

    @Test
    void configuresLocalPackageResourceFiltersWithScopedSourcePathMatching() throws Exception {
        Path cwd = tempDir.resolve("project");
        Path agentDir = tempDir.resolve("agent");
        Path globalSource = tempDir.resolve("sources").resolve("global-pack");
        Path projectSource = tempDir.resolve("sources").resolve("project-pack");
        Files.createDirectories(cwd.resolve(".pi"));
        Files.createDirectories(agentDir);
        Files.createDirectories(globalSource);
        Files.createDirectories(projectSource);
        String globalEntry = agentDir.relativize(globalSource).toString();
        String projectEntry = cwd.resolve(".pi").relativize(projectSource).toString();
        Files.writeString(agentDir.resolve("settings.json"), """
                {"packages":["%s"]}
                """.formatted(globalEntry.replace("\\", "\\\\")));
        Files.writeString(cwd.resolve(".pi").resolve("settings.json"), """
                {"packages":["%s"]}
                """.formatted(projectEntry.replace("\\", "\\\\")));
        SettingsManager settings = new SettingsManager(cwd, agentDir, true);

        String globalOutput = PackageManager.configurePackageResource(globalSource.toString(), "skills",
                "skills/global.md", true, false, cwd, agentDir, settings);
        String projectOutput = PackageManager.configurePackageResource(projectSource.toString(), "skills",
                "skills/project.md", false, true, cwd, agentDir, settings);

        JsonNode globalPackage = settings.getGlobalSettings().path("packages").get(0);
        JsonNode projectPackage = settings.getProjectSettings().path("packages").get(0);
        assertThat(globalOutput).contains("status: updated").contains("source: " + globalEntry);
        assertThat(projectOutput).contains("status: updated").contains("source: " + projectEntry);
        assertThat(globalPackage.path("source").asText()).isEqualTo(globalEntry);
        assertThat(globalPackage.path("skills")).extracting(JsonNode::asText).containsExactly("+skills/global.md");
        assertThat(projectPackage.path("source").asText()).isEqualTo(projectEntry);
        assertThat(projectPackage.path("skills")).extracting(JsonNode::asText).containsExactly("-skills/project.md");
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
    void packageManagerCliConfigTopLevelUpdatesGlobalSettings() throws Exception {
        String originalUserHome = System.getProperty("user.home");
        java.io.PrintStream originalOut = System.out;
        Path home = tempDir.resolve("home-top-level");
        Path agentDir = home.resolve(".pi").resolve("agent");
        Files.createDirectories(agentDir);
        java.io.ByteArrayOutputStream stdout = new java.io.ByteArrayOutputStream();

        try {
            System.setProperty("user.home", home.toString());
            System.setOut(new java.io.PrintStream(stdout));

            int exit = PackageManagerCli.handleCommand("config",
                    new String[]{"disable", "--top-level", "themes", "themes/private.json"});

            SettingsManager settings = new SettingsManager(Path.of(".").toAbsolutePath().normalize(), agentDir, true);
            assertThat(exit).isZero();
            assertThat(stdout.toString()).contains("Resource config").contains("filter: -themes/private.json");
            assertThat(settings.getGlobalSettings().path("themes"))
                    .extracting(JsonNode::asText)
                    .containsExactly("-themes/private.json");
        } finally {
            System.setOut(originalOut);
            System.setProperty("user.home", originalUserHome);
        }
    }

    @Test
    void packageManagerCliConfigListJsonPrintsStructuredSnapshot() throws Exception {
        String originalUserHome = System.getProperty("user.home");
        java.io.PrintStream originalOut = System.out;
        Path home = tempDir.resolve("home-config-json");
        Path agentDir = home.resolve(".pi").resolve("agent");
        Files.createDirectories(agentDir);
        Files.writeString(agentDir.resolve("settings.json"), """
                {"packages":[{"source":"npm:demo","skills":["+skills/a.md"]}],"themes":["-themes/private.json"]}
                """);
        java.io.ByteArrayOutputStream stdout = new java.io.ByteArrayOutputStream();

        try {
            System.setProperty("user.home", home.toString());
            System.setOut(new java.io.PrintStream(stdout));

            int packagesExit = PackageManagerCli.handleCommand("config",
                    new String[]{"list", "--json"});
            JsonNode packagesOutput = JsonCodec.parse(stdout.toString());
            stdout.reset();
            int resourcesExit = PackageManagerCli.handleCommand("config",
                    new String[]{"list", "--top-level", "--json"});
            JsonNode resourcesOutput = JsonCodec.parse(stdout.toString());

            assertThat(packagesExit).isZero();
            assertThat(resourcesExit).isZero();
            assertThat(packagesOutput.path("scope").asText()).isEqualTo("global");
            assertThat(packagesOutput.path("packages").get(0).path("source").asText()).isEqualTo("npm:demo");
            assertThat(packagesOutput.path("packages").get(0).path("skills"))
                    .extracting(JsonNode::asText)
                    .containsExactly("+skills/a.md");
            assertThat(resourcesOutput.path("scope").asText()).isEqualTo("global");
            assertThat(resourcesOutput.path("resources").path("themes"))
                    .extracting(JsonNode::asText)
                    .containsExactly("-themes/private.json");
        } finally {
            System.setOut(originalOut);
            System.setProperty("user.home", originalUserHome);
        }
    }

    @Test
    void packageManagerCliConfigListJsonResolvedPrintsDiscoveredPackageResources() throws Exception {
        String originalUserHome = System.getProperty("user.home");
        java.io.PrintStream originalOut = System.out;
        Path home = tempDir.resolve("home-config-json-resolved");
        Path agentDir = home.resolve(".pi").resolve("agent");
        Path pkg = agentDir.resolve("packages").resolve("demo-pack");
        Files.createDirectories(pkg.resolve("skills"));
        Files.writeString(agentDir.resolve("settings.json"), """
                {"packages":[{"source":"demo-pack","skills":["skills/public.md"]}]}
                """);
        Files.writeString(pkg.resolve("package.json"), """
                {
                  "name": "demo-pack",
                  "pi": {
                    "skills": ["skills/*.md"]
                  }
                }
                """);
        Files.writeString(pkg.resolve("skills").resolve("public.md"),
                "---\nname: public\ndescription: Public skill\n---\nPublic");
        Files.writeString(pkg.resolve("skills").resolve("private.md"),
                "---\nname: private\ndescription: Private skill\n---\nPrivate");
        java.io.ByteArrayOutputStream stdout = new java.io.ByteArrayOutputStream();

        try {
            System.setProperty("user.home", home.toString());
            System.setOut(new java.io.PrintStream(stdout));

            int exit = PackageManagerCli.handleCommand("config",
                    new String[]{"list", "--json", "--resolved"});
            JsonNode output = JsonCodec.parse(stdout.toString());

            assertThat(exit).isZero();
            assertThat(output.path("resolvedScope").asText()).isEqualTo("global");
            assertThat(output.path("resolvedResources").path("skills"))
                    .extracting(JsonNode::asText)
                    .containsExactly(pkg.resolve("skills").resolve("public.md").toAbsolutePath().normalize().toString());
            JsonNode item = output.path("resolvedResourceItems").get(0);
            JsonNode disabledItem = output.path("resolvedResourceItems").get(1);
            assertThat(item.path("type").asText()).isEqualTo("skills");
            assertThat(item.path("relativePath").asText()).isEqualTo("skills/public.md");
            assertThat(item.path("scope").asText()).isEqualTo("global");
            assertThat(item.path("packageRoot").asText()).isEqualTo(pkg.toAbsolutePath().normalize().toString());
            assertThat(item.path("packageName").asText()).isEqualTo("demo-pack");
            assertThat(item.path("source").asText()).isEqualTo("demo-pack");
            assertThat(item.path("identity").asText()).startsWith("local:");
            assertThat(item.path("enabled").asBoolean()).isTrue();
            assertThat(item.path("actions").path("disable"))
                    .extracting(JsonNode::asText)
                    .containsExactly("config", "disable", "demo-pack", "skills", "skills/public.md");
            assertThat(item.path("actions").path("enable"))
                    .extracting(JsonNode::asText)
                    .containsExactly("config", "enable", "demo-pack", "skills", "skills/public.md");
            assertThat(disabledItem.path("type").asText()).isEqualTo("skills");
            assertThat(disabledItem.path("relativePath").asText()).isEqualTo("skills/private.md");
            assertThat(disabledItem.path("enabled").asBoolean()).isFalse();
            assertThat(disabledItem.path("actions").path("enable"))
                    .extracting(JsonNode::asText)
                    .containsExactly("config", "enable", "demo-pack", "skills", "skills/private.md");
            assertThat(output.path("resolvedResources").path("prompts")).isEmpty();
        } finally {
            System.setOut(originalOut);
            System.setProperty("user.home", originalUserHome);
        }
    }

    @Test
    void configListJsonResolvedReportsShadowedPackageResources() throws Exception {
        Path project = tempDir.resolve("project-shadowed");
        Path agentDir = tempDir.resolve("agent-shadowed");
        Path projectPkg = project.resolve(".pi").resolve("packages").resolve("demo-pack");
        Path globalPkg = agentDir.resolve("packages").resolve("demo-pack");
        Files.createDirectories(projectPkg.resolve("skills"));
        Files.createDirectories(globalPkg.resolve("skills"));
        Files.writeString(projectPkg.resolve("package.json"), """
                {
                  "name": "demo-pack",
                  "pi": {
                    "skills": ["skills/project.md"]
                  }
                }
                """);
        Files.writeString(globalPkg.resolve("package.json"), """
                {
                  "name": "demo-pack",
                  "pi": {
                    "skills": ["skills/global.md"]
                  }
                }
                """);
        Files.writeString(projectPkg.resolve("skills").resolve("project.md"),
                "---\nname: project\ndescription: Project skill\n---\nProject");
        Files.writeString(globalPkg.resolve("skills").resolve("global.md"),
                "---\nname: global\ndescription: Global skill\n---\nGlobal");
        SettingsManager settings = new SettingsManager(project, agentDir, true);

        JsonNode output = JsonCodec.parse(PackageManager.listConfiguredPackagesJson(true, settings,
                project, agentDir, true));

        assertThat(output.path("resolvedResources").path("skills"))
                .extracting(JsonNode::asText)
                .containsExactly(projectPkg.resolve("skills").resolve("project.md")
                        .toAbsolutePath().normalize().toString());
        JsonNode projectItem = output.path("resolvedResourceItems").get(0);
        JsonNode shadowedItem = output.path("resolvedResourceItems").get(1);
        assertThat(projectItem.path("enabled").asBoolean()).isTrue();
        assertThat(projectItem.path("scope").asText()).isEqualTo("project");
        assertThat(projectItem.path("relativePath").asText()).isEqualTo("skills/project.md");
        assertThat(shadowedItem.path("enabled").asBoolean()).isFalse();
        assertThat(shadowedItem.path("scope").asText()).isEqualTo("global");
        assertThat(shadowedItem.path("relativePath").asText()).isEqualTo("skills/global.md");
        assertThat(shadowedItem.path("disabledReason").asText()).isEqualTo("shadowed-by-prior-package");
        assertThat(shadowedItem.path("overriddenByIdentity").asText()).isEqualTo("name:demo-pack");
        assertThat(shadowedItem.path("overriddenByScope").asText()).isEqualTo("project");
        assertThat(shadowedItem.path("overriddenByPackageRoot").asText())
                .isEqualTo(projectPkg.toAbsolutePath().normalize().toString());
        assertThat(shadowedItem.path("overriddenByPackageName").asText()).isEqualTo("demo-pack");
        assertThat(shadowedItem.path("overriddenBySource").asText()).isEmpty();
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
            assertThat(extensionsOutput)
                    .contains("Installed npm package @scope/review-pack")
                    .contains("Package update summary: updated 1, skipped 0.");
            assertThat(installedRoot.resolve("package.json")).exists();
        } finally {
            System.setOut(originalOut);
            System.setProperty("user.home", originalUserHome);
        }
    }

    @Test
    void packageManagerCliUpdateSelfRunsConfiguredNpmSelfUpdate() throws Exception {
        String originalUserHome = System.getProperty("user.home");
        java.io.PrintStream originalOut = System.out;
        Path home = tempDir.resolve("home-self-update");
        Path agentDir = home.resolve(".pi").resolve("agent");
        Files.createDirectories(agentDir);
        Path fakeNpm = createFakeSelfUpdateNpmCommand(tempDir.resolve("fake-npm-self-update"));
        Files.writeString(agentDir.resolve("settings.json"), """
                {
                  "npmCommand":["%s"],
                  "selfUpdatePackage":"@earendil-works/pi-coding-agent@9.9.9",
                  "selfUpdatePackageName":"@earendil-works/pi-java-old"
                }
                """.formatted(fakeNpm.toString().replace("\\", "\\\\")));
        java.io.ByteArrayOutputStream stdout = new java.io.ByteArrayOutputStream();

        try {
            System.setProperty("user.home", home.toString());
            System.setOut(new java.io.PrintStream(stdout));

            int exit = PackageManagerCli.handleCommand("update", new String[]{});

            String log = Files.readString(Path.of(fakeNpm + ".log"));
            assertThat(exit).isZero();
            assertThat(stdout.toString())
                    .contains("Self-update installed @earendil-works/pi-coding-agent@9.9.9")
                    .contains("Removed previous self-update package @earendil-works/pi-java-old");
            assertThat(log)
                    .contains("install -g --ignore-scripts --min-release-age=0 @earendil-works/pi-coding-agent@9.9.9")
                    .contains("uninstall -g @earendil-works/pi-java-old");
        } finally {
            System.setOut(originalOut);
            System.setProperty("user.home", originalUserHome);
        }
    }

    @Test
    void packageManagerCliUpdateSelfSkipsWhenConfiguredCurrentVersionIsLatest() throws Exception {
        String originalUserHome = System.getProperty("user.home");
        java.io.PrintStream originalOut = System.out;
        Path home = tempDir.resolve("home-self-update-current");
        Path agentDir = home.resolve(".pi").resolve("agent");
        Files.createDirectories(agentDir);
        Path fakeNpm = createFakeSelfUpdateNpmCommand(tempDir.resolve("fake-npm-self-update-current"));
        Files.writeString(agentDir.resolve("settings.json"), """
                {
                  "npmCommand":["%s"],
                  "selfUpdatePackage":"@earendil-works/pi-coding-agent@^9.0.0",
                  "selfUpdateCurrentVersion":"9.9.9"
                }
                """.formatted(fakeNpm.toString().replace("\\", "\\\\")));
        java.io.ByteArrayOutputStream stdout = new java.io.ByteArrayOutputStream();

        try {
            System.setProperty("user.home", home.toString());
            System.setOut(new java.io.PrintStream(stdout));

            int exit = PackageManagerCli.handleCommand("update", new String[]{});

            String log = Files.readString(Path.of(fakeNpm + ".log"));
            assertThat(exit).isZero();
            assertThat(stdout.toString())
                    .contains("Skipped self-update @earendil-works/pi-coding-agent already at 9.9.9")
                    .contains("Packages are skipped");
            assertThat(log)
                    .contains("view @earendil-works/pi-coding-agent@^9.0.0 version --json")
                    .doesNotContain("install -g");
        } finally {
            System.setOut(originalOut);
            System.setProperty("user.home", originalUserHome);
        }
    }

    @Test
    void packageManagerCliUpdateSelfForceReinstallsWhenCurrentVersionIsLatest() throws Exception {
        String originalUserHome = System.getProperty("user.home");
        java.io.PrintStream originalOut = System.out;
        Path home = tempDir.resolve("home-self-update-force-current");
        Path agentDir = home.resolve(".pi").resolve("agent");
        Files.createDirectories(agentDir);
        Path fakeNpm = createFakeSelfUpdateNpmCommand(tempDir.resolve("fake-npm-self-update-force-current"));
        Files.writeString(agentDir.resolve("settings.json"), """
                {
                  "npmCommand":["%s"],
                  "selfUpdatePackage":"@earendil-works/pi-coding-agent@^9.0.0",
                  "selfUpdateCurrentVersion":"9.9.9"
                }
                """.formatted(fakeNpm.toString().replace("\\", "\\\\")));
        java.io.ByteArrayOutputStream stdout = new java.io.ByteArrayOutputStream();

        try {
            System.setProperty("user.home", home.toString());
            System.setOut(new java.io.PrintStream(stdout));

            int exit = PackageManagerCli.handleCommand("update", new String[]{"--force"});

            String log = Files.readString(Path.of(fakeNpm + ".log"));
            assertThat(exit).isZero();
            assertThat(stdout.toString())
                    .contains("Self-update installed @earendil-works/pi-coding-agent@9.9.9")
                    .doesNotContain("Skipped self-update");
            assertThat(log)
                    .contains("view @earendil-works/pi-coding-agent@^9.0.0 version --json")
                    .contains("install -g --ignore-scripts --min-release-age=0 "
                            + "@earendil-works/pi-coding-agent@9.9.9");
        } finally {
            System.setOut(originalOut);
            System.setProperty("user.home", originalUserHome);
        }
    }

    @Test
    void packageManagerCliUpdateSelfPinsRangeToRegistryVersion() throws Exception {
        String originalUserHome = System.getProperty("user.home");
        java.io.PrintStream originalOut = System.out;
        Path home = tempDir.resolve("home-self-update-range");
        Path agentDir = home.resolve(".pi").resolve("agent");
        Files.createDirectories(agentDir);
        Path fakeNpm = createFakeSelfUpdateNpmCommand(tempDir.resolve("fake-npm-self-update-range"));
        Files.writeString(agentDir.resolve("settings.json"), """
                {
                  "npmCommand":["%s"],
                  "selfUpdatePackage":"@earendil-works/pi-coding-agent@^9.0.0"
                }
                """.formatted(fakeNpm.toString().replace("\\", "\\\\")));
        java.io.ByteArrayOutputStream stdout = new java.io.ByteArrayOutputStream();

        try {
            System.setProperty("user.home", home.toString());
            System.setOut(new java.io.PrintStream(stdout));

            int exit = PackageManagerCli.handleCommand("update", new String[]{});

            String log = Files.readString(Path.of(fakeNpm + ".log"));
            assertThat(exit).isZero();
            assertThat(stdout.toString())
                    .contains("Self-update installed @earendil-works/pi-coding-agent@9.9.9");
            assertThat(log)
                    .contains("view @earendil-works/pi-coding-agent@^9.0.0 version --json")
                    .contains("install -g --ignore-scripts --min-release-age=0 @earendil-works/pi-coding-agent@9.9.9");
        } finally {
            System.setOut(originalOut);
            System.setProperty("user.home", originalUserHome);
        }
    }

    @Test
    void packageManagerCliUpdateSelfFailurePrintsFallbackCommand() throws Exception {
        String originalUserHome = System.getProperty("user.home");
        java.io.PrintStream originalErr = System.err;
        Path home = tempDir.resolve("home-self-update-fail");
        Path agentDir = home.resolve(".pi").resolve("agent");
        Files.createDirectories(agentDir);
        Path fakeNpm = createFailingSelfUpdateNpmCommand(tempDir.resolve("fake-npm-self-update-fail"));
        Files.writeString(agentDir.resolve("settings.json"), """
                {
                  "npmCommand":["%s"],
                  "selfUpdatePackage":"@earendil-works/pi-coding-agent@9.9.9"
                }
                """.formatted(fakeNpm.toString().replace("\\", "\\\\")));
        java.io.ByteArrayOutputStream stderr = new java.io.ByteArrayOutputStream();

        try {
            System.setProperty("user.home", home.toString());
            System.setErr(new java.io.PrintStream(stderr));

            int exit = PackageManagerCli.handleCommand("update", new String[]{});

            assertThat(exit).isEqualTo(1);
            assertThat(stderr.toString())
                    .contains("Self-update command failed")
                    .contains("If this keeps failing, run this command yourself: " + fakeNpm
                            + " install -g --ignore-scripts --min-release-age=0 "
                            + "@earendil-works/pi-coding-agent@9.9.9");
        } finally {
            System.setErr(originalErr);
            System.setProperty("user.home", originalUserHome);
        }
    }

    @Test
    void packageManagerCliUpdateSelfCleanupFailurePrintsFallbackCommand() throws Exception {
        String originalUserHome = System.getProperty("user.home");
        java.io.PrintStream originalErr = System.err;
        Path home = tempDir.resolve("home-self-update-cleanup-fail");
        Path agentDir = home.resolve(".pi").resolve("agent");
        Files.createDirectories(agentDir);
        Path fakeNpm = createCleanupFailingSelfUpdateNpmCommand(tempDir.resolve("fake-npm-self-update-cleanup-fail"));
        Files.writeString(agentDir.resolve("settings.json"), """
                {
                  "npmCommand":["%s"],
                  "selfUpdatePackage":"@earendil-works/pi-coding-agent@9.9.9",
                  "selfUpdatePackageName":"@earendil-works/pi-java-old"
                }
                """.formatted(fakeNpm.toString().replace("\\", "\\\\")));
        java.io.ByteArrayOutputStream stderr = new java.io.ByteArrayOutputStream();

        try {
            System.setProperty("user.home", home.toString());
            System.setErr(new java.io.PrintStream(stderr));

            int exit = PackageManagerCli.handleCommand("update", new String[]{});

            assertThat(exit).isEqualTo(1);
            assertThat(stderr.toString())
                    .contains("Self-update installed @earendil-works/pi-coding-agent@9.9.9 "
                            + "but cleanup command failed")
                    .contains("If this keeps failing, run this command yourself: " + fakeNpm
                            + " uninstall -g @earendil-works/pi-java-old");
        } finally {
            System.setErr(originalErr);
            System.setProperty("user.home", originalUserHome);
        }
    }

    @Test
    void packageManagerCliUpdateAllSkipsSelfAndPackagesInOfflineMode() throws Exception {
        String originalUserHome = System.getProperty("user.home");
        java.io.PrintStream originalOut = System.out;
        Path home = tempDir.resolve("home-update-offline");
        Path agentDir = home.resolve(".pi").resolve("agent");
        Files.createDirectories(agentDir);
        Path fakeNpm = createFakeSelfUpdateNpmCommand(tempDir.resolve("fake-npm-offline-update"));
        Files.writeString(agentDir.resolve("settings.json"), """
                {
                  "npmCommand":["%s"],
                  "selfUpdatePackage":"@earendil-works/pi-coding-agent@9.9.9",
                  "packages":["npm:@scope/review-pack"]
                }
                """.formatted(fakeNpm.toString().replace("\\", "\\\\")));
        java.io.ByteArrayOutputStream stdout = new java.io.ByteArrayOutputStream();

        try {
            PackageManager.setOfflineModeOverrideForTests(true);
            System.setProperty("user.home", home.toString());
            System.setOut(new java.io.PrintStream(stdout));

            int exit = PackageManagerCli.handleCommand("update", new String[]{"--all"});

            assertThat(exit).isZero();
            assertThat(stdout.toString())
                    .contains("Offline mode enabled; skipped self-update.")
                    .contains("Offline mode enabled; skipped package update.");
            assertThat(Path.of(fakeNpm + ".log")).doesNotExist();
            assertThat(agentDir.resolve("npm").resolve("node_modules")
                    .resolve("@scope").resolve("review-pack")).doesNotExist();
        } finally {
            PackageManager.setOfflineModeOverrideForTests(null);
            System.setOut(originalOut);
            System.setProperty("user.home", originalUserHome);
        }
    }

    @Test
    void packageUpdateHonorsPiOfflineSystemProperty() throws Exception {
        String originalOffline = System.getProperty("PI_OFFLINE");
        Path cwd = tempDir.resolve("project-offline-property");
        Path agentDir = tempDir.resolve("agent-offline-property");
        Files.createDirectories(cwd);
        Files.createDirectories(agentDir);
        Path fakeNpm = createFakeSelfUpdateNpmCommand(tempDir.resolve("fake-npm-offline-property"));
        Files.writeString(agentDir.resolve("settings.json"), """
                {
                  "npmCommand":["%s"],
                  "packages":["npm:@scope/review-pack"]
                }
                """.formatted(fakeNpm.toString().replace("\\", "\\\\")));
        SettingsManager settings = new SettingsManager(cwd, agentDir, true);

        try {
            System.setProperty("PI_OFFLINE", "yes");

            String output = PackageManager.update("all", false, cwd, agentDir, settings);

            assertThat(output).contains("Offline mode enabled; skipped package update.");
            assertThat(Path.of(fakeNpm + ".log")).doesNotExist();
        } finally {
            if (originalOffline == null) {
                System.clearProperty("PI_OFFLINE");
            } else {
                System.setProperty("PI_OFFLINE", originalOffline);
            }
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
                    .contains("Skipped npm package npm:@scope/review-pack already at 2.0.0")
                    .contains("Installed npm package @scope/other-pack")
                    .contains("Package update summary: updated 1, skipped 1.");
            assertThat(conflictExit).isEqualTo(1);
            assertThat(stderr.toString()).contains("--all cannot be combined");
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
            System.setProperty("user.home", originalUserHome);
        }
    }

    @Test
    void packageManagerCliHelpAndInvalidArgsFollowTsShape() {
        java.io.PrintStream originalOut = System.out;
        java.io.PrintStream originalErr = System.err;
        java.io.ByteArrayOutputStream stdout = new java.io.ByteArrayOutputStream();
        java.io.ByteArrayOutputStream stderr = new java.io.ByteArrayOutputStream();

        try {
            System.setOut(new java.io.PrintStream(stdout));
            System.setErr(new java.io.PrintStream(stderr));

            int helpExit = PackageManagerCli.handleCommand("update", new String[]{"--help"});
            String helpOutput = stdout.toString();
            stdout.reset();
            int unknownExit = PackageManagerCli.handleCommand("update", new String[]{"--bogus"});
            String unknownError = stderr.toString();
            stderr.reset();
            int extraArgExit = PackageManagerCli.handleCommand("install", new String[]{"npm:@scope/a", "extra"});
            String extraArgError = stderr.toString();
            stderr.reset();
            int duplicateExtensionExit = PackageManagerCli.handleCommand("update",
                    new String[]{"--extension", "npm:@scope/a", "--extension", "npm:@scope/b"});
            String duplicateExtensionError = stderr.toString();

            assertThat(helpExit).isZero();
            assertThat(helpOutput)
                    .contains("Usage: pi update")
                    .contains("--force")
                    .contains("--approve|--no-approve");
            assertThat(unknownExit).isEqualTo(1);
            assertThat(unknownError)
                    .contains("Unknown option --bogus")
                    .contains("Usage: pi update");
            assertThat(extraArgExit).isEqualTo(1);
            assertThat(extraArgError)
                    .contains("Unexpected argument extra")
                    .contains("Usage: pi install");
            assertThat(duplicateExtensionExit).isEqualTo(1);
            assertThat(duplicateExtensionError)
                    .contains("--extension can only be provided once")
                    .contains("Usage: pi update");
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    @Test
    void packageManagerCliApproveTrustsProjectPackagesWithoutChangingGlobalScope() throws Exception {
        String originalUserHome = System.getProperty("user.home");
        String originalUserDir = System.getProperty("user.dir");
        java.io.PrintStream originalOut = System.out;
        Path cwd = tempDir.resolve("cli-approve-project");
        Path home = tempDir.resolve("home-cli-approve");
        Path agentDir = home.resolve(".pi").resolve("agent");
        Files.createDirectories(cwd.resolve(".pi"));
        Files.createDirectories(agentDir);
        Path fakeNpm = createFakeNpmCommand(tempDir.resolve("fake-npm-cli-approve"));
        Files.writeString(agentDir.resolve("settings.json"), """
                {
                  "npmCommand":["%s"],
                  "packages":["npm:@scope/global-pack"]
                }
                """.formatted(fakeNpm.toString().replace("\\", "\\\\")));
        Files.writeString(cwd.resolve(".pi").resolve("settings.json"), """
                {"packages":["npm:@scope/project-pack"]}
                """);
        java.io.ByteArrayOutputStream stdout = new java.io.ByteArrayOutputStream();

        try {
            System.setProperty("user.home", home.toString());
            System.setProperty("user.dir", cwd.toString());
            System.setOut(new java.io.PrintStream(stdout));

            int exit = PackageManagerCli.handleCommand("update", new String[]{"--extensions", "--approve"});

            assertThat(exit).isZero();
            assertThat(stdout.toString())
                    .contains("Installed npm package @scope/global-pack")
                    .contains("Installed npm package @scope/project-pack")
                    .contains("Package update summary: updated 2, skipped 0.");
            assertThat(agentDir.resolve("npm").resolve("node_modules")
                    .resolve("@scope").resolve("global-pack").resolve("package.json")).exists();
            assertThat(cwd.resolve(".pi").resolve("npm").resolve("node_modules")
                    .resolve("@scope").resolve("project-pack").resolve("package.json")).exists();
            assertThat(agentDir.resolve("npm").resolve("node_modules")
                    .resolve("@scope").resolve("project-pack")).doesNotExist();
        } finally {
            System.setOut(originalOut);
            System.setProperty("user.home", originalUserHome);
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void packageManagerCliListUsesApproveFlagsForProjectPackages() throws Exception {
        String originalUserHome = System.getProperty("user.home");
        String originalUserDir = System.getProperty("user.dir");
        java.io.PrintStream originalOut = System.out;
        Path cwd = tempDir.resolve("cli-list-project-trust");
        Path home = tempDir.resolve("home-cli-list");
        Path agentDir = home.resolve(".pi").resolve("agent");
        Files.createDirectories(cwd.resolve(".pi"));
        Files.createDirectories(agentDir);
        Path fakeNpm = createFakeNpmCommand(tempDir.resolve("fake-npm-cli-list"));
        Files.writeString(agentDir.resolve("settings.json"), """
                {
                  "npmCommand":["%s"],
                  "packages":[{"source":"npm:@scope/global-pack","skills":["skills"]}]
                }
                """.formatted(fakeNpm.toString().replace("\\", "\\\\")));
        Files.writeString(cwd.resolve(".pi").resolve("settings.json"), """
                {"packages":["npm:@scope/project-pack"]}
                """);
        SettingsManager setupSettings = new SettingsManager(cwd, agentDir, true);
        PackageManager.update("all", false, cwd, agentDir, setupSettings);
        java.io.ByteArrayOutputStream stdout = new java.io.ByteArrayOutputStream();

        try {
            System.setProperty("user.home", home.toString());
            System.setProperty("user.dir", cwd.toString());
            System.setOut(new java.io.PrintStream(stdout));

            int defaultExit = PackageManagerCli.handleCommand("list", new String[]{});
            String defaultOutput = stdout.toString();
            stdout.reset();
            int approvedExit = PackageManagerCli.handleCommand("list", new String[]{"--approve"});
            String approvedOutput = stdout.toString();
            stdout.reset();
            int deniedExit = PackageManagerCli.handleCommand("list", new String[]{"--no-approve"});
            String deniedOutput = stdout.toString();

            assertThat(defaultExit).isZero();
            assertThat(defaultOutput)
                    .contains("Configured packages (global):")
                    .contains("npm:@scope/global-pack (filtered)")
                    .contains(agentDir.resolve("npm").resolve("node_modules")
                            .resolve("@scope").resolve("global-pack").toString())
                    .contains("skills: skills")
                    .doesNotContain("npm:@scope/project-pack");
            assertThat(approvedExit).isZero();
            assertThat(approvedOutput)
                    .contains("Configured packages (global):")
                    .contains("npm:@scope/global-pack")
                    .contains(agentDir.resolve("npm").resolve("node_modules")
                            .resolve("@scope").resolve("global-pack").toString())
                    .contains("Configured packages (project):")
                    .contains("npm:@scope/project-pack")
                    .contains(cwd.resolve(".pi").resolve("npm").resolve("node_modules")
                            .resolve("@scope").resolve("project-pack").toString());
            assertThat(deniedExit).isZero();
            assertThat(deniedOutput)
                    .contains("Configured packages (global):")
                    .contains("npm:@scope/global-pack")
                    .doesNotContain("npm:@scope/project-pack");
        } finally {
            System.setOut(originalOut);
            System.setProperty("user.home", originalUserHome);
            System.setProperty("user.dir", originalUserDir);
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
                .contains("Skipped pinned npm package npm:@scope/pinned-pack@1.0.0")
                .contains("Package update summary: updated 1, skipped 1.");
        assertThat(reviewRoot.resolve("package.json")).exists();
        assertThat(pinnedRoot).doesNotExist();
        assertThat(packageSources(settings.getGlobalSettings()))
                .containsExactly("npm:@scope/review-pack", "npm:@scope/pinned-pack@1.0.0");
    }

    @Test
    void updateAllUsesGlobalAndTrustedProjectPackageScopes() throws Exception {
        Path cwd = tempDir.resolve("project-effective-update");
        Path agentDir = tempDir.resolve("agent-effective-update");
        Files.createDirectories(cwd.resolve(".pi"));
        Files.createDirectories(agentDir);
        Path fakeNpm = createFakeNpmCommand(tempDir.resolve("fake-npm-effective-update"));
        Files.writeString(agentDir.resolve("settings.json"), """
                {
                  "npmCommand":["%s"],
                  "packages":["npm:@scope/global-pack"]
                }
                """.formatted(fakeNpm.toString().replace("\\", "\\\\")));
        Files.writeString(cwd.resolve(".pi").resolve("settings.json"), """
                {"packages":["npm:@scope/project-pack"]}
                """);
        SettingsManager settings = new SettingsManager(cwd, agentDir, true);

        String output = PackageManager.update("all", false, cwd, agentDir, settings);

        assertThat(output)
                .contains("Updating package npm:@scope/global-pack (1/2)...")
                .contains("Updating package npm:@scope/project-pack (2/2)...")
                .contains("Installed npm package @scope/global-pack")
                .contains("Installed npm package @scope/project-pack")
                .contains("Package update summary: updated 2, skipped 0.");
        assertThat(agentDir.resolve("npm").resolve("node_modules")
                .resolve("@scope").resolve("global-pack").resolve("package.json")).exists();
        assertThat(cwd.resolve(".pi").resolve("npm").resolve("node_modules")
                .resolve("@scope").resolve("project-pack").resolve("package.json")).exists();
        assertThat(agentDir.resolve("npm").resolve("node_modules")
                .resolve("@scope").resolve("project-pack")).doesNotExist();
    }

    @Test
    void packageUpdateContinuesAfterPackageFailureAndReportsSummary() throws Exception {
        Path cwd = tempDir.resolve("project-failed-update");
        Path agentDir = tempDir.resolve("agent-failed-update");
        Files.createDirectories(cwd);
        Files.createDirectories(agentDir);
        Path fakeNpm = createFailingNpmCommand(tempDir.resolve("fake-npm-failed-update"));
        Files.writeString(agentDir.resolve("settings.json"), """
                {
                  "npmCommand":["%s"],
                  "packages":["npm:@scope/review-pack","npm:@scope/failing-pack","npm:@scope/pinned-pack@1.0.0"]
                }
                """.formatted(fakeNpm.toString().replace("\\", "\\\\")));
        SettingsManager settings = new SettingsManager(cwd, agentDir, true);

        String output = PackageManager.update("all", false, cwd, agentDir, settings);

        assertThat(output)
                .contains("Updating package npm:@scope/review-pack (1/3)...")
                .contains("Updating package npm:@scope/failing-pack (2/3)...")
                .contains("Updating package npm:@scope/pinned-pack@1.0.0 (3/3)...")
                .contains("Installed npm package @scope/review-pack")
                .contains("Failed package npm:@scope/failing-pack:")
                .contains("Skipped pinned npm package npm:@scope/pinned-pack@1.0.0")
                .contains("Package update summary: updated 1, skipped 1, failed 1.");
        assertThat(agentDir.resolve("npm").resolve("node_modules")
                .resolve("@scope").resolve("review-pack").resolve("package.json")).exists();
        assertThat(agentDir.resolve("npm").resolve("node_modules")
                .resolve("@scope").resolve("failing-pack")).doesNotExist();
    }

    @Test
    void updatesNpmRangeToLatestRegistryVersionAndSkipsWhenCurrent() throws Exception {
        Path cwd = tempDir.resolve("project");
        Path agentDir = tempDir.resolve("agent");
        Files.createDirectories(cwd);
        Files.createDirectories(agentDir);
        Path fakeNpm = createFakeNpmCommand(tempDir.resolve("fake-npm-range-update"));
        Files.writeString(agentDir.resolve("settings.json"), """
                {
                  "npmCommand":["%s"],
                  "packages":["npm:@scope/review-pack@^1.0.0"]
                }
                """.formatted(fakeNpm.toString().replace("\\", "\\\\")));
        SettingsManager settings = new SettingsManager(cwd, agentDir, true);

        String updated = PackageManager.update("all", false, cwd, agentDir, settings);
        String skipped = PackageManager.update("all", false, cwd, agentDir, settings);

        Path reviewRoot = agentDir.resolve("npm").resolve("node_modules").resolve("@scope").resolve("review-pack");
        JsonNode packageJson = JsonCodec.parse(Files.readString(reviewRoot.resolve("package.json")));
        assertThat(updated).contains("Installed npm package @scope/review-pack@1.5.0");
        assertThat(packageJson.path("version").asText()).isEqualTo("1.5.0");
        assertThat(skipped)
                .contains("Skipped npm package npm:@scope/review-pack@^1.0.0 already at 1.5.0")
                .contains("Package update summary: updated 0, skipped 1.");
        assertThat(packageSources(settings.getGlobalSettings())).containsExactly("npm:@scope/review-pack@^1.0.0");
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
        assertThat(updated)
                .contains("Updated git package localhost/")
                .contains("@v2")
                .contains("Package update summary: updated 1, skipped 0.");
        assertThat(Files.readString(installedRoot.resolve("skills").resolve("SKILL.md"))).contains("Version 2");
        assertThat(installedRoot.resolve("node_modules").resolve(".pi-dependencies-installed")).exists();
        assertThat(packageSources(settings.getGlobalSettings())).containsExactly(sourceV2);
    }

    @Test
    void skipsConfiguredGitPackageWhenRemoteHeadMatchesLocalHead() throws Exception {
        Path cwd = tempDir.resolve("project");
        Path agentDir = tempDir.resolve("agent");
        Path servedRoot = tempDir.resolve("served-git-skip");
        Files.createDirectories(cwd);
        Files.createDirectories(agentDir);
        Path bareRepo = createServedGitPackage(servedRoot);
        Path fakeNpm = createFakeNpmCommand(tempDir.resolve("fake-npm-git-skip"));
        Files.writeString(agentDir.resolve("settings.json"), """
                {"npmCommand":["%s"]}
                """.formatted(fakeNpm.toString().replace("\\", "\\\\")));
        String repoUrl = "file://localhost" + bareRepo.toAbsolutePath().normalize().toString().replace('\\', '/');
        SettingsManager settings = new SettingsManager(cwd, agentDir, true);

        PackageManager.installAndPersist(repoUrl, false, cwd, agentDir, settings);

        Path installedRoot = gitInstalledRoot(agentDir, "localhost",
                bareRepo.toAbsolutePath().normalize().toString().replaceFirst("^/+", "")
                        .replaceAll("\\.git$", ""));
        Path dependencyMarker = installedRoot.resolve("node_modules").resolve(".pi-dependencies-installed");
        assertThat(dependencyMarker).exists();
        Files.delete(dependencyMarker);

        String skipped = PackageManager.update("all", false, cwd, agentDir, settings);

        assertThat(skipped)
                .contains("Skipped git package localhost/")
                .contains("already at")
                .contains("Package update summary: updated 0, skipped 1.");
        assertThat(dependencyMarker).doesNotExist();
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
                if [ "$command" = "view" ]; then
                  printf '["1.0.0","1.5.0","2.0.0"]\\n'
                  exit 0
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
                version="0.0.0"
                case "$name" in
                  @*/*@*)
                    scope="${name%%/*}"
                    rest="${name#*/}"
                    pkg="${rest%%@*}"
                    version="${rest#*@}"
                    name="$scope/$pkg"
                    ;;
                  @*/*)
                    name="$name"
                    ;;
                  *@*)
                    version="${name#*@}"
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
                  printf '{"name":"%s","version":"%s","pi":{"skills":["skills"]}}\\n' "$name" "$version" > "$package_dir/package.json"
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

    private static Path createFailingNpmCommand(Path script) throws Exception {
        Files.writeString(script, """
                #!/bin/sh
                set -eu
                command="${1:-}"
                shift || true
                name_arg="${1:-}"
                if [ $# -gt 0 ]; then
                  shift || true
                fi
                if [ "$command" = "view" ]; then
                  printf '"1.0.0"\\n'
                  exit 0
                fi
                prefix=""
                previous=""
                for arg in "$@"; do
                  if [ "$previous" = "--prefix" ]; then
                    prefix="$arg"
                  fi
                  previous="$arg"
                done
                if [ "$command" != "install" ]; then
                  echo "unsupported command: $command" >&2
                  exit 1
                fi
                name="$name_arg"
                version="0.0.0"
                case "$name" in
                  @*/*@*)
                    scope="${name%%/*}"
                    rest="${name#*/}"
                    pkg="${rest%%@*}"
                    version="${rest#*@}"
                    name="$scope/$pkg"
                    ;;
                  @*/*)
                    name="$name"
                    ;;
                  *@*)
                    version="${name#*@}"
                    name="${name%%@*}"
                    ;;
                esac
                if [ "$name" = "@scope/failing-pack" ]; then
                  echo "simulated install failure for $name" >&2
                  exit 42
                fi
                if [ -z "$prefix" ]; then
                  echo "missing --prefix" >&2
                  exit 1
                fi
                package_dir="$prefix/node_modules/$name"
                mkdir -p "$package_dir/skills"
                printf '{"name":"%s","version":"%s","pi":{"skills":["skills"]}}\\n' "$name" "$version" > "$package_dir/package.json"
                printf 'Fake npm skill for %s\\n' "$name" > "$package_dir/skills/SKILL.md"
                """);
        script.toFile().setExecutable(true);
        return script;
    }

    private static Path createFakeSelfUpdateNpmCommand(Path script) throws Exception {
        Files.writeString(script, """
                #!/bin/sh
                set -eu
                printf '%s\\n' "$*" >> "$0.log"
                command="${1:-}"
                if [ "$command" = "view" ]; then
                  printf '"9.9.9"\\n'
                  exit 0
                fi
                if [ "$command" = "install" ] || [ "$command" = "uninstall" ]; then
                  exit 0
                fi
                echo "unsupported command: $command" >&2
                exit 1
                """);
        script.toFile().setExecutable(true);
        return script;
    }

    private static Path createFailingSelfUpdateNpmCommand(Path script) throws Exception {
        Files.writeString(script, """
                #!/bin/sh
                set -eu
                command="${1:-}"
                if [ "$command" = "view" ]; then
                  printf '"9.9.9"\\n'
                  exit 0
                fi
                if [ "$command" = "install" ]; then
                  echo "simulated self-update failure" >&2
                  exit 42
                fi
                echo "unsupported command: $command" >&2
                exit 1
                """);
        script.toFile().setExecutable(true);
        return script;
    }

    private static Path createCleanupFailingSelfUpdateNpmCommand(Path script) throws Exception {
        Files.writeString(script, """
                #!/bin/sh
                set -eu
                command="${1:-}"
                if [ "$command" = "view" ]; then
                  printf '"9.9.9"\\n'
                  exit 0
                fi
                if [ "$command" = "install" ]; then
                  exit 0
                fi
                if [ "$command" = "uninstall" ]; then
                  echo "simulated self-update cleanup failure" >&2
                  exit 42
                fi
                echo "unsupported command: $command" >&2
                exit 1
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
