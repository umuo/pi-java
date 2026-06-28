package works.earendil.pi.codingagent.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import works.earendil.pi.common.json.JsonCodec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SmallUtilsTest {
    @TempDir
    Path tempDir;

    @Test
    void stripsJsonCommentsAndTrailingCommas() {
        String json = """
                {
                  "url": "https://example.com//kept",
                  // removed
                  "items": [1, 2,],
                }
                """;

        assertThat(JsonCodec.parse(JsonUtils.stripJsonComments(json)).path("items")).hasSize(2);
        assertThat(JsonUtils.stripJsonComments(json)).contains("https://example.com//kept");
    }

    @Test
    void decodesHtmlEntities() {
        assertThat(HtmlUtils.decodeHtmlEntity("amp")).contains("&");
        assertThat(HtmlUtils.decodeHtmlEntity("#x1F600")).contains("\uD83D\uDE00");
        assertThat(HtmlUtils.decodeHtmlEntityAt("a &lt; b", 2))
                .hasValue(new HtmlUtils.DecodedHtmlEntity("<", 4));
        assertThat(HtmlUtils.decodeHtmlEntity("unknown")).isEmpty();
    }

    @Test
    void detectsSupportedImageMimeTypes() throws Exception {
        byte[] png = new byte[]{
                (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
                0, 0, 0, 13, 'I', 'H', 'D', 'R'
        };
        byte[] apng = new byte[]{
                (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
                0, 0, 0, 0, 'a', 'c', 'T', 'L'
        };
        byte[] webp = "RIFFxxxxWEBP".getBytes();
        byte[] bmp = new byte[30];
        bmp[0] = 'B';
        bmp[1] = 'M';
        bmp[10] = 26;
        bmp[14] = 12;
        bmp[22] = 1;
        bmp[24] = 24;
        Path file = tempDir.resolve("image.webp");
        Files.write(file, webp);

        assertThat(MimeUtils.detectSupportedImageMimeType(new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0}))
                .contains("image/jpeg");
        assertThat(MimeUtils.detectSupportedImageMimeType(png)).contains("image/png");
        assertThat(MimeUtils.detectSupportedImageMimeType(apng)).isEmpty();
        assertThat(MimeUtils.detectSupportedImageMimeType("GIF89a".getBytes())).contains("image/gif");
        assertThat(MimeUtils.detectSupportedImageMimeTypeFromFile(file)).contains("image/webp");
        assertThat(MimeUtils.detectSupportedImageMimeType(bmp)).contains("image/bmp");
    }

    @Test
    void sleepCompletesAndCanBeAborted() throws Exception {
        Sleep.sleep(Duration.ofMillis(1)).get(1, TimeUnit.SECONDS);
        CompletableFuture<Void> abort = new CompletableFuture<>();
        CompletableFuture<Void> sleeping = Sleep.sleep(Duration.ofSeconds(5), abort);

        abort.complete(null);

        assertThatThrownBy(() -> sleeping.get(1, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }
}
