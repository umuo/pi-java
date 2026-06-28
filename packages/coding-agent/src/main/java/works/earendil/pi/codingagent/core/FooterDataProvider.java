package works.earendil.pi.codingagent.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class FooterDataProvider implements AutoCloseable {
    private static final Duration WATCH_DEBOUNCE = Duration.ofMillis(500);
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "pi-footer-data-provider");
        thread.setDaemon(true);
        return thread;
    });
    private final java.util.concurrent.ExecutorService watchExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "pi-footer-data-provider-watch");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, String> extensionStatuses = new LinkedHashMap<>();
    private final Set<Runnable> branchChangeCallbacks = ConcurrentHashMap.newKeySet();
    private Path cwd;
    private GitPaths gitPaths;
    private String cachedBranch;
    private boolean branchResolved;
    private int availableProviderCount;
    private WatchService watchService;
    private CompletableFuture<Void> watchLoop;
    private ScheduledFuture<?> refreshTimer;
    private boolean refreshInFlight;
    private boolean refreshPending;
    private boolean disposed;

    public FooterDataProvider(Path cwd) {
        this.cwd = cwd.toAbsolutePath().normalize();
        this.gitPaths = findGitPaths(this.cwd);
        setupGitWatcher();
    }

    public synchronized String getGitBranch() {
        if (!branchResolved) {
            cachedBranch = resolveGitBranchSync();
            branchResolved = true;
        }
        return cachedBranch;
    }

    public synchronized Map<String, String> getExtensionStatuses() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(extensionStatuses));
    }

    public Runnable onBranchChange(Runnable callback) {
        branchChangeCallbacks.add(callback);
        return () -> branchChangeCallbacks.remove(callback);
    }

    public synchronized void setExtensionStatus(String key, String text) {
        if (text == null) {
            extensionStatuses.remove(key);
        } else {
            extensionStatuses.put(key, text);
        }
    }

    public synchronized void clearExtensionStatuses() {
        extensionStatuses.clear();
    }

    public synchronized int getAvailableProviderCount() {
        return availableProviderCount;
    }

    public synchronized void setAvailableProviderCount(int count) {
        availableProviderCount = count;
    }

    public synchronized void setCwd(Path cwd) {
        Path next = cwd.toAbsolutePath().normalize();
        if (this.cwd.equals(next)) {
            return;
        }
        this.cwd = next;
        cancelRefreshTimer();
        clearGitWatchers();
        branchResolved = false;
        cachedBranch = null;
        gitPaths = findGitPaths(next);
        setupGitWatcher();
        notifyBranchChange();
    }

    @Override
    public synchronized void close() {
        disposed = true;
        cancelRefreshTimer();
        clearGitWatchers();
        branchChangeCallbacks.clear();
        executor.shutdownNow();
        watchExecutor.shutdownNow();
    }

    public void dispose() {
        close();
    }

    private void notifyBranchChange() {
        for (Runnable callback : branchChangeCallbacks) {
            callback.run();
        }
    }

    private synchronized void scheduleRefresh() {
        if (disposed || refreshTimer != null) {
            return;
        }
        if (refreshInFlight) {
            refreshPending = true;
            return;
        }
        refreshTimer = executor.schedule(() -> {
            synchronized (FooterDataProvider.this) {
                refreshTimer = null;
            }
            refreshGitBranchAsync();
        }, WATCH_DEBOUNCE.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void refreshGitBranchAsync() {
        synchronized (this) {
            if (disposed) {
                return;
            }
            if (refreshInFlight) {
                refreshPending = true;
                return;
            }
            refreshInFlight = true;
        }
        CompletableFuture.supplyAsync(this::resolveGitBranchSync, executor).whenComplete((nextBranch, ignored) -> {
            boolean notify = false;
            synchronized (this) {
                if (!disposed) {
                    if (branchResolved && !java.util.Objects.equals(cachedBranch, nextBranch)) {
                        notify = true;
                    }
                    cachedBranch = nextBranch;
                    branchResolved = true;
                }
                refreshInFlight = false;
                if (refreshPending && !disposed) {
                    refreshPending = false;
                    scheduleRefresh();
                }
            }
            if (notify) {
                notifyBranchChange();
            }
        });
    }

    private String resolveGitBranchSync() {
        try {
            GitPaths paths = gitPaths;
            if (paths == null) {
                return null;
            }
            String content = Files.readString(paths.headPath(), StandardCharsets.UTF_8).trim();
            if (content.startsWith("ref: refs/heads/")) {
                String branch = content.substring("ref: refs/heads/".length());
                if (branch.equals(".invalid")) {
                    String resolved = resolveBranchWithGit(paths.repoDir());
                    return resolved == null ? "detached" : resolved;
                }
                return branch;
            }
            return "detached";
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static String resolveBranchWithGit(Path repoDir) {
        try {
            Process process = new ProcessBuilder("git", "--no-optional-locks", "symbolic-ref", "--quiet", "--short", "HEAD")
                    .directory(repoDir.toFile())
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            int exit = process.waitFor();
            return exit == 0 && !stdout.isBlank() ? stdout : null;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    private synchronized void setupGitWatcher() {
        clearGitWatchers();
        GitPaths paths = gitPaths;
        if (paths == null || disposed) {
            return;
        }
        try {
            watchService = paths.headPath().getFileSystem().newWatchService();
            paths.headPath().getParent().register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);
            Path reftableDir = paths.commonGitDir().resolve("reftable");
            if (Files.isDirectory(reftableDir)) {
                reftableDir.register(watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);
            }
            watchLoop = CompletableFuture.runAsync(this::watchLoop, watchExecutor);
        } catch (IOException ignored) {
            clearGitWatchers();
        }
    }

    private void watchLoop() {
        while (true) {
            WatchService service;
            synchronized (this) {
                if (disposed || watchService == null) {
                    return;
                }
                service = watchService;
            }
            try {
                WatchKey key = service.take();
                boolean relevant = false;
                for (WatchEvent<?> event : key.pollEvents()) {
                    Object context = event.context();
                    if (context == null || context.toString().equals("HEAD") || context.toString().equals("tables.list")) {
                        relevant = true;
                    } else {
                        Path watchedDir = (Path) key.watchable();
                        if (watchedDir.endsWith("reftable")) {
                            relevant = true;
                        }
                    }
                }
                key.reset();
                if (relevant) {
                    scheduleRefresh();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException ignored) {
                return;
            }
        }
    }

    private synchronized void clearGitWatchers() {
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException ignored) {
            }
            watchService = null;
        }
        watchLoop = null;
    }

    private synchronized void cancelRefreshTimer() {
        if (refreshTimer != null) {
            refreshTimer.cancel(false);
            refreshTimer = null;
        }
    }

    private static GitPaths findGitPaths(Path cwd) {
        Path dir = cwd.toAbsolutePath().normalize();
        while (dir != null) {
            Path gitPath = dir.resolve(".git");
            if (Files.exists(gitPath)) {
                try {
                    if (Files.isRegularFile(gitPath)) {
                        String content = Files.readString(gitPath, StandardCharsets.UTF_8).trim();
                        if (content.startsWith("gitdir: ")) {
                            Path gitDir = dir.resolve(content.substring("gitdir: ".length()).trim()).normalize().toAbsolutePath();
                            Path headPath = gitDir.resolve("HEAD");
                            if (!Files.exists(headPath)) {
                                return null;
                            }
                            Path commonDirPath = gitDir.resolve("commondir");
                            Path commonGitDir = Files.exists(commonDirPath)
                                    ? gitDir.resolve(Files.readString(commonDirPath, StandardCharsets.UTF_8).trim()).normalize().toAbsolutePath()
                                    : gitDir;
                            return new GitPaths(dir, commonGitDir, headPath);
                        }
                    } else if (Files.isDirectory(gitPath)) {
                        Path headPath = gitPath.resolve("HEAD");
                        if (!Files.exists(headPath)) {
                            return null;
                        }
                        return new GitPaths(dir, gitPath, headPath);
                    }
                } catch (IOException ignored) {
                    return null;
                }
            }
            dir = dir.getParent();
        }
        return null;
    }

    private record GitPaths(Path repoDir, Path commonGitDir, Path headPath) {
    }
}
