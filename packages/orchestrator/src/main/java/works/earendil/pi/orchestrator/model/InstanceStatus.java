package works.earendil.pi.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum InstanceStatus {
    STARTING("starting"),
    ONLINE("online"),
    STOPPING("stopping"),
    STOPPED("stopped"),
    ERROR("error");

    private final String value;

    InstanceStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static InstanceStatus fromValue(String text) {
        for (InstanceStatus b : InstanceStatus.values()) {
            if (String.valueOf(b.value).equalsIgnoreCase(text)) {
                return b;
            }
        }
        throw new IllegalArgumentException("Unexpected value '" + text + "'");
    }
}
