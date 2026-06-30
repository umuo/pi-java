package works.earendil.pi.codingagent.tools;

import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class EditDiff {
    private EditDiff() {
    }

    public record Edit(String oldText, String newText) {
    }

    public record Applied(String content, int replacements) {
    }

    private record Match(int editIndex, int start, int length, String newText) {
    }

    public static String detectLineEnding(String content) {
        return content.contains("\r\n") ? "\r\n" : "\n";
    }

    public static String normalizeToLf(String text) {
        return text.replace("\r\n", "\n");
    }

    public static String restoreLineEndings(String text, String ending) {
        return "\r\n".equals(ending) ? text.replace("\n", "\r\n") : text;
    }

    public static Applied apply(String content, List<Edit> edits) {
        if (edits == null || edits.isEmpty()) {
            throw new IllegalArgumentException("Edit tool input is invalid. edits must contain at least one replacement.");
        }
        String lineEnding = detectLineEnding(content);
        String normalizedContent = normalizeToLf(content);
        List<Edit> normalizedEdits = new ArrayList<>();
        for (int i = 0; i < edits.size(); i++) {
            Edit edit = edits.get(i);
            String oldText = normalizeToLf(edit.oldText());
            String newText = normalizeToLf(edit.newText());
            if (oldText.isEmpty()) {
                throw new IllegalArgumentException(edits.size() == 1
                        ? "oldText must not be empty."
                        : "edits[" + i + "].oldText must not be empty.");
            }
            normalizedEdits.add(new Edit(oldText, newText));
        }

        List<Match> matches = new ArrayList<>();
        for (int i = 0; i < normalizedEdits.size(); i++) {
            Edit edit = normalizedEdits.get(i);
            int idx = normalizedContent.indexOf(edit.oldText());
            if (idx < 0) {
                throw new IllegalArgumentException("Old text not found: " + preview(edit.oldText()));
            }
            int occurrences = countOccurrences(normalizedContent, edit.oldText());
            if (occurrences > 1) {
                throw new IllegalArgumentException("Old text is not unique (" + occurrences + " matches): "
                        + preview(edit.oldText()));
            }
            matches.add(new Match(i, idx, edit.oldText().length(), edit.newText()));
        }

        matches.sort((a, b) -> Integer.compare(a.start(), b.start()));
        for (int i = 1; i < matches.size(); i++) {
            Match previous = matches.get(i - 1);
            Match current = matches.get(i);
            if (previous.start() + previous.length() > current.start()) {
                throw new IllegalArgumentException("edits[" + previous.editIndex() + "] and edits["
                        + current.editIndex() + "] overlap. Merge them into one edit or target disjoint regions.");
            }
        }

        String next = normalizedContent;
        for (int i = matches.size() - 1; i >= 0; i--) {
            Match match = matches.get(i);
            next = next.substring(0, match.start())
                    + match.newText()
                    + next.substring(match.start() + match.length());
        }
        if (normalizedContent.equals(next)) {
            throw new IllegalArgumentException(edits.size() == 1
                    ? "No changes made. The replacement produced identical content."
                    : "No changes made. The replacements produced identical content.");
        }
        return new Applied(restoreLineEndings(next, lineEnding), matches.size());
    }

    public static String unifiedPatch(String path, String oldContent, String newContent, int contextLines) {
        List<String> oldLines = new ArrayList<>(Arrays.asList(normalizeToLf(oldContent).split("\n", -1)));
        List<String> newLines = new ArrayList<>(Arrays.asList(normalizeToLf(newContent).split("\n", -1)));
        Patch<String> patch = com.github.difflib.DiffUtils.diff(oldLines, newLines);
        List<String> unified = UnifiedDiffUtils.generateUnifiedDiff(path, path, oldLines, patch, contextLines);
        return String.join("\n", unified);
    }

    private static String preview(String text) {
        return text.length() <= 80 ? text : text.substring(0, 80) + "...";
    }

    private static int countOccurrences(String content, String needle) {
        int count = 0;
        int from = 0;
        while (from <= content.length()) {
            int idx = content.indexOf(needle, from);
            if (idx < 0) {
                return count;
            }
            count++;
            from = idx + needle.length();
        }
        return count;
    }
}
