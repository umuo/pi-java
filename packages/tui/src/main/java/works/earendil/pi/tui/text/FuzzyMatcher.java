package works.earendil.pi.tui.text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

public final class FuzzyMatcher {
    private FuzzyMatcher() {
    }

    public record Match(boolean matched, int score, List<Integer> positions) {
    }

    public static Match match(String query, String text) {
        if (query == null || query.isEmpty()) {
            return new Match(true, 0, List.of());
        }
        String q = query.toLowerCase();
        String t = text.toLowerCase();
        List<Integer> positions = new ArrayList<>();
        int last = -1;
        int score = 0;
        for (int i = 0; i < q.length(); i++) {
            char needle = q.charAt(i);
            int found = t.indexOf(needle, last + 1);
            if (found < 0) {
                return new Match(false, Integer.MIN_VALUE, List.of());
            }
            positions.add(found);
            score += found == last + 1 ? 10 : 1;
            if (found == 0 || Character.isWhitespace(t.charAt(found - 1)) || "-_/.".indexOf(t.charAt(found - 1)) >= 0) {
                score += 5;
            }
            last = found;
        }
        score -= positions.getFirst();
        return new Match(true, score, List.copyOf(positions));
    }

    public static <T> List<T> filter(List<T> items, String query, Function<T, String> text) {
        return items.stream()
                .map(item -> new Scored<>(item, match(query, text.apply(item))))
                .filter(scored -> scored.match.matched())
                .sorted(Comparator.comparingInt((Scored<T> scored) -> scored.match.score()).reversed())
                .map(Scored::item)
                .toList();
    }

    private record Scored<T>(T item, Match match) {
    }
}
