package works.earendil.pi.ai.provider;

import works.earendil.pi.ai.model.ImageGenModel;

import java.util.List;

public interface ImageGenerationProvider {
    String id();

    List<ImageGenModel> imageModels();

    default List<ImageGenModel> refreshImageModels() {
        return imageModels();
    }

    ImageGenModel.Response generateImages(ImageGenModel model, ImageGenModel.Request request,
                                          ImageGenerationOptions options);
}
