package works.earendil.pi.codingagent.cli;

import works.earendil.pi.ai.model.Content;
import works.earendil.pi.codingagent.tools.PathUtils;
import works.earendil.pi.codingagent.util.ImageProcessor;
import works.earendil.pi.codingagent.util.MimeUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class FileArgumentProcessor {
    private FileArgumentProcessor() {
    }

    static void process(CliArgs args, Path cwd, boolean autoResizeImages) throws IOException {
        if (args == null || args.messages == null || args.messages.isEmpty()) {
            return;
        }

        List<String> fileArgs = new ArrayList<>();
        List<String> prompts = new ArrayList<>();
        for (String message : args.messages) {
            if (message != null && message.startsWith("@") && message.length() > 1) {
                fileArgs.add(message);
            } else {
                prompts.add(message);
            }
        }
        if (fileArgs.isEmpty()) {
            return;
        }

        ProcessedFiles files = processFiles(fileArgs, cwd, autoResizeImages);
        List<String> messages = new ArrayList<>();
        if (!files.text().isEmpty() || !files.images().isEmpty()) {
            String initialMessage = files.text();
            if (!prompts.isEmpty()) {
                initialMessage += prompts.removeFirst();
            }
            messages.add(initialMessage);
        }
        messages.addAll(prompts);
        args.messages = messages;
        args.initialImages = files.images();
    }

    private static ProcessedFiles processFiles(List<String> fileArgs, Path cwd, boolean autoResizeImages) throws IOException {
        StringBuilder text = new StringBuilder();
        List<Content.Image> images = new ArrayList<>();

        for (String fileArg : fileArgs) {
            Path path = PathUtils.resolvePath(fileArg, cwd, PathUtils.PathInputOptions.cli());
            if (!Files.exists(path)) {
                throw new IllegalArgumentException("File not found: " + path);
            }
            if (!Files.isRegularFile(path)) {
                throw new IllegalArgumentException("Not a file: " + path);
            }
            if (Files.size(path) == 0) {
                continue;
            }

            Optional<String> mimeType = MimeUtils.detectSupportedImageMimeTypeFromFile(path);
            if (mimeType.isPresent()) {
                byte[] bytes = Files.readAllBytes(path);
                ImageProcessor.Result processed = ImageProcessor.process(bytes, mimeType.get(), autoResizeImages);
                if (!processed.ok()) {
                    text.append("<file name=\"").append(path).append("\">")
                            .append(processed.message())
                            .append("</file>\n");
                    continue;
                }
                images.add(new Content.Image(processed.mimeType(), processed.data(), null));
                text.append("<file name=\"").append(path).append("\">");
                if (!processed.hints().isEmpty()) {
                    text.append(String.join("\n", processed.hints()));
                }
                text.append("</file>\n");
            } else {
                String content = Files.readString(path, StandardCharsets.UTF_8);
                text.append("<file name=\"").append(path).append("\">\n")
                        .append(content)
                        .append("\n</file>\n");
            }
        }

        return new ProcessedFiles(text.toString(), images);
    }

    private record ProcessedFiles(String text, List<Content.Image> images) {
    }
}
