package io.github.tusharsri.blueprint.util;

/** Single place for the API base path and header names. Change BASE_PATH to "/api/v1", a service name, or "" if needed. */
public final class ApiConstants {
    private ApiConstants() {}

    public static final String BASE_PATH = "/api";
    public static final String VERSION_HEADER = "X-API-Version";
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String IDEMPOTENT_REPLAYED_HEADER = "Idempotent-Replayed";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String MDC_TRACE_ID = "traceId";
}
