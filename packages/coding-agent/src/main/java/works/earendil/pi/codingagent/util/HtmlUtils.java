package works.earendil.pi.codingagent.util;

import java.util.Optional;

public final class HtmlUtils {
    private HtmlUtils() {
    }

    public record DecodedHtmlEntity(String text, int length) {
    }

    public static Optional<String> decodeHtmlEntity(String entity) {
        return switch (entity) {
            case "amp" -> Optional.of("&");
            case "lt" -> Optional.of("<");
            case "gt" -> Optional.of(">");
            case "quot" -> Optional.of("\"");
            case "apos" -> Optional.of("'");
            default -> decodeNumericEntity(entity);
        };
    }

    public static Optional<DecodedHtmlEntity> decodeHtmlEntityAt(String html, int index) {
        int semicolonIndex = html.indexOf(';', index + 1);
        if (semicolonIndex == -1 || semicolonIndex - index > 16) {
            return Optional.empty();
        }
        String entity = html.substring(index + 1, semicolonIndex);
        return decodeHtmlEntity(entity)
                .map(decoded -> new DecodedHtmlEntity(decoded, semicolonIndex - index + 1));
    }

    private static Optional<String> decodeNumericEntity(String entity) {
        try {
            int codePoint;
            if (entity.startsWith("#x") || entity.startsWith("#X")) {
                codePoint = Integer.parseInt(entity.substring(2), 16);
            } else if (entity.startsWith("#")) {
                codePoint = Integer.parseInt(entity.substring(1));
            } else {
                return Optional.empty();
            }
            if (!Character.isValidCodePoint(codePoint)) {
                return Optional.empty();
            }
            return Optional.of(new String(Character.toChars(codePoint)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
