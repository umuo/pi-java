package works.earendil.pi.codingagent.util;

import works.earendil.pi.common.yaml.YamlCodec;

import java.util.Map;

public final class Frontmatter {
    private Frontmatter() {
    }

    public record Parsed(Map<String, Object> frontmatter, String body) {
    }

    public static Parsed parse(String content) {
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        if (!normalized.startsWith("---")) {
            return new Parsed(Map.of(), normalized);
        }
        int end = normalized.indexOf("\n---", 3);
        if (end < 0) {
            return new Parsed(Map.of(), normalized);
        }
        String yaml = normalized.substring(4, end);
        String body = normalized.substring(end + 4).trim();
        return new Parsed(YamlCodec.parseMap(yaml), body);
    }

    public static String strip(String content) {
        return parse(content).body();
    }
}
