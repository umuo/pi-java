package works.earendil.pi.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record InstanceRecord(
        String id,
        InstanceStatus status,
        String cwd,
        String createdAt,
        String lastSeenAt,
        String label,
        String sessionId,
        String sessionFile,
        String radiusPiId
) {
    public InstanceRecord withStatus(InstanceStatus newStatus) {
        return new InstanceRecord(id, newStatus, cwd, createdAt, lastSeenAt, label, sessionId, sessionFile, radiusPiId);
    }

    public InstanceRecord withLastSeenAt(String newLastSeenAt) {
        return new InstanceRecord(id, status, cwd, createdAt, newLastSeenAt, label, sessionId, sessionFile, radiusPiId);
    }
}
