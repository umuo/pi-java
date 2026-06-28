package works.earendil.pi.common.json;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;

public final class PartialJson {
    private PartialJson() {
    }

    public static Optional<JsonNode> tryParse(String input) {
        try {
            return Optional.of(JsonCodec.parse(input));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    public static Optional<JsonNode> tryParseWithClosure(String input) {
        Optional<JsonNode> parsed = tryParse(input);
        if (parsed.isPresent()) {
            return parsed;
        }
        return tryParse(close(input));
    }

    public static String close(String input) {
        StringBuilder out = new StringBuilder(input);
        boolean inString = false;
        boolean escape = false;
        int objects = 0;
        int arrays = 0;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\' && inString) {
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (!inString) {
                if (c == '{') objects++;
                if (c == '}') objects = Math.max(0, objects - 1);
                if (c == '[') arrays++;
                if (c == ']') arrays = Math.max(0, arrays - 1);
            }
        }
        if (inString) {
            out.append('"');
        }
        while (arrays-- > 0) {
            out.append(']');
        }
        while (objects-- > 0) {
            out.append('}');
        }
        return out.toString();
    }
}
