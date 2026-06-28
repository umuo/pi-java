package works.earendil.pi.codingagent.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class MimeUtils {
    private static final int IMAGE_TYPE_SNIFF_BYTES = 4100;
    private static final byte[] PNG_SIGNATURE = new byte[]{
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    };

    private MimeUtils() {
    }

    public static Optional<String> detectSupportedImageMimeType(byte[] buffer) {
        if (startsWith(buffer, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff})) {
            return buffer.length > 3 && (buffer[3] & 0xff) == 0xf7 ? Optional.empty() : Optional.of("image/jpeg");
        }
        if (startsWith(buffer, PNG_SIGNATURE)) {
            return isPng(buffer) && !isAnimatedPng(buffer) ? Optional.of("image/png") : Optional.empty();
        }
        if (startsWithAscii(buffer, 0, "GIF")) {
            return Optional.of("image/gif");
        }
        if (startsWithAscii(buffer, 0, "RIFF") && startsWithAscii(buffer, 8, "WEBP")) {
            return Optional.of("image/webp");
        }
        if (startsWithAscii(buffer, 0, "BM") && isBmp(buffer)) {
            return Optional.of("image/bmp");
        }
        return Optional.empty();
    }

    public static Optional<String> detectSupportedImageMimeTypeFromFile(Path filePath) throws IOException {
        try (InputStream input = Files.newInputStream(filePath)) {
            return detectSupportedImageMimeType(input.readNBytes(IMAGE_TYPE_SNIFF_BYTES));
        }
    }

    private static boolean isPng(byte[] buffer) {
        return buffer.length >= 16
                && readUint32BE(buffer, PNG_SIGNATURE.length) == 13
                && startsWithAscii(buffer, 12, "IHDR");
    }

    private static boolean isAnimatedPng(byte[] buffer) {
        int offset = PNG_SIGNATURE.length;
        while (offset + 8 <= buffer.length) {
            long chunkLength = readUint32BE(buffer, offset);
            int chunkTypeOffset = offset + 4;
            if (startsWithAscii(buffer, chunkTypeOffset, "acTL")) {
                return true;
            }
            if (startsWithAscii(buffer, chunkTypeOffset, "IDAT")) {
                return false;
            }
            long nextOffset = offset + 8L + chunkLength + 4;
            if (nextOffset <= offset || nextOffset > buffer.length || nextOffset > Integer.MAX_VALUE) {
                return false;
            }
            offset = (int) nextOffset;
        }
        return false;
    }

    private static boolean isBmp(byte[] buffer) {
        if (buffer.length < 26) {
            return false;
        }
        long declaredFileSize = readUint32LE(buffer, 2);
        long pixelDataOffset = readUint32LE(buffer, 10);
        long dibHeaderSize = readUint32LE(buffer, 14);
        if (declaredFileSize != 0 && declaredFileSize < 26) {
            return false;
        }
        if (pixelDataOffset < 14 + dibHeaderSize) {
            return false;
        }
        if (declaredFileSize != 0 && pixelDataOffset >= declaredFileSize) {
            return false;
        }

        int colorPlanes;
        int bitsPerPixel;
        if (dibHeaderSize == 12) {
            colorPlanes = readUint16LE(buffer, 22);
            bitsPerPixel = readUint16LE(buffer, 24);
        } else if (dibHeaderSize >= 40 && dibHeaderSize <= 124) {
            if (buffer.length < 30) {
                return false;
            }
            colorPlanes = readUint16LE(buffer, 26);
            bitsPerPixel = readUint16LE(buffer, 28);
        } else {
            return false;
        }
        return colorPlanes == 1 && (bitsPerPixel == 1 || bitsPerPixel == 4 || bitsPerPixel == 8
                || bitsPerPixel == 16 || bitsPerPixel == 24 || bitsPerPixel == 32);
    }

    private static int readUint16LE(byte[] buffer, int offset) {
        return byteAt(buffer, offset) + (byteAt(buffer, offset + 1) << 8);
    }

    private static long readUint32BE(byte[] buffer, int offset) {
        return ((long) byteAt(buffer, offset) << 24)
                + (byteAt(buffer, offset + 1) << 16)
                + (byteAt(buffer, offset + 2) << 8)
                + byteAt(buffer, offset + 3);
    }

    private static long readUint32LE(byte[] buffer, int offset) {
        return byteAt(buffer, offset)
                + ((long) byteAt(buffer, offset + 1) << 8)
                + ((long) byteAt(buffer, offset + 2) << 16)
                + ((long) byteAt(buffer, offset + 3) << 24);
    }

    private static boolean startsWith(byte[] buffer, byte[] bytes) {
        if (buffer.length < bytes.length) {
            return false;
        }
        for (int i = 0; i < bytes.length; i++) {
            if (buffer[i] != bytes[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsWithAscii(byte[] buffer, int offset, String text) {
        if (buffer.length < offset + text.length()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (byteAt(buffer, offset + i) != text.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static int byteAt(byte[] buffer, int offset) {
        return offset >= 0 && offset < buffer.length ? buffer[offset] & 0xff : 0;
    }
}
