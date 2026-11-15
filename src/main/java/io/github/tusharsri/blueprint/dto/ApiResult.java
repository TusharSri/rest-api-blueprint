package io.github.tusharsri.blueprint.dto;

import java.time.Instant;
import java.util.List;

/**
 * Standard envelope for every response, with nested classes:
 * ApiResult -> data (T), meta (Meta), error (ErrorInfo -> details[FieldIssue]).
 */
public record ApiResult<T>(int status, String message, Instant timestamp, T data, Meta meta, ErrorInfo error) {

    public record Meta(Integer page, Integer size, Long totalElements, Integer totalPages, String apiVersion) {
        public static Meta ofVersion(String apiVersion) {
            return new Meta(null, null, null, null, apiVersion);
        }

        public static Meta ofPage(PageResult<?> page, String apiVersion) {
            return new Meta(page.page(), page.size(), page.totalElements(), page.totalPages(), apiVersion);
        }
    }

    public record ErrorInfo(String code, List<FieldIssue> details, String path, String traceId) {}

    public record FieldIssue(String field, String message) {}

    public static <T> ApiResult<T> success(int status, String message, T data, Meta meta) {
        return new ApiResult<>(status, message, Instant.now(), data, meta, null);
    }

    public static <T> ApiResult<T> ok(String message, T data, Meta meta) {
        return success(200, message, data, meta);
    }

    public static ApiResult<Void> failure(int status, String message, ErrorInfo error) {
        return new ApiResult<>(status, message, Instant.now(), null, null, error);
    }
}
