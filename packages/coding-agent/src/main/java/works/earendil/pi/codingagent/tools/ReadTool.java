package works.earendil.pi.codingagent.tools;

import works.earendil.pi.ai.model.Content;
import works.earendil.pi.codingagent.util.ImageProcessor;
import works.earendil.pi.codingagent.util.MimeUtils;
import works.earendil.pi.common.text.Truncation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ReadTool {
    private final Path cwd;
    private final boolean autoResizeImages;

    public ReadTool(Path cwd) {
        this(cwd, true);
    }

    public ReadTool(Path cwd, boolean autoResizeImages) {
        this.cwd = cwd;
        this.autoResizeImages = autoResizeImages;
    }

    public record Result(List<Content> content, Object details) {
    }

    public Truncation.Result read(String path, Truncation.Options options) throws IOException {
        Path target = PathUtils.resolveInside(cwd, path);
        String content = Files.readString(target, StandardCharsets.UTF_8);
        return Truncation.truncateHead(content, options);
    }

    public Result readContent(String path, Truncation.Options options) throws IOException {
        Path target = PathUtils.resolveInside(cwd, path);
        Optional<String> imageMimeType = MimeUtils.detectSupportedImageMimeTypeFromFile(target);
        if (imageMimeType.isPresent()) {
            byte[] bytes = Files.readAllBytes(target);
            String mimeType = imageMimeType.get();
            ImageProcessor.Result image = ImageProcessor.process(bytes, mimeType, autoResizeImages);
            if (!image.ok()) {
                return new Result(List.of(new Content.Text(image.message())), imageDetails(target, mimeType, bytes.length, image));
            }
            String text = image.hints().isEmpty()
                    ? "Read image file [" + image.mimeType() + "]"
                    : "Read image file [" + image.mimeType() + "]\n" + String.join("\n", image.hints());
            return new Result(
                    List.of(new Content.Text(text), new Content.Image(image.mimeType(), image.data(), null)),
                    imageDetails(target, mimeType, bytes.length, image));
        }
        Truncation.Result text = read(path, options);
        return new Result(List.of(new Content.Text(text.content())), text);
    }

    private Map<String, Object> imageDetails(Path target, String originalMimeType, int originalBytes,
                                             ImageProcessor.Result image) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("path", target.toString());
        details.put("mimeType", image.mimeType() == null ? originalMimeType : image.mimeType());
        details.put("originalMimeType", originalMimeType);
        details.put("bytes", image.bytes() == null ? originalBytes : image.bytes().length);
        details.put("originalBytes", originalBytes);
        details.put("image", true);
        details.put("autoResizeImages", autoResizeImages);
        if (image.originalWidth() > 0 && image.originalHeight() > 0) {
            details.put("originalWidth", image.originalWidth());
            details.put("originalHeight", image.originalHeight());
            details.put("width", image.width());
            details.put("height", image.height());
        }
        if (!image.hints().isEmpty()) {
            details.put("hints", image.hints());
        }
        if (!image.ok()) {
            details.put("omitted", true);
        }
        return Map.copyOf(details);
    }
}
