package io.github.tusharsri.blueprint.unit.exception;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.tusharsri.blueprint.dto.ApiResult;
import io.github.tusharsri.blueprint.dto.ProductRequest;
import io.github.tusharsri.blueprint.exception.ConflictException;
import io.github.tusharsri.blueprint.exception.GlobalExceptionHandler;
import io.github.tusharsri.blueprint.exception.ResourceNotFoundException;
import io.github.tusharsri.blueprint.util.ApiConstants;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.server.ResponseStatusException;

/** Verifies the status code and the standard error envelope for every exception type, without Spring. */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    private static MockHttpServletRequest request() {
        return new MockHttpServletRequest("GET", "/api/test");
    }

    private static ApiResult<?> envelope(ResponseEntity<?> response) {
        assertThat(response.getBody()).isInstanceOf(ApiResult.class);
        return (ApiResult<?>) response.getBody();
    }

    private static void assertError(ResponseEntity<?> response, HttpStatus status, String code) {
        assertThat(response.getStatusCode().value()).isEqualTo(status.value());
        ApiResult<?> body = envelope(response);
        assertThat(body.status()).isEqualTo(status.value());
        assertThat(body.data()).isNull();
        assertThat(body.timestamp()).isNotNull();
        assertThat(body.error()).isNotNull();
        assertThat(body.error().code()).isEqualTo(code);
        assertThat(body.error().path()).isEqualTo("/api/test");
    }

    @Test
    void business_exceptions_map_to_their_own_status_and_code() {
        assertError(handler.handleApi(new ConflictException("duplicate"), request()), HttpStatus.CONFLICT, "CONFLICT");
        assertError(handler.handleApi(new ResourceNotFoundException("Product", 7), request()),
                HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND");
    }

    @Test
    void constraint_violations_return_400_with_field_details() {
        Set<ConstraintViolation<ProductRequest>> violations = Validation.buildDefaultValidatorFactory().getValidator()
                .validate(new ProductRequest("", null, new BigDecimal("1"), null, "ok-sku", null));

        ResponseEntity<ApiResult<Void>> response = handler.handleConstraint(new ConstraintViolationException(violations), request());

        assertError(response, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertThat(response.getBody().error().details()).extracting(ApiResult.FieldIssue::field)
                .contains("name", "category");
    }

    @Test
    void authentication_failure_maps_to_401() {
        ResponseEntity<ApiResult<Void>> response = handler.handleAuthentication(new BadCredentialsException("bad credentials"), request());
        assertError(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        assertThat(response.getBody().message()).isEqualTo("bad credentials");
    }

    @Test
    void access_denied_maps_to_403_without_leaking_the_reason() {
        ResponseEntity<ApiResult<Void>> response = handler.handleAccessDenied(new AccessDeniedException("internal rule X"), request());
        assertError(response, HttpStatus.FORBIDDEN, "FORBIDDEN");
        assertThat(response.getBody().message()).doesNotContain("internal rule X");
    }

    @Test
    void timeout_exception_maps_to_504() {
        assertError(handler.handleTimeout(new TimeoutException("slow"), request()), HttpStatus.GATEWAY_TIMEOUT, "GATEWAY_TIMEOUT");
    }

    @Test
    void upstream_read_timeout_maps_to_504() {
        ResourceAccessException ex = new ResourceAccessException("I/O error", new SocketTimeoutException("Read timed out"));
        assertError(handler.handleUpstream(ex, request()), HttpStatus.GATEWAY_TIMEOUT, "UPSTREAM_TIMEOUT");
    }

    @Test
    void unreachable_upstream_maps_to_502() {
        ResourceAccessException ex = new ResourceAccessException("I/O error", new ConnectException("Connection refused"));
        assertError(handler.handleUpstream(ex, request()), HttpStatus.BAD_GATEWAY, "UPSTREAM_UNAVAILABLE");
    }

    @Test
    void unexpected_exceptions_map_to_500_and_never_leak_internals() {
        ResponseEntity<ApiResult<Void>> response = handler.handleAny(new IllegalStateException("db password is hunter2"), request());

        assertError(response, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR");
        assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred");
        assertThat(response.getBody().message()).doesNotContain("hunter2");
    }

    @Test
    void response_status_exceptions_keep_their_status_in_the_catch_all() {
        ResponseEntity<ApiResult<Void>> response =
                handler.handleAny(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "slow down"), request());

        assertError(response, HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS");
        assertThat(response.getBody().message()).isEqualTo("slow down");
    }

    @Test
    void standard_spring_mvc_exceptions_use_the_same_envelope() throws Exception {
        ServletWebRequest web = new ServletWebRequest(request(), new MockHttpServletResponse());

        ResponseEntity<Object> methodNotAllowed = handler.handleException(new HttpRequestMethodNotSupportedException("POST"), web);
        assertError(methodNotAllowed, HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED");

        ResponseEntity<Object> asyncTimeout = handler.handleException(new AsyncRequestTimeoutException(), web);
        assertError(asyncTimeout, HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE");
    }

    @Test
    void error_body_includes_the_trace_id_from_the_log_context() {
        MDC.put(ApiConstants.MDC_TRACE_ID, "trace-abc-123");

        ResponseEntity<ApiResult<Void>> response = handler.handleApi(new ConflictException("x"), request());

        assertThat(response.getBody().error().traceId()).isEqualTo("trace-abc-123");
    }
}
