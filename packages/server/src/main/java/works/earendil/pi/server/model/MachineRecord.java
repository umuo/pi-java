package works.earendil.pi.server.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MachineRecord(
        String id,
        String createdAt,
        String lastSeenAt,
        String label
) {
    public MachineRecord withLastSeenAt(String newLastSeenAt) {
        return new MachineRecord(id, createdAt, newLastSeenAt, label);
    }
}
