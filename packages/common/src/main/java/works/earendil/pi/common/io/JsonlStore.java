package works.earendil.pi.common.io;

import works.earendil.pi.common.json.JsonCodec;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public final class JsonlStore<T> {
    private final Path path;
    private final Class<T> type;

    public JsonlStore(Path path, Class<T> type) {
        this.path = path;
        this.type = type;
    }

    public List<T> readAll() throws IOException {
        if (!Files.exists(path)) {
            return List.of();
        }
        List<T> values = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    values.add(JsonCodec.parse(line, type));
                }
            }
        }
        return values;
    }

    public void append(T value) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        try (FileChannel channel = FileChannel.open(path,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
             FileLock ignored = channel.lock();
             BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                     StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            writer.write(JsonCodec.stringify(value));
            writer.newLine();
        }
    }
}
