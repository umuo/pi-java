package works.earendil.pi.codingagent.util;

public final class JsonUtils {
    private JsonUtils() {
    }

    public static String stripJsonComments(String input) {
        return stripTrailingCommas(stripLineComments(input));
    }

    private static String stripLineComments(String input) {
        StringBuilder out = new StringBuilder(input.length());
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (inString) {
                out.append(ch);
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }
            if (ch == '"') {
                inString = true;
                out.append(ch);
            } else if (ch == '/' && i + 1 < input.length() && input.charAt(i + 1) == '/') {
                i += 2;
                while (i < input.length() && input.charAt(i) != '\n') {
                    i++;
                }
                if (i < input.length()) {
                    out.append(input.charAt(i));
                }
            } else {
                out.append(ch);
            }
        }
        return out.toString();
    }

    private static String stripTrailingCommas(String input) {
        StringBuilder out = new StringBuilder(input.length());
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (inString) {
                out.append(ch);
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }
            if (ch == '"') {
                inString = true;
                out.append(ch);
            } else if (ch == ',') {
                int j = i + 1;
                while (j < input.length() && Character.isWhitespace(input.charAt(j))) {
                    j++;
                }
                if (j < input.length() && (input.charAt(j) == '}' || input.charAt(j) == ']')) {
                    out.append(input, i + 1, j);
                    i = j - 1;
                } else {
                    out.append(ch);
                }
            } else {
                out.append(ch);
            }
        }
        return out.toString();
    }
}
