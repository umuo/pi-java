package works.earendil.pi.server.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RadiusRegistration(
        long heartbeatIntervalMs,
        long expiresInMs
) {}
