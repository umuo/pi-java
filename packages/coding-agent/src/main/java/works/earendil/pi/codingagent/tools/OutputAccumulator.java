package works.earendil.pi.codingagent.tools;

import works.earendil.pi.common.text.Truncation;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Incrementally tracks streaming output with bounded memory.
 */
public final class OutputAccumulator {
    private final int maxLines;
    private final int maxBytes;
    private final int maxRollingBytes;
    private final String tempFilePrefix;
    private final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE);

    private final List<byte[]> rawChunks = new ArrayList<>();
    private String tailText = "";
    private int tailBytes;
    private boolean tailStartsAtLineBoundary = true;
    private int totalRawBytes;
    private int totalDecodedBytes;
    private int completedLines;
    private int totalLines;
    private int currentLineBytes;
    private boolean hasOpenLine;
    private boolean finished;
    private Path tempFilePath;
    private byte[] pendingBytes = new byte[0];

    public OutputAccumulator() {
        this(new Options());
    }

    public OutputAccumulator(Options options) {
        this.maxLines = options.maxLines == null ? Truncation.DEFAULT_MAX_LINES : options.maxLines;
        this.maxBytes = options.maxBytes == null ? Truncation.DEFAULT_MAX_BYTES : options.maxBytes;
        this.maxRollingBytes = Math.max(this.maxBytes * 2, 1);
        this.tempFilePrefix = options.tempFilePrefix == null || options.tempFilePrefix.isBlank()
                ? "pi-output"
                : options.tempFilePrefix;
    }

    public void append(byte[] data) throws IOException {
        if (finished) {
            throw new IllegalStateException("Cannot append to a finished output accumulator");
        }
        if (data == null || data.length == 0) {
            return;
        }

        totalRawBytes += data.length;
        appendDecodedText(decode(data, false));

        if (tempFilePath != null || shouldUseTempFile()) {
            ensureTempFile();
            Files.write(tempFilePath, data, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } else {
            rawChunks.add(Arrays.copyOf(data, data.length));
        }
    }

    public void finish() throws IOException {
        if (finished) {
            return;
        }
        finished = true;
        appendDecodedText(decode(new byte[0], true));
        if (shouldUseTempFile()) {
            ensureTempFile();
        }
    }

    public Snapshot snapshot() throws IOException {
        return snapshot(false);
    }

    public Snapshot snapshot(boolean persistIfTruncated) throws IOException {
        Truncation.Result tailTruncation = Truncation.truncateTail(getSnapshotText(),
                new Truncation.Options(maxLines, maxBytes));
        boolean truncated = totalLines > maxLines || totalDecodedBytes > maxBytes;
        Truncation.Limit truncatedBy = null;
        if (truncated) {
            truncatedBy = tailTruncation.truncatedBy() != null
                    ? tailTruncation.truncatedBy()
                    : (totalDecodedBytes > maxBytes ? Truncation.Limit.BYTES : Truncation.Limit.LINES);
        }
        Truncation.Result truncation = new Truncation.Result(
                tailTruncation.content(),
                truncated,
                truncatedBy,
                totalLines,
                totalDecodedBytes,
                tailTruncation.outputLines(),
                tailTruncation.outputBytes(),
                tailTruncation.lastLinePartial(),
                tailTruncation.firstLineExceedsLimit(),
                maxLines,
                maxBytes);

        if (persistIfTruncated && truncation.truncated()) {
            ensureTempFile();
        }

        return new Snapshot(truncation.content(), truncation, tempFilePath);
    }

    public void closeTempFile() {
        // Files are written synchronously in Java, so there is no stream to close.
    }

    public int getLastLineBytes() {
        return currentLineBytes;
    }

    private String decode(byte[] data, boolean endOfInput) {
        try {
            StringBuilder decoded = new StringBuilder();
            ByteBuffer input = ByteBuffer.wrap(combinePending(data));
            CharBuffer output = CharBuffer.allocate(Math.max(32, data.length + 8));
            while (true) {
                var result = decoder.decode(input, output, endOfInput);
                output.flip();
                decoded.append(output);
                output.clear();
                if (result.isOverflow()) {
                    continue;
                }
                if (result.isError()) {
                    result.throwException();
                }
                break;
            }
            if (!endOfInput && input.hasRemaining()) {
                pendingBytes = new byte[input.remaining()];
                input.get(pendingBytes);
            } else {
                pendingBytes = new byte[0];
            }
            if (endOfInput) {
                while (true) {
                    var result = decoder.flush(output);
                    output.flip();
                    decoded.append(output);
                    output.clear();
                    if (!result.isOverflow()) {
                        break;
                    }
                }
            }
            return decoded.toString();
        } catch (CharacterCodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private byte[] combinePending(byte[] data) {
        if (pendingBytes.length == 0) {
            return data;
        }
        byte[] combined = Arrays.copyOf(pendingBytes, pendingBytes.length + data.length);
        System.arraycopy(data, 0, combined, pendingBytes.length, data.length);
        return combined;
    }

    private void appendDecodedText(String text) {
        if (text.isEmpty()) {
            return;
        }

        int bytes = byteLength(text);
        totalDecodedBytes += bytes;
        tailText += text;
        tailBytes += bytes;
        if (tailBytes > maxRollingBytes * 2) {
            trimTail();
        }

        int newlines = 0;
        int lastNewline = -1;
        for (int i = text.indexOf('\n'); i != -1; i = text.indexOf('\n', i + 1)) {
            newlines++;
            lastNewline = i;
        }
        if (newlines == 0) {
            currentLineBytes += bytes;
            hasOpenLine = true;
        } else {
            completedLines += newlines;
            String tail = text.substring(lastNewline + 1);
            currentLineBytes = byteLength(tail);
            hasOpenLine = !tail.isEmpty();
        }
        totalLines = completedLines + (hasOpenLine ? 1 : 0);
    }

    private void trimTail() {
        byte[] buffer = tailText.getBytes(StandardCharsets.UTF_8);
        if (buffer.length <= maxRollingBytes) {
            tailBytes = buffer.length;
            return;
        }

        int start = buffer.length - maxRollingBytes;
        while (start < buffer.length && (buffer[start] & 0xc0) == 0x80) {
            start++;
        }

        tailStartsAtLineBoundary = start == 0 ? tailStartsAtLineBoundary : buffer[start - 1] == 0x0a;
        tailText = new String(buffer, start, buffer.length - start, StandardCharsets.UTF_8);
        tailBytes = byteLength(tailText);
    }

    private String getSnapshotText() {
        if (tailStartsAtLineBoundary) {
            return tailText;
        }
        int firstNewline = tailText.indexOf('\n');
        return firstNewline == -1 ? tailText : tailText.substring(firstNewline + 1);
    }

    private boolean shouldUseTempFile() {
        return totalRawBytes > maxBytes || totalDecodedBytes > maxBytes || totalLines > maxLines;
    }

    private void ensureTempFile() throws IOException {
        if (tempFilePath != null) {
            return;
        }
        tempFilePath = Path.of(System.getProperty("java.io.tmpdir"),
                tempFilePrefix + "-" + UUID.randomUUID() + ".log");
        for (byte[] chunk : rawChunks) {
            Files.write(tempFilePath, chunk, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        rawChunks.clear();
    }

    private static int byteLength(String text) {
        return text.getBytes(StandardCharsets.UTF_8).length;
    }

    public static final class Options {
        private Integer maxLines;
        private Integer maxBytes;
        private String tempFilePrefix;

        public Options maxLines(int maxLines) {
            this.maxLines = maxLines;
            return this;
        }

        public Options maxBytes(int maxBytes) {
            this.maxBytes = maxBytes;
            return this;
        }

        public Options tempFilePrefix(String tempFilePrefix) {
            this.tempFilePrefix = tempFilePrefix;
            return this;
        }
    }

    public record Snapshot(String content, Truncation.Result truncation, Path fullOutputPath) {
    }
}
