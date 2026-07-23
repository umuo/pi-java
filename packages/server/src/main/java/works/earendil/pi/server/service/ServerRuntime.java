package works.earendil.pi.server.service;

import works.earendil.pi.server.config.ServerConfig;
import works.earendil.pi.server.storage.ServerStorage;

import java.util.Objects;

public final class ServerRuntime {
    private static volatile ServerRuntime shared;

    private final ServerStorage storage;
    private final ServerSupervisor supervisor;
    private final ServerStatusReporter statusReporter;

    public ServerRuntime(ServerStorage storage) {
        this(storage, new ServerSupervisor(storage));
    }

    public ServerRuntime(ServerStorage storage, ServerSupervisor supervisor) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.supervisor = Objects.requireNonNull(supervisor, "supervisor");
        this.statusReporter = new ServerStatusReporter(storage);
    }

    public static ServerRuntime shared() {
        ServerRuntime current = shared;
        if (current != null) {
            return current;
        }
        synchronized (ServerRuntime.class) {
            if (shared == null) {
                ServerStorage storage = new ServerStorage(new ServerConfig());
                shared = new ServerRuntime(storage);
            }
            return shared;
        }
    }

    public ServerStorage storage() {
        return storage;
    }

    public ServerSupervisor supervisor() {
        return supervisor;
    }

    public ServerStatusReporter statusReporter() {
        return statusReporter;
    }
}
