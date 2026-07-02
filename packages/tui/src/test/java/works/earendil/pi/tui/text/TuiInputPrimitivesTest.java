package works.earendil.pi.tui.text;

import org.junit.jupiter.api.Test;
import works.earendil.pi.tui.component.AutocompleteComponent;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TuiInputPrimitivesTest {

    @Test
    void testUndoStack() {
        UndoStack stack = new UndoStack(5);
        assertThat(stack.current().text()).isEmpty();

        stack.push("hello", 5);
        stack.push("hello world", 11);

        assertThat(stack.current().text()).isEqualTo("hello world");

        UndoStack.EditState undid = stack.undo().orElseThrow();
        assertThat(undid.text()).isEqualTo("hello");
        assertThat(undid.cursorPosition()).isEqualTo(5);

        UndoStack.EditState redid = stack.redo().orElseThrow();
        assertThat(redid.text()).isEqualTo("hello world");
    }

    @Test
    void testKillRing() {
        KillRing ring = new KillRing(3);
        ring.push("first");
        ring.push("second");
        ring.push("third");

        assertThat(ring.yank()).contains("third");
        assertThat(ring.yankPop()).contains("second");
        assertThat(ring.yankPop()).contains("first");
        assertThat(ring.yankPop()).contains("third");
    }

    @Test
    void testWordNavigation() {
        String text = "hello world foo-bar";
        int w1 = WordNavigation.nextWord(text, 0);
        assertThat(w1).isEqualTo(5); // end of hello

        int w0 = WordNavigation.previousWord(text, 10);
        assertThat(w0).isEqualTo(6); // start of world

        WordNavigation.EditResult res = WordNavigation.deleteWordBackward(text, 11);
        assertThat(res.deletedText()).isEqualTo("world");
        assertThat(res.text()).isEqualTo("hello  foo-bar");
    }

    @Test
    void testAutocompleteComponent() {
        List<AutocompleteComponent.Candidate> cands = List.of(
                new AutocompleteComponent.Candidate("/help", "Show help"),
                new AutocompleteComponent.Candidate("/clear", "Clear screen"),
                new AutocompleteComponent.Candidate("/grill-me", "Start interview")
        );
        AutocompleteComponent ac = new AutocompleteComponent(cands);
        ac.updateQuery("cl");
        assertThat(ac.selectedCandidate()).isPresent();
        assertThat(ac.selectedCandidate().get().value()).isEqualTo("/clear");

        String popup = ac.renderPopup(5, 80);
        assertThat(popup).contains("/clear").contains("Clear screen");
    }
}
