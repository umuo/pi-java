package works.earendil.pi.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import works.earendil.pi.ai.model.ImageGenModel;
import works.earendil.pi.common.json.JsonCodec;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ImageGenerationProvidersTest {
    @Test
    void defaultRegistryIncludesOpenRouterImageModels() {
        ImageGenerationRegistry registry = new ImageGenerationRegistry();
        registry.registerDefaults();

        assertThat(registry.provider("openrouter")).hasValueSatisfying(provider ->
                assertThat(provider).isInstanceOf(OpenRouterImagesProvider.class));
        assertThat(registry.imageModels())
                .extracting(ImageGenModel::modelId)
                .contains("google/gemini-3.1-flash-image-preview", "black-forest-labs/flux.2-pro");
        assertThat(registry.findModel("openrouter", "black-forest-labs/flux.2-pro"))
                .hasValueSatisfying(model ->
                        assertThat(model.defaultOptions()).containsEntry("api", "openrouter-images"));
    }

    @Test
    void openRouterImagesBuildsRequestBodyAndParsesImages() throws Exception {
        ImageGenModel model = testModel("https://example.test/api");
        ImageGenModel.Request request = new ImageGenModel.Request("Generate a dog", "1:1", "1024x1024", 2,
                Map.of("seed", 42));

        JsonNode body = OpenRouterImagesProvider.buildRequestBody(model, request);

        assertThat(body.path("model").asText()).isEqualTo("black-forest-labs/flux.2-pro");
        assertThat(body.path("stream").asBoolean()).isFalse();
        List<String> modalities = new ArrayList<>();
        body.path("modalities").forEach(node -> modalities.add(node.asText()));
        assertThat(modalities).containsExactly("image", "text");
        assertThat(body.at("/messages/0/content/0/type").asText()).isEqualTo("text");
        assertThat(body.at("/messages/0/content/0/text").asText()).isEqualTo("Generate a dog");
        assertThat(body.path("aspect_ratio").asText()).isEqualTo("1:1");
        assertThat(body.path("size").asText()).isEqualTo("1024x1024");
        assertThat(body.path("n").asInt()).isEqualTo(2);
        assertThat(body.path("seed").asInt()).isEqualTo(42);

        ImageGenModel.Response response = OpenRouterImagesProvider.parseResponse(model, JsonCodec.parse("""
                {
                  "id": "img-1",
                  "choices": [
                    {
                      "message": {
                        "content": "Here is your image.",
                        "images": [
                          { "image_url": "data:image/png;base64,ZmFrZS1wbmc=" },
                          { "image_url": { "url": "https://cdn.example/image.webp" } }
                        ]
                      }
                    }
                  ]
                }
                """));

        assertThat(response.provider()).isEqualTo("openrouter");
        assertThat(response.modelId()).isEqualTo("black-forest-labs/flux.2-pro");
        assertThat(response.images()).containsExactly(
                new ImageGenModel.GeneratedImage("ZmFrZS1wbmc=", "image/png", null, "Here is your image."),
                new ImageGenModel.GeneratedImage(null, null, "https://cdn.example/image.webp",
                        "Here is your image.")
        );
    }

    @Test
    void openRouterImagesGenerateImagesCallsHttpEndpoint() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = """
                    {
                      "id": "img-1",
                      "choices": [
                        {
                          "message": {
                            "content": "Generated.",
                            "images": [{ "image_url": "data:image/png;base64,ZmFrZS1wbmc=" }]
                          }
                        }
                      ]
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(bytes);
            }
        });
        server.start();
        try {
            ImageGenModel model = testModel("http://127.0.0.1:" + server.getAddress().getPort());
            OpenRouterImagesProvider provider = new OpenRouterImagesProvider(List.of(model));
            ImageGenerationOptions options = new ImageGenerationOptions("test-key", Map.of("X-Test", "yes"),
                    Duration.ofSeconds(5), 0, Map.of(), Map.of());

            ImageGenModel.Response response = provider.generateImages(model,
                    new ImageGenModel.Request("Generate a dog", null, null, 1, Map.of()), options);

            assertThat(authorization.get()).isEqualTo("Bearer test-key");
            assertThat(JsonCodec.parse(requestBody.get()).at("/messages/0/content/0/text").asText())
                    .isEqualTo("Generate a dog");
            assertThat(response.images()).containsExactly(
                    new ImageGenModel.GeneratedImage("ZmFrZS1wbmc=", "image/png", null, "Generated.")
            );
        } finally {
            server.stop(0);
        }
    }

    private static ImageGenModel testModel(String baseUrl) {
        return new ImageGenModel("openrouter", "black-forest-labs/flux.2-pro", "FLUX.2 Pro",
                List.of("1:1"), List.of("1024x1024"), Map.of("baseUrl", baseUrl, "api", "openrouter-images"));
    }
}
