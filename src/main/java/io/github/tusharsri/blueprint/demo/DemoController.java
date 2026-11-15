package io.github.tusharsri.blueprint.demo;

import io.github.tusharsri.blueprint.dto.ApiResult;
import io.github.tusharsri.blueprint.exception.BadRequestException;
import io.github.tusharsri.blueprint.util.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.server.ResponseStatusException;

/** Demo-only endpoints (public). Delete this package before using the blueprint in production. */
@RestController
@RequestMapping(ApiConstants.BASE_PATH + "/demo")
@Tag(name = "Demo (remove in production)")
@SecurityRequirements
public class DemoController {

    private static final Set<Integer> CODES =
            Set.of(200, 201, 202, 204, 400, 401, 403, 404, 405, 406, 409, 415, 422, 429, 500, 503, 504);
    private static final long UPSTREAM_TIMEOUT_SECONDS = 3;

    @GetMapping("/status/{code}")
    @Operation(summary = "Return the given HTTP status with the standard error format")
    public ResponseEntity<ApiResult<Void>> status(@PathVariable int code) {
        if (!CODES.contains(code)) {
            throw new BadRequestException("Unsupported demo code " + code + ". Supported: " + new TreeSet<>(CODES));
        }
        HttpStatus status = HttpStatus.valueOf(code);
        if (code == 204) {
            return ResponseEntity.noContent().build();
        }
        if (code < 300) {
            return ResponseEntity.status(status)
                    .body(ApiResult.<Void>success(code, "Demo " + code + " " + status.getReasonPhrase(), null, null));
        }
        throw new ResponseStatusException(status, "Demo response for HTTP " + code);
    }

    @GetMapping("/slow")
    @Operation(summary = "Simulated slow upstream call", description = "delaySeconds > 3 triggers a 504 (upstream timeout)")
    public CompletableFuture<ApiResult<String>> slow(
            @RequestParam(defaultValue = "1") @Min(0) @Max(30) int delaySeconds) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                TimeUnit.SECONDS.sleep(delaySeconds);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            return ApiResult.ok("Finished after " + delaySeconds + "s", "done", null);
        }).orTimeout(UPSTREAM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @GetMapping("/server-timeout")
    @Operation(summary = "Never completes: the server-side async timeout (spring.mvc.async.request-timeout) returns 503")
    public DeferredResult<ApiResult<String>> serverTimeout() {
        return new DeferredResult<>();
    }

    @GetMapping("/boom")
    @Operation(summary = "Throws an unexpected exception: the catch-all handler returns 500")
    public ApiResult<Void> boom() {
        throw new IllegalStateException("Simulated unexpected failure");
    }
}
