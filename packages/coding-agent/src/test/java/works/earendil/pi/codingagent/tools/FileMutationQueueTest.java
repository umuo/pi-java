package works.earendil.pi.codingagent.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class FileMutationQueueTest {
    @TempDir
    Path tempDir;

    @Test
    void serializesMutationsForSameFile() throws Exception {
        Path file = tempDir.resolve("same.txt");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicBoolean secondEntered = new AtomicBoolean(false);
        List<String> order = new CopyOnWriteArrayList<>();

        try {
            Future<Void> first = executor.submit(() -> FileMutationQueue.withFileMutationQueue(file, () -> {
                order.add("first");
                firstEntered.countDown();
                assertThat(releaseFirst.await(1, TimeUnit.SECONDS)).isTrue();
                return null;
            }));
            assertThat(firstEntered.await(1, TimeUnit.SECONDS)).isTrue();

            Future<Void> second = executor.submit(() -> FileMutationQueue.withFileMutationQueue(file, () -> {
                secondEntered.set(true);
                order.add("second");
                return null;
            }));

            Thread.sleep(100);
            assertThat(secondEntered).isFalse();
            releaseFirst.countDown();

            first.get(1, TimeUnit.SECONDS);
            second.get(1, TimeUnit.SECONDS);
            assertThat(order).containsExactly("first", "second");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void allowsMutationsForDifferentFilesToRunConcurrently() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch bothEntered = new CountDownLatch(2);
        CountDownLatch releaseBoth = new CountDownLatch(1);

        try {
            Future<Void> first = executor.submit(() -> FileMutationQueue.withFileMutationQueue(tempDir.resolve("a.txt"), () -> {
                bothEntered.countDown();
                assertThat(releaseBoth.await(1, TimeUnit.SECONDS)).isTrue();
                return null;
            }));
            Future<Void> second = executor.submit(() -> FileMutationQueue.withFileMutationQueue(tempDir.resolve("b.txt"), () -> {
                bothEntered.countDown();
                assertThat(releaseBoth.await(1, TimeUnit.SECONDS)).isTrue();
                return null;
            }));

            assertThat(bothEntered.await(1, TimeUnit.SECONDS)).isTrue();
            releaseBoth.countDown();
            first.get(1, TimeUnit.SECONDS);
            second.get(1, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }
}
