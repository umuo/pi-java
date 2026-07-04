package works.earendil.pi.codingagent.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import works.earendil.pi.ai.model.ImageGenModel;
import works.earendil.pi.ai.provider.ImageGenerationOptions;
import works.earendil.pi.ai.provider.ImageGenerationProvider;
import works.earendil.pi.ai.provider.ImageGenerationRegistry;
import works.earendil.pi.codingagent.core.AuthStorage;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ImageCommandTest {
    @TempDir
    Path tempDir;

    @Test
    void listsImageGenerationModels() {
        ImageGenerationRegistry registry = registry(new FakeImageProvider(null, null));

        String output = InteractiveModeRunner.handleImageCommand(tempDir, AuthStorage.inMemory(), "list", registry);

        assertThat(output).contains("Image generation")
                .contains("status: available")
                .contains("test/fake-image-model - Fake Image Model")
                .contains("aspect: 1:1")
                .contains("size: 512x512");
    }

    @Test
    void generatesImagesAndWritesBase64OutputsToDisk() throws Exception {
        AuthStorage authStorage = AuthStorage.inMemory();
        authStorage.set("test", new AuthStorage.ApiKeyCredential("stored-image-key", null));
        AtomicReference<ImageGenModel.Request> request = new AtomicReference<>();
        AtomicReference<ImageGenerationOptions> options = new AtomicReference<>();
        ImageGenerationRegistry registry = registry(new FakeImageProvider(request, options));

        String output = InteractiveModeRunner.handleImageCommand(tempDir, authStorage,
                "generate --model test/fake-image-model --out generated --aspect 1:1 --size 512x512 --n 2 draw a cat",
                registry);

        assertThat(output).contains("Image generation")
                .contains("status: generated")
                .contains("model: test/fake-image-model")
                .contains("prompt: draw a cat")
                .contains("files:")
                .contains("urls:")
                .contains("https://example.test/generated.png")
                .contains("revisedPrompt: revised prompt")
                .contains("images: 2");
        assertThat(request.get().prompt()).isEqualTo("draw a cat");
        assertThat(request.get().aspectRatio()).isEqualTo("1:1");
        assertThat(request.get().resolution()).isEqualTo("512x512");
        assertThat(request.get().n()).isEqualTo(2);
        assertThat(options.get().apiKey()).isEqualTo("stored-image-key");

        List<Path> generatedFiles;
        try (var stream = Files.list(tempDir.resolve("generated"))) {
            generatedFiles = stream.toList();
        }
        assertThat(generatedFiles).hasSize(1);
        assertThat(Files.readString(generatedFiles.getFirst(), StandardCharsets.UTF_8)).isEqualTo("fake-png");
    }

    @Test
    void reportsMissingProviderApiKey() {
        ImageGenerationRegistry registry = registry(new FakeImageProvider(null, null));

        String output = InteractiveModeRunner.handleImageCommand(tempDir, AuthStorage.inMemory(),
                "generate --model test/fake-image-model draw a cat", registry);

        assertThat(output).contains("Image generation")
                .contains("error: missing API key for provider: test")
                .contains("/login test <api-key>");
    }

    private static ImageGenerationRegistry registry(ImageGenerationProvider provider) {
        ImageGenerationRegistry registry = new ImageGenerationRegistry();
        registry.register(provider);
        return registry;
    }

    private static final class FakeImageProvider implements ImageGenerationProvider {
        private final AtomicReference<ImageGenModel.Request> request;
        private final AtomicReference<ImageGenerationOptions> options;

        private FakeImageProvider(AtomicReference<ImageGenModel.Request> request,
                                  AtomicReference<ImageGenerationOptions> options) {
            this.request = request;
            this.options = options;
        }

        @Override
        public String id() {
            return "test";
        }

        @Override
        public List<ImageGenModel> imageModels() {
            return List.of(new ImageGenModel("test", "fake-image-model", "Fake Image Model",
                    List.of("1:1"), List.of("512x512"), Map.of()));
        }

        @Override
        public ImageGenModel.Response generateImages(ImageGenModel model, ImageGenModel.Request request,
                                                     ImageGenerationOptions options) {
            if (this.request != null) {
                this.request.set(request);
            }
            if (this.options != null) {
                this.options.set(options);
            }
            return new ImageGenModel.Response(List.of(
                    new ImageGenModel.GeneratedImage(Base64.getEncoder().encodeToString("fake-png".getBytes(
                            StandardCharsets.UTF_8)), "image/png", null, "revised prompt"),
                    new ImageGenModel.GeneratedImage(null, null, "https://example.test/generated.png",
                            "revised prompt")
            ), model.provider(), model.modelId());
        }
    }
}
