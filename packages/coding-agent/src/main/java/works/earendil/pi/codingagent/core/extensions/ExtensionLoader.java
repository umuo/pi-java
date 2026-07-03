package works.earendil.pi.codingagent.core.extensions;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Stream;

public final class ExtensionLoader {

    private ExtensionLoader() {}

    public static List<ExtensionPlugin> loadExtensions(List<String> explicitPaths, boolean noExtensions, Path cwd) {
        if (noExtensions && (explicitPaths == null || explicitPaths.isEmpty())) {
            return Collections.emptyList();
        }

        List<ExtensionPlugin> plugins = new ArrayList<>();
        if (!noExtensions) {
            ServiceLoader.load(ExtensionPlugin.class).forEach(plugins::add);
        }

        List<Path> scanDirs = new ArrayList<>();
        if (!noExtensions) {
            String userHome = System.getProperty("user.home");
            scanDirs.add(Paths.get(userHome, ".pi", "agent", "extensions"));
            scanDirs.add(cwd.resolve(".pi").resolve("extensions"));
        }

        if (explicitPaths != null) {
            for (String p : explicitPaths) {
                if (p != null && !p.isBlank()) {
                    scanDirs.add(resolvePath(p, cwd));
                }
            }
        }

        for (Path path : scanDirs) {
            if (Files.isDirectory(path)) {
                try (Stream<Path> files = Files.list(path)) {
                    files.filter(ExtensionLoader::isJar).forEach(jarPath -> loadJar(jarPath, plugins));
                } catch (Exception ignored) {
                }
            } else if (isJar(path)) {
                loadJar(path, plugins);
            }
        }

        return List.copyOf(plugins);
    }

    private static Path resolvePath(String input, Path cwd) {
        Path path = Paths.get(input);
        if (path.isAbsolute()) {
            return path.toAbsolutePath().normalize();
        }
        Path base = cwd == null ? Path.of(".") : cwd;
        return base.resolve(path).toAbsolutePath().normalize();
    }

    private static boolean isJar(Path path) {
        return Files.isRegularFile(path) && path.getFileName() != null
                && path.getFileName().toString().endsWith(".jar");
    }

    private static void loadJar(Path jarPath, List<ExtensionPlugin> plugins) {
        try {
            URL[] urls = new URL[]{jarPath.toUri().toURL()};
            try (URLClassLoader loader = new URLClassLoader(urls, ExtensionLoader.class.getClassLoader())) {
                ServiceLoader.load(ExtensionPlugin.class, loader).forEach(plugins::add);
            }
        } catch (Exception ignored) {
        }
    }
}
