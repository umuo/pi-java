package works.earendil.pi.codingagent.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrustManagerTest {
    @TempDir
    Path tempDir;

    @Test
    void buildsTrustOptionsWithParentAndSessionOnlyChoices() throws Exception {
        Path project = tempDir.resolve("parent").resolve("project");
        Files.createDirectories(project);

        List<TrustManager.ProjectTrustOption> options = TrustManager.getProjectTrustOptions(project, true);

        assertThat(options).extracting(TrustManager.ProjectTrustOption::label).containsExactly(
                "Trust",
                "Trust parent folder (" + TrustManager.normalizeCwd(project).getParent() + ")",
                "Trust (this session only)",
                "Do not trust",
                "Do not trust (this session only)");
        assertThat(options.get(1).updates()).hasSize(2);
    }

    @Test
    void storePersistsNearestTrustDecisionAndCanRemoveChildDecision() throws Exception {
        Path agentDir = tempDir.resolve("agent");
        Path parent = tempDir.resolve("parent");
        Path project = parent.resolve("project");
        Files.createDirectories(project);
        TrustManager.ProjectTrustStore store = new TrustManager.ProjectTrustStore(agentDir);

        store.set(parent, true);
        store.set(project, false);
        assertThat(store.get(project)).isFalse();
        assertThat(store.getEntry(project)).hasValueSatisfying(entry ->
                assertThat(entry.path()).isEqualTo(TrustManager.normalizeCwd(project)));

        store.set(project, null);

        assertThat(store.get(project)).isTrue();
        assertThat(Files.readString(store.trustPath())).contains(TrustManager.normalizeCwd(parent).toString());
        assertThat(Files.readString(store.trustPath())).doesNotContain(TrustManager.normalizeCwd(project).toString());
    }

    @Test
    void detectsTrustRequiringProjectResources() throws Exception {
        Path home = tempDir.resolve("home");
        Path project = tempDir.resolve("project");
        Files.createDirectories(project.resolve(".pi"));

        assertThat(TrustManager.hasTrustRequiringProjectResources(project, home)).isFalse();

        Files.writeString(project.resolve(".pi").resolve("SYSTEM.md"), "system");
        assertThat(TrustManager.hasTrustRequiringProjectResources(project, home)).isTrue();

        Path child = tempDir.resolve("workspace").resolve("child");
        Files.createDirectories(child);
        Files.createDirectories(tempDir.resolve("workspace").resolve(".agents").resolve("skills"));
        assertThat(TrustManager.hasTrustRequiringProjectResources(child, home)).isTrue();

        Files.createDirectories(home.resolve(".agents").resolve("skills"));
        assertThat(TrustManager.hasTrustRequiringProjectResources(home, home)).isFalse();
    }

    @Test
    void rejectsInvalidTrustStoreValues() throws Exception {
        Path agentDir = tempDir.resolve("agent");
        Files.createDirectories(agentDir);
        Files.writeString(agentDir.resolve("trust.json"), "{\"/tmp\":\"yes\"}");
        TrustManager.ProjectTrustStore store = new TrustManager.ProjectTrustStore(agentDir);

        assertThatThrownBy(() -> store.get(tempDir))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be true, false, or null");
    }

    @Test
    void formatsAuthGuidanceMessages() {
        Path docs = tempDir.resolve("docs");

        assertThat(AuthGuidance.getProviderLoginHelp(docs)).contains(
                docs.resolve("providers.md").toString(),
                docs.resolve("models.md").toString());
        assertThat(AuthGuidance.formatNoModelSelectedMessage(docs))
                .contains("No model selected.", "Then use /model to select a model.");
        assertThat(AuthGuidance.formatNoApiKeyFoundMessage("unknown", docs))
                .contains("No API key found for the selected model.");
    }
}
