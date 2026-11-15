package io.github.tusharsri.blueprint.exception;

import io.github.tusharsri.blueprint.dto.ApiResult;
import io.github.tusharsri.blueprint.util.ApiConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * One place where every error becomes an ApiResult.
 * The parent class already maps the standard Spring MVC exceptions (404, 405, 406, 415, malformed JSON,
 * missing params, type mismatch, async timeout 503, invalid API version ...) and funnels them into
 * handleExceptionInternal(), which we override to emit our own format.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ---- our own exceptions ----
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResult<Void>> handleApi(ApiException ex, HttpServletRequest req) {
        return build(ex.getStatus(), ex.getCode(), ex.getMessage(), List.of(), req.getRequestURI());
    }

    // ---- bean validation on @RequestParam/@PathVariable (proxy-based) ----
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResult<Void>> handleConstraint(ConstraintViolationException ex, HttpServletRequest req) {
        List<ApiResult.FieldIssue> details = ex.getConstraintViolations().stream()
                .map(v -> new ApiResult.FieldIssue(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", details, req.getRequestURI());
    }

    // ---- security ----
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResult<Void>> handleAuthentication(AuthenticationException ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", ex.getMessage(), List.of(), req.getRequestURI());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResult<Void>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action",
                List.of(), req.getRequestURI());
    }

    // ---- timeouts ----
    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<ApiResult<Void>> handleTimeout(TimeoutException ex, HttpServletRequest req) {
        return build(HttpStatus.GATEWAY_TIMEOUT, "GATEWAY_TIMEOUT", "Operation timed out", List.of(), req.getRequestURI());
    }

    /** RestClient/RestTemplate I/O failure when calling another service. */
    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ApiResult<Void>> handleUpstream(ResourceAccessException ex, HttpServletRequest req) {
        Throwable cause = ex.getCause();
        boolean timeout = cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException;
        return timeout
                ? build(HttpStatus.GATEWAY_TIMEOUT, "UPSTREAM_TIMEOUT", "Upstream service timed out", List.of(), req.getRequestURI())
                : build(HttpStatus.BAD_GATEWAY, "UPSTREAM_UNAVAILABLE", "Upstream service is unreachable", List.of(), req.getRequestURI());
    }

    // ---- catch-all ----
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleAny(Exception ex, HttpServletRequest req) {
        if (ex instanceof ErrorResponse er) { // e.g. ResponseStatusException, versioning errors
            HttpStatusCode status = er.getStatusCode();
            return build(status, codeFor(status), er.getBody().getDetail(), List.of(), req.getRequestURI());
        }
        log.error("Unhandled exception on {}", req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred",
                List.of(), req.getRequestURI());
    }

    // ---- overrides for the standard Spring MVC exceptions ----
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ApiResult.FieldIssue> details = new ArrayList<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(f -> details.add(new ApiResult.FieldIssue(f.getField(), f.getDefaultMessage())));
        ex.getBindingResult().getGlobalErrors()
                .forEach(g -> details.add(new ApiResult.FieldIssue(g.getObjectName(), g.getDefaultMessage())));
        return ResponseEntity.status(status).headers(headers)
                .body(body(status, "VALIDATION_FAILED", "Request validation failed", details, pathOf(request)));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        String message = (body instanceof ProblemDetail pd && pd.getDetail() != null) ? pd.getDetail() : ex.getMessage();
        if (statusCode.is5xxServerError()) {
            log.warn("Server error {} on {}: {}", statusCode.value(), pathOf(request), ex.toString());
        }
        return ResponseEntity.status(statusCode).headers(headers)
                .body(body(statusCode, codeFor(statusCode), message, List.of(), pathOf(request)));
    }

    // ---- helpers ----
    private static ResponseEntity<ApiResult<Void>> build(HttpStatusCode status, String code, String message,
            List<ApiResult.FieldIssue> details, String path) {
        return ResponseEntity.status(status).body(body(status, code, message, details, path));
    }

    private static ApiResult<Void> body(HttpStatusCode status, String code, String message,
            List<ApiResult.FieldIssue> details, String path) {
        var error = new ApiResult.ErrorInfo(code, details, path, MDC.get(ApiConstants.MDC_TRACE_ID));
        return ApiResult.failure(status.value(), message, error);
    }

    private static String codeFor(HttpStatusCode status) {
        return status instanceof HttpStatus hs ? hs.name() : "HTTP_" + status.value();
    }

    private static String pathOf(WebRequest request) {
        return request instanceof ServletWebRequest swr ? swr.getRequest().getRequestURI() : "";
    }
}
