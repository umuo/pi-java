package works.earendil.pi.codingagent.tools;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EditDiffTest {
    @Test
    void appliesMultipleEditsAgainstOriginalContent() {
        EditDiff.Applied applied = EditDiff.apply("""
                alpha
                beta
                gamma
                """, List.of(
                new EditDiff.Edit("alpha", "one"),
                new EditDiff.Edit("gamma", "three")));

        assertThat(applied.replacements()).isEqualTo(2);
        assertThat(applied.content()).isEqualTo("""
                one
                beta
                three
                """);
    }

    @Test
    void rejectsDuplicateOldTextMatches() {
        assertThatThrownBy(() -> EditDiff.apply("""
                value
                value
                """, List.of(new EditDiff.Edit("value", "next"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not unique");
    }

    @Test
    void rejectsOverlappingEdits() {
        assertThatThrownBy(() -> EditDiff.apply("abcdef",
                List.of(new EditDiff.Edit("abc", "x"), new EditDiff.Edit("bcd", "y"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlap");
    }

    @Test
    void rejectsNoChangeEdits() {
        assertThatThrownBy(() -> EditDiff.apply("same",
                List.of(new EditDiff.Edit("same", "same"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No changes made");
    }
}
