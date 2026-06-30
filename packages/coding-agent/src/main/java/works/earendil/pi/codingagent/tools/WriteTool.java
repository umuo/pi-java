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

    public record Result(Path path, String oldContent, String newContent, boolean created) {
    }

    public Result write(String path, String content, boolean overwrite) throws IOException {
        Path target = PathUtils.resolveInside(cwd, path);
        try {
            return FileMutationQueue.withFileMutationQueue(target, () -> {
                boolean exists = Files.exists(target);
                if (exists && !overwrite) {
                    throw new IllegalStateException("File already exists: " + path);
                }
                String oldContent = exists ? Files.readString(target, StandardCharsets.UTF_8) : "";
                Files.createDirectories(target.getParent());
                Files.writeString(target, content, StandardCharsets.UTF_8);
                return new Result(target, oldContent, content, !exists);
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
