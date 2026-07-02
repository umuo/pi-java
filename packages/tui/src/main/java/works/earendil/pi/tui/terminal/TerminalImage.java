package works.earendil.pi.tui.terminal;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class TerminalImage {
    private TerminalImage() {
    }

    public enum ImageProtocol {
        KITTY,
        ITERM2,
        NONE
    }

    public record Dimensions(int width, int height) {
    }

    public record CellSize(int columns, int rows) {
    }

    public static ImageProtocol detectImageProtocol() {
        String termProgram = getEnv("TERM_PROGRAM");
        termProgram = termProgram == null ? "" : termProgram.toLowerCase();
        String term = getEnv("TERM");
        term = term == null ? "" : term.toLowerCase();

        if (getEnv("TMUX") != null || term.startsWith("tmux") || term.startsWith("screen")) {
            return ImageProtocol.NONE;
        }
        if (getEnv("KITTY_WINDOW_ID") != null || "kitty".equals(termProgram) ||
                "ghostty".equals(termProgram) || term.contains("ghostty") || getEnv("GHOSTTY_RESOURCES_DIR") != null ||
                getEnv("WEZTERM_PANE") != null || "wezterm".equals(termProgram) ||
                "warpterminal".equals(termProgram) || getEnv("WARP_SESSION_ID") != null) {
            return ImageProtocol.KITTY;
        }
        if (getEnv("ITERM_SESSION_ID") != null || "iterm.app".equals(termProgram) ||
                "vscode".equals(termProgram) || getEnv("TERM_PROGRAM_VERSION") != null) {
            return ImageProtocol.ITERM2;
        }
        return ImageProtocol.NONE;
    }

    private static String getEnv(String name) {
        String val = System.getenv(name);
        return (val == null || val.isBlank()) ? null : val;
    }

    public static int allocateImageId() {
        return ThreadLocalRandom.current().nextInt(1, 0xfffffff);
    }

    public static String encodeKitty(byte[] data, Integer columns, Integer rows, Integer imageId, Boolean moveCursor) {
        String base64Data = Base64.getEncoder().encodeToString(data);
        int chunkSize = 4096;
        List<String> params = new ArrayList<>();
        params.add("a=T");
        params.add("f=100");
        params.add("q=2");
        if (Boolean.FALSE.equals(moveCursor)) params.add("C=1");
        if (columns != null && columns > 0) params.add("c=" + columns);
        if (rows != null && rows > 0) params.add("r=" + rows);
        if (imageId != null && imageId > 0) params.add("i=" + imageId);

        String paramStr = String.join(",", params);
        if (base64Data.length() <= chunkSize) {
            return "\u001B_G" + paramStr + ";" + base64Data + "\u001B\\";
        }

        StringBuilder sb = new StringBuilder();
        int offset = 0;
        boolean isFirst = true;
        while (offset < base64Data.length()) {
            int end = Math.min(offset + chunkSize, base64Data.length());
            String chunk = base64Data.substring(offset, end);
            boolean isLast = (end >= base64Data.length());
            if (isFirst) {
                sb.append("\u001B_G").append(paramStr).append(",m=1;").append(chunk).append("\u001B\\");
                isFirst = false;
            } else if (isLast) {
                sb.append("\u001B_Gm=0;").append(chunk).append("\u001B\\");
            } else {
                sb.append("\u001B_Gm=1;").append(chunk).append("\u001B\\");
            }
            offset = end;
        }
        return sb.toString();
    }

    public static String deleteKittyImage(int imageId) {
        return "\u001B_Ga=d,d=I,i=" + imageId + ",q=2\u001B\\";
    }

    public static String deleteAllKittyImages() {
        return "\u001B_Ga=d,d=A,q=2\u001B\\";
    }

    public static String encodeITerm2(byte[] data, String mimeType, String name) {
        String encoded = Base64.getEncoder().encodeToString(data);
        String safeName = Base64.getEncoder().encodeToString((name == null ? "image" : name).getBytes(StandardCharsets.UTF_8));
        return "\u001B]1337;File=name=" + safeName + ";inline=1:" + encoded + "\u0007";
    }

    public static CellSize calculateImageCellSize(Dimensions imageDimensions, int maxWidthCells, Integer maxHeightCells, int cellWidthPx, int cellHeightPx) {
        int maxWidth = Math.max(1, maxWidthCells);
        int imageWidth = Math.max(1, imageDimensions.width());
        int imageHeight = Math.max(1, imageDimensions.height());

        double widthScale = (double) (maxWidth * cellWidthPx) / imageWidth;
        double heightScale = maxHeightCells == null ? widthScale : (double) (maxHeightCells * cellHeightPx) / imageHeight;
        double scale = Math.min(widthScale, heightScale);

        int scaledWidthPx = (int) (imageWidth * scale);
        int scaledHeightPx = (int) (imageHeight * scale);
        int columns = (int) Math.ceil((double) scaledWidthPx / cellWidthPx);
        int rows = (int) Math.ceil((double) scaledHeightPx / cellHeightPx);

        int finalCols = Math.max(1, Math.min(maxWidth, columns));
        int finalRows = Math.max(1, maxHeightCells == null ? rows : Math.min(maxHeightCells, rows));
        return new CellSize(finalCols, finalRows);
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
