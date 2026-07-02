package works.earendil.pi.codingagent.core;

import org.junit.jupiter.api.Test;
import works.earendil.pi.codingagent.resources.SkillLoader;
import works.earendil.pi.codingagent.session.SessionManager;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillDiagnosticHistoryTest {
    @Test
    void persistsRestoresAndClearsRecentDiagnostics() throws Exception {
        SessionManager manager = SessionManager.inMemory(Path.of("/tmp/project"));
        SkillDiagnosticHistory history = new SkillDiagnosticHistory();
        history.record(new AgentSession.AgentSessionEvent.SkillTriggerDiagnostic(List.of(
                new SkillLoader.SkillTriggerMatch("diagnose",
                        Path.of("/tmp/project/.agents/skills/diagnose/SKILL.md"),
                        true,
                        List.of("term:flaky")))));
        history.record(new AgentSession.AgentSessionEvent.SkillTriggerDiagnostic(List.of(
                new SkillLoader.SkillTriggerMatch("manual-audit",
                        Path.of("/tmp/project/.agents/skills/manual-audit/SKILL.md"),
                        false,
                        List.of("pattern:security.*review")))));
        history.persist(manager);

        SkillDiagnosticHistory restored = SkillDiagnosticHistory.fromSession(manager);

        assertThat(restored.entries()).hasSize(2);
        assertThat(restored.latest().matches()).singleElement().satisfies(match -> {
            assertThat(match.skillName()).isEqualTo("manual-audit");
            assertThat(match.modelVisible()).isFalse();
            assertThat(match.reasons()).containsExactly("pattern:security.*review");
            assertThat(match.skillPath().toString()).endsWith(".agents/skills/manual-audit/SKILL.md");
        });
        assertThat(restored.entries(new SkillDiagnosticHistory.Filter("diagnose", "visible", "flaky")))
                .singleElement()
                .satisfies(entry -> assertThat(entry.matches()).singleElement().satisfies(match ->
                        assertThat(match.skillName()).isEqualTo("diagnose")));
        assertThat(restored.entries(new SkillDiagnosticHistory.Filter("", "manual", "security")))
                .singleElement()
                .satisfies(entry -> assertThat(entry.matches()).singleElement().satisfies(match ->
                        assertThat(match.skillName()).isEqualTo("manual-audit")));
        assertThat(restored.toJson(new SkillDiagnosticHistory.Filter("manual-audit", "manual", "security"))
                .path("entries").get(0).path("matches").get(0).path("skill").asText())
                .isEqualTo("manual-audit");
        var paged = restored.toJson(new SkillDiagnosticHistory.Query(
                SkillDiagnosticHistory.Filter.empty(), 0, 1, "newest", true));
        assertThat(paged.path("page").path("sort").asText()).isEqualTo("newest");
        assertThat(paged.path("page").path("totalEntries").asInt()).isEqualTo(2);
        assertThat(paged.path("entries")).hasSize(1);
        assertThat(paged.path("entries").get(0).path("matches").get(0).path("skill").asText())
                .isEqualTo("manual-audit");
        assertThat(paged.path("summary").path("matches").asInt()).isEqualTo(2);
        assertThat(paged.path("summary").path("reasons").get(0).path("value").asText())
                .isEqualTo("pattern:security.*review");
        assertThat(paged.path("summary").path("reasonDrillDown")).hasSize(2);
        assertThat(paged.path("summary").path("reasonDrillDown").get(0).path("reason").asText())
                .isEqualTo("pattern:security.*review");
        assertThat(paged.path("summary").path("reasonDrillDown").get(0).path("skills").get(0).path("skill").asText())
                .isEqualTo("manual-audit");

        restored.clear();
        restored.persist(manager);

        assertThat(SkillDiagnosticHistory.fromSession(manager).entries()).isEmpty();
    }

    @Test
    void restoresDiagnosticsFromSpecificBranch() throws Exception {
        SessionManager manager = SessionManager.inMemory(Path.of("/tmp/project"),
                new SessionManager.NewSessionOptions("session-branch-test", null));
        SkillDiagnosticHistory history = new SkillDiagnosticHistory();
        history.record(new AgentSession.AgentSessionEvent.SkillTriggerDiagnostic(List.of(
                new SkillLoader.SkillTriggerMatch("first-skill",
                        Path.of("/tmp/project/.agents/skills/first-skill/SKILL.md"),
                        true,
                        List.of("term:first")))));
        String firstBranch = history.persist(manager);

        history.record(new AgentSession.AgentSessionEvent.SkillTriggerDiagnostic(List.of(
                new SkillLoader.SkillTriggerMatch("second-skill",
                        Path.of("/tmp/project/.agents/skills/second-skill/SKILL.md"),
                        true,
                        List.of("term:second")))));
        String secondBranch = history.persist(manager);

        SkillDiagnosticHistory first = SkillDiagnosticHistory.fromSession(manager, firstBranch);
        SkillDiagnosticHistory second = SkillDiagnosticHistory.fromSession(manager, secondBranch);

        assertThat(first.entries()).singleElement().satisfies(entry ->
                assertThat(entry.matches()).singleElement().satisfies(match ->
                        assertThat(match.skillName()).isEqualTo("first-skill")));
        assertThat(second.entries()).hasSize(2);
        assertThat(first.toJson(SkillDiagnosticHistory.Query.defaultQuery(),
                        SkillDiagnosticHistory.Source.from(manager, firstBranch))
                .path("source").path("branch").asText())
                .isEqualTo(firstBranch);
        assertThat(first.toJson(SkillDiagnosticHistory.Query.defaultQuery(),
                        SkillDiagnosticHistory.Source.from(manager, firstBranch))
                .path("source").path("sessionId").asText())
                .isEqualTo("session-branch-test");
        var sourceIndex = SkillDiagnosticHistory.sourceIndex(manager, 10, false);
        assertThat(sourceIndex.path("current").path("sessionId").asText()).isEqualTo("session-branch-test");
        assertThat(sourceIndex.path("sessions")).hasSize(1);
        assertThat(sourceIndex.path("sessions").get(0).path("branchTree")).isNotEmpty();
        assertThat(sourceIndex.path("sessions").get(0).path("branchTree").get(0).path("diagnostics").path("matches").asInt())
                .isGreaterThan(0);
        var picker = SkillDiagnosticHistory.sourcePicker(manager, 10, false);
        assertThat(picker.path("totalItems").asInt()).isGreaterThan(0);
        assertThat(picker.path("items").get(0).path("title").asText()).contains("matches:");
        assertThat(picker.path("items").get(0).path("subtitle").asText()).contains("top reason:");
        assertThat(picker.path("items").get(0).path("branch").asText()).isNotBlank();
        var inspectByIndex = SkillDiagnosticHistory.inspect(manager, "1", null, 10, false);
        assertThat(inspectByIndex.path("selectedSource").path("index").asInt()).isEqualTo(1);
        assertThat(inspectByIndex.path("summary").path("reasonDrillDown")).isNotEmpty();
        var inspectByBranch = SkillDiagnosticHistory.inspect(manager, "branch=" + firstBranch, null, 10, false);
        assertThat(inspectByBranch.path("source").path("branch").asText()).isEqualTo(firstBranch);
        assertThat(inspectByBranch.path("entries")).hasSize(1);
        assertThatThrownBy(() -> SkillDiagnosticHistory.fromSession(manager, "missing-entry"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing-entry");
    }
}
