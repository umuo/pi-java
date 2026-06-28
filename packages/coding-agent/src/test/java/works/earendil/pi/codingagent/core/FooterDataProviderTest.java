package works.earendil.pi.codingagent.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class FooterDataProviderTest {
    @TempDir
    Path tempDir;

    @Test
    void resolvesBranchFromRegularRepoAndNestedDirectory() throws Exception {
        Path repo = createPlainRepo("main");
        Path nested = repo.resolve("src/nested");
        Files.createDirectories(nested);

        FooterDataProvider provider = new FooterDataProvider(nested);
        try {
            assertThat(provider.getGitBranch()).isEqualTo("main");
        } finally {
            provider.dispose();
        }
    }

    @Test
    void resolvesDetachedHead() throws Exception {
        Path repo = createPlainRepo("main");
        Files.writeString(repo.resolve(".git/HEAD"), "8adf00d\n");

        FooterDataProvider provider = new FooterDataProvider(repo);
        try {
            assertThat(provider.getGitBranch()).isEqualTo("detached");
        } finally {
            provider.dispose();
        }
    }

    @Test
    void resolvesGitFileWorktreeAndCommonDir() throws Exception {
        Path repo = tempDir.resolve("repo");
        Path commonGitDir = repo.resolve(".git");
        Path gitDir = commonGitDir.resolve("worktrees/src");
        Path worktree = tempDir.resolve("worktree");
        Files.createDirectories(gitDir);
        Files.createDirectories(worktree);
        Files.writeString(worktree.resolve(".git"), "gitdir: " + gitDir + "\n");
        Files.writeString(gitDir.resolve("HEAD"), "ref: refs/heads/feature\n");
        Files.writeString(gitDir.resolve("commondir"), "../..\n");

        FooterDataProvider provider = new FooterDataProvider(worktree);
        try {
            assertThat(provider.getGitBranch()).isEqualTo("feature");
        } finally {
            provider.dispose();
        }
    }

    @Test
    void managesExtensionStatusesProviderCountAndCwdSwitch() throws Exception {
        Path repo = createPlainRepo("main");
        Path other = createPlainRepo("next");

        FooterDataProvider provider = new FooterDataProvider(repo);
        try {
            provider.setExtensionStatus("a", "ready");
            provider.setExtensionStatus("b", "busy");
            provider.setExtensionStatus("b", null);
            provider.setAvailableProviderCount(3);

            assertThat(provider.getExtensionStatuses()).containsExactly(java.util.Map.entry("a", "ready"));
            assertThat(provider.getAvailableProviderCount()).isEqualTo(3);
            assertThat(provider.getGitBranch()).isEqualTo("main");

            provider.setCwd(other);

            assertThat(provider.getGitBranch()).isEqualTo("next");
            provider.clearExtensionStatuses();
            assertThat(provider.getExtensionStatuses()).isEmpty();
        } finally {
            provider.dispose();
        }
    }

    @Test
    void notifiesWhenHeadChanges() throws Exception {
        Path repo = createPlainRepo("main");
        FooterDataProvider provider = new FooterDataProvider(repo);
        AtomicInteger notifications = new AtomicInteger();
        provider.onBranchChange(notifications::incrementAndGet);
        try {
            assertThat(provider.getGitBranch()).isEqualTo("main");

            Files.writeString(repo.resolve(".git/HEAD"), "ref: refs/heads/feature\n");

            waitFor(() -> notifications.get() == 1 && "feature".equals(provider.getGitBranch()));
            assertThat(provider.getGitBranch()).isEqualTo("feature");
        } finally {
            provider.dispose();
        }
    }

    private Path createPlainRepo(String branch) throws Exception {
        Path repo = tempDir.resolve("repo-" + branch + "-" + System.nanoTime());
        Files.createDirectories(repo.resolve(".git"));
        Files.writeString(repo.resolve(".git/HEAD"), "ref: refs/heads/" + branch + "\n");
        return repo;
    }

    private static void waitFor(Condition condition) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (!condition.done()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Timed out waiting for condition");
            }
            Thread.sleep(25);
        }
    }

    @FunctionalInterface
    private interface Condition {
        boolean done();
    }
}
