package works.earendil.pi.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RadiusRegistration(
        long heartbeatIntervalMs,
        long expiresInMs
) {}
