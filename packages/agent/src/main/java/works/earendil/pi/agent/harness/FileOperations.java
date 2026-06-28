package works.earendil.pi.agent.harness;

import java.util.Set;
import java.util.TreeSet;

public final class FileOperations {
    private final Set<String> read = new TreeSet<>();
    private final Set<String> written = new TreeSet<>();
    private final Set<String> edited = new TreeSet<>();

    public Set<String> read() {
        return read;
    }

    public Set<String> written() {
        return written;
    }

    public Set<String> edited() {
        return edited;
    }

    public FileLists compute() {
        TreeSet<String> modified = new TreeSet<>();
        modified.addAll(written);
        modified.addAll(edited);
        TreeSet<String> readOnly = new TreeSet<>(read);
        readOnly.removeAll(modified);
        return new FileLists(readOnly, modified);
    }

    public record FileLists(Set<String> readFiles, Set<String> modifiedFiles) {
        public String format() {
            StringBuilder out = new StringBuilder();
            if (!readFiles.isEmpty()) {
                out.append("\n\n<read-files>\n");
                readFiles.forEach(path -> out.append(path).append('\n'));
                out.append("</read-files>");
            }
            if (!modifiedFiles.isEmpty()) {
                out.append("\n\n<modified-files>\n");
                modifiedFiles.forEach(path -> out.append(path).append('\n'));
                out.append("</modified-files>");
            }
            return out.toString();
        }
    }
}
