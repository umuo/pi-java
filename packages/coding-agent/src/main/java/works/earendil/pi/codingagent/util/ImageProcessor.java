package works.earendil.pi.codingagent.util;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;

public final class ImageProcessor {
    public static final int MAX_DIMENSION = 2000;

    private ImageProcessor() {
    }

    public record Result(boolean ok, byte[] bytes, String data, String mimeType, List<String> hints, int originalWidth,
                         int originalHeight, int width, int height, String message) {
    }

    public static Result process(byte[] bytes, String mimeType, boolean autoResizeImages) {
        String normalizedMimeType = normalizeMimeType(mimeType);
        boolean requiresPng = "image/bmp".equals(normalizedMimeType);
        if (!autoResizeImages && !requiresPng) {
            return success(bytes, normalizedMimeType, List.of(), 0, 0, 0, 0);
        }
        if (!canDecodeWithImageIo(normalizedMimeType)) {
            return success(bytes, normalizedMimeType, List.of(), 0, 0, 0, 0);
        }

        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            return omitted();
        }
        if (image == null) {
            return omitted();
        }

        int originalWidth = image.getWidth();
        int originalHeight = image.getHeight();
        int targetWidth = originalWidth;
        int targetHeight = originalHeight;
        boolean resized = false;

        if (autoResizeImages && (targetWidth > MAX_DIMENSION || targetHeight > MAX_DIMENSION)) {
            double scale = Math.min((double) MAX_DIMENSION / targetWidth, (double) MAX_DIMENSION / targetHeight);
            targetWidth = Math.max(1, (int) Math.round(targetWidth * scale));
            targetHeight = Math.max(1, (int) Math.round(targetHeight * scale));
            resized = true;
        }

        if (!requiresPng && !resized) {
            return success(bytes, normalizedMimeType, List.of(), originalWidth, originalHeight, originalWidth, originalHeight);
        }

        BufferedImage output = resized ? resize(image, targetWidth, targetHeight) : image;
        byte[] encoded;
        try {
            encoded = encodePng(output);
        } catch (IOException e) {
            return omitted();
        }

        List<String> hints = hints(normalizedMimeType, resized, originalWidth, originalHeight, targetWidth, targetHeight);
        return success(encoded, "image/png", hints, originalWidth, originalHeight, targetWidth, targetHeight);
    }

    private static Result success(byte[] bytes, String mimeType, List<String> hints, int originalWidth, int originalHeight,
                                  int width, int height) {
        return new Result(true, bytes, Base64.getEncoder().encodeToString(bytes), mimeType, hints, originalWidth,
                originalHeight, width, height, null);
    }

    private static Result omitted() {
        return new Result(false, null, null, null, List.of(), 0, 0, 0, 0,
                "[Image omitted: could not be converted to a supported inline image format.]");
    }

    private static String normalizeMimeType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return "image/png";
        }
        String base = mimeType.split(";", 2)[0].trim().toLowerCase();
        return "image/jpg".equals(base) ? "image/jpeg" : base;
    }

    private static boolean canDecodeWithImageIo(String mimeType) {
        return "image/png".equals(mimeType)
                || "image/jpeg".equals(mimeType)
                || "image/bmp".equals(mimeType);
    }

    private static BufferedImage resize(BufferedImage image, int width, int height) {
        int type = image.getTransparency() == BufferedImage.OPAQUE
                ? BufferedImage.TYPE_INT_RGB
                : BufferedImage.TYPE_INT_ARGB;
        BufferedImage output = new BufferedImage(width, height, type);
        Graphics2D graphics = output.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(image, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return output;
    }

    private static byte[] encodePng(BufferedImage image) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", output)) {
            throw new IOException("No PNG writer available");
        }
        return output.toByteArray();
    }

    private static List<String> hints(String mimeType, boolean resized, int originalWidth, int originalHeight,
                                      int width, int height) {
        String conversion = "image/bmp".equals(mimeType) ? "[Image converted from image/bmp to image/png.]" : null;
        String dimension = resized
                ? "[Image: original " + originalWidth + "x" + originalHeight + ", displayed at " + width + "x"
                + height + ". Multiply coordinates by "
                + String.format(java.util.Locale.ROOT, "%.2f", (double) originalWidth / width)
                + " to map to original image.]"
                : null;
        if (conversion != null && dimension != null) {
            return List.of(conversion, dimension);
        }
        if (conversion != null) {
            return List.of(conversion);
        }
        if (dimension != null) {
            return List.of(dimension);
        }
        return List.of();
    }
}
