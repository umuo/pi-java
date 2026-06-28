package works.earendil.pi.codingagent.core;

import org.junit.jupiter.api.Test;
import works.earendil.pi.codingagent.resources.ResourceDiagnostic;
import works.earendil.pi.codingagent.resources.SourceInfo;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExperimentalAndDiagnosticsTest {
    @Test
    void enablesExperimentalFeaturesOnlyForExactOne() {
        assertThat(Experimental.areExperimentalFeaturesEnabled(Map.of("PI_EXPERIMENTAL", "1"))).isTrue();
        assertThat(Experimental.areExperimentalFeaturesEnabled(Map.of("PI_EXPERIMENTAL", "true"))).isFalse();
        assertThat(Experimental.areExperimentalFeaturesEnabled(Map.of())).isFalse();
    }

    @Test
    void sourceInfoMatchesTypeScriptDefaults() {
        SourceInfo synthetic = SourceInfo.synthetic(Path.of("/tmp/file"), "sdk", Path.of("/tmp"));
        SourceInfo local = SourceInfo.local(Path.of("/tmp/project/.pi/skills/x/SKILL.md"), "project", Path.of("/tmp/project"));
        SourceInfo packaged = SourceInfo.packageSource(Path.of("/tmp/pkg/index.ts"), "npm:pkg", "user", Path.of("/tmp/pkg"));

        assertThat(synthetic.scope()).isEqualTo("temporary");
        assertThat(synthetic.origin()).isEqualTo("top-level");
        assertThat(local.scope()).isEqualTo("project");
        assertThat(local.origin()).isEqualTo("top-level");
        assertThat(packaged.origin()).isEqualTo("package");
        assertThat(packaged.source()).isEqualTo("npm:pkg");
    }

    @Test
    void diagnosticsExposeWarningErrorAndCollisionPayload() {
        ResourceDiagnostic warning = new ResourceDiagnostic.Warning("careful", Path.of("a"));
        ResourceDiagnostic error = new ResourceDiagnostic.Error("bad", Path.of("b"));
        ResourceDiagnostic.Collision collision = new ResourceDiagnostic.Collision(
                "skill", "review", Path.of("winner"), Path.of("loser"), "user", "project");

        assertThat(warning.type()).isEqualTo("warning");
        assertThat(error.type()).isEqualTo("error");
        assertThat(collision.type()).isEqualTo("collision");
        assertThat(collision.message()).contains("review");
        assertThat(collision.path()).isEqualTo(Path.of("loser"));
        assertThat(collision.winnerSource()).isEqualTo("user");
        assertThat(collision.loserSource()).isEqualTo("project");
    }
}
