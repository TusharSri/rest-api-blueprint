package io.github.tusharsri.blueprint.controller;

import io.github.tusharsri.blueprint.dto.ApiResult;
import io.github.tusharsri.blueprint.dto.LoginRequest;
import io.github.tusharsri.blueprint.dto.TokenResponse;
import io.github.tusharsri.blueprint.service.AuthService;
import io.github.tusharsri.blueprint.util.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.BASE_PATH + "/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @SecurityRequirements // login itself needs no token
    @Operation(summary = "Login and receive a JWT", description = "Users: admin/admin123 (ADMIN), user/user123 (USER)")
    public ApiResult<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResult.ok("Login successful", authService.login(request.username(), request.password()), null);
    }
}
