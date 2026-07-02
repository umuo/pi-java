package works.earendil.pi.codingagent.core.extensions;

import java.io.File;
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
        ServiceLoader.load(ExtensionPlugin.class).forEach(plugins::add);

        List<Path> scanDirs = new ArrayList<>();
        if (!noExtensions) {
            String userHome = System.getProperty("user.home");
            scanDirs.add(Paths.get(userHome, ".pi", "agent", "extensions"));
            scanDirs.add(cwd.resolve(".pi").resolve("extensions"));
        }

        if (explicitPaths != null) {
            for (String p : explicitPaths) {
                scanDirs.add(Paths.get(p));
            }
        }

        for (Path dir : scanDirs) {
            if (Files.exists(dir) && Files.isDirectory(dir)) {
                try (Stream<Path> files = Files.list(dir)) {
                    files.filter(p -> p.toString().endsWith(".jar")).forEach(jarPath -> {
                        try {
                            URL[] urls = new URL[]{jarPath.toUri().toURL()};
                            try (URLClassLoader loader = new URLClassLoader(urls, ExtensionLoader.class.getClassLoader())) {
                                ServiceLoader.load(ExtensionPlugin.class, loader).forEach(plugins::add);
                            }
                        } catch (Exception ignored) {
                        }
                    });
                } catch (Exception ignored) {
                }
            }
        }

        return List.copyOf(plugins);
    }
}
