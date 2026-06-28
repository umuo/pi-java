package works.earendil.pi.tui.terminal;

import java.util.Base64;

public final class TerminalImage {
    private TerminalImage() {
    }

    public record Dimensions(int width, int height) {
    }

    public static String encodeITerm2(byte[] data, String mimeType, String name) {
        String encoded = Base64.getEncoder().encodeToString(data);
        String safeName = Base64.getEncoder().encodeToString((name == null ? "image" : name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return "\u001B]1337;File=name=" + safeName + ";inline=1:" + encoded + "\u0007";
    }

    public static String imageFallback(String mimeType, Dimensions dimensions, String filename) {
        StringBuilder out = new StringBuilder("[image");
        if (mimeType != null) {
            out.append(' ').append(mimeType);
        }
        if (dimensions != null) {
            out.append(' ').append(dimensions.width()).append('x').append(dimensions.height());
        }
        if (filename != null && !filename.isBlank()) {
            out.append(' ').append(filename);
        }
        return out.append(']').toString();
    }
}
