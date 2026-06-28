package works.earendil.pi.codingagent.core;

import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Consumer;

public interface BashOperations {
    Result exec(String command, Path cwd, Options options) throws Exception;

    record Options(Consumer<byte[]> onData, Duration timeout) {
    }

    record Result(Integer exitCode) {
    }
}
