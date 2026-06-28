package works.earendil.pi.codingagent.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class WriteTool {
    private final Path cwd;

    public WriteTool(Path cwd) {
        this.cwd = cwd;
    }

    public Path write(String path, String content, boolean overwrite) throws IOException {
        Path target = PathUtils.resolveInside(cwd, path);
        try {
            return FileMutationQueue.withFileMutationQueue(target, () -> {
                if (Files.exists(target) && !overwrite) {
                    throw new IllegalStateException("File already exists: " + path);
                }
                Files.createDirectories(target.getParent());
                Files.writeString(target, content, StandardCharsets.UTF_8);
                return target;
            });
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }
}
