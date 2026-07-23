package works.earendil.pi.ai.model;

public enum ThinkingLevel {
    OFF("off"),
    MINIMAL("minimal"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    XHIGH("xhigh"),
    MAX("max");

    private final String wireName;

    ThinkingLevel(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static ThinkingLevel fromWireName(String value) {
        for (ThinkingLevel level : values()) {
            if (level.wireName.equals(value)) {
                return level;
            }
        }
        throw new IllegalArgumentException("Unknown thinking level: " + value);
    }
}
