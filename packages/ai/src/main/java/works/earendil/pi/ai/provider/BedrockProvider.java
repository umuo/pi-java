package works.earendil.pi.ai.provider;

import works.earendil.pi.ai.model.Content;
import works.earendil.pi.ai.model.Context;
import works.earendil.pi.ai.model.Message;
import works.earendil.pi.ai.model.Model;
import works.earendil.pi.ai.model.StopReason;
import works.earendil.pi.ai.model.Usage;
import works.earendil.pi.ai.stream.AssistantMessageEvent;
import works.earendil.pi.ai.stream.AssistantMessageEventStream;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class BedrockProvider implements Provider {
    private static final String ID = "amazon-bedrock";
    private static final String DEFAULT_API = "https://bedrock-runtime.us-east-1.amazonaws.com";

    private final List<Model> models = List.of(
            new Model(ID, "us.anthropic.claude-3-7-sonnet-20250219-v1:0", "Claude 3.7 Sonnet (Bedrock)", DEFAULT_API, 200000, 64000, true, true, Map.of()),
            new Model(ID, "us.anthropic.claude-3-5-sonnet-20241022-v2:0", "Claude 3.5 Sonnet v2 (Bedrock)", DEFAULT_API, 200000, 8192, true, true, Map.of()),
            new Model(ID, "us.anthropic.claude-3-5-haiku-20241022-v1:0", "Claude 3.5 Haiku (Bedrock)", DEFAULT_API, 200000, 8192, true, false, Map.of()),
            new Model(ID, "amazon.titan-text-premier-v1:0", "Amazon Titan Text Premier", DEFAULT_API, 32000, 4096, true, false, Map.of())
    );

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<Model> models() {
        return models;
    }

    @Override
    public AssistantMessageEventStream stream(Model model, Context context, StreamOptions options) {
        AssistantMessageEventStream stream = new AssistantMessageEventStream();
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(50);
                stream.emit(new AssistantMessageEvent.Start(ID, model.modelId()));

                String apiKey = options != null ? options.apiKey() : null;
                if (apiKey == null || apiKey.isBlank()) {
                    apiKey = System.getenv("AWS_ACCESS_KEY_ID");
                }

                if (apiKey == null || apiKey.isBlank()) {
                    stream.emit(new AssistantMessageEvent.Error("Missing AWS credentials for provider: " + ID, new IllegalStateException("Missing AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY")));
                    return;
                }

                Content.Text content = new Content.Text("Hello from AWS Bedrock " + model.modelId());
                stream.emit(new AssistantMessageEvent.ContentDelta(content));
                Usage usage = new Usage(15, 8, 0, 0, 0);
                stream.emit(new AssistantMessageEvent.UsageDelta(usage));
                stream.emit(new AssistantMessageEvent.End(new Message.Assistant(List.<Content>of(content), ID, model.modelId(), StopReason.STOP, usage, null, Instant.now())));
            } catch (Exception e) {
                stream.emit(new AssistantMessageEvent.Error(e.getMessage(), e));
            } finally {
                stream.close();
            }
        });
        return stream;
    }
}
