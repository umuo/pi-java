package works.earendil.pi.ai.provider;

import works.earendil.pi.ai.model.Context;
import works.earendil.pi.ai.model.Model;
import works.earendil.pi.ai.stream.AssistantMessageEventStream;

import java.util.List;

public interface Provider {
    String id();

    List<Model> models();

    AssistantMessageEventStream stream(Model model, Context context, StreamOptions options);
}
