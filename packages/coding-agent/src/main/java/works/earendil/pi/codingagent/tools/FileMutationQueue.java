package works.earendil.pi.codingagent.tools;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Serializes mutation operations that target the same file.
 */
public final class FileMutationQueue {
    private static final Object REGISTRATION_LOCK = new Object();
    private static final Map<Path, LockEntry> QUEUES = new HashMap<>();

    private FileMutationQueue() {
    }

    public static <T> T withFileMutationQueue(Path filePath, Callable<T> fn) throws Exception {
        Path key = mutationQueueKey(filePath);
        LockEntry entry = acquire(key);
        entry.lock.lock();
        try {
            return fn.call();
        } finally {
            try {
                entry.lock.unlock();
            } finally {
                release(key, entry);
            }
        }
    }

    public static void withFileMutationQueue(Path filePath, ThrowingRunnable fn) throws Exception {
        withFileMutationQueue(filePath, () -> {
            fn.run();
            return null;
        });
    }

    static Path mutationQueueKey(Path filePath) throws IOException {
        Path resolved = filePath.toAbsolutePath().normalize();
        try {
            return resolved.toRealPath();
        } catch (NoSuchFileException | NotDirectoryException e) {
            return resolved;
        }
    }

    private static LockEntry acquire(Path key) {
        synchronized (REGISTRATION_LOCK) {
            LockEntry entry = QUEUES.computeIfAbsent(key, ignored -> new LockEntry());
            entry.references++;
            return entry;
        }
    }

    private static void release(Path key, LockEntry entry) {
        synchronized (REGISTRATION_LOCK) {
            entry.references--;
            if (entry.references == 0 && QUEUES.get(key) == entry) {
                QUEUES.remove(key);
            }
        }
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class LockEntry {
        private final ReentrantLock lock = new ReentrantLock();
        private int references;
    }
}
