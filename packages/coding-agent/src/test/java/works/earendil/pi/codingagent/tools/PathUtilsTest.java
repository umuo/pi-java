package works.earendil.pi.codingagent.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PathUtilsTest {
    @TempDir
    Path tempDir;

    @Test
    void normalizesCliPathInput() {
        Path home = tempDir.resolve("home");
        PathUtils.PathInputOptions options = new PathUtils.PathInputOptions(true, true, home, true, true);

        assertThat(PathUtils.normalizePath("@~/My\u00a0File.txt", options))
                .isEqualTo(home.resolve("My File.txt").toString());
    }

    @Test
    void resolvesFileUrlsAndRelativePaths() throws Exception {
        Path file = tempDir.resolve("space file.txt");
        Files.writeString(file, "content");

        assertThat(PathUtils.resolvePath(file.toUri().toString(), tempDir))
                .isEqualTo(file.toAbsolutePath().normalize());
        assertThat(PathUtils.resolveInside(tempDir, "space file.txt"))
                .isEqualTo(file.toAbsolutePath().normalize());
    }

    @Test
    void detectsLocalPaths() {
        assertThat(PathUtils.isLocalPath("./local")).isTrue();
        assertThat(PathUtils.isLocalPath("file:///tmp/local")).isTrue();
        assertThat(PathUtils.isLocalPath("https://example.com/pkg")).isFalse();
        assertThat(PathUtils.isLocalPath("github:owner/repo")).isFalse();
    }

    @Test
    void formatsCwdRelativeOrAbsolute() {
        Path inside = tempDir.resolve("src/Main.java");
        Path outside = tempDir.getParent().resolve("elsewhere.txt");

        assertThat(PathUtils.getCwdRelativePath(inside, tempDir)).contains("src/Main.java");
        assertThat(PathUtils.formatPathRelativeToCwdOrAbsolute(inside, tempDir)).isEqualTo("src/Main.java");
        assertThat(PathUtils.getCwdRelativePath(outside, tempDir)).isEmpty();
        assertThat(PathUtils.formatPathRelativeToCwdOrAbsolute(outside, tempDir))
                .isEqualTo(outside.toAbsolutePath().normalize().toString().replace('\\', '/'));
    }

    @Test
    void canonicalizeFallsBackForMissingPaths() {
        Path missing = tempDir.resolve("missing");

        assertThat(PathUtils.canonicalizePath(missing)).isEqualTo(missing);
    }
}
