package works.earendil.pi.codingagent.tools;

import works.earendil.pi.common.text.Truncation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ReadTool {
    private final Path cwd;

    public ReadTool(Path cwd) {
        this.cwd = cwd;
    }

    public Truncation.Result read(String path, Truncation.Options options) throws IOException {
        Path target = PathUtils.resolveInside(cwd, path);
        String content = Files.readString(target, StandardCharsets.UTF_8);
        return Truncation.truncateHead(content, options);
    }
}
