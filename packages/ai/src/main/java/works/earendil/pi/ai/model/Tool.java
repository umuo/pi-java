package works.earendil.pi.ai.model;

import com.fasterxml.jackson.databind.JsonNode;

public record Tool(
        String name,
        String description,
        JsonNode parameters,
        String promptGuidelines
) {
}
