package works.earendil.pi.codingagent.core;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

public interface BashOperations {
    Result exec(String command, Path cwd, Options options) throws Exception;

    record Options(Consumer<byte[]> onData, Duration timeout, Map<String, String> environment) {
        public Options(Consumer<byte[]> onData, Duration timeout) {
            this(onData, timeout, Map.of());
        }

        public Options {
            environment = environment == null ? Map.of() : Map.copyOf(environment);
        }
    }

    record Result(Integer exitCode) {
    }
}
