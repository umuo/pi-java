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
        String lineEnding = detectLineEnding(content);
        String next = normalizeToLf(content);
        int replacements = 0;
        for (Edit edit : edits) {
            String oldText = normalizeToLf(edit.oldText());
            String newText = normalizeToLf(edit.newText());
            int idx = next.indexOf(oldText);
            if (idx < 0) {
                throw new IllegalArgumentException("Old text not found: " + preview(oldText));
            }
            next = next.substring(0, idx) + newText + next.substring(idx + oldText.length());
            replacements++;
        }
        return new Applied(restoreLineEndings(next, lineEnding), replacements);
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
}
