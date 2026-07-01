package works.earendil.pi.orchestrator.service;

import works.earendil.pi.orchestrator.config.OrchestratorConfig;
import works.earendil.pi.orchestrator.storage.OrchestratorStorage;

import java.util.Objects;

public final class OrchestratorRuntime {
    private static volatile OrchestratorRuntime shared;

    private final OrchestratorStorage storage;
    private final OrchestratorSupervisor supervisor;
    private final OrchestratorStatusReporter statusReporter;

    public OrchestratorRuntime(OrchestratorStorage storage) {
        this(storage, new OrchestratorSupervisor(storage));
    }

    public OrchestratorRuntime(OrchestratorStorage storage, OrchestratorSupervisor supervisor) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.supervisor = Objects.requireNonNull(supervisor, "supervisor");
        this.statusReporter = new OrchestratorStatusReporter(storage);
    }

    public static OrchestratorRuntime shared() {
        OrchestratorRuntime current = shared;
        if (current != null) {
            return current;
        }
        synchronized (OrchestratorRuntime.class) {
            if (shared == null) {
                OrchestratorStorage storage = new OrchestratorStorage(new OrchestratorConfig());
                shared = new OrchestratorRuntime(storage);
            }
            return shared;
        }
    }

    public OrchestratorStorage storage() {
        return storage;
    }

    public OrchestratorSupervisor supervisor() {
        return supervisor;
    }

    public OrchestratorStatusReporter statusReporter() {
        return statusReporter;
    }
}
