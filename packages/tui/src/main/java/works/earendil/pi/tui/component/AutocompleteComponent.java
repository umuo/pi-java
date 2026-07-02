package works.earendil.pi.tui.component;

import works.earendil.pi.tui.text.FuzzyMatcher;

import java.util.List;
import java.util.Optional;

public final class AutocompleteComponent {
    private final List<Candidate> allCandidates;
    private List<Candidate> filteredCandidates = List.of();
    private int selectedIndex = 0;

    public record Candidate(String value, String description) {
        public Candidate(String value) {
            this(value, "");
        }
    }

    public AutocompleteComponent(List<Candidate> allCandidates) {
        this.allCandidates = List.copyOf(allCandidates);
        updateQuery("");
    }

    public void updateQuery(String query) {
        if (query == null || query.isEmpty()) {
            filteredCandidates = allCandidates;
        } else {
            filteredCandidates = FuzzyMatcher.filter(allCandidates, query, Candidate::value);
        }
        selectedIndex = filteredCandidates.isEmpty() ? -1 : Math.min(selectedIndex, filteredCandidates.size() - 1);
        if (selectedIndex < 0 && !filteredCandidates.isEmpty()) {
            selectedIndex = 0;
        }
    }

    public void selectNext() {
        if (!filteredCandidates.isEmpty()) {
            selectedIndex = (selectedIndex + 1) % filteredCandidates.size();
        }
    }

    public void selectPrevious() {
        if (!filteredCandidates.isEmpty()) {
            selectedIndex = (selectedIndex - 1 + filteredCandidates.size()) % filteredCandidates.size();
        }
    }

    public Optional<Candidate> selectedCandidate() {
        if (selectedIndex >= 0 && selectedIndex < filteredCandidates.size()) {
            return Optional.of(filteredCandidates.get(selectedIndex));
        }
        return Optional.empty();
    }

    public List<Candidate> filteredCandidates() {
        return filteredCandidates;
    }

    public int selectedIndex() {
        return selectedIndex;
    }

    public String renderPopup(int maxVisibleRows, int width) {
        if (filteredCandidates.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int rows = Math.min(maxVisibleRows, filteredCandidates.size());
        int start = Math.max(0, Math.min(selectedIndex - rows / 2, filteredCandidates.size() - rows));
        for (int i = start; i < start + rows; i++) {
            Candidate cand = filteredCandidates.get(i);
            boolean isSel = (i == selectedIndex);
            String prefix = isSel ? "\u001b[36m> " : "  ";
            String suffix = isSel ? "\u001b[0m" : "";
            String desc = cand.description().isEmpty() ? "" : " (" + cand.description() + ")";
            String line = prefix + cand.value() + desc + suffix;
            if (line.length() > width + 10) {
                line = line.substring(0, width);
            }
            sb.append(line).append("\n");
        }
        return sb.toString().stripTrailing();
    }
}
