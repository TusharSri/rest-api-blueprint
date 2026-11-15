package io.github.tusharsri.blueprint.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Type-safe binding for the "app.*" block in application.yml. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Idempotency idempotency, Repository repository) {
    public record Jwt(String secret, long ttlMinutes) {}
    public record Idempotency(long ttlMinutes) {}
    public record Repository(long simulatedLatencyMs) {}
}
